package com.littlestar.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.widget.LinearLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.littlestar.app.core.ui.StepperView;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.feature.parent.ParentGateActivity;
import com.littlestar.app.feature.settings.SettingsActivity;
import com.littlestar.app.feature.home.HomeActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** 学习任务配置：PIN、保存、取消、页面重建及旧时长设置兼容。 */
@RunWith(AndroidJUnit4.class)
public final class SettingsFlowTest {

    private AppContainer container;

    @Before
    public void prepare() throws InterruptedException {
        container = ((LittleStarApp) ApplicationProvider.getApplicationContext()).container();
        container.settings.setOnboarded(true);
        container.settings.setParentPin("1234");
        container.settings.setDailyGoal(3);
        container.settings.setReminderDays(Set.of());
        container.store.update(state -> {
            state.words.clear();
            state.sessions.clear();
            state.dailyPlans.clear();
            state.usage.clear();
            state.nextSessionId = 1;
            container.content.ensureLoaded();
            container.sessionBuilder.todayPlan(state, 3);
        });
        awaitStore();
    }

    @Test
    public void existingPin_wrongStays_correctOpensSettings() {
        try (ActivityScenario<ParentGateActivity> scenario = ActivityScenario.launch(ParentGateActivity.class)) {
            onView(withId(R.id.answerInput)).perform(replaceText("0000"));
            closeSoftKeyboard();
            onView(withId(R.id.btnConfirm)).perform(click());
            onView(withId(R.id.gateRoot)).check(matches(isDisplayed()));
            onView(withId(R.id.answerInput)).check(matches(withText("")));
            enterPin();
            waitFor(R.id.settingsTitle);
            onView(withId(R.id.btnBack)).perform(click());
        }
    }

    @Test
    public void firstSettingsVisit_createsPin() {
        container.settings.setParentPin("");
        try (ActivityScenario<ParentGateActivity> scenario = ActivityScenario.launch(ParentGateActivity.class)) {
            onView(withId(R.id.questionText)).check(matches(withText(R.string.pg_pin_create)));
            enterPin();
            onView(withId(R.id.questionText))
                    .check(matches(withText(R.string.peer_settings_pin_confirm)));
            assertEquals("", container.settings.getParentPin());
            enterPin();
            waitFor(R.id.settingsTitle);
            assertEquals("1234", container.settings.getParentPin());
            onView(withId(R.id.btnBack)).perform(click());
        }
    }

    @Test
    public void firstPin_mismatchDoesNotStore_andAllowsFreshPair() {
        container.settings.setParentPin("");
        try (ActivityScenario<ParentGateActivity> scenario = ActivityScenario.launch(ParentGateActivity.class)) {
            enterPin();
            onView(withId(R.id.answerInput)).perform(replaceText("9876"));
            closeSoftKeyboard();
            onView(withId(R.id.btnConfirm)).perform(click());
            assertEquals("", container.settings.getParentPin());
            onView(withId(R.id.questionText)).check(matches(withText(R.string.pg_pin_create)));
            enterPin();
            assertEquals("", container.settings.getParentPin());
            enterPin();
            waitFor(R.id.settingsTitle);
            assertEquals("1234", container.settings.getParentPin());
            onView(withId(R.id.btnBack)).perform(click());
        }
    }

    @Test
    public void firstPin_backBeforeConfirmationDoesNotStore() {
        container.settings.setParentPin("");
        try (ActivityScenario<ParentGateActivity> scenario = ActivityScenario.launch(ParentGateActivity.class)) {
            enterPin();
            onView(withId(R.id.btnBack)).perform(click());
            assertEquals("", container.settings.getParentPin());
        }
    }

