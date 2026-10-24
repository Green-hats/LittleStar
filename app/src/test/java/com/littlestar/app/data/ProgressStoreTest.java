package com.littlestar.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.littlestar.app.AppExecutors;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.data.store.ProgressStore;
import com.littlestar.app.data.repo.ProgressRepository;
import com.littlestar.app.util.FakeClock;
import com.littlestar.app.util.FileStateFile;
import com.littlestar.app.util.TestContent;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;

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
