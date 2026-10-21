package com.littlestar.app.core.screentime;

import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.littlestar.app.core.time.AppClock;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.data.prefs.SettingsStore;
import com.littlestar.app.data.repo.UsageRepository;
import com.littlestar.app.domain.ScreenTimePolicy;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.function.BiConsumer;

/**
 * 孩子时长跟踪（§10.1）：只计孩子区 Activity 处于 RESUMED 的时间。
 * 后台播放不计；家长区和 TimeUp 不计。精度 15 秒；跨零点按零点拆成两天。
 * 判断逻辑在纯 Java 的 {@link ScreenTimePolicy}；这里只负责计时和跳页。
 */
public final class ScreenTimeTracker {

    public enum Kind {REST, LIMIT}

    private static final long TICK_MS = 15_000;
    /** 暂停 ≥ 5 分钟重置连续计时 */
    private static final long CONTINUOUS_RESET_MIN = 5;

    private final UsageRepository usage;
    private final SettingsStore settings;
    private final AppClock clock;
    private final Handler main;

    @Nullable
    private Instant resumedAt;
    @Nullable
    private Instant continuousSince;
    @Nullable
    private Instant lastPausedAt;
    @Nullable
    private WeakReference<ChildActivity> current;   // 不要强引用 Activity

    /** 跳转出口（M7 接 TimeUpActivity）；测试可注入。 */
    @NonNull
    private BiConsumer<ChildActivity, Kind> timeUpLauncher = (a, k) -> {
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            flushAndCheck();
            main.postDelayed(this, TICK_MS);
        }
    };

    public ScreenTimeTracker(@NonNull UsageRepository usage, @NonNull SettingsStore settings,
                             @NonNull AppClock clock, @NonNull Handler main) {
        this.usage = usage;
        this.settings = settings;
        this.clock = clock;
        this.main = main;
    }

    public void setTimeUpLauncher(@NonNull BiConsumer<ChildActivity, Kind> launcher) {
        timeUpLauncher = launcher;
    }

    public void onChildResumed(@NonNull ChildActivity activity) {
        current = new WeakReference<>(activity);
        Instant now = clock.now();
        resumedAt = now;
        if (continuousSince == null || lastPausedAt == null
                || Duration.between(lastPausedAt, now).toMinutes() >= CONTINUOUS_RESET_MIN) {
            continuousSince = now;
        }
        flushAndCheck();                      // 一进来就检查：超限的直接去 LIMIT
        main.removeCallbacks(tick);
        main.postDelayed(tick, TICK_MS);
    }

    public void onChildPaused() {
        main.removeCallbacks(tick);
        flush();
        resumedAt = null;
        current = null;
        lastPausedAt = clock.now();
    }

    /** 主线程调用：把 resumedAt → now 的增量记到 usage；跨零点按零点拆成两段。 */
    private void flush() {
        Instant from = resumedAt;
        if (from == null) {
            return;
        }
        Instant now = clock.now();
        LocalDate fromDate = from.atZone(clock.zone()).toLocalDate();
        LocalDate nowDate = now.atZone(clock.zone()).toLocalDate();
        if (fromDate.equals(nowDate)) {
            usage.addSeconds(fromDate, (int) Duration.between(from, now).getSeconds());
        } else {
            // 跨零点：from → 当天 24:00 记到 from 那天；0:00 → now 记到今天
            Instant midnight = nowDate.atTime(LocalTime.MIDNIGHT).atZone(clock.zone()).toInstant();
            usage.addSeconds(fromDate, (int) Duration.between(from, midnight).getSeconds());
            usage.addSeconds(nowDate, (int) Duration.between(midnight, now).getSeconds());
        }
        resumedAt = now;
    }

    private void flushAndCheck() {
        flush();
        usage.loadToday(today -> {
            ChildActivity a = current == null ? null : current.get();
            if (a == null || a.isFinishing()) {
                return;
            }
            long continuousMin = continuousSince == null ? 0
                    : Duration.between(continuousSince, clock.now()).toMinutes();
            ScreenTimePolicy.Decision decision = ScreenTimePolicy.decide(
                    today.childSeconds, settings.getDailyLimitMin(), today.extraMinutesGranted,
                    continuousMin, settings.isRestReminder(), a.deferTimeUp());
            if (decision == ScreenTimePolicy.Decision.LIMIT) {
                timeUpLauncher.accept(a, Kind.LIMIT);
            } else if (decision == ScreenTimePolicy.Decision.REST) {
                continuousSince = null;
                timeUpLauncher.accept(a, Kind.REST);
            }
        });
    }

    /** 会话（Flashcard/Quiz）defer 期间的兜底：Reward 完成时主动调一次。 */
    public void checkNow() {
        flushAndCheck();
    }
}
