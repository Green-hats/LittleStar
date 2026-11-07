package com.littlestar.app.core.ui;

import androidx.lifecycle.ViewModel;

/** 仅在内存中保留超时状态；进程恢复且状态丢失时要求重新验证。 */
public final class ParentAccessViewModel extends ViewModel {
    private static final long REAUTH_AFTER_MS = 60_000;
    private long stoppedAt = -1;

    public void onStopped(long elapsedMillis) {
        stoppedAt = elapsedMillis;
    }

    public boolean requiresReauth(boolean restoredInstance, long elapsedMillis) {
        if (stoppedAt < 0) {
            return restoredInstance;
        }
        return elapsedMillis < stoppedAt || elapsedMillis - stoppedAt > REAUTH_AFTER_MS;
    }
}
