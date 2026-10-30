package com.littlestar.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
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
import com.littlestar.app.feature.player.PlayerActivity;
import com.littlestar.app.playback.PlaybackService;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
public final class CultureReviewChangesTest {
    @Test public void poemsHasEnglishLabelAndStillOpensCorrectCategory() throws Exception {
        try (ActivityScenario<CultureCategoriesActivity> scenario =
                ActivityScenario.launch(CultureCategoriesActivity.class)) {
            waitForText(R.id.namePoemsEn, "Poems");
            UiEvidence.capture("review_categories_light");
            onView(withId(R.id.catPoems)).perform(click());
            waitForText(R.id.categoryName, "古诗");
            onView(withId(R.id.categoryCount)).check(matches(withText(containsString("3"))));
            pressBack();
            onView(withId(R.id.catSongs)).perform(click());
            waitForText(R.id.categoryName, "儿歌");
            UiEvidence.capture("review_song_list_light");
            onView(withText("小星星")).perform(click());
            waitForText(R.id.detailTitle, "小星星");
        }
    }

    @Test public void trackButtonsFollowFirstMiddleLastAndSingleItemQueue() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ArrayList<String> queue = new ArrayList<>(Arrays.asList(
                "xiaoxingxing", "liangzhilaohu", "baluobo", "zhaopengyou"));
        AtomicReference<ListenableFuture<MediaController>> future = new AtomicReference<>();
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                new Intent(context, PlayerActivity.class).putExtra("itemId", queue.get(0))
                        .putStringArrayListExtra("queue", queue))) {
            waitForText(R.id.songTitle, "小星星");
            onMain(() -> future.set(new MediaController.Builder(context,
                    new SessionToken(context, new ComponentName(context, PlaybackService.class)))
                    .buildAsync()));
            MediaController controller = future.get().get(10, TimeUnit.SECONDS);
            waitFor(controller::isPlaying);
            onMain(controller::pause);
            waitFor(() -> !controller.isPlaying());
            checkEnabled(R.id.btnPrev, false); checkEnabled(R.id.btnNext, true);
            UiEvidence.capture("review_player_first");
            onView(withId(R.id.btnNext)).perform(click());
            waitForText(R.id.songTitle, "两只老虎");
            checkEnabled(R.id.btnPrev, true); checkEnabled(R.id.btnNext, true);
            waitFor(() -> !controller.isPlaying());
            onMain(() -> controller.seekToDefaultPosition(3));
            waitForText(R.id.songTitle, "找朋友");
            checkEnabled(R.id.btnPrev, true); checkEnabled(R.id.btnNext, false);
            UiEvidence.capture("review_player_last");
            onMain(() -> controller.setMediaItems(
                    java.util.Collections.singletonList(controller.getMediaItemAt(0)), 0, 0));
            waitForText(R.id.songTitle, "小星星");
            checkEnabled(R.id.btnPrev, false); checkEnabled(R.id.btnNext, false);
        } finally {
            if (future.get() != null) {
                MediaController controller = future.get().get(10, TimeUnit.SECONDS);
                onMain(() -> { controller.stop(); controller.clearMediaItems(); controller.release(); });
            }
            context.stopService(new Intent(context, PlaybackService.class));
        }
    }
    private static void checkEnabled(int id, boolean enabled) {
        long end = SystemClock.uptimeMillis() + 5000;
        do { try {
            onView(withId(id)).check(matches(enabled ? isEnabled() : not(isEnabled()))); return;
        } catch (AssertionError e) {
            if (SystemClock.uptimeMillis() >= end) throw e;
            SystemClock.sleep(100);
        }} while (true);
    }
    private static void waitForText(int id, String value) {
        long end = SystemClock.uptimeMillis() + 10000;
        do { try {
            onView(withId(id)).check(matches(withText(value))); return;
        } catch (RuntimeException | AssertionError e) {
            if (SystemClock.uptimeMillis() >= end) throw e;
            SystemClock.sleep(100);
        }} while (true);
    }
    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }
    private static void waitFor(BooleanSupplier condition) {
        long end = SystemClock.uptimeMillis() + 10000;
        AtomicBoolean ready = new AtomicBoolean();
        do { onMain(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < end);
        assertTrue("Playback condition timed out", ready.get());
    }
}
