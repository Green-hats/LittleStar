package com.littlestar.app.data.repo;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.littlestar.app.core.time.AppClock;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.data.store.ProgressStore;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 每日时长入口。 */
public final class UsageRepository {

    private final ProgressStore store;
    private final AppClock clock;

    public UsageRepository(@NonNull ProgressStore store, @NonNull AppClock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** 增加某天孩子区秒数（diskIO 线程；防抖保存）。 */
    public void addSeconds(@NonNull LocalDate date, int seconds) {
        if (seconds <= 0) {
            return;
        }
        String key = date.toString();
        store.update(state -> {
            ProgressState.DailyUsage u =
                    state.usage.computeIfAbsent(key, k -> new ProgressState.DailyUsage());
            u.childSeconds += seconds;
        });
    }

    /** 读今天的用量，回调到主线程。 */
    public void loadToday(@NonNull Consumer<ProgressState.DailyUsage> onMain) {
        String today = clock.today().toString();
        store.read(state -> {
            ProgressState.DailyUsage u = state.usage.get(today);
            return u != null ? u : new ProgressState.DailyUsage();
        }, onMain);
    }

    /** 观察今天的用量（UI）。 */
    @NonNull
    public LiveData<ProgressState.DailyUsage> observeToday() {
        String today = clock.today().toString();
        return Transformations.map(store.observe(), state -> {
            ProgressState.DailyUsage u = state.usage.get(today);
            return u != null ? u : new ProgressState.DailyUsage();
        });
    }

    /** 家长「今天再给 10 分钟」。立即保存。 */
    public void grantExtraMinutes(int minutes) {
        String today = clock.today().toString();
        store.update(state -> {
            ProgressState.DailyUsage u =
                    state.usage.computeIfAbsent(today, k -> new ProgressState.DailyUsage());
            u.extraMinutesGranted += minutes;
        });
        store.flushNow();
    }

    /** 近 7 天柱状图数据（今天在最后），单位分钟。 */
    @NonNull
    public static List<Integer> weekMinutes(@NonNull ProgressState state, @NonNull LocalDate today) {
        List<Integer> result = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            String key = today.minusDays(i).toString();
            ProgressState.DailyUsage u = state.usage.get(key);
            result.add(u == null ? 0 : u.childSeconds / 60);
        }
        return result;
    }
}
