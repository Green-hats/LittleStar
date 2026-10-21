package com.littlestar.app.domain;

import androidx.annotation.NonNull;

import java.util.Random;

/**
 * 家长验证（§7.7）：两位数进位加法（结果 20–99）或一位数乘法，各占一半。
 * 答错换一道新题，不锁定、不计次。
 */
public final class ParentChallenge {

    /** 一道验证题。 */
    public record Challenge(@NonNull String questionText, int answer) {
    }

    private final Random random;

    public ParentChallenge(@NonNull Random random) {
        this.random = random;
    }

    @NonNull
    public Challenge next() {
        if (random.nextBoolean()) {
            return addition();
        }
        return multiplication();
    }

    /** 两位数加法：结果 20–99，且个位必须进位。 */
    private Challenge addition() {
        for (int tries = 0; tries < 100; tries++) {
            int a = 10 + random.nextInt(90);
            int b = 10 + random.nextInt(90);
            int sum = a + b;
            boolean carries = (a % 10) + (b % 10) >= 10;
            if (sum >= 20 && sum <= 99 && carries) {
                return new Challenge(a + " + " + b + " = ?", sum);
            }
        }
        return new Challenge("27 + 38 = ?", 65);   // 理论上不会到这，兜底一题合法题
    }

    /** 一位数乘法：因子 2–9。 */
    private Challenge multiplication() {
        int a = 2 + random.nextInt(8);
        int b = 2 + random.nextInt(8);
        return new Challenge(a + " × " + b + " = ?", a * b);
    }

    /** 判定：忽略首尾空格。 */
    public static boolean check(@NonNull String input, @NonNull Challenge challenge) {
        try {
            return Integer.parseInt(input.trim()) == challenge.answer();
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
