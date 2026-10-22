package com.littlestar.app.feature.flashcard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.core.lifecycle.Event;
import com.littlestar.app.data.content.ContentRepository;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.store.ProgressState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 识字卡。mode = SESSION（一轮会话，卡完进测验）/ BROWSE（词表浏览）。
 * extras 只传 ID 与模式：wordIds、startIndex、sessionId（SESSION）。
 */
public final class FlashcardViewModel extends ViewModel {

    public enum Mode {SESSION, BROWSE}

    /** 效果（一次性事件）：跳 Quiz / 关闭。 */
    public sealed interface Effect {
        record GoQuiz(long sessionId) implements Effect {
        }

        record Close() implements Effect {
        }
    }

    public static final class UiState {
        public final Mode mode;
        public final List<Word> words;
        public final int index;
        public final boolean currentLearned;
        public final boolean isLast;

        public UiState(Mode mode, List<Word> words, int index, boolean currentLearned, boolean isLast) {
            this.mode = mode;
            this.words = words;
            this.index = index;
            this.currentLearned = currentLearned;
            this.isLast = isLast;
        }

        @Nullable
        public Word current() {
            return words.isEmpty() ? null : words.get(index);
        }
    }

    private final AppContainer container;
    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<Effect>> effects = new MutableLiveData<>();

    private Mode mode = Mode.SESSION;
    private long sessionId = -1;
    private List<String> wordIds = new ArrayList<>();
    private int index;

    public FlashcardViewModel(@NonNull AppContainer container) {
        this.container = container;
    }

    /** Activity 在 content 加载完成后调用一次。先过滤无效 id，再夹 index（防止越界）。 */
    public void init(@NonNull Mode mode, @NonNull List<String> wordIds, int startIndex, long sessionId) {
        this.mode = mode;
        this.wordIds = new ArrayList<>();
        for (String id : wordIds) {
            if (container.content.wordById(id) != null) {
                this.wordIds.add(id);
            }
        }
        this.sessionId = sessionId;
        this.index = Math.max(0, Math.min(startIndex, this.wordIds.size() - 1));
        publish();
    }

    private void publish() {
        ContentRepository content = container.content;
        List<Word> words = new ArrayList<>();
        for (String id : wordIds) {
            Word w = content.wordById(id);
            if (w != null) {
                words.add(w);
            }
        }
        if (index >= words.size()) {
            index = Math.max(0, words.size() - 1);
        }
        Word cur = words.isEmpty() ? null : words.get(index);
        boolean learned = false;
        if (cur != null) {
            ProgressState ps = container.store.observe().getValue();
            if (ps != null) {
                ProgressState.WordProgress wp = ps.words.get(cur.id);
                learned = wp != null && wp.learnedAt != null;
            }
        }
        state.setValue(new UiState(mode, Collections.unmodifiableList(words), index, learned,
                index == words.size() - 1));
    }

    /** 翻页（ViewPager2 回调）。 */
    public void onPageSelected(int position) {
        if (position != index && position >= 0 && position < this.wordIds.size()) {
            index = position;
            publish();
        }
    }

    /** 数据刷新（学会状态）。 */
    public void refresh() {
        publish();
    }

    /** 点印章：学会；再点不撤销。返回是否新学会（用于播放盖章动效/音效）。 */
    public boolean stamp() {
        UiState s = state.getValue();
        if (s == null || s.current() == null || s.currentLearned) {
            return false;
        }
        container.progress.markLearned(s.current().id);
        return true;
    }

    /** NEXT / 完成。 */
    public void next() {
        UiState s = state.getValue();
        if (s == null) {
            return;
        }
        if (s.isLast) {
            if (mode == Mode.SESSION) {
                effects.setValue(new Event<>(new Effect.GoQuiz(sessionId)));
            } else {
                effects.setValue(new Event<>(new Effect.Close()));
            }
        } else {
            index++;
            publish();
        }
    }

    @NonNull
    public LiveData<UiState> state() {
        return state;
    }

    @NonNull
    public LiveData<Event<Effect>> effects() {
        return effects;
    }

    public long sessionId() {
        return sessionId;
    }
}
