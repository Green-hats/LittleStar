package com.littlestar.app.feature.parent;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.core.ui.ParentActivity;
import com.littlestar.app.databinding.ActivityParentDashboardBinding;

import java.util.List;

/** 学习概览（§7.13 / §9.13）。三行入口 Time/Plan/Records；右上 Settings → SettingsSheet。 */
public final class ParentDashboardActivity extends ParentActivity {

    private ActivityParentDashboardBinding binding;
    private ParentDashboardViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityParentDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new ParentDashboardViewModel(container())))
                .get(ParentDashboardViewModel.class);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSettings.setOnClickListener(v ->
                new SettingsSheet().show(getSupportFragmentManager(), "settings"));
        binding.rowTime.setOnClickListener(v -> open(ParentTimeActivity.class));
        binding.rowPlan.setOnClickListener(v -> open(ParentPlanActivity.class));
        binding.rowRecords.setOnClickListener(v -> open(ParentRecordsActivity.class));

        vm.state().observe(this, this::render);
        vm.load();
    }

    private void open(Class<? extends android.app.Activity> dest) {
        startActivity(new android.content.Intent(this, dest));
        Motion.parentFade(this);
    }

    private void render(ParentDashboardViewModel.UiState s) {
        if (s == null) {
            return;
        }
        String[] weekdays = getResources().getStringArray(R.array.weekday_short);
        java.time.LocalDate today = container().clock.today();
        binding.todayDate.setText(getString(R.string.pd_date_fmt,
                today.format(java.time.format.DateTimeFormatter.ofPattern("M月d日")),
                weekdays[today.getDayOfWeek().getValue() - 1]));

        binding.statMinutes.setText(String.valueOf(s.todayMinutes));
        binding.statLearned.setText(String.valueOf(s.todayLearned));
        binding.statQuiz.setText(s.todayQuizPercent < 0
                ? getString(R.string.pd_quiz_none) : String.valueOf(s.todayQuizPercent));

        int[] week = new int[7];
        List<Integer> data = s.weekMinutes;
        for (int i = 0; i < 7 && i < data.size(); i++) {
            week[i] = data.get(i);
        }
        binding.weekChart.setData(week);
        // 柱状图是分钟数；标题右侧是本周学会字数（与设计图一致）
        binding.weekTotal.setText(getString(R.string.pd_week_fmt, s.weekLearned));

        binding.rowTimeSub.setText(getString(R.string.pd_row_time_sub_fmt,
                container().settings.getDailyLimitMin(),
                getString(container().settings.isRestReminder() ? R.string.pd_on : R.string.pd_off)));
        binding.rowPlanSub.setText(getString(R.string.pd_row_plan_sub_fmt,
                container().settings.getDailyGoal(), container().settings.getReminderTime()));
    }
}
