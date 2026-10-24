package com.littlestar.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.littlestar.app.feature.home.HomeActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * 冒烟（M3）：清到首次启动 → 引导两步 → START → 识字卡 NEXT×N → 每题点第一个选项
 * → Reward → 完成回 Home。
 * 数据是持久的：@Before 通过容器把 settings / progress 重置（进程内存态也一并清），
 * 保证测试从「firstLaunch + 0 进度」开始。
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public final class SmokeTest {

    @Before
    public void resetToFirstLaunch() throws InterruptedException {
        AppContainer c = ((LittleStarApp) ApplicationProvider.getApplicationContext()).container();
        c.settings.setOnboarded(false);
        c.settings.setDailyGoal(3);
        c.settings.setEnglishHint(false);
        c.settings.setParentPin("");
        c.store.update(state -> {
            state.words.clear();
            state.sessions.clear();
            state.dailyPlans.clear();
            state.usage.clear();
            state.nextSessionId = 1;
        });
        c.store.flushNow();
        Thread.sleep(500);   // 等 diskIO 落盘
    }

    @Test
    public void fullSessionFlow() throws InterruptedException {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            // 1. 首次引导：下一步 → 先不用
            clickIfDisplayed(R.id.obNext);
            clickIfDisplayed(R.id.obLater);

            // 2. 首页：切到字态（17:00 后默认文态，LISTEN M3 不导航）再 START
            clickIfDisplayed(R.id.modeWord);
            waitFor(R.id.btnStart, 10_000);
            onView(withId(R.id.btnStart)).perform(click());

            // 3. 识字卡：NEXT 点到 Quiz 出现（REVIEW 会话会直接跳过识字卡）
            for (int i = 0; i < 8 && !isShown(R.id.quizRoot); i++) {
                waitFor(R.id.flashcardRoot, 10_000);
                onView(withId(R.id.btnNext)).perform(click());
                Thread.sleep(600);
            }

            // 4. 测验：每题点第一个选项，再点 NEXT
            for (int i = 0; i < 12 && !isShown(R.id.rewardRoot); i++) {
                waitFor(R.id.quizRoot, 10_000);
                if (isShown(R.id.bottomFeedback)) {
                    onView(withId(R.id.btnNext)).perform(click());
                } else {
                    onView(withId(R.id.option0)).perform(click());
                }
                Thread.sleep(600);
            }

            // 5. Reward 出现 → 完成回 Home
            waitFor(R.id.rewardRoot, 10_000);
            onView(withId(R.id.btnDone)).check(matches(isDisplayed())).perform(click());
            Thread.sleep(800);
            onView(withId(R.id.btnStart)).check(matches(isDisplayed()));
        }
    }

    private static void clickIfDisplayed(int id) throws InterruptedException {
        waitFor(id, 10_000);
        onView(withId(id)).perform(click());
        Thread.sleep(600);
    }

    private static boolean isShown(int id) {
        try {
            onView(withId(id)).check(matches(isDisplayed()));
            return true;
        } catch (RuntimeException | AssertionError e) {
            return false;
        }
    }

    private static void waitFor(int id, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!isShown(id)) {
            if (System.currentTimeMillis() > deadline) {
                // 最后一次用 check 抛出带堆栈的失败
                onView(withId(id)).check(matches(isDisplayed()));
                return;
            }
            Thread.sleep(200);
        }
    }
}
