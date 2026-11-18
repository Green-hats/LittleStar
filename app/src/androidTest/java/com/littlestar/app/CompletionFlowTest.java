package com.littlestar.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.feature.reward.RewardActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 完成页重建仍保留学习结果，并且只记录一次完成时间。 */
@RunWith(AndroidJUnit4.class)
public final class CompletionFlowTest {

    @Test
    public void recreate_preservesCompletionAndLearnedWords() throws InterruptedException {
        AppContainer container = ((LittleStarApp) ApplicationProvider.getApplicationContext()).container();
        container.settings.setSoundEnabled(false);
        container.store.update(state -> {
            container.content.ensureLoaded();
            state.sessions.clear();
            ProgressState.Session session = new ProgressState.Session();
            session.id = 1;
            session.date = container.clock.today().toString();
            session.kind = "DAILY";
            session.newWordIds = List.of("mao", "gou", "niao");
            session.quizWordIds = session.newWordIds;
            session.answers = List.of(answer("mao", true), answer("gou", false), answer("niao", true));
            session.correctedWordIds.add("gou");
            session.hintAttempts.put("gou", 1);
            session.retestResults.put("gou", true);
            session.correct = 2;
            session.total = 3;
            state.sessions.add(session);
            state.nextSessionId = 2;
        });
        readSession(container);
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), RewardActivity.class)
                .putExtra("sessionId", 1L);
        try (ActivityScenario<RewardActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.rewardSubtitle)).check(matches(withText(
                    "You practiced 3 characters")));
            ProgressState.Session completed = readSession(container);
            assertNotNull(completed.completedAt);
            CountDownLatch playbackStarted = new CountDownLatch(1);
            scenario.onActivity(activity -> {
                LinearLayout words = activity.findViewById(R.id.practicedWords);
                assertEquals(3, words.getChildCount());
                TextView replay = words.getChildAt(0).findViewById(R.id.btnReplayWord);
                assertEquals("猫", replay.getText().toString());
                assertEquals("Listen to 猫", replay.getContentDescription().toString());
                expectStatus(words, 0, R.string.reward_word_independent);
                expectStatus(words, 1, R.string.reward_word_retested);
                container.speech.isPlaying().observe(activity, playing -> {
                    if (Boolean.TRUE.equals(playing)) playbackStarted.countDown();
                });
                replay.performClick();
            });
            assertTrue("Audio should play when tapping a practiced character",
                    playbackStarted.await(5, TimeUnit.SECONDS));
            scenario.recreate();
            onView(withId(R.id.rewardSubtitle)).check(matches(withText(
                    "You practiced 3 characters")));
            ProgressState.Session restored = readSession(container);
            assertEquals(completed.completedAt, restored.completedAt);
            assertEquals(List.of("mao", "gou", "niao"), restored.newWordIds);
            assertEquals(2, restored.correct);
            assertEquals(3, restored.total);
            assertEquals(completed.hintAttempts, restored.hintAttempts);
            scenario.onActivity(activity -> expectStatus(activity.findViewById(R.id.practicedWords),
                    1, R.string.reward_word_retested));
        }
    }

    private static void expectStatus(LinearLayout words, int index, int stringRes) {
        TextView status = words.getChildAt(index).findViewById(R.id.practicedResult);
        assertEquals(status.getContext().getString(stringRes), status.getText().toString());
    }

    private static ProgressState.Answer answer(String id, boolean correct) {
        ProgressState.Answer answer = new ProgressState.Answer();
        answer.wordId = id;
        answer.correct = correct;
        answer.firstTry = true;
        return answer;
    }

    private static ProgressState.Session readSession(AppContainer container) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<ProgressState.Session> result = new AtomicReference<>();
        container.store.read(state -> state.deepCopy(new com.google.gson.Gson()).sessions.get(0), session -> {
            result.set(session);
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        return result.get();
    }
}
