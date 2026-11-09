package com.littlestar.app.domain;

import com.littlestar.app.core.time.AppClock;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

/** 每个选中日期至多一个提醒候选，不依赖 Android 调度服务。 */
public final class ReminderSchedule {
    private ReminderSchedule() {
    }

    public static long nextTriggerMillis(AppClock clock, Set<Integer> days, LocalTime time) {
        Instant now = clock.now();
        ZoneId zone = clock.zone();
        LocalDate today = now.atZone(zone).toLocalDate();
        for (int d = 0; d < 8; d++) {
            LocalDate date = today.plusDays(d);
            if (!days.contains(date.getDayOfWeek().getValue())) {
                continue;
            }
            Instant candidate = ZonedDateTime.of(date, time, zone).toInstant();
            if (candidate.isAfter(now)) {
                return candidate.toEpochMilli();
            }
        }
        return -1;
    }
}
