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
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.core.view.WindowCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.littlestar.app.feature.culture.CultureDetailActivity;
import com.littlestar.app.data.content.model.CultureItem;

import java.util.concurrent.atomic.AtomicBoolean;

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
            waitForCover(scenario, true);
            onView(withId(R.id.detailTitle)).check(matches(withText("静夜思")));
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.detailFocus).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.detailTranscript).getVisibility());
                assertTrue(!((View) activity.findViewById(R.id.detailCover).getParent().getParent()).isClickable());
            });
            UiEvidence.capture("culture_detail_light");
            onView(withId(R.id.btnPreview)).perform(scrollTo(), click());
            onView(withId(R.id.detailFocus)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.detailTranscript)).perform(scrollTo()).check(matches(isDisplayed()));
            scenario.recreate();
            waitFor(R.id.detailTitle);
            waitForCover(scenario, true);
            onView(withId(R.id.detailTitle)).check(matches(withText("静夜思")));
            scenario.onActivity(activity -> assertEquals(android.view.View.VISIBLE,
                    activity.findViewById(R.id.detailTranscript).getVisibility()));
            onView(withId(R.id.btnListen)).check(matches(isEnabled()));
            onView(withId(R.id.detailTitle)).perform(scrollTo());
            UiEvidence.capture("culture_detail_preview");
            scenario.onActivity(activity -> {
                assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_NO,
                        activity.getResources().getConfiguration().uiMode
                                & android.content.res.Configuration.UI_MODE_NIGHT_MASK);
                assertTrue(WindowCompat.getInsetsController(activity.getWindow(),
                        activity.getWindow().getDecorView()).isAppearanceLightStatusBars());
                assertTrue(WindowCompat.getInsetsController(activity.getWindow(),
                        activity.getWindow().getDecorView()).isAppearanceLightNavigationBars());
            });
            UiEvidence.capture("culture_detail_light_recreated");
        }
    }

    @Test
    public void allCovers_preserveGlyphTitleAndPlaybackEntry() {
        Context context = ApplicationProvider.getApplicationContext();
        AppContainer container = ((LittleStarApp) context).container();
        container.content.ensureLoaded();
        String[] ids = {"xiaoxingxing", "liangzhilaohu", "baluobo", "zhaopengyou",
                "changebenyue", "shouzhudaitu", "guitusaipao", "huashetianzu",
                "jingyesi", "yonge", "chunxiao"};
        for (String id : ids) {
            CultureItem item = container.content.itemById(id);
            try (ActivityScenario<CultureDetailActivity> scenario = ActivityScenario.launch(
                    new Intent(context, CultureDetailActivity.class).putExtra("itemId", id))) {
                waitForCover(scenario, true);
                scenario.onActivity(activity -> {
                    assertEquals(item.titleZh, ((TextView) activity.findViewById(R.id.detailTitle)).getText().toString());
                    assertEquals(item.glyph, ((TextView) activity.findViewById(R.id.detailGlyph)).getText().toString());
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.detailGlyph).getVisibility());
                    assertEquals(item.summary, ((TextView) activity.findViewById(R.id.detailSummary)).getText().toString());
                    assertTrue(activity.findViewById(R.id.btnListen).isEnabled());
                    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                            activity.findViewById(R.id.detailCover).getImportantForAccessibility());
                });
            }
        }
    }

    @Test
    public void missingOrNonBitmapCover_keepsExistingContentUsable() {
        Context context = ApplicationProvider.getApplicationContext();
        AppContainer container = ((LittleStarApp) context).container();
        container.content.ensureLoaded();
        CultureItem item = container.content.itemById("jingyesi");
        String original = item.cover;
        try {
            for (String cover : new String[]{null, "ill_placeholder", "missing_cover", "ic_back"}) {
                item.cover = cover;
                try (ActivityScenario<CultureDetailActivity> scenario = ActivityScenario.launch(
                        new Intent(context, CultureDetailActivity.class).putExtra("itemId", item.id))) {
                    waitFor(R.id.detailTitle);
                    waitForCover(scenario, false);
                    onView(withId(R.id.detailTitle)).check(matches(withText(item.titleZh)));
                    onView(withId(R.id.btnListen)).check(matches(isEnabled()));
                    onView(withId(R.id.btnPreview)).perform(scrollTo(), click());
                    String chinese = item.lyrics.isEmpty() ? item.transcript : item.lyrics.get(0).hanzi;
                    String english = item.lyrics.isEmpty() ? item.transcriptEn : item.lyrics.get(0).english;
                    onView(withId(R.id.detailTranscript)).perform(scrollTo())
                            .check(matches(withText(containsString(chinese))))
                            .check(matches(withText(containsString(english))));
                }
            }
        } finally {
            item.cover = original;
        }
    }

    private static void waitForCover(ActivityScenario<CultureDetailActivity> scenario, boolean present) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        AtomicBoolean ready = new AtomicBoolean();
        do {
            scenario.onActivity(activity -> {
                ImageView cover = activity.findViewById(R.id.detailCover);
                var drawable = cover.getDrawable();
                boolean empty = drawable == null || drawable instanceof BitmapDrawable
                        && ((BitmapDrawable) drawable).getBitmap() == null;
                ready.set(activity.findViewById(R.id.detailContent).getVisibility() == View.VISIBLE
                        && (present ? cover.getVisibility() == View.VISIBLE && !empty
                                    : cover.getVisibility() == View.GONE && empty));
            });
            if (ready.get()) {
                return;
            }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Cover did not reach expected state: " + present);
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
