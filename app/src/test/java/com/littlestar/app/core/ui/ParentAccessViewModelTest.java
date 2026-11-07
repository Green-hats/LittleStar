package com.littlestar.app.core.ui;

import com.littlestar.app.util.FakeClock;
import org.junit.Test;
import java.time.Duration;
import java.time.LocalDateTime;
import static org.junit.Assert.*;

public final class ParentAccessViewModelTest {
    @Test public void newEntryDoesNotReauthenticateButUnknownRestorationDoes() {
        var state = new ParentAccessViewModel();
        assertFalse(state.requiresReauth(false, 0));
        assertTrue(state.requiresReauth(true, 0));
    }

    @Test public void retainedStatePreservesSixtySecondBoundary() {
        var state = new ParentAccessViewModel();
        state.onStopped(0);
        assertFalse(state.requiresReauth(true, 1));
        assertFalse(state.requiresReauth(false, 60_000));
        assertTrue(state.requiresReauth(false, 60_001));
    }

    @Test public void processRestorationAndCounterResetFailClosed() {
        var state = new ParentAccessViewModel();
        state.onStopped(100_000);
        assertTrue(state.requiresReauth(true, 1));
        assertTrue(new ParentAccessViewModel().requiresReauth(true, 100_001));
    }

    @Test public void wallClockChangesCannotExtendAuthentication() {
        var clock = new FakeClock(LocalDateTime.of(2026, 11, 7, 8, 0));
        var state = new ParentAccessViewModel();
        state.onStopped(clock.elapsedRealtimeMillis());
        clock.set(LocalDateTime.of(2026, 10, 1, 0, 0));
        clock.advance(Duration.ofSeconds(61));
        assertTrue(state.requiresReauth(false, clock.elapsedRealtimeMillis()));
        clock.set(LocalDateTime.of(2027, 1, 1, 0, 0));
        state.onStopped(clock.elapsedRealtimeMillis());
        assertFalse(state.requiresReauth(false, clock.elapsedRealtimeMillis()));
    }
}
