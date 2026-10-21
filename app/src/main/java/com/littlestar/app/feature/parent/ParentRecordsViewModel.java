package com.littlestar.app.feature.parent;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.data.repo.ProgressRepository;
import com.littlestar.app.data.store.ProgressState;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 学习记录：按天分组 + 本周汇总。 */
public final class ParentRecordsViewModel extends ViewModel {

    public enum Sort {NEWEST, OLDEST}

    /** 一天的记录行。 */
    public static final class DayRecord {
        public final LocalDate date;
        public final int learnedCount;
        public final int quizCorrect;
        public final int quizTotal;
        public final int minutes;

        public DayRecord(LocalDate date, int learnedCount, int quizCorrect, int quizTotal, int minutes) {
            this.date = date;
            this.learnedCount = learnedCount;
            this.quizCorrect = quizCorrect;
            this.quizTotal = quizTotal;
            this.minutes = minutes;
        }
    }

    public static final class UiState {
        public final int weekLearned;
        public final int weekQuizCount;
        public final int lastWeekLearned;       // 与上周对比
        public final List<DayRecord> days;
        public final Sort sort;

        public UiState(int weekLearned, int weekQuizCount, int lastWeekLearned,
                       List<DayRecord> days, Sort sort) {
            this.weekLearned = weekLearned;
            this.weekQuizCount = weekQuizCount;
            this.lastWeekLearned = lastWeekLearned;
            this.days = days;
            this.sort = sort;
        }
    }

    private final AppContainer container;
    private final MediatorLiveData<UiState> state = new MediatorLiveData<>();
    private Sort sort = Sort.NEWEST;
    private boolean attached;
    private ProgressState last;

    public ParentRecordsViewModel(@NonNull AppContainer container) {
        this.container = container;
    }

    public void load() {
        if (attached) {
            return;
        }
        attached = true;
        state.addSource(container.progress.observe(), ps -> {
            last = ps;
            publish();
        });
    }

    public void setSort(@NonNull Sort newSort) {
        sort = newSort;
        publish();
    }

    private void publish() {
        if (last == null) {
            return;
        }
        ProgressState ps = last;
        LocalDate today = container.clock.today();

        // 按天收集：有学习或有用量的日子
        List<LocalDate> dates = new ArrayList<>();
        for (ProgressState.Session s : ps.sessions) {
            LocalDate d = LocalDate.parse(s.date);
            if (!dates.contains(d)) {
                dates.add(d);
            }
        }
        for (String key : ps.usage.keySet()) {
            LocalDate d = LocalDate.parse(key);
            if (!dates.contains(d)) {
                dates.add(d);
            }
        }
        Collections.sort(dates);
        if (sort == Sort.NEWEST) {
            Collections.reverse(dates);
        }

        List<DayRecord> days = new ArrayList<>();
        int weekLearned = 0;
        int weekQuiz = 0;
        int lastWeekLearned = 0;
        for (LocalDate d : dates) {
            int learned = ProgressRepository.learnedOn(ps, d, container.clock);
            int correct = 0;
            int total = 0;
            int quizCount = 0;
            for (ProgressState.Session s : ps.sessions) {
                if (d.toString().equals(s.date) && s.completedAt != null) {
                    correct += s.correct;
                    total += s.total;
                    quizCount++;
                }
            }
            ProgressState.DailyUsage u = ps.usage.get(d.toString());
            int minutes = u == null ? 0 : u.childSeconds / 60;
            if (learned == 0 && quizCount == 0 && minutes == 0) {
                continue;
            }
            days.add(new DayRecord(d, learned, correct, total, minutes));
            if (!d.isBefore(today.minusDays(6))) {
                weekLearned += learned;
                weekQuiz += quizCount;
            } else if (!d.isBefore(today.minusDays(13))) {
                lastWeekLearned += learned;
            }
        }
        state.setValue(new UiState(weekLearned, weekQuiz, lastWeekLearned,
                Collections.unmodifiableList(days), sort));
    }

    @NonNull
    public LiveData<UiState> state() {
        return state;
    }
}
