package com.littlestar.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.util.concurrent.ListenableFuture;
import com.littlestar.app.feature.player.PlayerActivity;
import com.littlestar.app.feature.player.PlayerViewModel;
import com.littlestar.app.playback.PlaybackService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
public final class PlayerRegressionTest {

    private Context context;
    private AppContainer container;
    private ListenableFuture<MediaController> controllerFuture;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        container = ((LittleStarApp) context).container();
        container.content.ensureLoaded();
    }

    @After
    public void tearDown() throws Exception {
        if (controllerFuture != null) {
            MediaController controller = controllerFuture.get(10, TimeUnit.SECONDS);
            onMain(() -> {
                controller.stop();
                controller.clearMediaItems();
                controller.release();
            });
            controllerFuture = null;
        }
        context.stopService(new Intent(context, PlaybackService.class));
    }

    @Test
    public void normalizedQueue_replacesUnknownAndSingletonWhenEmpty() throws Exception {
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                playerIntent("xiaoxingxing", List.of("unknown", "liangzhilaohu")))) {
            MediaController controller = connect().get(10, TimeUnit.SECONDS);
            waitFor(() -> controller.isPlaying() && controller.getCurrentMediaItem() != null
                    && "xiaoxingxing".equals(controller.getCurrentMediaItem().mediaId));
            onMain(() -> {
                assertEquals(2, controller.getMediaItemCount());
                assertEquals("xiaoxingxing", controller.getMediaItemAt(0).mediaId);
                assertEquals("liangzhilaohu", controller.getMediaItemAt(1).mediaId);
            });
            waitForText(R.id.songTitle, "小星星");
        }
    }

    @Test
    public void normalizedQueue_emptyBecomesSingleton() throws Exception {
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                playerIntent("zhaopengyou", List.of()))) {
            MediaController controller = connect().get(10, TimeUnit.SECONDS);
            waitFor(() -> controller.getCurrentMediaItem() != null
                    && "zhaopengyou".equals(controller.getCurrentMediaItem().mediaId));
            onMain(() -> assertEquals(1, controller.getMediaItemCount()));
        }
    }

    @Test
    public void viewModel_initFiltersAndTrackChangeIsIdempotent() {
        PlayerViewModel vm = new PlayerViewModel(container);
        onMain(() -> vm.init(null, List.of("xiaoxingxing")));
        assertNull(readState(vm));
        onMain(() -> vm.init("unknown", List.of()));
        assertNull(readState(vm));
        onMain(() -> vm.init("xiaoxingxing", List.of("unknown", "liangzhilaohu")));
        PlayerViewModel.UiState state = readState(vm);
        assertNotNull(state);
        assertEquals(List.of("xiaoxingxing", "liangzhilaohu"), state.queue);
        assertEquals(0, state.queueIndex);
        assertEquals("xiaoxingxing", state.item.id);
        onMain(() -> {
            vm.onProgress(5000, 13000, true);
            vm.onTrackChanged("xiaoxingxing");
        });
        assertEquals(5000, readState(vm).positionMs);
        onMain(() -> vm.onTrackChanged("liangzhilaohu"));
        PlayerViewModel.UiState changed = readState(vm);
        assertEquals("liangzhilaohu", changed.item.id);
        assertEquals(1, changed.queueIndex);
        assertEquals(0, changed.positionMs);
    }

    @Test
    public void backgroundTrackChange_keepsPausedPositionAcrossReconnect() throws Exception {
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                playerIntent("xiaoxingxing", List.of("xiaoxingxing", "liangzhilaohu")))) {
            MediaController controller = connect().get(10, TimeUnit.SECONDS);
            waitFor(() -> controller.isPlaying() && controller.getCurrentMediaItem() != null
                    && "xiaoxingxing".equals(controller.getCurrentMediaItem().mediaId));
            onView(withId(R.id.btnPlay)).perform(click());
            waitFor(() -> !controller.isPlaying()
                    && controller.getPlaybackState() == Player.STATE_READY);
            onMain(() -> controller.seekTo(2000));

            scenario.moveToState(Lifecycle.State.CREATED);
            onMain(() -> controller.seekTo(1, 3000));
            waitFor(() -> controller.getCurrentMediaItemIndex() == 1
                    && !controller.isPlaying()
                    && controller.getPlaybackState() == Player.STATE_READY);

            scenario.moveToState(Lifecycle.State.RESUMED);
            waitForText(R.id.songTitle, "两只老虎");
            onMain(() -> {
                assertNotNull(controller.getCurrentMediaItem());
                assertEquals("liangzhilaohu", controller.getCurrentMediaItem().mediaId);
                assertFalse(controller.isPlaying());
                long position = controller.getCurrentPosition();
                assertTrue("position " + position,
                        Math.abs(position - 3000) <= 100);
            });
            onView(withId(R.id.timeCurrent)).check(matches(withText("0:03")));
            scenario.onActivity(activity -> {
                assertTrue(activity.findViewById(R.id.btnPrev).isEnabled());
                assertFalse(activity.findViewById(R.id.btnNext).isEnabled());
            });

            scenario.recreate();
            waitForText(R.id.songTitle, "两只老虎");
            onView(withId(R.id.timeCurrent)).check(matches(withText("0:03")));
            onMain(() -> {
                assertFalse(controller.isPlaying());
                long position = controller.getCurrentPosition();
                assertTrue("position " + position,
                        Math.abs(position - 3000) <= 100);
            });
            scenario.onActivity(activity -> {
                assertTrue(activity.findViewById(R.id.btnPrev).isEnabled());
                assertFalse(activity.findViewById(R.id.btnNext).isEnabled());
            });

            onView(withId(R.id.btnPrev)).perform(click());
            waitFor(() -> controller.getCurrentMediaItem() != null
                    && "xiaoxingxing".equals(controller.getCurrentMediaItem().mediaId));
            scenario.onActivity(activity -> {
                assertFalse(activity.findViewById(R.id.btnPrev).isEnabled());
                assertTrue(activity.findViewById(R.id.btnNext).isEnabled());
            });
        }
    }

    @Test
    public void restoredVisit_contentReadyAfterController_preparesQueue() throws Exception {
        CountDownLatch gateEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        container.executors.diskIO().execute(() -> {
            gateEntered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(gateEntered.await(10, TimeUnit.SECONDS));
        try {
            try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                    playerIntent("xiaoxingxing", List.of("xiaoxingxing")))) {
                MediaController controller = connect().get(10, TimeUnit.SECONDS);
                AtomicReference<PlayerActivity> activityRef = new AtomicReference<>();
                scenario.onActivity(activityRef::set);
                onMain(() -> setBooleanField(activityRef.get(), "playbackStarted", true));
                waitFor(() -> field(activityRef.get(), "controller") != null);
                scenario.onActivity(activity ->
                        assertFalse(activity.findViewById(R.id.btnPlay).isEnabled()));
                onMain(() -> assertEquals(0, controller.getMediaItemCount()));
                release.countDown();
                waitFor(() -> controller.getMediaItemCount() == 1
                        && "xiaoxingxing".equals(controller.getMediaItemAt(0).mediaId)
                        && controller.getPlaybackState() == Player.STATE_READY);
                scenario.onActivity(activity -> {
                    assertTrue(activity.findViewById(R.id.btnPlay).isEnabled());
                    assertTrue(activity.findViewById(R.id.progress).isEnabled());
                });
            }
        } finally {
            release.countDown();
        }
    }

    @Test
    public void sameLengthQueue_withDifferentItems_isReplaced() throws Exception {
        MediaController controller = connect().get(10, TimeUnit.SECONDS);
        onMain(() -> {
            controller.setMediaItems(List.of(
                            mediaItem("xiaoxingxing"), mediaItem("xiaoxingxing")), 0, 0);
            controller.prepare();
        });
        waitFor(() -> controller.getPlaybackState() == Player.STATE_READY);
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                playerIntent("xiaoxingxing", List.of("xiaoxingxing", "liangzhilaohu")))) {
            waitFor(() -> controller.getMediaItemCount() == 2
                    && "liangzhilaohu".equals(controller.getMediaItemAt(1).mediaId));
        }
    }

    @Test
    public void endedTrack_playRestartsFromBeginning() throws Exception {
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                playerIntent("xiaoxingxing", List.of("xiaoxingxing")))) {
            MediaController controller = connect().get(10, TimeUnit.SECONDS);
            waitFor(() -> controller.isPlaying());
            onView(withId(R.id.btnPlay)).perform(click());
            waitFor(() -> !controller.isPlaying()
                    && controller.getPlaybackState() == Player.STATE_READY);
            onMain(() -> controller.seekTo(controller.getDuration()));
            onView(withId(R.id.btnPlay)).perform(click());
            waitFor(() -> controller.getPlaybackState() == Player.STATE_ENDED);
            onView(withId(R.id.btnPlay)).perform(click());
            waitFor(() -> controller.isPlaying()
                    && controller.getCurrentPosition() < 2000);
            onView(withId(R.id.btnPlay)).check(matches(
                    withContentDescription(context.getString(R.string.player_desc_pause))));
            scenario.onActivity(activity -> {
                assertFalse(activity.findViewById(R.id.btnPrev).isEnabled());
                assertFalse(activity.findViewById(R.id.btnNext).isEnabled());
            });
        }
    }

    @Test
    public void rapidLifecycleTransitions_doNotCrash() throws Exception {
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                playerIntent("xiaoxingxing", List.of("xiaoxingxing", "liangzhilaohu")))) {
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.recreate();
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            waitForView(R.id.songTitle);
        }
    }

    @Test
    public void playerScreen_lightAndDarkEvidence() throws Exception {
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                playerIntent("xiaoxingxing", List.of("xiaoxingxing", "liangzhilaohu")))) {
            MediaController controller = connect().get(10, TimeUnit.SECONDS);
            waitFor(() -> controller.isPlaying()
                    || controller.getPlaybackState() == Player.STATE_READY);
            onMain(controller::pause);
            waitFor(() -> !controller.getPlayWhenReady());
            scenario.onActivity(activity -> activity.getDelegate()
                    .setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO));
            waitForView(R.id.songTitle);
            waitForBarAppearance(scenario, false);
            UiEvidence.capture("player_light");
            scenario.onActivity(activity -> activity.getDelegate()
                    .setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES));
            waitForView(R.id.songTitle);
            waitForBarAppearance(scenario, true);
            UiEvidence.capture("player_dark");
            scenario.onActivity(activity -> activity.getDelegate()
                    .setLocalNightMode(AppCompatDelegate.MODE_NIGHT_UNSPECIFIED));
        }
    }

    private Intent playerIntent(String itemId, List<String> queue) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("itemId", itemId);
        intent.putStringArrayListExtra("queue", new ArrayList<>(queue));
        return intent;
    }

    private ListenableFuture<MediaController> connect() {
        AtomicReference<ListenableFuture<MediaController>> ref = new AtomicReference<>();
        onMain(() -> ref.set(new MediaController.Builder(context,
                new SessionToken(context, new ComponentName(context, PlaybackService.class)))
                .buildAsync()));
        controllerFuture = ref.get();
        return controllerFuture;
    }

    private static MediaItem mediaItem(String id) {
        return new MediaItem.Builder()
                .setMediaId(id)
                .setUri("asset:///audio/culture/" + id + ".ogg")
                .build();
    }

    private PlayerViewModel.UiState readState(PlayerViewModel vm) {
        AtomicReference<PlayerViewModel.UiState> ref = new AtomicReference<>();
        onMain(() -> ref.set(vm.state().getValue()));
        return ref.get();
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

    private static Object field(Object target, String name) {
        try {
            Field f = PlayerActivity.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void setBooleanField(Object target, String name, boolean value) {
        try {
            Field f = PlayerActivity.class.getDeclaredField(name);
            f.setAccessible(true);
            f.setBoolean(target, value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void waitForBarAppearance(ActivityScenario<PlayerActivity> scenario,
                                             boolean dark) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        AtomicBoolean ready = new AtomicBoolean();
        do {
            scenario.onActivity(activity -> {
                int mode = activity.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK;
                boolean night = mode == Configuration.UI_MODE_NIGHT_YES;
                WindowInsetsControllerCompat insets = WindowCompat.getInsetsController(
                        activity.getWindow(), activity.getWindow().getDecorView());
                ready.set(night == dark
                        && insets.isAppearanceLightStatusBars() == !dark
                        && insets.isAppearanceLightNavigationBars() == !dark);
            });
            if (ready.get()) {
                return;
            }
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        assertTrue("Timed out waiting for bar appearance dark=" + dark, ready.get());
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

    private static void waitForText(int id, String expected) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        do {
            try {
                onView(withId(id)).check(matches(withText(expected)));
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
