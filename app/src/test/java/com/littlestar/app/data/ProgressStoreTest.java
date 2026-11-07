package com.littlestar.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.littlestar.app.AppExecutors;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.data.store.ProgressStore;
import com.littlestar.app.data.repo.ProgressRepository;
import com.littlestar.app.domain.QuizGenerator;
import com.littlestar.app.domain.ReviewScheduler;
import com.littlestar.app.domain.SessionBuilder;
import com.littlestar.app.util.FakeClock;
import com.littlestar.app.util.FileStateFile;
import com.littlestar.app.util.TestContent;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** ProgressStore 用临时目录在 JVM 上直接测（同步执行器）。 */
public final class ProgressStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 10, 21, 8, 0);

    private ProgressStore newStore(FileStateFile file, FakeClock clock, long debounceMs) {
        return new ProgressStore(file, new Gson(), clock,
                new AppExecutors(Runnable::run, Runnable::run), debounceMs);
    }

    @Test
    public void writeThenReload_contentMatches() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        FakeClock clock = new FakeClock(T1);
        ProgressStore store = newStore(file, clock, 100);
        store.ensureLoaded();
        store.update(state -> {
            ProgressState.WordProgress wp = new ProgressState.WordProgress();
            wp.learnedAt = 123L;
            wp.box = 2;
            state.words.put("yue", wp);
        });
        store.flushNow();

        ProgressStore store2 = newStore(file, clock, 100);
        store2.ensureLoaded();
        final ProgressState[] holder = new ProgressState[1];
        store2.read(s -> s, s -> holder[0] = s);
        assertEquals(Long.valueOf(123L), holder[0].words.get("yue").learnedAt);
        assertEquals(2, holder[0].words.get("yue").box);
    }

    @Test
    public void debouncedSave_multipleUpdatesOneWrite() throws Exception {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        FakeClock clock = new FakeClock(T1);
        ProgressStore store = newStore(file, clock, 300);
        store.ensureLoaded();
        int writesAfterLoad = file.writeCount;
        for (int i = 0; i < 5; i++) {
            final int n = i;
            store.update(state -> state.nextSessionId = n + 2);
        }
        Thread.sleep(700);   // 等防抖落盘
        assertEquals(writesAfterLoad + 1, file.writeCount);   // 5 次 update 只写 1 次
    }

    @Test
    public void flushNow_writesImmediately() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        FakeClock clock = new FakeClock(T1);
        ProgressStore store = newStore(file, clock, 60_000);   // 长防抖
        store.ensureLoaded();
        int before = file.writeCount;
        store.update(state -> state.nextSessionId = 99);
        store.flushNow();
        assertEquals(before + 1, file.writeCount);
    }

    @Test
    public void corruptMain_fallsBackToBackup() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        FakeClock clock = new FakeClock(T1);
        // 先造一个合法库并备份
        ProgressStore store = newStore(file, clock, 50);
        store.ensureLoaded();
        store.update(state -> state.nextSessionId = 42);
        store.flushNow();
        file.writeBackup(file.read());
        // 写坏主文件
        file.write("{ not json !!!");
        file.writeCount = 0;

        ProgressStore store2 = newStore(file, clock, 50);
        store2.ensureLoaded();
        final long[] id = new long[1];
        store2.read(s -> s.nextSessionId, v -> id[0] = v);
        assertEquals(42, id[0]);
        // 坏文件被改名保留
        boolean renamed = false;
        for (File f : tmp.getRoot().listFiles()) {
            if (f.getName().startsWith("progress.json.corrupt-")) {
                renamed = true;
            }
        }
        assertTrue("坏文件应改名保留", renamed);
    }

    @Test
    public void nullSessionsInMain_fallsBackToBackup() {
        assertInvalidMainRestoresBackup("{\"schemaVersion\":3,\"sessions\":null}");
    }

    @Test
    public void nullSessionInMain_fallsBackToBackup() {
        assertInvalidMainRestoresBackup("{\"schemaVersion\":3,\"sessions\":[null]}");
    }

    @Test
    public void nullDailyPlansInMain_fallsBackToBackup() {
        assertInvalidMainRestoresBackup("{\"schemaVersion\":3,\"dailyPlans\":null}");
    }

    @Test
    public void nullWordsInMain_fallsBackToBackup() {
        assertInvalidMainRestoresBackup("{\"schemaVersion\":3,\"words\":null}");
    }

    @Test
    public void nullWordProgressInMain_fallsBackToBackup() {
        assertInvalidMainRestoresBackup("{\"schemaVersion\":3,\"words\":{\"mao\":null}}");
    }

    @Test
    public void nullSessionNewWordsInMain_fallsBackToBackup() {
        assertInvalidMainRestoresBackup("{\"schemaVersion\":3,\"sessions\":[{\"newWordIds\":null}]}");
    }

    @Test
    public void nullSessionQuizWordsInMain_fallsBackToBackup() {
        assertInvalidMainRestoresBackup("{\"schemaVersion\":3,\"sessions\":[{\"quizWordIds\":null}]}");
    }

    @Test
    public void nullAnswerInMain_fallsBackToBackup() {
        assertInvalidMainRestoresBackup("{\"schemaVersion\":3,\"sessions\":[{\"answers\":[null]}]}");
    }

    @Test
    public void nullPlanWords_recoversBeforeStartingAndCompletingTwoSessions() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        file.write("""
                {"schemaVersion":3,"nextSessionId":17,
                 "dailyPlans":{"2026-10-21":{"goal":3,"newWordIds":null}}}
                """);
        file.writeBackup("""
                {"schemaVersion":3,"nextSessionId":42,
                 "words":{"niu":{"learnedAt":123,"box":2}},
                 "dailyPlans":{"2026-10-21":{"goal":3,"newWordIds":["mao","gou","niao"]}}}
                """);
        FakeClock clock = new FakeClock(T1);
        var content = TestContent.load();
        SessionBuilder builder = new SessionBuilder(content, new ReviewScheduler(), clock, new Random(7));
        ProgressStore store = newStore(file, clock, 60_000);
        ProgressState.Session[] created = new ProgressState.Session[1];
        store.update(state -> created[0] = builder.buildDailySession(state, 10));
        ProgressState.Session first = created[0];
        assertEquals(42, first.id);
        assertEquals(List.of("mao", "gou", "niao"), first.newWordIds);
        assertEquals(4, first.total);
        ProgressRepository progress = new ProgressRepository(store, content, clock);
        for (String id : first.quizWordIds) {
            progress.recordAnswer(first.id, id, content.wordById(id).hanzi, true, true);
        }
        progress.completeSession(first.id);

        clock.advance(Duration.ofMinutes(1));
        ProgressStore reloaded = newStore(file, clock, 60_000);
        reloaded.update(state -> created[0] = builder.buildDailySession(state, 10));
        ProgressState.Session second = created[0];
        assertEquals(43, second.id);
        assertEquals(SessionBuilder.KIND_REVIEW, second.kind);
        assertTrue(second.newWordIds.isEmpty());
        assertEquals(4, second.total);
        ProgressRepository continued = new ProgressRepository(reloaded, content, clock);
        QuizGenerator generator = new QuizGenerator(content, new Random(7));
        Map<String, String> wrongChoices = new HashMap<>();
        for (String id : second.quizWordIds) {
            var question = generator.generate(content.wordById(id));
            String chosen = question.options().get((question.correctIndex() + 1)
                    % question.options().size());
            wrongChoices.put(id, chosen);
            continued.recordAnswer(second.id, id, chosen, false, true);
        }
        continued.completeSession(second.id);

        ProgressStore finalReload = newStore(file, clock, 60_000);
        finalReload.read(state -> state, state -> {
            assertEquals(44, state.nextSessionId);
            assertEquals(2, state.sessions.size());
            assertEquals(3, state.dailyPlans.get("2026-10-21").goal);
            assertEquals(3, SessionBuilder.learnedTodayCount(state,
                    state.dailyPlans.get("2026-10-21")));
            assertEquals(Long.valueOf(123), state.words.get("niu").learnedAt);
            for (int i = 0; i < state.sessions.size(); i++) {
                ProgressState.Session session = state.sessions.get(i);
                assertEquals(4, session.total);
                assertEquals(4, session.answers.size());
                assertEquals(i == 0 ? 4 : 0, session.correct);
                assertTrue(session.completedAt != null);
                for (ProgressState.Answer answer : session.answers) {
                    assertEquals(i == 0, answer.correct);
                    assertTrue(answer.firstTry);
                    assertEquals(i == 0 ? content.wordById(answer.wordId).hanzi
                                    : wrongChoices.get(answer.wordId),
                            answer.chosen);
                    assertEquals(1, state.words.get(answer.wordId).wrongCount);
                }
            }
        });
    }

    @Test
    public void nullSessionAnswers_recoversBeforeRecordingAndCompletingSession() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        file.write("""
                {"schemaVersion":3,"nextSessionId":8,
                 "sessions":[{"id":7,"date":"2026-10-21","answers":null}]}
                """);
        file.writeBackup("""
                {"schemaVersion":3,"nextSessionId":8,
                 "words":{"mao":{"learnedAt":123,"box":2}},
                 "sessions":[{"id":7,"date":"2026-10-21","kind":"REVIEW",
                   "quizWordIds":["mao"],"total":1}]}
                """);
        FakeClock clock = new FakeClock(T1);
        ProgressStore store = newStore(file, clock, 60_000);
        ProgressRepository progress = new ProgressRepository(store, TestContent.load(), clock);
        progress.recordAnswer(7, "mao", "猫", true, true);
        progress.completeSession(7);

        ProgressStore reloaded = newStore(file, clock, 60_000);
        reloaded.read(state -> state, state -> {
            ProgressState.Session session = SessionBuilder.sessionById(state, 7);
            assertEquals(8, state.nextSessionId);
            assertEquals(1, session.total);
            assertEquals(1, session.correct);
            assertEquals(1, session.answers.size());
            assertEquals("猫", session.answers.get(0).chosen);
            assertTrue(session.answers.get(0).correct);
            assertTrue(session.answers.get(0).firstTry);
            assertEquals(Long.valueOf(clock.now().toEpochMilli()), session.completedAt);
            assertEquals(Long.valueOf(123), state.words.get("mao").learnedAt);
            assertEquals(3, state.words.get("mao").box);
        });
    }

    @Test
    public void nestedInvalidBackup_usesExistingFreshStateFallback() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        file.writeBackup("{\"schemaVersion\":3,\"nextSessionId\":42,"
                + "\"sessions\":[{\"answers\":null}]}");
        ProgressStore store = newStore(file, new FakeClock(T1), 60_000);
        store.read(state -> state, state -> {
            assertEquals(1, state.nextSessionId);
            assertTrue(state.sessions.isEmpty());
        });
    }

    @Test
    public void validNestedDefaultsAndNullableFields_keepMainFile() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        file.write("""
                {"schemaVersion":3,"nextSessionId":42,"words":{"mao":{}},
                 "dailyPlans":{"2026-10-21":{"goal":3}},
                 "sessions":[{"id":7,"date":"2026-10-21","themeId":null,"completedAt":null}]}
                """);
        file.writeBackup("{\"schemaVersion\":3,\"nextSessionId\":17}");
        ProgressStore store = newStore(file, new FakeClock(T1), 60_000);
        store.read(state -> state, state -> {
            assertEquals(42, state.nextSessionId);
            assertTrue(state.words.get("mao").learnedAt == null);
            assertTrue(state.dailyPlans.get("2026-10-21").newWordIds.isEmpty());
            ProgressState.Session session = SessionBuilder.sessionById(state, 7);
            assertTrue(session.themeId == null);
            assertTrue(session.completedAt == null);
            assertTrue(session.newWordIds.isEmpty());
            assertTrue(session.quizWordIds.isEmpty());
            assertTrue(session.answers.isEmpty());
        });
        assertEquals(0, tmp.getRoot().listFiles(
                (dir, name) -> name.startsWith("progress.json.corrupt-")).length);
    }

    private void assertInvalidMainRestoresBackup(String invalidMain) {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        file.write(invalidMain);
        file.writeBackup("{\"schemaVersion\":3,\"nextSessionId\":42,"
                + "\"words\":{\"mao\":{\"learnedAt\":123,\"box\":2}}}");

        ProgressStore store = newStore(file, new FakeClock(T1), 60_000);
        store.ensureLoaded();
        store.read(state -> state, state -> {
            assertEquals(42, state.nextSessionId);
            assertEquals(Long.valueOf(123), state.words.get("mao").learnedAt);
            assertEquals(2, state.words.get("mao").box);
        });
        File[] corruptFiles = tmp.getRoot().listFiles(
                (dir, name) -> name.startsWith("progress.json.corrupt-"));
        assertEquals(1, corruptFiles.length);
        assertEquals(42, JsonParser.parseString(file.read()).getAsJsonObject()
                .get("nextSessionId").getAsLong());
        assertEquals(42, JsonParser.parseString(file.readBackup()).getAsJsonObject()
                .get("nextSessionId").getAsLong());
    }

    @Test
    public void nullDailyPlansInBackup_usesExistingFreshStateFallback() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        file.writeBackup("{\"schemaVersion\":3,\"dailyPlans\":null}");

        ProgressStore store = newStore(file, new FakeClock(T1), 60_000);
        store.ensureLoaded();
        store.read(state -> state, state -> {
            assertEquals(1, state.nextSessionId);
            assertTrue(state.words.isEmpty());
            assertTrue(state.sessions.isEmpty());
            assertTrue(state.dailyPlans.isEmpty());
        });
        ProgressStore reloaded = newStore(file, new FakeClock(T1), 60_000);
        reloaded.ensureLoaded();
        reloaded.read(state -> state.nextSessionId, id -> assertEquals(1L, id.longValue()));
    }

    @Test
    public void omittedCollections_keepDefaultsAndMigrate() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        file.write("{\"schemaVersion\":1,\"nextSessionId\":42}");

        ProgressStore store = newStore(file, new FakeClock(T1), 60_000);
        store.ensureLoaded();
        store.read(state -> state, state -> {
            assertEquals(ProgressStore.CURRENT_VERSION, state.schemaVersion);
            assertEquals(42, state.nextSessionId);
            assertTrue(state.words.isEmpty());
            assertTrue(state.sessions.isEmpty());
            assertTrue(state.dailyPlans.isEmpty());
        });
        assertEquals(0, tmp.getRoot().listFiles(
                (dir, name) -> name.startsWith("progress.json.corrupt-")).length);
    }

    @Test
    public void missingMain_restoresLearningHistoryFromBackup() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        ProgressState backup = new ProgressState();
        ProgressState.WordProgress word = new ProgressState.WordProgress();
        word.learnedAt = 123L;
        word.box = 2;
        backup.words.put("mao", word);
        ProgressState.DailyPlan plan = new ProgressState.DailyPlan();
        plan.goal = 3;
        plan.newWordIds.add("mao");
        backup.dailyPlans.put("2026-10-21", plan);
        ProgressState.Session session = new ProgressState.Session();
        session.id = 7;
        session.date = "2026-10-21";
        session.completedAt = 456L;
        backup.sessions.add(session);
        backup.nextSessionId = 8;
        file.writeBackup(new Gson().toJson(backup));

        ProgressStore store = newStore(file, new FakeClock(T1), 60_000);
        store.ensureLoaded();
        store.read(state -> state, state -> {
            assertTrue(state.words.containsKey("mao"));
            assertEquals(Long.valueOf(123), state.words.get("mao").learnedAt);
            assertEquals(2, state.words.get("mao").box);
            assertEquals(3, state.dailyPlans.get("2026-10-21").goal);
            assertEquals(Long.valueOf(456), state.sessions.get(0).completedAt);
            assertEquals(8, state.nextSessionId);
        });
        assertEquals(new Gson().toJson(backup), file.read());
        assertEquals(file.read(), file.readBackup());
        assertEquals(2, tmp.getRoot().listFiles().length);
    }

    @Test
    public void firstLaunch_withoutMainOrBackupCreatesDefaultState() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        ProgressStore store = newStore(file, new FakeClock(T1), 60_000);
        store.ensureLoaded();
        store.read(state -> state, state -> {
            assertTrue(state.words.isEmpty());
            assertTrue(state.sessions.isEmpty());
            assertTrue(state.dailyPlans.isEmpty());
            assertEquals(1, state.nextSessionId);
        });
        assertEquals(file.read(), file.readBackup());
        assertEquals(2, tmp.getRoot().listFiles().length);
    }

    @Test
    public void missingMain_withCorruptBackupUsesExistingFreshStateFallback() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        file.writeBackup("###broken");
        ProgressStore store = newStore(file, new FakeClock(T1), 60_000);
        store.ensureLoaded();
        store.read(state -> state, state -> {
            assertTrue(state.words.isEmpty());
            assertTrue(state.sessions.isEmpty());
            assertEquals(1, state.nextSessionId);
        });
        assertEquals(ProgressStore.CURRENT_VERSION,
                JsonParser.parseString(file.read()).getAsJsonObject().get("schemaVersion").getAsInt());
        assertEquals(file.read(), file.readBackup());
    }

    @Test
    public void validMain_takesPriorityOverOlderBackup() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        ProgressState main = new ProgressState();
        main.nextSessionId = 42;
        ProgressState backup = new ProgressState();
        backup.nextSessionId = 7;
        file.write(new Gson().toJson(main));
        file.writeBackup(new Gson().toJson(backup));

        ProgressStore store = newStore(file, new FakeClock(T1), 60_000);
        store.ensureLoaded();
        store.read(state -> state.nextSessionId, id -> assertEquals(Long.valueOf(42), id));
        assertEquals(new Gson().toJson(main), file.read());
        assertEquals(file.read(), file.readBackup());
    }

    @Test
    public void recoveredBackup_canSaveAndReloadAcrossMultipleCycles() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        ProgressState backup = new ProgressState();
        backup.nextSessionId = 8;
        file.writeBackup(new Gson().toJson(backup));
        FakeClock clock = new FakeClock(T1);

        for (int cycle = 0; cycle < 3; cycle++) {
            int expectedWords = cycle;
            ProgressStore store = newStore(file, clock, 60_000);
            store.ensureLoaded();
            store.read(state -> state, state -> {
                assertEquals(expectedWords, state.words.size());
                assertEquals(8 + expectedWords, state.nextSessionId);
            });
            String wordId = "word-" + cycle;
            store.update(state -> {
                ProgressState.WordProgress word = new ProgressState.WordProgress();
                word.learnedAt = 123L;
                state.words.put(wordId, word);
                state.nextSessionId++;
            });
            store.flushNow();
        }

        ProgressStore reloaded = newStore(file, clock, 60_000);
        reloaded.ensureLoaded();
        reloaded.read(state -> state, state -> {
            assertEquals(3, state.words.size());
            assertEquals(11, state.nextSessionId);
            for (int cycle = 0; cycle < 3; cycle++) {
                assertEquals(Long.valueOf(123), state.words.get("word-" + cycle).learnedAt);
            }
        });
        assertEquals(file.read(), file.readBackup());
    }

    @Test
    public void missingMain_migratesLegacyBackupAndRetainsAnswersOnReload() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        file.writeBackup("""
                {"schemaVersion":1,"favourites":{"xiaoxingxing":123},
                 "words":{"mao":{"learnedAt":123,"box":2}},
                 "dailyPlans":{"2026-10-21":{"goal":3,"newWordIds":["mao"]}},
                 "sessions":[{"id":7,"date":"2026-10-21","kind":"DAILY","stars":3,
                   "completedAt":456,"correct":1,"total":1,
                   "answers":[{"wordId":"mao","chosen":"猫","correct":true,"firstTry":true}]}],
                 "nextSessionId":8}
                """);
        FakeClock clock = new FakeClock(T1);
        ProgressStore store = newStore(file, clock, 60_000);
        store.ensureLoaded();
        ProgressStore reloaded = newStore(file, clock, 60_000);
        reloaded.ensureLoaded();
        reloaded.read(state -> state, state -> {
            assertEquals(ProgressStore.CURRENT_VERSION, state.schemaVersion);
            assertTrue(state.words.containsKey("mao"));
            assertEquals(Long.valueOf(123), state.words.get("mao").learnedAt);
            assertEquals(2, state.words.get("mao").box);
            assertEquals(3, state.dailyPlans.get("2026-10-21").goal);
            assertEquals(Long.valueOf(456), state.sessions.get(0).completedAt);
            assertEquals("猫", state.sessions.get(0).answers.get(0).chosen);
            assertTrue(state.sessions.get(0).answers.get(0).correct);
            assertEquals(8, state.nextSessionId);
        });
        var persisted = JsonParser.parseString(file.read()).getAsJsonObject();
        assertTrue(!persisted.has("favourites"));
        assertTrue(!persisted.getAsJsonArray("sessions").get(0).getAsJsonObject().has("stars"));
        assertEquals(file.read(), file.readBackup());
    }

    @Test
    public void corruptBoth_startsFresh() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        FakeClock clock = new FakeClock(T1);
        file.write("###broken");
        file.writeBackup("###also broken");
        ProgressStore store = newStore(file, clock, 50);
        store.ensureLoaded();
        final int[] words = new int[1];
        store.read(s -> s.words.size(), v -> words[0] = v);
        assertEquals(0, words[0]);
    }

    @Test
    public void sessionsOlderThan180Days_cleaned() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        FakeClock clock = new FakeClock(T1);
        ProgressStore store = newStore(file, clock, 50);
        store.update(state -> {
            ProgressState.Session old = new ProgressState.Session();
            old.id = 1;
            old.date = clock.today().minusDays(200).toString();
            ProgressState.Session recent = new ProgressState.Session();
            recent.id = 2;
            recent.date = clock.today().minusDays(10).toString();
            state.sessions.add(old);
            state.sessions.add(recent);
            state.dailyPlans.put(clock.today().minusDays(40).toString(), new ProgressState.DailyPlan());
            state.dailyPlans.put(clock.today().toString(), new ProgressState.DailyPlan());
        });
        store.flushNow();

        // 时钟前进一天再加载 → 触发清理
        clock.advance(Duration.ofDays(1));
        ProgressStore store2 = newStore(file, clock, 50);
        store2.ensureLoaded();
        store2.read(s -> s, s -> {
            assertEquals(1, s.sessions.size());
            assertEquals(2, s.sessions.get(0).id);
            assertEquals(1, s.dailyPlans.size());
        });
    }

    @Test
    public void legacyStarSave_reloadsWithoutLosingLearningProgress() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        file.write("""
                {"schemaVersion":1,"words":{"mao":{"learnedAt":123,"box":2}},
                 "dailyPlans":{"2026-10-21":{"goal":3,"newWordIds":["mao"]}},
                 "sessions":[{"id":7,"date":"2026-10-21","kind":"DAILY","stars":3,
                   "completedAt":456,"correct":1,"total":1,
                   "answers":[{"wordId":"mao","chosen":"猫","correct":true,"firstTry":true}]}],
                 "nextSessionId":8}
                """);
        ProgressStore store = newStore(file, new FakeClock(T1), 50);
        store.ensureLoaded();
        store.read(state -> state, state -> {
            assertEquals(ProgressStore.CURRENT_VERSION, state.schemaVersion);
            assertEquals(2, state.words.get("mao").box);
            assertEquals(Long.valueOf(123), state.words.get("mao").learnedAt);
            assertEquals(3, state.dailyPlans.get("2026-10-21").goal);
            assertEquals(Long.valueOf(456), state.sessions.get(0).completedAt);
            assertEquals("猫", state.sessions.get(0).answers.get(0).chosen);
            assertEquals(8, state.nextSessionId);
        });
        assertTrue(!JsonParser.parseString(file.read()).getAsJsonObject()
                .getAsJsonArray("sessions").get(0).getAsJsonObject().has("stars"));
    }

    @Test
    public void completeSession_persistsCompletionOnlyOnce() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        FakeClock clock = new FakeClock(T1);
        ProgressStore store = newStore(file, clock, 60_000);
        store.update(state -> {
            ProgressState.Session session = new ProgressState.Session();
            session.id = 1;
            session.date = clock.today().toString();
            session.correct = 2;
            session.total = 3;
            state.sessions.add(session);
        });
        ProgressRepository progress = new ProgressRepository(store, TestContent.load(), clock);
        long completedAt = clock.now().toEpochMilli();
        progress.completeSession(1);
        clock.advance(Duration.ofMinutes(5));
        progress.completeSession(1);

        ProgressStore reloaded = newStore(file, clock, 60_000);
        reloaded.ensureLoaded();
        reloaded.read(state -> state.sessions.get(0), session -> {
            assertEquals(Long.valueOf(completedAt), session.completedAt);
            assertEquals(2, session.correct);
            assertEquals(3, session.total);
        });
    }
    @Test
    public void version2Save_removesFavourites_andKeepsLearningHistory() {
        FileStateFile file = new FileStateFile(tmp.getRoot());
        file.write("""
                {"schemaVersion":2,"favourites":{"xiaoxingxing":123},
                 "words":{"mao":{"learnedAt":123,"box":2}},
                 "dailyPlans":{"2026-10-21":{"goal":3,"newWordIds":["mao"]}},
                 "sessions":[{"id":7,"date":"2026-10-21","kind":"DAILY",
                   "completedAt":456,"correct":1,"total":1,
                   "answers":[{"wordId":"mao","chosen":"猫","correct":true,"firstTry":true}]}],
                 "nextSessionId":8}
                """);
        ProgressStore store = newStore(file, new FakeClock(T1), 50);
        store.ensureLoaded();
        store.read(state -> state, state -> {
            assertEquals(ProgressStore.CURRENT_VERSION, state.schemaVersion);
            assertEquals(Long.valueOf(123), state.words.get("mao").learnedAt);
            assertEquals(2, state.words.get("mao").box);
            assertEquals(3, state.dailyPlans.get("2026-10-21").goal);
            assertEquals(Long.valueOf(456), state.sessions.get(0).completedAt);
            assertEquals("猫", state.sessions.get(0).answers.get(0).chosen);
            assertEquals(8, state.nextSessionId);
        });
        assertTrue(!JsonParser.parseString(file.read()).getAsJsonObject().has("favourites"));
    }

}
