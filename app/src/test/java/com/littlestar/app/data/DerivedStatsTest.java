package com.littlestar.app.data;

import static org.junit.Assert.assertEquals;

import com.littlestar.app.data.repo.ProgressRepository;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.util.FakeClock;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 派生计算（§6.3.4）：累计星星、某天学会数。 */
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
