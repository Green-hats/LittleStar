package com.littlestar.app.domain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public final class ParentChallengeTest {

    @Test
    public void addition_result20to99_mustCarry() {
        ParentChallenge pc = new ParentChallenge(new Random(1));
        for (int i = 0; i < 500; i++) {
            ParentChallenge.Challenge c = pc.next();
            if (c.questionText().contains("+")) {
                String[] parts = c.questionText().replace(" = ?", "").split(" \\+ ");
                int a = Integer.parseInt(parts[0]);
                int b = Integer.parseInt(parts[1]);
                assertTrue(a >= 10 && a <= 99 && b >= 10 && b <= 99);
                assertTrue("必须进位: " + c.questionText(), (a % 10) + (b % 10) >= 10);
                assertTrue(c.answer() >= 20 && c.answer() <= 99);
                assertTrue(c.answer() == a + b);
            }
        }
    }

    @Test
    public void multiplication_factors2to9() {
        ParentChallenge pc = new ParentChallenge(new Random(2));
        boolean seenMul = false;
        for (int i = 0; i < 500; i++) {
            ParentChallenge.Challenge c = pc.next();
            if (c.questionText().contains("×")) {
                seenMul = true;
                String[] parts = c.questionText().replace(" = ?", "").split(" × ");
                int a = Integer.parseInt(parts[0]);
                int b = Integer.parseInt(parts[1]);
                assertTrue(a >= 2 && a <= 9 && b >= 2 && b <= 9);
                assertTrue(c.answer() == a * b);
            }
        }
        assertTrue("500 次里应出现乘法", seenMul);
    }

    @Test
    public void check_trimsSpaces() {
        ParentChallenge.Challenge c = new ParentChallenge.Challenge("6 × 7 = ?", 42);
        assertTrue(ParentChallenge.check(" 42 ", c));
        assertTrue(ParentChallenge.check("42", c));
        assertFalse(ParentChallenge.check("41", c));
        assertFalse(ParentChallenge.check("", c));
        assertFalse(ParentChallenge.check("abc", c));
    }
}
