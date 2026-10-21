package com.littlestar.app.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.littlestar.app.data.content.ContentRepository;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.core.time.AppClock;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 组一轮会话（§7.2）。所有方法都在 diskIO 线程上被调用（接收内存中的 ProgressState）。
 */
public final class SessionBuilder {

    public static final String KIND_DAILY = "DAILY";
    public static final String KIND_REVIEW = "REVIEW";
    public static final String KIND_THEME = "THEME";

    /** 每轮最多新字数；一轮题量 5。 */
    public static final int MAX_NEW_PER_ROUND = 3;
    public static final int QUIZ_SIZE = 5;

    private final ContentRepository content;
    private final ReviewScheduler scheduler;
    private final AppClock clock;
    private final Random random;

    public SessionBuilder(@NonNull ContentRepository content, @NonNull ReviewScheduler scheduler,
                          @NonNull AppClock clock, @NonNull Random random) {
        this.content = content;
        this.scheduler = scheduler;
        this.clock = clock;
        this.random = random;
    }

    /**
     * 取今日计划；没有就生成并写入 state（当天不变；改 daily_goal 当天不生效）。
     * 今日新字 = 按主题 order、字顺序的前 N 个未学会的字；全部学完时 newWordIds 为空。
     */
    @NonNull
    public ProgressState.DailyPlan todayPlan(@NonNull ProgressState state, int dailyGoal) {
        String today = clock.today().toString();
        ProgressState.DailyPlan plan = state.dailyPlans.get(today);
        if (plan != null) {
            return plan;
        }
        plan = new ProgressState.DailyPlan();
        plan.goal = dailyGoal;
        for (Word w : content.wordsInOrder()) {
            if (plan.newWordIds.size() >= dailyGoal) {
                break;
            }
            ProgressState.WordProgress wp = state.words.get(w.id);
            if (wp == null || wp.learnedAt == null) {
                plan.newWordIds.add(w.id);
            }
        }
        state.dailyPlans.put(today, plan);
        return plan;
    }

    /**
     * 创建一轮 DAILY / REVIEW 会话并写入 state.sessions（不保存，由调用方 flushNow）。
     */
    @NonNull
    public ProgressState.Session buildDailySession(@NonNull ProgressState state, int dailyGoal) {
        content.ensureLoaded();
        ProgressState.DailyPlan plan = todayPlan(state, dailyGoal);

        List<String> newWords = new ArrayList<>();
        for (String id : plan.newWordIds) {
            if (newWords.size() >= MAX_NEW_PER_ROUND) {
                break;
            }
            ProgressState.WordProgress wp = state.words.get(id);
            if (wp == null || wp.learnedAt == null) {
                newWords.add(id);
            }
        }

        LocalDate today = clock.today();
        List<String> reviews = scheduler.pickReviews(state,
                QUIZ_SIZE - newWords.size(), new HashSet<>(newWords), today);

        List<String> quiz = new ArrayList<>(newWords);
        quiz.addAll(reviews);
        Collections.shuffle(quiz, random);

        ProgressState.Session s = new ProgressState.Session();
        s.id = state.nextSessionId++;
        s.date = today.toString();
        s.kind = newWords.isEmpty() ? KIND_REVIEW : KIND_DAILY;
        s.themeId = null;
        s.newWordIds = newWords;
        s.reviewWordIds = reviews;
        s.quizWordIds = quiz;
        s.total = quiz.size();
        s.startedAt = clock.now().toEpochMilli();
        state.sessions.add(s);
        return s;
    }

    /** THEME 会话：从该主题 6 字里随机取 5 题；不发星星，不影响今日进度，但会更新字进度。 */
    @NonNull
    public ProgressState.Session buildThemeSession(@NonNull ProgressState state, @NonNull String themeId) {
        content.ensureLoaded();
        List<String> ids = new ArrayList<>();
        for (Word w : content.wordsOfTheme(themeId)) {
            ids.add(w.id);
        }
        Collections.shuffle(ids, random);
        List<String> quiz = new ArrayList<>(ids.subList(0, Math.min(QUIZ_SIZE, ids.size())));

        ProgressState.Session s = new ProgressState.Session();
        s.id = state.nextSessionId++;
        s.date = clock.today().toString();
        s.kind = KIND_THEME;
        s.themeId = themeId;
        s.quizWordIds = quiz;
        s.total = quiz.size();
        s.startedAt = clock.now().toEpochMilli();
        state.sessions.add(s);
        return s;
    }

    /** 今日新字里已学会的数量 k。 */
    public static int learnedTodayCount(@NonNull ProgressState state, @NonNull ProgressState.DailyPlan plan) {
        int k = 0;
        for (String id : plan.newWordIds) {
            ProgressState.WordProgress wp = state.words.get(id);
            if (wp != null && wp.learnedAt != null) {
                k++;
            }
        }
        return k;
    }

    @Nullable
    public static ProgressState.Session sessionById(@NonNull ProgressState state, long sessionId) {
        for (ProgressState.Session s : state.sessions) {
            if (s.id == sessionId) {
                return s;
            }
        }
        return null;
    }
}
