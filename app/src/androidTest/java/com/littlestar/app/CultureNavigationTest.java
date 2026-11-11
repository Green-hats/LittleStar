package com.littlestar.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.littlestar.app.feature.culture.CultureDetailActivity;
import com.littlestar.app.feature.culture.CultureListActivity;
import com.littlestar.app.feature.player.PlayerActivity;
import com.littlestar.app.playback.PlaybackService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public final class CultureNavigationTest {
    private Context context;
    private AppContainer container;
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private boolean onboarded;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        container = ((LittleStarApp) context).container();
        container.content.ensureLoaded();
        onboarded = container.settings.isOnboarded();
        container.settings.setOnboarded(true);
    }

    @After public void tearDown() {
        container.settings.setOnboarded(onboarded);
        context.stopService(new Intent(context, PlaybackService.class));
    }

    @Test public void titleOpensDetailAndBackReturnsToList() {
        var selected = container.content.itemsOfCategory("songs").get(0);
        var monitor = instrumentation.addMonitor(CultureDetailActivity.class.getName(), null, false);
        Activity detail = null;
        try (var scenario = ActivityScenario.<CultureListActivity>launch(
                new Intent(context, CultureListActivity.class).putExtra("categoryId", "songs"))) {
            waitForRow(scenario, 0);
            onView(withText(selected.titleEn)).perform(click());
            detail = instrumentation.waitForMonitorWithTimeout(monitor, 5000);
            assertNotNull("Title should open details", detail);
            assertEquals(selected.id, detail.getIntent().getStringExtra("itemId"));
            pressBack();
            onView(withId(R.id.itemList)).check(matches(isDisplayed()));
        } finally {
            Activity opened = detail;
            if (opened != null) instrumentation.runOnMainSync(opened::finish);
            instrumentation.removeMonitor(monitor);
        }
    }

    @Test public void playStartsSelectedItemWithCategoryQueueAndReturnsToList() {
        var songs = container.content.itemsOfCategory("songs");
        var selected = songs.get(1);
        var monitor = instrumentation.addMonitor(PlayerActivity.class.getName(), null, false);
        Activity player = null;
        try (var scenario = ActivityScenario.<CultureListActivity>launch(
                new Intent(context, CultureListActivity.class).putExtra("categoryId", "songs"))) {
            waitForRow(scenario, 1);
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.itemList);
                list.findViewHolderForAdapterPosition(1).itemView.findViewById(R.id.btnPlay).performClick();
            });
            player = instrumentation.waitForMonitorWithTimeout(monitor, 5000);
            assertNotNull("PLAY should open the player directly", player);
            assertEquals(selected.id, player.getIntent().getStringExtra("itemId"));
            var expected = new ArrayList<String>();
            for (var song : songs) expected.add(song.id);
            assertEquals(expected, player.getIntent().getStringArrayListExtra("queue"));
            pressBack();
            onView(withId(R.id.itemList)).check(matches(isDisplayed()));
        } finally {
            Activity opened = player;
            if (opened != null) instrumentation.runOnMainSync(opened::finish);
            instrumentation.removeMonitor(monitor);
        }
    }

    private static void waitForRow(ActivityScenario<CultureListActivity> scenario, int position) {
        AtomicBoolean ready = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.itemList);
                if (list.getAdapter().getItemCount() <= position) return;
                ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(position, 0);
                ready.set(list.findViewHolderForAdapterPosition(position) != null);
            });
            if (ready.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Culture row did not become available");
    }
}
