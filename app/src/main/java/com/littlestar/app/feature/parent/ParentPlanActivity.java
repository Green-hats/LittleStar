package com.littlestar.app.feature.parent;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.littlestar.app.R;
import com.littlestar.app.core.ui.BlockLayout;
import com.littlestar.app.core.ui.HanziTextView;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.ParentActivity;
import com.littlestar.app.databinding.ActivityParentPlanBinding;

import java.util.HashSet;
import java.util.Set;

/**
 * 学习计划（§7.15 / §9.15）。保存：SettingsStore + reminders.reschedule()，
 * 当天 daily_plan 不重算（明天起生效，Snackbar 写明）。
 * 首次保存提醒时间：先一句说明再申请 POST_NOTIFICATIONS；被拒显示提示行。
 */
public final class ParentPlanActivity extends ParentActivity {

    private ActivityParentPlanBinding binding;
    private final Set<Integer> days = new HashSet<>();
    private String reminderTime;
    private int goal;
    private BlockLayout[] dayBlocks;

    private final ActivityResultLauncher<String> notifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    binding.notifOffHint.setVisibility(android.view.View.VISIBLE);
                }
                finishSave();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityParentPlanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        goal = container().settings.getDailyGoal();
        reminderTime = container().settings.getReminderTime();
        days.addAll(container().settings.getReminderDays());

        binding.goalStepper.setRange(1, 10);
        binding.goalStepper.setValue(goal);
        binding.goalStepper.setOnValueChangeListener(v -> {
            goal = v;
            updateWeekly();
        });
        binding.timeValue.setText(reminderTime);
        binding.timeRow.setOnClickListener(v -> pickTime());
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCancel.setOnClickListener(v -> finish());
        binding.btnSave.setOnClickListener(v -> save());

        buildWeekdayRow();
        updateWeekly();
        updateSaveEnabled();
    }

    /** 7 个 44×48 星期块，间距 0（评审 P1-5）；选中 = 黄底。 */
    private void buildWeekdayRow() {
        String[] names = getResources().getStringArray(R.array.weekday_short);
        dayBlocks = new BlockLayout[7];
        for (int i = 0; i < 7; i++) {
            int day = i + 1;
            BlockLayout block = new BlockLayout(this);
            block.setShadowOffset(getResources().getDimension(R.dimen.ls_shadow_parent));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    getResources().getDimensionPixelSize(R.dimen.ls_weekday_w),
                    getResources().getDimensionPixelSize(R.dimen.ls_weekday_h));
            block.setLayoutParams(lp);
            HanziTextView text = new HanziTextView(this);
            text.setText(names[i]);
            text.setTextSize(16);
            text.setGravity(android.view.Gravity.CENTER);
            block.addView(text);
            block.setOnClickListener(v -> {
                if (days.contains(day)) {
                    days.remove(day);
                } else {
                    days.add(day);
                }
                updateWeekly();
                updateSaveEnabled();
                refreshDayBlocks();
            });
            dayBlocks[i] = block;
            binding.weekdayRow.addView(block);
        }
        refreshDayBlocks();
    }

    private void refreshDayBlocks() {
        int yellow = ContextCompat.getColor(this, R.color.ls_yellow);
        int card = ContextCompat.getColor(this, R.color.ls_card);
        for (int i = 0; i < 7; i++) {
            dayBlocks[i].setFillColor(days.contains(i + 1) ? yellow : card);
        }
    }

    private void pickTime() {
        int hour = 19;
        int minute = 30;
        try {
            hour = Integer.parseInt(reminderTime.substring(0, 2));
            minute = Integer.parseInt(reminderTime.substring(3, 5));
        } catch (RuntimeException ignored) {
            // 坏数据回退默认
        }
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(hour)
                .setMinute(minute)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            reminderTime = String.format(java.util.Locale.ROOT, "%02d:%02d",
                    picker.getHour(), picker.getMinute());
            binding.timeValue.setText(reminderTime);
        });
        picker.show(getSupportFragmentManager(), "time");
    }

    private void updateWeekly() {
        binding.weeklyGoal.setText(getString(R.string.pp_weekly_fmt, goal * days.size()));
        binding.weeklyGoalSub.setText(getString(R.string.pp_weekly_sub_fmt, goal, days.size()));
    }

    private void updateSaveEnabled() {
        boolean ok = !days.isEmpty();
        binding.btnSave.setAlpha(ok ? 1f : 0.4f);
        binding.btnSave.setEnabled(ok);
    }

    private void save() {
        if (days.isEmpty()) {
            Snackbar.make(binding.getRoot(), R.string.pp_need_day, Snackbar.LENGTH_SHORT).show();
            return;
        }
        container().settings.setDailyGoal(goal);
        container().settings.setReminderTime(reminderTime);
        container().settings.setReminderDays(days);
        // 首次保存提醒时间：先说明再申请通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            if (!container().settings.isNotifRationaleShown()) {
                container().settings.setNotifRationaleShown(true);
                Snackbar.make(binding.getRoot(), R.string.pp_notif_rationale,
                        Snackbar.LENGTH_SHORT).show();
            }
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        finishSave();
    }

    private void finishSave() {
        container().reminders.reschedule();
        Snackbar.make(binding.getRoot(), R.string.pp_saved, Snackbar.LENGTH_SHORT).show();
        binding.getRoot().postDelayed(this::finish, 800);
    }
}
