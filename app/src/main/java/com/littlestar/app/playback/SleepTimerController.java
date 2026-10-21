package com.littlestar.app.playback;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.littlestar.app.core.time.AppClock;

import java.time.Duration;
import java.time.Instant;

/**
 * 睡前定时（应用级单例，AppContainer 持有）。
 * deadline = null 表示未计时；remaining 每秒更新，供 Player / Sheet 显示。
 */
public final class SleepTimerController {

    private final AppClock clock;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final MutableLiveData<Instant> deadline = new MutableLiveData<>(null);
    private final MutableLiveData<Duration> remaining = new MutableLiveData<>(Duration.ZERO);
    private volatile boolean finishTrack = true;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Instant dl = deadline.getValue();
            if (dl == null) {
                remaining.setValue(Duration.ZERO);
                return;
            }
            Duration left = Duration.between(clock.now(), dl);
            remaining.setValue(left.isNegative() ? Duration.ZERO : left);
            main.postDelayed(this, 1_000);
        }
    };

    public SleepTimerController(@NonNull AppClock clock) {
        this.clock = clock;
    }

    public void start(int minutes, boolean finishTrack) {
        this.finishTrack = finishTrack;
        deadline.setValue(clock.now().plus(Duration.ofMinutes(minutes)));
        main.removeCallbacks(tick);
        main.post(tick);
    }

    public void cancel() {
        deadline.setValue(null);
        main.removeCallbacks(tick);
        remaining.setValue(Duration.ZERO);
    }

    /** null = 未计时。PlaybackService 用 observeForever 订阅。 */
    @NonNull
    public LiveData<Instant> deadline() {
        return deadline;
    }

    public boolean isFinishTrack() {
        return finishTrack;
    }

    @NonNull
    public LiveData<Duration> remaining() {
        return remaining;
    }

    public boolean isRunning() {
        return deadline.getValue() != null;
    }
}