    @Test
    public void firstPin_recreationRestartsUnconfirmedSetup() {
        container.settings.setParentPin("");
        try (ActivityScenario<ParentGateActivity> scenario = ActivityScenario.launch(ParentGateActivity.class)) {
            enterPin();
            scenario.recreate();
            onView(withId(R.id.questionText)).check(matches(withText(R.string.pg_pin_create)));
            assertEquals("", container.settings.getParentPin());
            enterPin();
            assertEquals("", container.settings.getParentPin());
            enterPin();
            waitFor(R.id.settingsTitle);
            onView(withId(R.id.btnBack)).perform(click());
        }
    }

    @Test
    public void cancel_discardsDraft() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                ((StepperView) activity.findViewById(R.id.goalStepper)).setValue(5);
                ((LinearLayout) activity.findViewById(R.id.weekdayRow)).getChildAt(1).performClick();
                activity.findViewById(R.id.btnCancel).performClick();
            });
            assertEquals(3, container.settings.getDailyGoal());
            assertTrue(container.settings.getReminderDays().isEmpty());
        }
    }

    @Test
    public void save_keepsTodaysPlanAndHistory() throws InterruptedException {
        container.settings.setReminderDays(Set.of(1, 3));
        container.store.update(state -> {
            ProgressState.Session session = new ProgressState.Session();
            session.id = 42;
            session.completedAt = 1L;
            state.sessions.add(session);
        });
        awaitStore();
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                ((StepperView) activity.findViewById(R.id.goalStepper)).setValue(5);
                activity.findViewById(R.id.btnSave).performClick();
            });
            assertEquals(5, container.settings.getDailyGoal());
            assertEquals(Set.of(1, 3), container.settings.getReminderDays());
            int[] snapshot = readState(state -> new int[]{
                    state.dailyPlans.get(container.clock.today().toString()).goal,
                    state.dailyPlans.get(container.clock.today().toString()).newWordIds.size(),
                    (int) state.sessions.get(0).id, state.sessions.get(0).completedAt.intValue()});
            assertArrayEquals(new int[]{3, 3, 42, 1}, snapshot);
        }
    }

    /** Run with app notifications or the reminder channel disabled; no device settings changed here. */
    @Test
    public void save_blockedUnchangedReminderWarns_andKeepsSavedPlan() {
        Context context = ApplicationProvider.getApplicationContext();
        android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
        android.app.NotificationChannel channel = manager.getNotificationChannel(
                com.littlestar.app.notify.Notifier.CHANNEL_REMINDER);
        org.junit.Assume.assumeTrue("Requires blocked app notifications or reminder channel",
                !manager.areNotificationsEnabled() || (channel != null
                        && channel.getImportance() == android.app.NotificationManager.IMPORTANCE_NONE));
        container.settings.setReminderDays(Set.of(1, 3));
        container.settings.setReminderTime("19:30");
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                ((StepperView) activity.findViewById(R.id.goalStepper)).setValue(5);
                activity.findViewById(R.id.btnSave).performClick();
            });
            onView(withText(R.string.peer_settings_reminders_blocked_message))
                    .inRoot(isDialog()).check(matches(isDisplayed()));
            assertEquals(5, container.settings.getDailyGoal());
            assertEquals(Set.of(1, 3), container.settings.getReminderDays());
            assertEquals("19:30", container.settings.getReminderTime());
            scenario.recreate();
            onView(withText(R.string.peer_settings_reminders_blocked_message))
                    .inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
        }
    }

    /** Parent should run once in 12-hour mode and once in 24-hour mode. */
    @Test
    public void reminderTime_usesDeviceFormat_andPreserves24HourStorage() {
        container.settings.setReminderTime("19:30");
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                java.util.Calendar time = new java.util.GregorianCalendar(2000,
                        java.util.Calendar.JANUARY, 1, 19, 30);
                assertEquals(android.text.format.DateFormat.getTimeFormat(activity).format(time.getTime()),
                        ((android.widget.TextView) activity.findViewById(R.id.timeValue)).getText().toString());
            });
            onView(withId(R.id.timeRow)).perform(click());
            boolean is24Hour = android.text.format.DateFormat.is24HourFormat(
                    ApplicationProvider.getApplicationContext());
            if (is24Hour) {
                onView(org.hamcrest.Matchers.allOf(
                        withId(com.google.android.material.R.id.material_clock_period_toggle), isDisplayed()))
                        .inRoot(isDialog()).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
            } else {
                onView(org.hamcrest.Matchers.allOf(
                        withId(com.google.android.material.R.id.material_clock_period_toggle), isDisplayed()))
                        .inRoot(isDialog()).check(matches(isDisplayed()));
            }
            onView(withId(com.google.android.material.R.id.material_timepicker_ok_button))
                    .inRoot(isDialog()).perform(click());
            onView(withId(R.id.btnSave)).perform(click());
            assertEquals("19:30", container.settings.getReminderTime());
        }
    }

    @Test
    public void recreation_keepsUnsavedDraft() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                ((StepperView) activity.findViewById(R.id.goalStepper)).setValue(7);
                ((LinearLayout) activity.findViewById(R.id.weekdayRow)).getChildAt(1).performClick();
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(7, ((StepperView) activity.findViewById(R.id.goalStepper)).getValue());
                assertTrue(((LinearLayout) activity.findViewById(R.id.weekdayRow)).getChildAt(1).isSelected());
                assertEquals(3, container.settings.getDailyGoal());
            });
        }
    }

    @Test
    public void deselectAllDays_disablesDailyReminder() {
        container.settings.setReminderDays(Set.of(1, 3));
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                LinearLayout weekdays = activity.findViewById(R.id.weekdayRow);
                weekdays.getChildAt(0).performClick();
                weekdays.getChildAt(2).performClick();
                activity.findViewById(R.id.btnSave).performClick();
            });
            assertTrue(container.settings.getReminderDays().isEmpty());
            assertEquals(-1, container.reminders.nextTriggerMillis());
        }
    }

    @Test
    public void helpAndAboutMenu_keepUnsavedPlanDraft() throws Exception {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity ->
                    ((StepperView) activity.findViewById(R.id.goalStepper)).setValue(7));
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext());
            UiEvidence.capture("settings_menu");
            onView(withText(R.string.help_title)).perform(click());
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext());
            onView(withText(R.string.help_menu_about)).perform(click());
            onView(withText(R.string.help_about_title)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
            scenario.onActivity(activity -> assertEquals(7,
                    ((StepperView) activity.findViewById(R.id.goalStepper)).getValue()));
            assertEquals(3, container.settings.getDailyGoal());
        }
    }

    @Test
    public void legacyTimeLimit_doesNotBlockLearning() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putInt("daily_limit_min", 1).putBoolean("rest_reminder", true).apply();
        container.store.update(state -> {
            ProgressState.DailyUsage usage = new ProgressState.DailyUsage();
            usage.childSeconds = 60 * 60;
            state.usage.put(container.clock.today().toString(), usage);
        });
        awaitStore();
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            waitFor(R.id.btnStart);
            onView(withId(R.id.modeWord)).perform(click());
            onView(withId(R.id.btnStart)).perform(click());
            waitFor(R.id.flashcardRoot);
        }
    }

    private void awaitStore() throws InterruptedException {
        readState(state -> true);
    }

    private <T> T readState(Function<ProgressState, T> query) throws InterruptedException {
        AtomicReference<T> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        container.store.read(query, value -> {
            result.set(value);
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        return result.get();
    }

    private static void enterPin() {
        onView(withId(R.id.answerInput)).perform(replaceText("1234"));
        closeSoftKeyboard();
        onView(withId(R.id.btnConfirm)).perform(click());
    }

    private static void waitFor(int id) {
        long deadline = SystemClock.elapsedRealtime() + 10_000;
        do {
            try {
                onView(withId(id)).check(matches(isDisplayed()));
                return;
            } catch (RuntimeException | AssertionError ignored) {
                SystemClock.sleep(100);
            }
        } while (SystemClock.elapsedRealtime() < deadline);
        onView(withId(id)).check(matches(isDisplayed()));
    }
}
