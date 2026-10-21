package com.littlestar.app.data;

import static org.junit.Assert.assertEquals;

import com.littlestar.app.core.time.AppClock;
import com.littlestar.app.data.repo.ProgressRepository;
import com.littlestar.app.data.repo.UsageRepository;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.util.FakeClock;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** 派生计算（§6.3.4）：累计星星、某天学会数、本周柱状图。 */
public final class DerivedStatsTest {

    private static final FakeClock CLOCK =
            new FakeClock(LocalDateTime.of(2026, 10, 21, 12, 0));

    @Test
    public void totalStars_sumsSessions() {
        ProgressState state = new ProgressState();
        state.sessions.add(session(3));
        state.sessions.add(session(1));
        state.sessions.add(session(2));
        assertEquals(6, ProgressRepository.totalStars(state));
    }

    @Test
    public void learnedOn_countsOnlyThatDay() {
        ProgressState state = new ProgressState();
        LocalDate today = CLOCK.today();
        putLearned(state, "a", today.atTime(LocalTime.of(9, 0)).atZone(CLOCK.zone()).toInstant().toEpochMilli());
        putLearned(state, "b", today.atTime(LocalTime.of(23, 59)).atZone(CLOCK.zone()).toInstant().toEpochMilli());
        putLearned(state, "c", today.minusDays(1).atTime(LocalTime.of(23, 59)).atZone(CLOCK.zone()).toInstant().toEpochMilli());
        assertEquals(2, ProgressRepository.learnedOn(state, today, CLOCK));
        assertEquals(1, ProgressRepository.learnedOn(state, today.minusDays(1), CLOCK));
    }

    @Test
    public void weekMinutes_lastSevenDays_todayLast() {
        ProgressState state = new ProgressState();
        LocalDate today = CLOCK.today();
        ProgressState.DailyUsage u1 = new ProgressState.DailyUsage();
        u1.childSeconds = 600;   // 10 分钟
        state.usage.put(today.toString(), u1);
        ProgressState.DailyUsage u2 = new ProgressState.DailyUsage();
        u2.childSeconds = 300;
        state.usage.put(today.minusDays(6).toString(), u2);
        List<Integer> week = UsageRepository.weekMinutes(state, today);
        assertEquals(7, week.size());
        assertEquals(Integer.valueOf(5), week.get(0));    // 6 天前
        assertEquals(Integer.valueOf(10), week.get(6));   // 今天
    }

    private static ProgressState.Session session(int stars) {
        ProgressState.Session s = new ProgressState.Session();
        s.stars = stars;
        return s;
    }

    private static void putLearned(ProgressState state, String id, long learnedAt) {
        ProgressState.WordProgress wp = new ProgressState.WordProgress();
        wp.learnedAt = learnedAt;
        state.words.put(id, wp);
    }
}
