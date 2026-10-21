package com.littlestar.app.domain;

import androidx.annotation.NonNull;

import com.littlestar.app.data.store.ProgressState;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 间隔复习（Leitner）。纯逻辑：所有方法接收/修改传入的 WordProgress，
 * 由调用方（Repository，diskIO 线程）负责落库。
 */
public final class ReviewScheduler {

    public static final int MAX_BOX = 5;

    /** box → 下次复习间隔（天）。box 0 视为 1。 */
    public static int intervalDays(int box) {
        switch (Math.max(1, Math.min(box, MAX_BOX))) {
            case 1: return 1;
            case 2: return 2;
            case 3: return 4;
            case 4: return 7;
            default: return 14;
        }
    }

    /** 第一次学会（印章或测验答对未学过的字）：box=1，due=today+1，写 learnedAt。 */
    public static void markLearned(@NonNull ProgressState.WordProgress wp,
                                   @NonNull Instant now, @NonNull LocalDate today) {
        if (wp.learnedAt == null) {
            wp.learnedAt = now.toEpochMilli();
            wp.box = 1;
            wp.dueDate = today.plusDays(1).toString();
        }
        wp.lastSeenAt = now.toEpochMilli();
    }

    /**
     * 测验判分后更新进度。
     * 第一次就答对：box 升一级封顶 5，due = today + interval；未学过的字同时算学会。
     * 答错：box 回 1，due = today + 1；learnedAt 不清空。
     */
    public static void onQuizAnswer(@NonNull ProgressState.WordProgress wp, boolean correctFirstTry,
                                    @NonNull Instant now, @NonNull LocalDate today) {
        if (correctFirstTry) {
            if (wp.learnedAt == null) {
                markLearned(wp, now, today);
            } else {
                wp.box = Math.min(wp.box + 1, MAX_BOX);
                wp.dueDate = today.plusDays(intervalDays(wp.box)).toString();
            }
            wp.correctCount++;
        } else {
            wp.box = 1;
            wp.dueDate = today.plusDays(1).toString();
            wp.wrongCount++;
            // learnedAt 不清空：学会就是学会，只是要多复习
        }
        wp.lastSeenAt = now.toEpochMilli();
    }

    /**
     * 挑复习字：先取 dueDate <= today（dueDate 升序），不够再取已学会里 lastSeenAt 最早的。
     * 同值按 wordId 稳定排序，保证可测。
     */
    @NonNull
    public List<String> pickReviews(@NonNull ProgressState state, int limit,
                                    @NonNull Set<String> exclude, @NonNull LocalDate today) {
        List<Map.Entry<String, ProgressState.WordProgress>> due = new ArrayList<>();
        List<Map.Entry<String, ProgressState.WordProgress>> stale = new ArrayList<>();
        for (Map.Entry<String, ProgressState.WordProgress> e : state.words.entrySet()) {
            ProgressState.WordProgress wp = e.getValue();
            if (wp.learnedAt == null || exclude.contains(e.getKey())) {
                continue;
            }
            if (wp.dueDate != null && wp.dueDate.compareTo(today.toString()) <= 0) {
                due.add(e);
            } else {
                stale.add(e);
            }
        }
        due.sort(Comparator.comparing((Map.Entry<String, ProgressState.WordProgress> e) -> e.getValue().dueDate)
                .thenComparing(Map.Entry::getKey));
        stale.sort(Comparator.comparingLong((Map.Entry<String, ProgressState.WordProgress> e) ->
                        e.getValue().lastSeenAt == null ? 0L : e.getValue().lastSeenAt)
                .thenComparing(Map.Entry::getKey));

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, ProgressState.WordProgress> e : due) {
            if (result.size() >= limit) {
                break;
            }
            result.add(e.getKey());
        }
        for (Map.Entry<String, ProgressState.WordProgress> e : stale) {
            if (result.size() >= limit) {
                break;
            }
            result.add(e.getKey());
        }
        return result;
    }
}
