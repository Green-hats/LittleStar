package com.littlestar.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.domain.QuizGenerator;
import com.littlestar.app.feature.home.HomeActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class SmokeTest {
    private static final int[] OPTIONS = {R.id.option0, R.id.option1, R.id.option2, R.id.option3};
    private AppContainer container;

    @Before
    public void resetToFirstLaunch() throws Exception {
        container = ((LittleStarApp) ApplicationProvider.getApplicationContext()).container();
        container.settings.setOnboarded(false);
        container.settings.setDailyGoal(3);
        container.settings.setEnglishHint(false);
        container.store.update(s -> {
            s.words.clear();
            s.sessions.clear();
            s.dailyPlans.clear();
            s.nextSessionId = 1;
        });
        read(s -> s.sessions.size());
    }

    @Test
    public void firstLaunchAndReturnShowLessonActionWithoutScrolling() {
        try (ActivityScenario<HomeActivity> ignored = ActivityScenario.launch(HomeActivity.class)) {
            waitUntilShown(R.id.btnStart);
            onView(withId(R.id.btnStart)).check(matches(allOf(isCompletelyDisplayed(), withText(R.string.sprout_start))));
            tap(R.id.btnStart);
            waitUntilShown(R.id.pageHanzi);
            onView(withId(R.id.pageIllustration)).check(matches(isCompletelyDisplayed()));
            onView(withId(R.id.btnListen)).check(matches(isCompletelyDisplayed()));
            onView(withId(R.id.btnNext)).check(matches(isCompletelyDisplayed()));
            pressBack();
            waitUntilShown(R.id.btnStart);
            onView(withId(R.id.btnStart)).check(matches(allOf(isCompletelyDisplayed(), withText(R.string.sprout_continue))));
        }
    }

    @Test
    public void fullSessionFlow() throws Exception {
        try (ActivityScenario<HomeActivity> ignored = ActivityScenario.launch(HomeActivity.class)) {
            tap(R.id.btnStart);
            tap(R.id.btnListen);
            tap(R.id.btnNext);
            pressBack();
            onView(withId(R.id.btnStart)).check(matches(withText(R.string.sprout_continue)));
            tap(R.id.btnStart);
            tap(R.id.btnListen);
            onView(withId(R.id.cardCounter)).check(matches(withText("Word 2 of 3")));
            tap(R.id.btnNext);
            tap(R.id.btnNext);

            ProgressState.Session session = read(s -> s.sessions.get(0));
            int retestIndex = -1;
            for (int i = 0; i < session.quizWordIds.size(); i++) {
                String id = session.quizWordIds.get(i);
                long seed = session.id * 10007L + (long) i * 31L + id.hashCode();
                QuizGenerator.QuizQuestion question = new QuizGenerator(container.content, new Random(seed))
                        .generate(container.content.wordById(id));
                int correct = question.correctIndex();
                if (i == 0) {
                    tap(OPTIONS[(correct + 1) % OPTIONS.length]);
                    waitUntilShown(R.id.btnRetry);
                    assertFalse("An incorrect answer must not unlock Next", shownAndEnabled(R.id.btnNext));
                    pressBack();
                    tap(R.id.btnStart);
                    tap(R.id.btnRetry);
                    correct = question.reordered(1).correctIndex();
                    retestIndex = question.reordered(2).correctIndex();
                }
                tap(OPTIONS[correct]);
                tap(R.id.btnNext);
                if (i == 0) {
                    onView(withId(R.id.questionHint)).check(matches(isCompletelyDisplayed()));
                }
                if (i == 1) {
                    tap(OPTIONS[retestIndex]);
                    tap(R.id.btnNext);
                }
            }

            waitUntilShown(R.id.btnReview);
            onView(withId(R.id.rewardScore)).check(matches(withText("First try: 2 of 3")));
            UiEvidence.capture("lesson_complete_palette");
            tap(R.id.btnReview);
            waitUntilShown(R.id.answerList);
            pressBack();
            tap(R.id.btnDone);
            onView(withId(R.id.btnStart)).check(matches(withText(R.string.sprout_review)));
            ProgressState state = read(s -> s);
            ProgressState.Session completed = state.sessions.get(0);
            assertEquals(1, state.sessions.size());
            assertNotNull(completed.completedAt);
            assertEquals(3, completed.answers.size());
            assertEquals(2, completed.correct);
            assertFalse(completed.answers.get(0).correct);
            String corrected = completed.answers.get(0).wordId;
            assertTrue(completed.correctedWordIds.contains(corrected));
            assertEquals(Integer.valueOf(1), completed.hintAttempts.get(corrected));
            assertEquals(Boolean.TRUE, completed.retestResults.get(corrected));
            assertEquals(container.clock.today().plusDays(1).toString(), state.words.get(corrected).dueDate);
        }
    }

    private <T> T read(Function<ProgressState, T> fn) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        container.progress.read(state -> fn.apply(state.deepCopy(new com.google.gson.Gson())), result::complete);
        return result.get(10, TimeUnit.SECONDS);
    }

    private static void tap(int id) {
        waitUntilShown(id);
        onView(withId(id)).perform(click());
    }

    private static boolean shownAndEnabled(int id) {
        try {
            onView(withId(id)).check(matches(allOf(isDisplayed(), isEnabled())));
            return true;
        } catch (RuntimeException | AssertionError e) {
            return false;
        }
    }

    private static void waitUntilShown(int id) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (!shownAndEnabled(id) && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50);
        }
        onView(withId(id)).check(matches(allOf(isDisplayed(), isEnabled())));
    }
}
