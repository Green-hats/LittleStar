package com.littlestar.app.feature.parent;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.data.repo.ProgressRepository;
import com.littlestar.app.data.repo.UsageRepository;
import com.littlestar.app.data.store.ProgressState;

import java.util.List;

/** 学习概览：今日时长 / 今日学会 / 测验正确率（不显示星星）+ 近 7 天柱状图。 */
public final class ParentDashboardViewModel extends ViewModel {

    public static final class UiState {
        public final int todayMinutes;
        public final int todayLearned;
        /** 今日测验正确率 0–100；今天没测验为 -1。 */
        public final int todayQuizPercent;
        public final List<Integer> weekMinutes;
        /** 本周学会字数（汇总卡标题右侧）。 */
        public final int weekLearned;

        public UiState(int todayMinutes, int todayLearned, int todayQuizPercent,
                       List<Integer> weekMinutes) {
            this(todayMinutes, todayLearned, todayQuizPercent, weekMinutes, 0);
        }

        public UiState(int todayMinutes, int todayLearned, int todayQuizPercent,
                       List<Integer> weekMinutes, int weekLearned) {
            this.todayMinutes = todayMinutes;
            this.todayLearned = todayLearned;
            this.todayQuizPercent = todayQuizPercent;
            this.weekMinutes = weekMinutes;
            this.weekLearned = weekLearned;
        }
    }

    private final AppContainer container;
    private final MediatorLiveData<UiState> state = new MediatorLiveData<>();
    private boolean attached;

    public ParentDashboardViewModel(@NonNull AppContainer container) {
        this.container = container;
    }

    public void load() {
        if (attached) {
            return;
        }
        attached = true;
        state.addSource(container.progress.observe(), this::compute);
    }

    private void compute(ProgressState ps) {
        if (ps == null) {
            return;
        }
        String today = container.clock.today().toString();
        ProgressState.DailyUsage u = ps.usage.get(today);
        int minutes = u == null ? 0 : u.childSeconds / 60;
        int learned = ProgressRepository.learnedOn(ps, container.clock.today(), container.clock);
        int correct = 0;
        int total = 0;
        for (ProgressState.Session s : ps.sessions) {
            if (today.equals(s.date) && s.completedAt != null) {
                correct += s.correct;
                total += s.total;
            }
        }
        int percent = total == 0 ? -1 : Math.round(100f * correct / total);
        // 本周学会字数
        int weekLearned = 0;
        for (int i = 0; i < 7; i++) {
            weekLearned += ProgressRepository.learnedOn(ps,
                    container.clock.today().minusDays(i), container.clock);
        }
        state.setValue(new UiState(minutes, learned, percent,
                UsageRepository.weekMinutes(ps, container.clock.today()), weekLearned));
    }

    @NonNull
    public LiveData<UiState> state() {
        return state;
    }
}
