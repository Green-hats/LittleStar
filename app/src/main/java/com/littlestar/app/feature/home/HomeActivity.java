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
import com.littlestar.app.data.content.model.CultureItem;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.databinding.ActivityHomeBinding;
import com.littlestar.app.domain.HomeModeResolver;
import com.littlestar.app.feature.flashcard.FlashcardActivity;

/**
 * 首页（§7.1）。字态主流程：今日字 + START → Flashcard(SESSION)。
 * firstLaunch=true 时盖首次引导（§7.20）。模块与家长入口 M3 不导航（见 DECISIONS）。
 */
public final class HomeActivity extends ChildActivity {

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

        // 字 / 文 切换：只切主角卡（§9.1）
        binding.modeWord.setOnClickListener(v -> vm.setMode(HomeModeResolver.Mode.WORDS));
        binding.modeCulture.setOnClickListener(v -> vm.setMode(HomeModeResolver.Mode.CULTURE));

        binding.btnStart.setOnClickListener(v -> startSession());
        binding.btnListen.setOnClickListener(v -> speakHero());
        binding.heroCard.setOnClickListener(v -> speakHero());

        // 模块与家长入口：M3 不导航（ThemesActivity / CultureCategoriesActivity / ParentGate 后续里程碑）
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
        binding.parentEntry.setOnClickListener(v -> { /* M6 接 ParentGateActivity */ });

        setupOnboarding();
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

        // 模式切换外观：选中 = 彩色描边 + 彩色字；未选 = 墨线
        boolean wordMode = s.mode == HomeModeResolver.Mode.WORDS;
        int red = ContextCompat.getColor(this, R.color.ls_red);
        int green = ContextCompat.getColor(this, R.color.ls_green);
        int ink = ContextCompat.getColor(this, R.color.ls_ink);
        int stroke = ContextCompat.getColor(this, R.color.ls_stroke);
        binding.modeWord.setStrokeColor(wordMode ? red : stroke);
        binding.modeWordText.setTextColor(wordMode ? red : ink);
        binding.modeCulture.setStrokeColor(wordMode ? stroke : green);
        binding.modeCultureText.setTextColor(wordMode ? ink : green);

        boolean done = s.goal > 0 && s.learnedToday >= s.goal;
        if (wordMode) {
            Word w = s.nextWord;
            binding.todayLabel.setText(done ? R.string.home_today_done : R.string.home_today_words);
            binding.todayLabel.setTextColor(done ? green : ink);
            binding.heroHanzi.setText(w != null ? w.hanzi : "");
            binding.heroPinyin.setText(w != null ? w.pinyin : "");
            binding.heroEnglish.setText(w != null ? w.english : "");
            maybeAutoSpeak(s);
        } else {
            // 文态主角卡（M3 简化：展示推荐条目标题；播放进 Player 留 M5）
            CultureItem c = s.featured;
            binding.todayLabel.setText(R.string.home_today_culture);
            binding.todayLabel.setTextColor(ink);
            binding.heroHanzi.setText(c != null ? c.glyph : "");
            binding.heroPinyin.setText(c != null ? c.titleZh : "");
            binding.heroEnglish.setText(c != null && c.subtitle != null ? c.subtitle : "");
        }

        // 主按钮：目标完成变「复习一下」（§7.21）；文态 LISTEN 蓝块（§7.1）
        binding.btnStartText.setText(wordMode
                ? (done ? R.string.home_review : R.string.home_start)
                : R.string.home_listen);
        int blue = ContextCompat.getColor(this, R.color.ls_blue);
        int onRed = ContextCompat.getColor(this, R.color.ls_on_red);
        int onBlue = ContextCompat.getColor(this, R.color.ls_on_blue);
        binding.btnStart.setFillColor(wordMode ? red : blue);
        binding.btnStartText.setTextColor(wordMode ? onRed : onBlue);
        binding.btnStartIcon.setColorFilter(wordMode ? onRed : onBlue);

        // 今日进度
        binding.todayProgress.setSegments(Math.max(1, s.goal));
        binding.todayProgress.setFilled(s.learnedToday);
        binding.todayProgressText.setText(done
                ? getString(R.string.home_progress_done)
                : getString(R.string.home_today_progress, s.learnedToday, s.goal));

        // 星星胶囊（不可点）
        binding.starCount.setText(String.valueOf(s.totalStars));
        binding.starCapsule.setContentDescription(getString(R.string.home_star_capsule_desc, s.totalStars));
    }

    /** 进入时当天第一次自动朗读今日字（§9.1）。 */
    private void maybeAutoSpeak(HomeViewModel.UiState s) {
        if (spokenToday || s.nextWord == null) {
            return;
        }
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        String today = container().clock.today().toString();
        if (today.equals(prefs.getString(PREF_LAST_SPEAK_DATE, null))) {
            spokenToday = true;
            return;
        }
        spokenToday = true;
        prefs.edit().putString(PREF_LAST_SPEAK_DATE, today).apply();
        speakHero();
    }

    private void speakHero() {
        HomeViewModel.UiState s = vm.state().getValue();
        if (s != null && s.mode == HomeModeResolver.Mode.WORDS && s.nextWord != null) {
            container().speech.play(s.nextWord);
        }
    }

    /** START：进会话。会话由 Flashcard(SESSION) 进入时用 SessionBuilder 创建。 */
    private void startSession() {
        HomeViewModel.UiState s = vm.state().getValue();
        if (s != null && s.mode == HomeModeResolver.Mode.CULTURE) {
            // 文态 LISTEN → 播放器（推荐条目，queue = 同类全部）
            if (s.featured != null) {
                java.util.List<String> queue = new java.util.ArrayList<>();
                for (com.littlestar.app.data.content.model.CultureItem it
                        : container().content.itemsOfCategory(s.featured.categoryId)) {
                    queue.add(it.id);
                }
                startActivity(com.littlestar.app.feature.player.PlayerActivity.intent(
                        this, s.featured.id, queue));
                Motion.childEnter(this);
            }
            return;
        }
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
            binding.onboarding.obStep1.setVisibility(View.GONE);
            binding.onboarding.obStep2.setVisibility(View.VISIBLE);
            binding.onboarding.obNotifyDesc.setText(getString(R.string.ob_notify_desc, obGoal));
        });
        binding.onboarding.obSkip1.setOnClickListener(v -> vm.completeOnboarding(obGoal));
        // 允许提醒：M3 只写设置，权限申请留 M7
        binding.onboarding.obAllow.setOnClickListener(v -> {
            container().settings.setNotifRationaleShown(true);
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
