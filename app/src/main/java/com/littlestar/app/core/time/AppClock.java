package com.littlestar.app.core.time;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 所有时间逻辑的唯一天花板。禁止直接调 System.currentTimeMillis() / LocalDate.now()。
 * 注意：不要用 LocalDate.ofInstant（Android 需 API 34），统一 atZone().toLocalDate()。
 */
public interface AppClock {

    @NonNull
    Instant now();

    /** 本次开机以来的经过时间，包含休眠，不受墙上时钟调整影响。 */
    long elapsedRealtimeMillis();

    @NonNull
    ZoneId zone();

    @NonNull
    default LocalDate today() {
        return now().atZone(zone()).toLocalDate();
    }

    @NonNull
    default LocalTime localTime() {
        return now().atZone(zone()).toLocalTime();
    }
}
