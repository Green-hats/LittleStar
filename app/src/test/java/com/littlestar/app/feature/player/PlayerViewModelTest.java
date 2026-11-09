package com.littlestar.app.feature.player;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.littlestar.app.util.TestContent;
import org.junit.Rule;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class PlayerViewModelTest {
    @Rule public InstantTaskExecutorRule liveData = new InstantTaskExecutorRule();
    private static final String A = "xiaoxingxing";
    private static final String B = "liangzhilaohu";
    private static final List<String> QUEUE = List.of(A, B);

    private PlayerViewModel player() {
        var vm = new PlayerViewModel(TestContent.load());
        vm.init(A, QUEUE);
        return vm;
    }

    @Test public void reconnectKeepsBackgroundTrackWhileFreshEntryHonoursSelection() {
        var vm = player();
        assertTrue(vm.canKeepPlayback(QUEUE, B, true));
        assertFalse(vm.canKeepPlayback(QUEUE, B, false));
        assertTrue(vm.canKeepPlayback(QUEUE, A, false));
    }

    @Test public void equalQueueLengthDoesNotMeanEqualQueue() {
        var vm = player();
        assertFalse(vm.canKeepPlayback(List.of(A, "baluobo"), A, true));
        assertFalse(vm.canKeepPlayback(List.of(B, A), A, true));
        assertFalse(vm.canKeepPlayback(QUEUE, null, true));
        assertFalse(vm.canKeepPlayback(QUEUE, "missing", true));
        assertFalse(vm.canKeepPlayback(List.of(), null, true));
    }

    @Test public void backgroundTrackAndPausedPositionCanBeAdopted() {
        var vm = player();
        vm.onTrackChanged(B);
        vm.onProgress(5_000, 20_000, false);
        var ui = vm.state().getValue();
        assertEquals(B, ui.item.id);
        assertEquals(1, ui.queueIndex);
        assertEquals(5_000, ui.positionMs);
        assertFalse(ui.playing);
        vm.onTrackChanged(B);
        assertEquals(5_000, vm.state().getValue().positionMs);
    }

    @Test public void repeatedInitKeepsCurrentTrackAndPausedProgress() {
        var vm = player();
        vm.onTrackChanged(B);
        vm.onProgress(5_000, 20_000, false);
        vm.init(A, QUEUE);
        var ui = vm.state().getValue();
        assertEquals(B, ui.item.id);
        assertEquals(5_000, ui.positionMs);
        assertFalse(ui.playing);
    }

    @Test public void nullSelectionAndNullQueueEntriesAreIgnored() {
        var vm = new PlayerViewModel(TestContent.load());
        vm.init(null, QUEUE);
        assertNull(vm.state().getValue());
        vm.init(A, java.util.Arrays.asList(null, A, A, "missing", B));
        assertEquals(QUEUE, vm.state().getValue().queue);
        vm.onTrackChanged("baluobo");
        assertEquals(A, vm.state().getValue().item.id);
    }

    @Test public void emptyOrInvalidQueueStillContainsSelectedItem() {
        var vm = new PlayerViewModel(TestContent.load());
        vm.init(B, List.of("missing", A, A));
        assertEquals(List.of(B, A), vm.state().getValue().queue);
        assertEquals(B, vm.state().getValue().queue.get(vm.state().getValue().queueIndex));
        var empty = new PlayerViewModel(TestContent.load());
        empty.init(A, List.of());
        assertEquals(List.of(A), empty.state().getValue().queue);
    }
}
