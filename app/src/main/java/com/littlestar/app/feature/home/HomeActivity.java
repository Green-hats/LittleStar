package com.littlestar.app.feature.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.databinding.ActivityHomeBinding;
import com.littlestar.app.feature.flashcard.FlashcardActivity;

/**
 * 首页展示每日学习进度，学习入口进入 Flashcard(SESSION)。
 * firstLaunch=true 时盖首次引导（§7.20）；设置入口经过 PIN 验证。
 */
public final class HomeActivity extends ChildActivity {

    private static final int REQ_POST_NOTIFICATIONS = 41;

    private ActivityHomeBinding binding;
    private HomeViewModel vm;

    // 引导态本地状态
    private int obAge = 4;
    private int obGoal = 3;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());
        getLifecycle().addObserver(new HomePlaybackControls(this, binding));

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new HomeViewModel(container()))).get(HomeViewModel.class);
        vm.state().observe(this, this::render);

        binding.btnStart.setOnClickListener(v -> startSession());
        // 内容模块与受 PIN 保护的设置入口。
        binding.moduleWords.setOnClickListener(v -> {
            startActivity(new Intent(this,
                    com.littlestar.app.feature.words.ThemesActivity.class));
            Motion.childEnter(this);
        });
        binding.moduleCulture.setOnClickListener(v -> {
            startActivity(new Intent(this,
                    com.littlestar.app.feature.culture.CultureCategoriesActivity.class));
            Motion.childEnter(this);
        });
        binding.parentEntry.setOnClickListener(v -> {
            startActivity(com.littlestar.app.feature.parent.ParentGateActivity.intent(this));
            Motion.parentFade(this);
        });

        setupOnboarding();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (vm != null) {
            vm.refresh();
        }
    }

    // ---------- 正常首页 ----------

    private void render(HomeViewModel.UiState s) {
        if (s == null) {
            return;
        }
        if (s.firstLaunch) {
            binding.onboarding.getRoot().setVisibility(View.VISIBLE);
            return;
        }
        binding.onboarding.getRoot().setVisibility(View.GONE);
        if (s.loading) {
            return;
        }

        boolean done = s.isDailyComplete();
        binding.btnStart.setText(done ? R.string.home_review : R.string.home_start);

        // 今日进度
        binding.todayProgress.setMax(Math.max(1, s.goal));
        binding.todayProgress.setProgressCompat(s.learnedToday, false);
        binding.todayProgressText.setText(done
                ? getString(R.string.home_progress_done)
                : getString(R.string.home_today_progress, s.learnedToday, s.goal));
    }

    /** START：进会话。会话由 Flashcard(SESSION) 进入时用 SessionBuilder 创建。 */
    private void startSession() {
        // 防连点：双击 START 不重复建会话（#10）
        if (!binding.btnStart.isEnabled()) {
            return;
        }
        binding.btnStart.setEnabled(false);
        binding.btnStart.postDelayed(() -> binding.btnStart.setEnabled(true), 800);

        startActivity(FlashcardActivity.sessionIntent(this));
        Motion.childEnter(this);
    }

    // ---------- 首次引导（§7.20） ----------
    private void setupOnboarding() {
        int[] ages = {3, 4, 5, 6};
        Chip[] ageChips = {
                binding.onboarding.obAge3, binding.onboarding.obAge4,
                binding.onboarding.obAge5, binding.onboarding.obAge6};
        Chip[] goalChips = {
                binding.onboarding.obGoal1, binding.onboarding.obGoal3, binding.onboarding.obGoal5};
        int[] goals = {1, 3, 5};

        for (int i = 0; i < ages.length; i++) {
            int age = ages[i];
            ageChips[i].setText(getString(R.string.ob_age_years, age));
            ageChips[i].setChecked(age == obAge);
            ageChips[i].setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    obAge = age;
                    updateSuggestion();
                }
            });
        }
        for (int i = 0; i < goals.length; i++) {
            int goal = goals[i];
            goalChips[i].setText(getString(R.string.ob_goal_words, goal));
            goalChips[i].setChecked(goal == obGoal);
            goalChips[i].setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    obGoal = goal;
                    updateSuggestion();
                }
            });
        }
        updateSuggestion();

        binding.onboarding.obNext.setOnClickListener(v -> {
            // PIN 移至首次打开设置时创建；引导仅保留目标与提醒。
            binding.onboarding.obStep1.setVisibility(View.GONE);
            binding.onboarding.obStep2.setVisibility(View.VISIBLE);
            binding.onboarding.getRoot().scrollTo(0, 0);
            binding.onboarding.obNotifyDesc.setText(getString(R.string.ob_notify_desc, obGoal));
        });
        binding.onboarding.obSkip1.setOnClickListener(v -> vm.completeOnboarding(obGoal));
        // 允许提醒：用户点了才申请 POST_NOTIFICATIONS（Android 13+）；拒绝不阻塞（§10.3）
        binding.onboarding.obAllow.setOnClickListener(v -> {
            container().settings.setNotifRationaleShown(true);
            if (android.os.Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        REQ_POST_NOTIFICATIONS);
            }
            vm.completeOnboarding(obGoal);
        });
        binding.onboarding.obLater.setOnClickListener(v -> vm.completeOnboarding(obGoal));
    }

    private void updateSuggestion() {
        binding.onboarding.obSuggestion.setText(getString(R.string.ob_suggestion, obAge, obGoal));
    }
}
