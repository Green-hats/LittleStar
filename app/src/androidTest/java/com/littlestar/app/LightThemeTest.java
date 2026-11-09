package com.littlestar.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.TypedValue;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.littlestar.app.feature.home.HomeActivity;
import com.littlestar.app.feature.culture.CultureDetailActivity;
import com.littlestar.app.feature.parent.ParentGateActivity;
import com.littlestar.app.feature.player.PlayerActivity;
import com.littlestar.app.feature.settings.SettingsActivity;
import com.littlestar.app.feature.words.ThemesActivity;
import com.littlestar.app.playback.PlaybackService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public final class LightThemeTest {
    private Context context;
    private String originalNightMode;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        originalNightMode = shell("cmd uimode night").replace("Night mode: ", "");
        assertTrue(List.of("yes", "no", "auto", "custom_schedule", "custom_bedtime")
                .contains(originalNightMode));
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode());
    }

    @After
    public void tearDown() throws Exception {
        context.stopService(new Intent(context, PlaybackService.class));
        if (originalNightMode != null) {
            shell("cmd uimode night " + originalNightMode);
        }
    }

    @Test
    public void childParentAndPlayerStayLightWithSystemDarkMode() throws Exception {
        setSystemNightMode("yes");
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            assertLight(scenario);
        }
        try (ActivityScenario<ThemesActivity> scenario = ActivityScenario.launch(ThemesActivity.class)) {
            assertLight(scenario);
        }
        try (ActivityScenario<CultureDetailActivity> scenario = ActivityScenario.launch(
                new Intent(context, CultureDetailActivity.class).putExtra("itemId", "huashetianzu"))) {
            assertLight(scenario);
        }
        try (ActivityScenario<ParentGateActivity> scenario = ActivityScenario.launch(ParentGateActivity.class)) {
            assertLight(scenario);
        }
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            assertLight(scenario);
        }
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                new Intent(context, PlayerActivity.class).putExtra("itemId", "zhaopengyou"))) {
            assertLight(scenario);
        }
    }

    @Test
    public void activePageStaysLightAcrossSystemToggleAndRecreation() throws Exception {
        setSystemNightMode("no");
        try (ActivityScenario<ThemesActivity> scenario = ActivityScenario.launch(ThemesActivity.class)) {
            assertLight(scenario);
            setSystemNightMode("yes");
            assertLight(scenario);
            scenario.recreate();
            assertLight(scenario);
            setSystemNightMode("no");
            assertLight(scenario);
        }
    }

    private static void setSystemNightMode(String mode) throws Exception {
        assertEquals("Night mode: " + mode, shell("cmd uimode night " + mode));
    }

    private static <A extends Activity> void assertLight(ActivityScenario<A> scenario) {
        AtomicBoolean ready = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            scenario.onActivity(activity -> {
                TypedValue lightTheme = new TypedValue();
                boolean light = activity.getTheme().resolveAttribute(
                        android.R.attr.isLightTheme, lightTheme, true) && lightTheme.data != 0;
                int mode = activity.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK;
                var bars = WindowCompat.getInsetsController(activity.getWindow(),
                        activity.getWindow().getDecorView());
                ready.set(light && mode == Configuration.UI_MODE_NIGHT_NO
                        && bars.isAppearanceLightStatusBars()
                        && bars.isAppearanceLightNavigationBars());
            });
            if (ready.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        assertTrue("Page must use a light theme and dark system-bar icons", ready.get());
    }

    private static String shell(String command) throws Exception {
        var descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
        try (var reader = new BufferedReader(new InputStreamReader(
                new ParcelFileDescriptor.AutoCloseInputStream(descriptor), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n")).trim();
        }
    }
}
