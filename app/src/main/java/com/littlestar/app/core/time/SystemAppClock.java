package com.littlestar.app.core.time;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.time.ZoneId;

/** 生产环境时钟。 */
public final class SystemAppClock implements AppClock {

    @NonNull
    @Override
    public Instant now() {
        return Instant.now();
    }

    @NonNull
    @Override
    public ZoneId zone() {
        return ZoneId.systemDefault();
    }
}
