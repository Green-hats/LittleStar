package com.littlestar.app.domain;

import static org.junit.Assert.assertEquals;

import com.littlestar.app.data.store.ProgressState;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class StarCalculatorTest {

    private static ProgressState.Answer answer(String wordId, boolean correct, boolean firstTry) {
        ProgressState.Answer a = new ProgressState.Answer();
        a.wordId = wordId;
        a.chosen = "x";
        a.correct = correct;
        a.firstTry = firstTry;
        a.at = 0;
        return a;
    }

    @Test
    public void daily_allWrong_stillOneStar() {
        List<String> newWords = List.of("a", "b", "c");
        List<ProgressState.Answer> answers = new ArrayList<>();
        answers.add(answer("a", false, false));
        answers.add(answer("b", false, false));
        answers.add(answer("c", false, false));
        assertEquals(1, StarCalculator.compute(SessionBuilder.KIND_DAILY, newWords, answers));
    }

    @Test
    public void daily_threeNewAllFirstTryCorrect_threeStars() {
        List<String> newWords = List.of("a", "b", "c");
        List<ProgressState.Answer> answers = new ArrayList<>();
        answers.add(answer("a", true, true));
        answers.add(answer("b", true, true));
        answers.add(answer("c", true, true));
        assertEquals(3, StarCalculator.compute(SessionBuilder.KIND_DAILY, newWords, answers));
    }

    @Test
    public void daily_secondTryCorrect_doesNotCount() {
        List<String> newWords = List.of("a", "b");
        List<ProgressState.Answer> answers = new ArrayList<>();
        answers.add(answer("a", true, false));   // 第二次才对
        answers.add(answer("b", true, true));
        assertEquals(1, StarCalculator.compute(SessionBuilder.KIND_DAILY, newWords, answers));
    }

    @Test
    public void review_oneStar() {
        assertEquals(1, StarCalculator.compute(SessionBuilder.KIND_REVIEW, List.of(), List.of()));
    }

    @Test
    public void theme_zeroStars() {
        assertEquals(0, StarCalculator.compute(SessionBuilder.KIND_THEME, List.of("a"),
                List.of(answer("a", true, true))));
    }
}
