package com.littlestar.app.domain;

import com.littlestar.app.util.FakeClock;
import org.junit.Test;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import static org.junit.Assert.*;

public final class ReminderScheduleTest {
    private static final Set<Integer> EVERY_DAY = Set.of(1, 2, 3, 4, 5, 6, 7);

    private long next(String now, String time, Set<Integer> days) {
        ZoneId zone = ZoneId.of("America/New_York");
        FakeClock clock = new FakeClock(LocalDateTime.of(2026, 1, 1, 0, 0), zone);
        clock.advance(java.time.Duration.between(clock.now(), Instant.parse(now)));
        return ReminderSchedule.nextTriggerMillis(clock, days, LocalTime.parse(time));
    }

    @Test public void secondOccurrenceDuringFallBack_neverSchedulesPastOrSecondReminder() {
        assertEquals(Instant.parse("2026-11-02T06:30:00Z").toEpochMilli(),
                next("2026-11-01T06:15:00Z", "01:30", EVERY_DAY));
    }

    @Test public void beforeFallBack_usesEarlierOffset() {
        assertEquals(Instant.parse("2026-11-01T05:30:00Z").toEpochMilli(),
                next("2026-11-01T04:45:00Z", "01:30", EVERY_DAY));
    }

    @Test public void springGap_preservesForwardShift() {
        assertEquals(Instant.parse("2026-03-08T07:30:00Z").toEpochMilli(),
                next("2026-03-08T06:45:00Z", "02:30", EVERY_DAY));
    }

    @Test public void exactTrigger_movesToNextSelectedDay() {
        assertEquals(Instant.parse("2026-11-09T06:30:00Z").toEpochMilli(),
                next("2026-11-02T06:30:00Z", "01:30", Set.of(1)));
    }

    @Test public void ordinaryFutureAndNoDays() {
        assertEquals(Instant.parse("2026-11-02T06:30:00Z").toEpochMilli(),
                next("2026-11-02T06:15:00Z", "01:30", EVERY_DAY));
        assertEquals(-1, next("2026-11-02T06:15:00Z", "01:30", Set.of()));
    }
}
