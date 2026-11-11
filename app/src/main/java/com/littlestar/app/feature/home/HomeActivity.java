package com.littlestar.app.feature.home;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.lifecycle.ViewModelProvider;

import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.databinding.ActivityHomeBinding;
import com.littlestar.app.feature.flashcard.FlashcardActivity;

/**
 * 首页（§7.1）。每日学习主流程：今日字 + START → Flashcard(SESSION)。
 * firstLaunch=true 时盖首次引导（§7.20）；设置入口经过 PIN 验证。
 */
public final class HomeActivity extends ChildActivity {

    private static final int REQ_POST_NOTIFICATIONS = 41;

    /** 当天第一次进入自动朗读的日期记录（Activity 级 SharedPreferences，不进 SettingsStore）。 */
    private static final String PREF_LAST_SPEAK_DATE = "last_speak_date";

    private ActivityHomeBinding binding;
    private HomeViewModel vm;

    // 引导态本地状态
    private int obAge = 4;
    private int obGoal = 3;
    private boolean spokenToday;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new HomeViewModel(container()))).get(HomeViewModel.class);
        vm.state().observe(this, this::render);

        binding.btnStart.setOnClickListener(v -> startSession());
        binding.btnListen.setOnClickListener(v -> speakHero());
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

        int green = ContextCompat.getColor(this, R.color.ls_green);
        int ink = ContextCompat.getColor(this, R.color.ls_ink);
        boolean done = s.isDailyComplete();
        Word w = s.nextWord;
        binding.todayLabel.setText(done ? R.string.home_today_done : R.string.home_today_words);
        binding.todayLabel.setTextColor(done ? green : ink);
        binding.heroHanzi.setText(w != null ? w.hanzi : "");
        binding.heroPinyin.setText(w != null ? w.pinyin : "");
        binding.heroEnglish.setText(w != null ? w.english : "");
        binding.btnStartText.setText(done ? R.string.home_review : R.string.home_start);
        maybeAutoSpeak(s);

        // 今日进度
        binding.todayProgress.setSegments(Math.max(1, s.goal));
        binding.todayProgress.setFilled(s.learnedToday);
        binding.todayProgressText.setText(done
                ? getString(R.string.home_progress_done)
                : getString(R.string.home_today_progress, s.learnedToday, s.goal));

    }

    /** 进入时当天第一次自动朗读今日字（§9.1）。 */
    private void maybeAutoSpeak(HomeViewModel.UiState s) {
        if (spokenToday || s.nextWord == null) {
            return;
        }
        spokenToday = true;   // 先占位，防止多次 render 重复触发
        String today = container().clock.today().toString();
        // SharedPreferences 首次读会加载文件：放到 diskIO，避免主线程磁盘读（StrictMode）
        container().executors.diskIO().execute(() -> {
            SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
            boolean already = today.equals(prefs.getString(PREF_LAST_SPEAK_DATE, null));
            if (!already) {
                prefs.edit().putString(PREF_LAST_SPEAK_DATE, today).apply();
            }
            container().executors.mainThread().execute(() -> {
                if (!already && !isFinishing() && !isDestroyed()) {
                    speakHero();
                }
            });
        });
    }

    private void speakHero() {
        HomeViewModel.UiState s = vm.state().getValue();
        if (s != null && s.nextWord != null) {
            container().speech.play(s.nextWord);
        }
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
        com.littlestar.app.core.ui.BlockLayout[] ageChips = {
                binding.onboarding.obAge3, binding.onboarding.obAge4,
                binding.onboarding.obAge5, binding.onboarding.obAge6};
        com.littlestar.app.core.ui.BlockLayout[] goalChips = {
                binding.onboarding.obGoal1, binding.onboarding.obGoal3, binding.onboarding.obGoal5};
        int[] goals = {1, 3, 5};

        for (int i = 0; i < ages.length; i++) {
            int age = ages[i];
            setChipText(ageChips[i], getString(R.string.ob_age_years, age));
            ageChips[i].setOnClickListener(v -> selectAge(age, ageChips, ages));
        }
        for (int i = 0; i < goals.length; i++) {
            int goal = goals[i];
            setChipText(goalChips[i], getString(R.string.ob_goal_words, goal));
            goalChips[i].setOnClickListener(v -> selectGoal(goal, goalChips, goals));
        }
        selectAge(obAge, ageChips, ages);
        selectGoal(obGoal, goalChips, goals);

        binding.onboarding.obNext.setOnClickListener(v -> {
            // PIN 移至首次打开设置时创建；引导仅保留目标与提醒。
            binding.onboarding.obStep1.setVisibility(View.GONE);
            binding.onboarding.obStep2.setVisibility(View.VISIBLE);
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

    private void setChipText(com.littlestar.app.core.ui.BlockLayout chip, String text) {
        View child = chip.getChildAt(0);
        if (child instanceof TextView) {
            ((TextView) child).setText(text);
        }
    }

    private void selectAge(int age, com.littlestar.app.core.ui.BlockLayout[] chips, int[] values) {
        obAge = age;
        updateChips(chips, values, age);
        updateSuggestion();
    }

    private void selectGoal(int goal, com.littlestar.app.core.ui.BlockLayout[] chips, int[] values) {
        obGoal = goal;
        updateChips(chips, values, goal);
        updateSuggestion();
    }

    /** 选中 = 黄底；未选 = 卡片底（§7.20 设计图）。 */
    private void updateChips(com.littlestar.app.core.ui.BlockLayout[] chips, int[] values, int selected) {
        int yellow = ContextCompat.getColor(this, R.color.ls_yellow);
        int card = ContextCompat.getColor(this, R.color.ls_card);
        for (int i = 0; i < chips.length; i++) {
            chips[i].setFillColor(values[i] == selected ? yellow : card);
        }
    }

    private void updateSuggestion() {
        binding.onboarding.obSuggestion.setText(getString(R.string.ob_suggestion, obAge, obGoal));
    }
}
