package com.littlestar.app.util;

import androidx.annotation.NonNull;

import com.littlestar.app.core.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 测试时钟：可 advance / set。 */
public final class FakeClock implements AppClock {

    private Instant now;
    private final ZoneId zone;

    public FakeClock(@NonNull LocalDateTime start) {
        this(start, ZoneId.of("Australia/Melbourne"));
    }

    public FakeClock(@NonNull LocalDateTime start, @NonNull ZoneId zone) {
        this.zone = zone;
        this.now = start.atZone(zone).toInstant();
    }

    public void advance(@NonNull Duration d) {
        now = now.plus(d);
    }

    public void set(@NonNull LocalDateTime t) {
        now = t.atZone(zone).toInstant();
    }

    @NonNull
    @Override
    public Instant now() {
        return now;
    }

    @NonNull
    @Override
    public ZoneId zone() {
        return zone;
    }
}
