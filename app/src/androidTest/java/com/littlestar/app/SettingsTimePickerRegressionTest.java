package com.littlestar.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.littlestar.app.feature.settings.SettingsActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
public final class SettingsTimePickerRegressionTest {

    private static final String PICKER_TAG = "reminder_time";

    private AppContainer container;
    private String originalTime;
    private Set<Integer> originalDays;
    private int originalGoal;

    @Before
    public void setUp() {
        container = ((LittleStarApp) ApplicationProvider.getApplicationContext()).container();
        originalTime = container.settings.getReminderTime();
        originalDays = container.settings.getReminderDays();
        originalGoal = container.settings.getDailyGoal();
        container.settings.setReminderTime("19:30");
        container.settings.setReminderDays(Set.of());
    }

    @After
    public void tearDown() {
        container.settings.setReminderTime(originalTime);
        container.settings.setReminderDays(originalDays);
        container.settings.setDailyGoal(originalGoal);
    }

    @Test
    public void recreatedPicker_positiveStillUpdatesDraftAndSavePersists() {
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.timeValue)).check(matches(withText("19:30")));
            AtomicReference<SettingsActivity> activityRef = new AtomicReference<>();
            scenario.onActivity(activityRef::set);
            scenario.onActivity(activity ->
                    activity.findViewById(R.id.timeRow).performClick());
            MaterialTimePicker first = waitForPicker(activityRef);
            onMain(() -> {
                first.setHour(8);
                first.setMinute(45);
            });

            scenario.recreate();
            scenario.onActivity(activityRef::set);

            MaterialTimePicker restored = waitForPicker(activityRef);
            onMain(() -> {
                assertEquals(8, restored.getHour());
                assertEquals(45, restored.getMinute());
                restored.requireDialog()
                        .findViewById(com.google.android.material.R.id.material_timepicker_ok_button)
                        .performClick();
            });
            onView(withId(R.id.timeValue)).check(matches(withText("08:45")));
            assertEquals("19:30", container.settings.getReminderTime());
            scenario.onActivity(activity -> activity.findViewById(R.id.btnSave).performClick());
            assertEquals("08:45", container.settings.getReminderTime());
        }
    }

    @Test
    public void doubleTimeRowClick_showsSinglePicker() {
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.timeRow).performClick();
                activity.findViewById(R.id.timeRow).performClick();
            });
            scenario.onActivity(activity -> {
                long count = activity.getSupportFragmentManager().getFragments().stream()
                        .filter(f -> PICKER_TAG.equals(f.getTag()))
                        .count();
                assertEquals(1, count);
            });
            scenario.onActivity(activity -> {
                MaterialTimePicker picker = picker(activity);
                if (picker != null) {
                    picker.dismiss();
                }
            });
        }
    }

    @Test
    public void cancelPicker_keepsDraftAndPersistence() {
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            AtomicReference<SettingsActivity> activityRef = new AtomicReference<>();
            scenario.onActivity(activityRef::set);
            scenario.onActivity(activity ->
                    activity.findViewById(R.id.timeRow).performClick());
            MaterialTimePicker picker = waitForPicker(activityRef);
            onMain(() -> {
                picker.setHour(8);
                picker.setMinute(45);
            });
            onMain(() -> picker.requireDialog()
                    .findViewById(com.google.android.material.R.id.material_timepicker_cancel_button)
                    .performClick());
            onView(withId(R.id.timeValue)).check(matches(withText("19:30")));
            assertEquals("19:30", container.settings.getReminderTime());
        }
    }

    private static MaterialTimePicker waitForPicker(
            AtomicReference<SettingsActivity> activityRef) {
        AtomicReference<MaterialTimePicker> ref = new AtomicReference<>();
        waitFor(() -> {
            SettingsActivity activity = activityRef.get();
            if (activity == null) {
                return false;
            }
            MaterialTimePicker picker = picker(activity);
            if (picker != null && picker.getDialog() != null
                    && picker.getDialog().isShowing()) {
                ref.set(picker);
                return true;
            }
            return false;
        });
        return ref.get();
    }

    private static MaterialTimePicker picker(SettingsActivity activity) {
        Fragment fragment = activity.getSupportFragmentManager()
                .findFragmentByTag(PICKER_TAG);
        return fragment instanceof MaterialTimePicker ? (MaterialTimePicker) fragment : null;
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
        assertTrue("Timed out waiting for condition", ready.get());
    }
}
