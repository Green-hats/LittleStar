package com.littlestar.app.feature.settings;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.text.format.DateFormat;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.littlestar.app.BuildConfig;
import com.littlestar.app.R;
import com.littlestar.app.core.ui.BlockLayout;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.ParentActivity;
import com.littlestar.app.databinding.ActivitySettingsBinding;
import com.littlestar.app.feature.home.HomeActivity;
import com.littlestar.app.notify.Notifier;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** PIN 保护的学习任务页；目标与学习提醒只在 Save 时写入，今日计划保持固定。 */
public final class SettingsActivity extends ParentActivity {

    private ActivitySettingsBinding binding;
    private final Set<Integer> days = new HashSet<>();
    private final List<BlockLayout> dayButtons = new ArrayList<>();
    private String reminderTime;
    private boolean saving;
    private boolean awaitingNotificationPermission;
    private androidx.appcompat.app.AlertDialog saveWarningDialog;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                awaitingNotificationPermission = false;
                finishSaveWithFeedback();
            });

    // 测试通知不保存表单、不关闭设置页，与保存计划的权限回调分开。
    private final ActivityResultLauncher<String> testNotificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    sendTestNotificationNow();
                } else {
                    showMessage(R.string.help_notification_off);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());
        setSupportActionBar(binding.settingsToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        var settings = container().settings;
        binding.goalStepper.setRange(1, 10);
        binding.goalStepper.setValue(savedInstanceState == null ? settings.getDailyGoal()
                : savedInstanceState.getInt("goal"));
        reminderTime = savedInstanceState == null ? settings.getReminderTime()
                : savedInstanceState.getString("time", settings.getReminderTime());
        if (savedInstanceState == null) {
            days.addAll(settings.getReminderDays());
        } else {
            ArrayList<Integer> restored = savedInstanceState.getIntegerArrayList("days");
            if (restored != null) {
                days.addAll(restored);
            }
        }
        saving = savedInstanceState != null && savedInstanceState.getBoolean("saving");
        awaitingNotificationPermission = savedInstanceState != null
                && savedInstanceState.getBoolean("awaiting_notification_permission");
        binding.btnSave.setEnabled(!saving);
        setupDays();
        renderTime();
        if (saving && !awaitingNotificationPermission) {
            binding.getRoot().post(this::finishSaveWithFeedback);
        }
        binding.timeRow.setOnClickListener(v -> pickTime());
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCancel.setOnClickListener(v -> finish());
        binding.btnSave.setOnClickListener(v -> save());
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_help) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.help_title)
                    .setMessage(getString(R.string.settings_help_message,
                            getString(R.string.help_reminders_body),
                            getString(R.string.help_permission_hint),
                            getString(R.string.help_offline_body)))
                    .setPositiveButton(android.R.string.ok, null).show();
            return true;
        }
        if (id == R.id.action_test_notification) {
            if (Build.VERSION.SDK_INT >= 33
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                testNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                sendTestNotificationNow();
            }
            return true;
        }
        if (id == R.id.action_about) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.help_about_title)
                    .setMessage(getString(R.string.help_about_message, BuildConfig.VERSION_NAME))
                    .setPositiveButton(android.R.string.ok, null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void sendTestNotificationNow() {
        if (reminderNotificationsBlocked()) {
            showMessage(R.string.help_notification_off);
            return;
        }
        container().notifier.ensureChannels();
        container().notifier.test(new Intent(this, HomeActivity.class));
        showMessage(R.string.help_test_sent);
    }

    private void showMessage(int stringId) {
        Snackbar.make(binding.getRoot(), stringId, Snackbar.LENGTH_LONG).show();
    }

    private void setupDays() {
        String[] labels = getResources().getStringArray(R.array.weekday_short);
        String[] descriptions = getResources().getStringArray(R.array.settings_weekdays);
        for (int i = 0; i < 7; i++) {
            int day = i + 1;
            BlockLayout button = new BlockLayout(this, null, 0, R.style.Widget_LS_Block_Icon);
            button.setShadowOffset(getResources().getDimension(R.dimen.ls_shadow_parent));
            button.setLayoutParams(new LinearLayout.LayoutParams(0,
                    getResources().getDimensionPixelSize(R.dimen.ls_weekday_h), 1f));
            button.setContentDescription(descriptions[i]);
            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextAppearance(R.style.TextAppearance_LS_Body);
            label.setGravity(android.view.Gravity.CENTER);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            button.addView(label);
            button.setOnClickListener(v -> {
                if (!days.remove(day)) {
                    days.add(day);
                }
                renderDays();
            });
            dayButtons.add(button);
            binding.weekdayRow.addView(button);
        }
        renderDays();
    }

    private void renderDays() {
        for (int i = 0; i < dayButtons.size(); i++) {
            boolean selected = days.contains(i + 1);
            dayButtons.get(i).setSelected(selected);
            dayButtons.get(i).setFillColor(ContextCompat.getColor(this,
                    selected ? R.color.ls_yellow : R.color.ls_card));
            ((TextView) dayButtons.get(i).getChildAt(0)).setTextColor(ContextCompat.getColor(this,
                    selected ? R.color.ls_on_yellow : R.color.ls_ink));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderTime();
    }

    private LocalTime parsedReminderTime() {
        try {
            return LocalTime.parse(reminderTime);
        } catch (RuntimeException ignored) {
            return LocalTime.of(19, 30);
        }
    }

    private void renderTime() {
        LocalTime time = parsedReminderTime();
        java.util.Calendar displayTime = new java.util.GregorianCalendar(2000,
                java.util.Calendar.JANUARY, 1, time.getHour(), time.getMinute());
        binding.timeValue.setText(DateFormat.getTimeFormat(this).format(displayTime.getTime()));
    }

    private void pickTime() {
        LocalTime time = parsedReminderTime();
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(this)
                        ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(time.getHour()).setMinute(time.getMinute()).build();
        picker.addOnPositiveButtonClickListener(v -> {
            reminderTime = String.format(java.util.Locale.ROOT, "%02d:%02d",
                    picker.getHour(), picker.getMinute());
            renderTime();
        });
        picker.show(getSupportFragmentManager(), "reminder_time");
    }

    private void save() {
        if (saving) {
            return;
        }
        saving = true;
        binding.btnSave.setEnabled(false);
        var settings = container().settings;
        boolean reminderChanged = !days.equals(settings.getReminderDays())
                || !reminderTime.equals(settings.getReminderTime());
        settings.setDailyGoal(binding.goalStepper.getValue());
        settings.setReminderTime(reminderTime);
        settings.setReminderDays(new HashSet<>(days));
        container().reminders.reschedule();
        if (reminderChanged && !days.isEmpty() && Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            settings.setNotifRationaleShown(true);
            awaitingNotificationPermission = true;
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            finishSaveWithFeedback();
        }
    }

    private boolean reminderNotificationsBlocked() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) {
            return true;
        }
        NotificationChannel channel = manager.getNotificationChannel(Notifier.CHANNEL_REMINDER);
        if (channel == null) {
            container().notifier.ensureChannels();
            channel = manager.getNotificationChannel(Notifier.CHANNEL_REMINDER);
        }
        if (channel == null || channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            return true;
        }
        // A blocked channel group also suppresses its otherwise enabled channels.
        android.app.NotificationChannelGroup group = channel.getGroup() == null ? null
                : manager.getNotificationChannelGroup(channel.getGroup());
        return group != null && group.isBlocked();
    }

    private void finishSaveWithFeedback() {
        if (isFinishing() || isDestroyed()
                || (saveWarningDialog != null && saveWarningDialog.isShowing())) {
            return;
        }
        if (!days.isEmpty() && reminderNotificationsBlocked()) {
            saveWarningDialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.peer_settings_reminders_blocked_title)
                    .setMessage(R.string.peer_settings_reminders_blocked_message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                    .setOnCancelListener(dialog -> finish())
                    .show();
        } else {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (saveWarningDialog != null) {
            saveWarningDialog.dismiss();
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("goal", binding.goalStepper.getValue());
        out.putString("time", reminderTime);
        out.putIntegerArrayList("days", new ArrayList<>(days));
        out.putBoolean("saving", saving);
        out.putBoolean("awaiting_notification_permission", awaitingNotificationPermission);
    }
}
