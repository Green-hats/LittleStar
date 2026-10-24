package com.littlestar.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

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
                    ApplicationProvider.getApplicationContext().getString(R.string.reward_subtitle, 3))));
            ProgressState.Session completed = readSession(container);
            assertNotNull(completed.completedAt);
            scenario.recreate();
            onView(withId(R.id.rewardSubtitle)).check(matches(withText(
                    ApplicationProvider.getApplicationContext().getString(R.string.reward_subtitle, 3))));
            ProgressState.Session restored = readSession(container);
            assertEquals(completed.completedAt, restored.completedAt);
            assertEquals(List.of("mao", "gou", "niao"), restored.newWordIds);
            assertEquals(2, restored.correct);
            assertEquals(3, restored.total);
        }
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
