package com.littlestar.app.feature.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.AppExecutors;
import com.littlestar.app.data.content.ContentRepository;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.prefs.SettingsStore;
import com.littlestar.app.data.repo.ProgressRepository;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.domain.SessionBuilder;

import java.util.List;

/**
 * 首页固定展示每日识字任务；听读通过独立浏览入口进入。
 */
public final class HomeViewModel extends ViewModel {

    /** 首页状态。 */
    public static final class UiState {
        public final boolean loading;
        public final int goal;                 // 今日实际计划量，不改写配置目标
        public final int learnedToday;         // k
        @Nullable
        public final Word nextWord;            // 今日下一个未学的字
        public final boolean allDone;          // 全部学完
        public final boolean firstLaunch;      // 首次引导态

        public UiState(boolean loading, int goal, int learnedToday, @Nullable Word nextWord,
                       boolean allDone, boolean firstLaunch) {
            this.loading = loading;
            this.goal = goal;
            this.learnedToday = learnedToday;
            this.nextWord = nextWord;
            this.allDone = allDone;
            this.firstLaunch = firstLaunch;
        }

        public boolean isDailyComplete() {
            return allDone || (goal > 0 && learnedToday >= goal);
        }
    }

    private final SessionBuilder sessionBuilder;
    private final SettingsStore settings;
    private final ContentRepository content;
    private final ProgressRepository progress;

    private final MediatorLiveData<UiState> state = new MediatorLiveData<>();
    @Nullable
    private ProgressState lastProgress;
    private boolean contentReady;

    public HomeViewModel(@NonNull AppContainer container) {
        this(container.settings, container.content, container.progress,
                container.sessionBuilder, container.executors);
    }

    HomeViewModel(SettingsStore settings, ContentRepository content,
                  ProgressRepository progress, SessionBuilder sessionBuilder, AppExecutors executors) {
        this.settings = settings;
        this.content = content;
        this.progress = progress;
        this.sessionBuilder = sessionBuilder;
        state.setValue(new UiState(true, settings.getDailyGoal(), 0, null, false, !settings.isOnboarded()));
        // 内容先在 diskIO 线程解析，回调到主线程后再计算（不在主线程做 IO）
        content.load(executors, () -> {
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
        // 注意：这里只读。计划由 SessionBuilder 在会话创建时写入；首页展示用「按同样规则算一遍」。
        ProgressState.DailyPlan plan = sessionBuilder.previewTodayPlan(ps, goal);
        goal = Math.min(plan.goal, plan.newWordIds.size());
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
        List<Word> words = content.wordsInOrder();
        boolean allDone = words.stream().allMatch(word -> {
            ProgressState.WordProgress wp = ps.words.get(word.id);
            return wp != null && wp.learnedAt != null;
        });
        if (next == null && allDone && !words.isEmpty()) {
            next = words.get(words.size() - 1);
        }

        state.setValue(new UiState(false, goal, k, next,
                allDone, !settings.isOnboarded()));
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
            state.setValue(new UiState(cur.loading, goal,
                    cur.learnedToday, cur.nextWord, cur.allDone, false));
        }
    }
}
