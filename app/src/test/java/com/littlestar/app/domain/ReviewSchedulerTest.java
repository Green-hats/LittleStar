package com.littlestar.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.littlestar.app.data.store.ProgressState;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class ReviewSchedulerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final Instant NOW = Instant.ofEpochMilli(1792546800000L);

    private static ProgressState.WordProgress newWp() {
        return new ProgressState.WordProgress();
    }

    @Test
    public void firstLearned_box1_dueTomorrow() {
        ProgressState.WordProgress wp = newWp();
        ReviewScheduler.markLearned(wp, NOW, TODAY);
        assertEquals(1, wp.box);
        assertEquals(TODAY.plusDays(1).toString(), wp.dueDate);
        assertNotNull(wp.learnedAt);
    }

    @Test
    public void correctAnswer_upgradesBox_cappedAt5() {
        ProgressState.WordProgress wp = newWp();
        ReviewScheduler.markLearned(wp, NOW, TODAY);
        ReviewScheduler.onQuizAnswer(wp, true, NOW, TODAY);
        assertEquals(2, wp.box);
        assertEquals(TODAY.plusDays(2).toString(), wp.dueDate);
        for (int i = 0; i < 10; i++) {
            ReviewScheduler.onQuizAnswer(wp, true, NOW, TODAY);
        }
        assertEquals(5, wp.box);
        assertEquals(TODAY.plusDays(14).toString(), wp.dueDate);
    }

    @Test
    public void wrongAnswer_backToBox1_learnedAtKept() {
        ProgressState.WordProgress wp = newWp();
        ReviewScheduler.markLearned(wp, NOW, TODAY);
        ReviewScheduler.onQuizAnswer(wp, true, NOW, TODAY);
        ReviewScheduler.onQuizAnswer(wp, true, NOW, TODAY);
        Long learned = wp.learnedAt;
        ReviewScheduler.onQuizAnswer(wp, false, NOW, TODAY);
        assertEquals(1, wp.box);
        assertEquals(TODAY.plusDays(1).toString(), wp.dueDate);
        assertEquals(learned, wp.learnedAt);
    }

    @Test
    public void correctOnUnlearned_marksLearned() {
        ProgressState.WordProgress wp = newWp();
        ReviewScheduler.onQuizAnswer(wp, true, NOW, TODAY);
        assertNotNull(wp.learnedAt);
        assertEquals(1, wp.box);
        assertEquals(1, wp.correctCount);
    }

    @Test
    public void pickReviews_dueFirst_thenStale_respectsLimitAndExclude() {
        ProgressState state = new ProgressState();
        // a: due 昨天；b: due 今天；c: due 下周（stale）；d: due 昨天但被排除
        putWord(state, "a", TODAY.minusDays(1), 1000L);
        putWord(state, "b", TODAY, 2000L);
        putWord(state, "c", TODAY.plusDays(6), 500L);
        putWord(state, "d", TODAY.minusDays(1), 100L);

        ReviewScheduler scheduler = new ReviewScheduler();
        List<String> picks = scheduler.pickReviews(state, 5, Set.of("d"), TODAY);
        assertEquals(List.of("a", "b", "c"), picks);   // due 先按 dueDate 升序，再 stale 按 lastSeen

        // limit=1：due 最早（a 和 d 都是昨天），wordId 最小者优先 → "a"
        List<String> limited = scheduler.pickReviews(state, 1, Set.of(), TODAY);
        assertEquals(List.of("a"), limited);
    }

    private static void putWord(ProgressState state, String id, LocalDate due, long lastSeen) {
        ProgressState.WordProgress wp = new ProgressState.WordProgress();
        wp.learnedAt = lastSeen;
        wp.box = 1;
        wp.dueDate = due.toString();
        wp.lastSeenAt = lastSeen;
        state.words.put(id, wp);
    }
}
