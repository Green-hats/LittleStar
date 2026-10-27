package com.littlestar.app.feature.settings;

import android.Manifest;
import android.content.Intent;
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

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> finish());

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
        binding.btnSave.setEnabled(!saving);
        setupDays();
        binding.timeValue.setText(reminderTime);
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
        android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
        if (!manager.areNotificationsEnabled()) {
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

    private void pickTime() {
        LocalTime time;
        try {
            time = LocalTime.parse(reminderTime);
        } catch (RuntimeException ignored) {
            time = LocalTime.of(19, 30);
        }
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(time.getHour()).setMinute(time.getMinute()).build();
        picker.addOnPositiveButtonClickListener(v -> {
            reminderTime = String.format(java.util.Locale.ROOT, "%02d:%02d",
                    picker.getHour(), picker.getMinute());
            binding.timeValue.setText(reminderTime);
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
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("goal", binding.goalStepper.getValue());
        out.putString("time", reminderTime);
        out.putIntegerArrayList("days", new ArrayList<>(days));
        out.putBoolean("saving", saving);
    }
}
