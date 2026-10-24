package com.littlestar.app.feature.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.core.time.AppClock;
import com.littlestar.app.data.content.ContentRepository;
import com.littlestar.app.data.content.model.CultureItem;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.prefs.SettingsStore;
import com.littlestar.app.data.repo.ProgressRepository;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.domain.HomeModeResolver;
import com.littlestar.app.domain.SessionBuilder;

import java.util.List;

/**
 * 首页。模式（字/文）默认按时间规则，手动切换只存在内存里（不写 SharedPreferences）。
 */
public final class HomeViewModel extends ViewModel {

    /** 首页状态。 */
    public static final class UiState {
        public final boolean loading;
        public final HomeModeResolver.Mode mode;
        public final boolean bedtimeOnly;
        public final int goal;                 // 每日目标 N
        public final int learnedToday;         // k
        @Nullable
        public final Word nextWord;            // 今日下一个未学的字
        @Nullable
        public final CultureItem featured;     // 推荐文化条目
        public final boolean allDone;          // 全部学完
        public final boolean firstLaunch;      // 首次引导态

        public UiState(boolean loading, HomeModeResolver.Mode mode, boolean bedtimeOnly,
                       int goal, int learnedToday, @Nullable Word nextWord,
                       @Nullable CultureItem featured,
                       boolean allDone, boolean firstLaunch) {
            this.loading = loading;
            this.mode = mode;
            this.bedtimeOnly = bedtimeOnly;
            this.goal = goal;
            this.learnedToday = learnedToday;
            this.nextWord = nextWord;
            this.featured = featured;
            this.allDone = allDone;
            this.firstLaunch = firstLaunch;
        }

        UiState with(HomeModeResolver.Mode newMode, @Nullable CultureItem newFeatured) {
            return new UiState(loading, newMode, bedtimeOnly, goal, learnedToday, nextWord,
                    newFeatured, allDone, firstLaunch);
        }
    }

    private final AppContainer container;
    private final AppClock clock;
    private final SettingsStore settings;
    private final ContentRepository content;
    private final ProgressRepository progress;

    private final MediatorLiveData<UiState> state = new MediatorLiveData<>();
    /** 手动覆盖的模式；null = 按时间规则。 */
    @Nullable
    private HomeModeResolver.Mode manualMode;
    @Nullable
    private ProgressState lastProgress;
    private boolean contentReady;

    public HomeViewModel(@NonNull AppContainer container) {
        this.container = container;
        this.clock = container.clock;
        this.settings = container.settings;
        this.content = container.content;
        this.progress = container.progress;
        state.setValue(new UiState(true, HomeModeResolver.resolve(clock.localTime()),
                HomeModeResolver.bedtimeOnly(clock.localTime()),
                settings.getDailyGoal(), 0, null, null, false, !settings.isOnboarded()));
        // 内容先在 diskIO 线程解析，回调到主线程后再计算（不在主线程做 IO）
        content.load(container.executors, () -> {
            contentReady = true;
            onProgress(lastProgress);
        });
        state.addSource(progress.observe(), this::onProgress);
    }

    private void onProgress(@Nullable ProgressState ps) {
        if (ps == null) {
            return;
        }
        lastProgress = ps;
        if (!contentReady) {
            return;
        }
        int goal = settings.getDailyGoal();
        ProgressState.DailyPlan plan = null;
        // 注意：这里只读。计划由 SessionBuilder 在会话创建时写入；首页展示用「按同样规则算一遍」。
        String today = clock.today().toString();
        plan = ps.dailyPlans.get(today);
        if (plan == null) {
            plan = previewPlan(ps, goal);
        }
        goal = plan.goal;
        int k = SessionBuilder.learnedTodayCount(ps, plan);

        Word next = null;
        for (String id : plan.newWordIds) {
            ProgressState.WordProgress wp = ps.words.get(id);
            if (wp == null || wp.learnedAt == null) {
                next = content.wordById(id);
                break;
            }
        }
        // 今日目标完成时展示计划里最后一个字（复习入口的主角卡不空白）
        if (next == null && !plan.newWordIds.isEmpty()) {
            next = content.wordById(plan.newWordIds.get(plan.newWordIds.size() - 1));
        }
        boolean allDone = plan.newWordIds.isEmpty();

        UiState cur = state.getValue();
        HomeModeResolver.Mode mode = manualMode != null ? manualMode
                : HomeModeResolver.resolve(clock.localTime());
        boolean bedtimeOnly = HomeModeResolver.bedtimeOnly(clock.localTime());
        CultureItem featured = pickFeatured(bedtimeOnly);
        state.setValue(new UiState(false, mode, bedtimeOnly, goal, k, next, featured,
                allDone, !settings.isOnboarded()));
    }

    /** 首页展示用的计划预览：不写入 state（只读首页不写库）。 */
    @NonNull
    private ProgressState.DailyPlan previewPlan(@NonNull ProgressState ps, int goal) {
        ProgressState.DailyPlan plan = new ProgressState.DailyPlan();
        plan.goal = goal;
        for (Word w : content.wordsInOrder()) {
            if (plan.newWordIds.size() >= goal) {
                break;
            }
            ProgressState.WordProgress wp = ps.words.get(w.id);
            if (wp == null || wp.learnedAt == null) {
                plan.newWordIds.add(w.id);
            }
        }
        return plan;
    }

    @Nullable
    private CultureItem pickFeatured(boolean bedtimeOnly) {
        List<CultureItem> featured = content.featuredToday(clock.today());
        if (!featured.isEmpty()) {
            return featured.get(0);
        }
        if (bedtimeOnly) {
            List<CultureItem> bedtime = content.bedtimeItems();
            return bedtime.isEmpty() ? null : bedtime.get(0);
        }
        // 默认：儿歌类第一条
        List<CultureItem> songs = content.itemsOfCategory("songs");
        return songs.isEmpty() ? null : songs.get(0);
    }

    /** 用户点「字 / 文」切换：只影响本次会话（内存）。 */
    public void setMode(@NonNull HomeModeResolver.Mode mode) {
        manualMode = mode;
        UiState cur = state.getValue();
        if (cur != null && !cur.loading) {
            state.setValue(cur.with(mode, pickFeatured(cur.bedtimeOnly)));
        }
    }

    @NonNull
    public LiveData<UiState> state() {
        return state;
    }

    /** 从设置返回后刷新；已生成计划仍使用当天固定的目标。 */
    public void refresh() {
        onProgress(lastProgress);
    }

    public void completeOnboarding(int goal) {
        settings.setDailyGoal(goal);
        settings.setOnboarded(true);
        UiState cur = state.getValue();
        if (cur != null) {
            state.setValue(new UiState(cur.loading, cur.mode, cur.bedtimeOnly, goal,
                    cur.learnedToday, cur.nextWord, cur.featured, cur.allDone, false));
        }
    }
}
