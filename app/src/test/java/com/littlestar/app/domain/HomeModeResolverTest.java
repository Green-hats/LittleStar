package com.littlestar.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalTime;

public final class HomeModeResolverTest {

    @Test
    public void at0559_isCulture() {
        assertEquals(HomeModeResolver.Mode.CULTURE, HomeModeResolver.resolve(LocalTime.of(5, 59)));
    }

    @Test
    public void at0600_isWords() {
        assertEquals(HomeModeResolver.Mode.WORDS, HomeModeResolver.resolve(LocalTime.of(6, 0)));
    }

    @Test
    public void at1659_isWords() {
        assertEquals(HomeModeResolver.Mode.WORDS, HomeModeResolver.resolve(LocalTime.of(16, 59)));
    }

    @Test
    public void at1700_isCulture() {
        assertEquals(HomeModeResolver.Mode.CULTURE, HomeModeResolver.resolve(LocalTime.of(17, 0)));
    }

    @Test
    public void at2230_cultureAndBedtimeOnly() {
        LocalTime t = LocalTime.of(22, 30);
        assertEquals(HomeModeResolver.Mode.CULTURE, HomeModeResolver.resolve(t));
        assertTrue(HomeModeResolver.bedtimeOnly(t));
    }

    @Test
    public void at2159_notBedtimeOnly() {
        assertFalse(HomeModeResolver.bedtimeOnly(LocalTime.of(21, 59)));
    }

    @Test
    public void at0800_wordsNotBedtime() {
        LocalTime t = LocalTime.of(8, 0);
        assertEquals(HomeModeResolver.Mode.WORDS, HomeModeResolver.resolve(t));
    }
}
