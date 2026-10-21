package com.littlestar.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.littlestar.app.feature.home.HomeActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * 冒烟：冷启动 →（首启引导过掉）→ START → 3 张卡 NEXT → 5 题点第一个选项 → Reward → 完成回 Home。
 * M3 布局落地后补齐具体步骤；当前先验证 Home 能启动。
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public final class SmokeTest {

    @Rule
    public ActivityScenarioRule<HomeActivity> rule = new ActivityScenarioRule<>(HomeActivity.class);

    @Test
    public void homeLaunches() {
        onView(withId(android.R.id.content)).check(matches(isDisplayed()));
    }
}
