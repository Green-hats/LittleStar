package com.littlestar.app.core.screentime;

import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.littlestar.app.core.time.AppClock;
import com.littlestar.app.core.ui.ChildActivity;

import java.lang.ref.WeakReference;

/**
 * 孩子时长跟踪（M7 完整实现计时 / 休息 / 上限）。
 * 当前只维护「当前孩子页」弱引用，供基类调用。
 */
public final class ScreenTimeTracker {

    private final AppClock clock;
    private final Handler main;

    @Nullable
    private WeakReference<ChildActivity> current;

    public ScreenTimeTracker(@NonNull AppClock clock, @NonNull Handler main) {
        this.clock = clock;
        this.main = main;
    }

    public void onChildResumed(@NonNull ChildActivity activity) {
        current = new WeakReference<>(activity);
        // TODO(M7): 计时、超限检查（§10.1）
    }

    public void onChildPaused() {
        current = null;
        // TODO(M7): flush 时长（§10.1）
    }
}
