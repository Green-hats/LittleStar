package com.littlestar.app.feature.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.data.content.ContentRepository;
import com.littlestar.app.data.content.model.CultureItem;
import com.littlestar.app.data.content.model.LyricLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;

/**
 * 播放器 UI 状态（播放控制走 MediaController，在 Activity 里）。
 * ViewModel 只管内容和歌词高亮。
 */
public final class PlayerViewModel extends ViewModel {

    public static final class UiState {
        public final CultureItem item;
        public final List<String> queue;
        public final int queueIndex;
        public final int lyricIndex;            // 当前歌词句；-1 = 无歌词/未开始
        public final long positionMs;
        public final long durationMs;
        public final boolean playing;

        public UiState(CultureItem item, List<String> queue, int queueIndex,
                       int lyricIndex, long positionMs, long durationMs, boolean playing) {
            this.item = item;
            this.queue = queue;
            this.queueIndex = queueIndex;
            this.lyricIndex = lyricIndex;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.playing = playing;
        }

        UiState with(int newLyricIndex, long newPositionMs,
                     long newDurationMs, boolean newPlaying) {
            return new UiState(item, queue, queueIndex, newLyricIndex,
                    newPositionMs, newDurationMs, newPlaying);
        }

        UiState withItem(CultureItem newItem, int newQueueIndex) {
            return new UiState(newItem, queue, newQueueIndex, -1, 0, 0, playing);
        }
    }

    private final ContentRepository content;
    private final MutableLiveData<UiState> state = new MutableLiveData<>();

    public PlayerViewModel(@NonNull AppContainer container) {
        this(container.content);
    }

    PlayerViewModel(ContentRepository content) {
        this.content = content;
    }

    /** 内容就绪后调用一次。 */
    public void init(@Nullable String itemId, @NonNull List<String> queue) {
        CultureItem item = itemId == null ? null : content.itemById(itemId);
        if (item == null || state.getValue() != null) {
            return;
        }
        List<String> q = new ArrayList<>();
        for (String id : new LinkedHashSet<>(queue)) {
            if (id != null && content.itemById(id) != null) {
                q.add(id);
            }
        }
        if (!q.contains(itemId)) {
            q.add(0, itemId);
        }
        state.setValue(new UiState(item, Collections.unmodifiableList(q), q.indexOf(itemId),
                -1, 0, 0, false));
    }

    /** 当前条目切换（上一首/下一首）。 */
    public void onTrackChanged(@NonNull String itemId) {
        CultureItem item = content.itemById(itemId);
        if (item == null) {
            return;
        }
        UiState current = state.getValue();
        if (current != null && !current.item.id.equals(itemId) && current.queue.contains(itemId)) {
            state.setValue(current.withItem(item, current.queue.indexOf(itemId)));
        }
    }

    /** 每 250ms 由 Activity 用 MediaController 位置驱动。 */
    public void onProgress(long positionMs, long durationMs, boolean playing) {
        update(s -> s.with(lyricIndex(s.item, positionMs), positionMs, durationMs, playing));
    }

    /** 显式打开遵从选择；重连同一队列时遵从服务当前曲目。 */
    public boolean canKeepPlayback(List<String> activeQueue, @Nullable String currentId,
                                   boolean reconnecting) {
        UiState s = state.getValue();
        return s != null && currentId != null && s.queue.equals(activeQueue)
                && s.queue.contains(currentId) && (reconnecting || s.item.id.equals(currentId));
    }

    /** 按 startMs 找当前句；无歌词返回 -1。 */
    static int lyricIndex(@Nullable CultureItem item, long positionMs) {
        if (item == null || item.lyrics == null || item.lyrics.isEmpty()) {
            return -1;
        }
        int idx = -1;
        for (int i = 0; i < item.lyrics.size(); i++) {
            LyricLine line = item.lyrics.get(i);
            if (positionMs >= line.startMs) {
                idx = i;
            } else {
                break;
            }
        }
        return idx;
    }

    @NonNull
    public LiveData<UiState> state() {
        return state;
    }

    private void update(@NonNull java.util.function.UnaryOperator<UiState> op) {
        UiState s = state.getValue();
        if (s != null) {
            state.setValue(op.apply(s));
        }
    }
}
