package com.littlestar.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.littlestar.app.data.content.ContentRepository;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.util.FakeClock;
import com.littlestar.app.util.TestContent;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

public final class SessionBuilderTest {

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 10, 21, 8, 0);

    private static SessionBuilder newBuilder(FakeClock clock, ContentRepository content) {
        return new SessionBuilder(content, new ReviewScheduler(), clock, new Random(42));
    }

    /** N=3, k=0 → 3 新 + 2 复习。 */
    @Test
    public void goal3_k0_threeNewPlusTwoReviews() {
        FakeClock clock = new FakeClock(T1);
        ContentRepository content = TestContent.load();
        SessionBuilder builder = newBuilder(clock, content);
        ProgressState state = new ProgressState();

        // 先学会 2 个不属于今日计划的字（今日计划取前 3 个未学：mao/gou/niao）
        learn(state, "yu", clock);
        learn(state, "ma", clock);

        ProgressState.Session s = builder.buildDailySession(state, 3);
        assertEquals(SessionBuilder.KIND_DAILY, s.kind);
        assertEquals(List.of("mao", "gou", "niao"), s.newWordIds);
        assertEquals(2, s.reviewWordIds.size());
        assertEquals(5, s.quizWordIds.size());
        // quiz = 新字 + 复习，乱序
        assertTrue(s.quizWordIds.containsAll(s.newWordIds));
        assertTrue(s.quizWordIds.containsAll(s.reviewWordIds));
    }

    /** N=5, k=3 → 2 新 + 3 复习。 */
    @Test
    public void goal5_k3_twoNewPlusThreeReviews() {
        FakeClock clock = new FakeClock(T1);
        ContentRepository content = TestContent.load();
        SessionBuilder builder = newBuilder(clock, content);
        ProgressState state = new ProgressState();

        // 先生成今日计划（前 5 个未学：mao,gou,niao,yu,ma），再学会其中 3 个
        builder.todayPlan(state, 5);
        learn(state, "mao", clock);
        learn(state, "gou", clock);
        learn(state, "niao", clock);
        // 复习池（非今日新字）：niu, guo, gua
        learn(state, "niu", clock);
        learn(state, "guo", clock);
        learn(state, "gua", clock);

        ProgressState.Session s = builder.buildDailySession(state, 5);
        assertEquals(3, SessionBuilder.learnedTodayCount(state, builder.todayPlan(state, 5)));
        assertEquals(List.of("yu", "ma"), s.newWordIds);
        assertEquals(3, s.reviewWordIds.size());
        assertEquals(5, s.quizWordIds.size());
    }

    /** k=N → REVIEW：跳过识字卡直接测验。 */
    @Test
    public void kEqualsN_reviewSession() {
        FakeClock clock = new FakeClock(T1);
        ContentRepository content = TestContent.load();
        SessionBuilder builder = newBuilder(clock, content);
        ProgressState state = new ProgressState();
        builder.todayPlan(state, 3);   // 计划 = mao,gou,niao
        learn(state, "mao", clock);
        learn(state, "gou", clock);
        learn(state, "niao", clock);
        learn(state, "yu", clock);

        ProgressState.Session s = builder.buildDailySession(state, 3);
        assertEquals(SessionBuilder.KIND_REVIEW, s.kind);
        assertTrue(s.newWordIds.isEmpty());
        assertEquals(4, s.reviewWordIds.size());   // 4 个已学，limit=5
    }

    /** 全部学完 → 新字为空。 */
    @Test
    public void allLearned_emptyPlan() {
        FakeClock clock = new FakeClock(T1);
        ContentRepository content = TestContent.load();
        SessionBuilder builder = newBuilder(clock, content);
        ProgressState state = new ProgressState();
        for (com.littlestar.app.data.content.model.Word w : content.wordsInOrder()) {
            learn(state, w.id, clock);
        }
        ProgressState.DailyPlan plan = builder.todayPlan(state, 3);
        assertTrue(plan.newWordIds.isEmpty());
    }

    /** daily_plan 当天不变；改 N 当天不生效，第二天生效。 */
    @Test
    public void planStableWithinDay_changesNextDay() {
        FakeClock clock = new FakeClock(T1);
        ContentRepository content = TestContent.load();
        SessionBuilder builder = newBuilder(clock, content);
        ProgressState state = new ProgressState();

        ProgressState.DailyPlan p1 = builder.todayPlan(state, 3);
        assertEquals(3, p1.newWordIds.size());
        ProgressState.DailyPlan p2 = builder.todayPlan(state, 5);   // 当天改目标
        assertEquals(3, p2.newWordIds.size());                      // 当天不变
        clock.advance(Duration.ofDays(1));
        ProgressState.DailyPlan p3 = builder.todayPlan(state, 5);
        assertEquals(5, p3.newWordIds.size());                      // 第二天生效
    }

    /** THEME 会话：5 题、kind=THEME，不影响今日计划。 */
    @Test
    public void themeSession_fiveQuestions() {
        FakeClock clock = new FakeClock(T1);
        ContentRepository content = TestContent.load();
        SessionBuilder builder = newBuilder(clock, content);
        ProgressState state = new ProgressState();
        ProgressState.Session s = builder.buildThemeSession(state, "animals");
        assertEquals(SessionBuilder.KIND_THEME, s.kind);
        assertEquals("animals", s.themeId);
        assertEquals(5, s.quizWordIds.size());
        // 不影响今日计划
        assertTrue(state.dailyPlans.isEmpty());
    }

    private static void learn(ProgressState state, String wordId, FakeClock clock) {
        ProgressState.WordProgress wp = new ProgressState.WordProgress();
        ReviewScheduler.markLearned(wp, clock.now(), clock.today());
        state.words.put(wordId, wp);
    }
}
