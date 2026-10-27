package com.littlestar.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.littlestar.app.feature.culture.CultureDetailActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

/** 详情重建后保留所选内容、文本展开状态；坏链接不会进入播放器。 */
@RunWith(AndroidJUnit4.class)
public final class CultureDetailTest {

    @Test
    public void recreation_restoresSelectedContentAndExpandedTranscript() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, CultureDetailActivity.class).putExtra("itemId", "jingyesi");
        try (ActivityScenario<CultureDetailActivity> scenario = ActivityScenario.launch(intent)) {
            waitFor(R.id.detailTitle);
            onView(withId(R.id.detailTitle)).check(matches(withText("静夜思")));
            UiEvidence.capture("culture_detail_light");
            onView(withId(R.id.btnPreview)).perform(scrollTo(), click());
            onView(withId(R.id.detailTranscript)).perform(scrollTo()).check(matches(isDisplayed()));
            scenario.recreate();
            waitFor(R.id.detailTitle);
            onView(withId(R.id.detailTitle)).check(matches(withText("静夜思")));
            scenario.onActivity(activity -> assertEquals(android.view.View.VISIBLE,
                    activity.findViewById(R.id.detailTranscript).getVisibility()));
            onView(withId(R.id.btnListen)).check(matches(isEnabled()));
            onView(withId(R.id.detailTitle)).perform(scrollTo());
            UiEvidence.capture("culture_detail_preview");
            scenario.onActivity(activity -> activity.getDelegate()
                    .setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES));
            waitFor(R.id.detailTitle);
            onView(withId(R.id.detailTitle)).perform(scrollTo());
            scenario.onActivity(activity -> {
                assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_YES,
                        activity.getResources().getConfiguration().uiMode
                                & android.content.res.Configuration.UI_MODE_NIGHT_MASK);
                assertFalse(WindowCompat.getInsetsController(activity.getWindow(),
                        activity.getWindow().getDecorView()).isAppearanceLightStatusBars());
                assertFalse(WindowCompat.getInsetsController(activity.getWindow(),
                        activity.getWindow().getDecorView()).isAppearanceLightNavigationBars());
            });
            UiEvidence.capture("culture_detail_dark");
        }
    }

    @Test
    public void unknownContent_showsMessageAndDisablesPlayback() {
        Context context = ApplicationProvider.getApplicationContext();
        try (ActivityScenario<CultureDetailActivity> scenario = ActivityScenario.launch(
                new Intent(context, CultureDetailActivity.class).putExtra("itemId", "missing"))) {
            waitFor(R.id.detailStatus);
            // 等待异步本地内容加载完成，避免把 loading 状态误判成错误状态。
            long deadline = SystemClock.uptimeMillis() + 10_000;
            do {
                try {
                    onView(withId(R.id.detailStatus)).check(matches(withText(R.string.culture_detail_missing)));
                    break;
                } catch (AssertionError error) {
                    if (SystemClock.uptimeMillis() >= deadline) {
                        throw error;
                    }
                    SystemClock.sleep(100);
                }
            } while (true);
            onView(withId(R.id.btnListen)).check(matches(not(isEnabled())));
        }
    }

    private static void waitFor(int id) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        do {
            try {
                onView(withId(id)).check(matches(isDisplayed()));
                return;
            } catch (RuntimeException | AssertionError error) {
                if (SystemClock.uptimeMillis() >= deadline) {
                    throw error;
                }
                SystemClock.sleep(100);
            }
        } while (true);
    }
}
