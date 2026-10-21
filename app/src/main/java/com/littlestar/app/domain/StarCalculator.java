package com.littlestar.app.domain;

import androidx.annotation.NonNull;

import com.littlestar.app.data.store.ProgressState;

import java.util.List;

/**
 * 星星（§7.5）：
 * DAILY = max(1, 本轮 newWords 中第一次就答对的个数)，上限 3；
 * REVIEW = 1（完成星）；THEME = 0（奖励页隐藏星星行）。
 */
public final class StarCalculator {

    private StarCalculator() {
    }

    public static int compute(@NonNull String kind, @NonNull List<String> newWordIds,
                              @NonNull List<ProgressState.Answer> answers) {
        switch (kind) {
            case SessionBuilder.KIND_DAILY: {
                int firstTryCorrect = 0;
                for (ProgressState.Answer a : answers) {
                    if (a.correct && a.firstTry && newWordIds.contains(a.wordId)) {
                        firstTryCorrect++;
                    }
                }
                return Math.min(3, Math.max(1, firstTryCorrect));
            }
            case SessionBuilder.KIND_REVIEW:
                return 1;
            default:
                return 0;
        }
    }
}
