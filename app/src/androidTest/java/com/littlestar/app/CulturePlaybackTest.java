package com.littlestar.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;
import com.littlestar.app.feature.culture.CultureCategoriesActivity;
import com.littlestar.app.playback.PlaybackService;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** 精简后仍可从分类进入播放器、暂停/切歌并在后台继续播放。 */
@RunWith(AndroidJUnit4.class)
public final class CulturePlaybackTest {

    @Test
    public void cultureNavigation_controlsAndBackgroundPlaybackStillWork() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        AtomicReference<ListenableFuture<MediaController>> future = new AtomicReference<>();
        try (ActivityScenario<CultureCategoriesActivity> scenario = ActivityScenario.launch(
                new Intent(context, CultureCategoriesActivity.class))) {
            waitForView(R.id.catSongs);
            onView(withId(R.id.catSongs)).perform(click());
            waitForView(R.id.categoryName);
            onView(withText("小星星")).perform(click());
            waitForView(R.id.songTitle);
            onMain(() -> future.set(new MediaController.Builder(context,
                    new SessionToken(context, new ComponentName(context, PlaybackService.class)))
                    .buildAsync()));
            MediaController controller = future.get().get(10, TimeUnit.SECONDS);
            waitFor(() -> controller.isPlaying() && controller.getCurrentMediaItem() != null
                    && "xiaoxingxing".equals(controller.getCurrentMediaItem().mediaId));

            onView(withId(R.id.btnPlay)).perform(click());
            waitFor(() -> !controller.isPlaying());
            onMain(() -> controller.seekTo(0));
            onView(withId(R.id.btnPlay)).perform(click());
            waitFor(controller::isPlaying);
            onView(withId(R.id.btnNext)).perform(click());
            waitFor(() -> controller.getCurrentMediaItem() != null
                    && "liangzhilaohu".equals(controller.getCurrentMediaItem().mediaId));
            onView(withId(R.id.songTitle)).check(matches(withText("两只老虎")));
            onView(withId(R.id.btnPrev)).perform(click());
            waitFor(() -> controller.getCurrentMediaItem() != null
                    && "xiaoxingxing".equals(controller.getCurrentMediaItem().mediaId));
            onMain(() -> controller.seekTo(0));
            waitFor(controller::isPlaying);

            // Home 键使应用退到后台；用独立控制器确认服务仍在推进音频位置。
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
            AtomicReference<Long> before = new AtomicReference<>();
            onMain(() -> before.set(controller.getCurrentPosition()));
            waitFor(() -> controller.isPlaying()
                    && controller.getCurrentPosition() > before.get() + 400);
        } finally {
            if (future.get() != null) {
                MediaController controller = future.get().get(10, TimeUnit.SECONDS);
                onMain(() -> {
                    controller.stop();
                    controller.clearMediaItems();
                    controller.release();
                });
            }
            context.stopService(new Intent(context, PlaybackService.class));
        }
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static void waitFor(BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        AtomicBoolean ready = new AtomicBoolean();
        do {
            onMain(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) {
                return;
            }
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        assertTrue("Playback condition did not become true", ready.get());
    }

    private static void waitForView(int id) {
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
