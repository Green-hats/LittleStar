package com.littlestar.app.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ScreenTimePolicyTest {

    @Test
    public void underLimit_noContinuous_none() {
        assertEquals(ScreenTimePolicy.Decision.NONE,
                ScreenTimePolicy.decide(60, 30, 0, 0, true, false));
    }

    @Test
    public void atLimit_limit() {
        assertEquals(ScreenTimePolicy.Decision.LIMIT,
                ScreenTimePolicy.decide(30 * 60, 30, 0, 0, true, false));
    }

    @Test
    public void extraMinutes_extendLimit() {
        // 30 分钟上限 + 10 分钟 extra：用 35 分钟不到限
        assertEquals(ScreenTimePolicy.Decision.NONE,
                ScreenTimePolicy.decide(35 * 60, 30, 10, 0, false, false));
        assertEquals(ScreenTimePolicy.Decision.LIMIT,
                ScreenTimePolicy.decide(40 * 60, 30, 10, 0, false, false));
    }

    @Test
    public void continuous15min_rest() {
        assertEquals(ScreenTimePolicy.Decision.REST,
                ScreenTimePolicy.decide(15 * 60, 30, 0, 15, true, false));
    }

    @Test
    public void restReminderOff_none() {
        assertEquals(ScreenTimePolicy.Decision.NONE,
                ScreenTimePolicy.decide(15 * 60, 30, 0, 20, false, false));
    }

    @Test
    public void defer_returnsNone() {
        assertEquals(ScreenTimePolicy.Decision.NONE,
                ScreenTimePolicy.decide(60 * 60, 30, 0, 30, true, true));
    }
}
