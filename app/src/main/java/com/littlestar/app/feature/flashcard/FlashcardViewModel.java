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
import com.littlestar.app.domain.SessionBuilder;

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
    private boolean initialized;
    /** 主线程 gate，覆盖 content load / store read / build 的完整异步链。 */
    private boolean initializing;
    private boolean cleared;

    /** 只跨线程传递副本，绝不把 store 内可变 Session 暴露给主线程。 */
    private record SessionResult(long id, List<String> newIds, boolean hasQuiz) {
        static SessionResult copyOf(ProgressState.Session session) {
            return new SessionResult(session.id, new ArrayList<>(session.newWordIds),
                    !session.quizWordIds.isEmpty());
        }
    }

    public FlashcardViewModel(@NonNull AppContainer container) {
        this.container = container;
    }

    /** Activity 每次创建均可调用。初始化中的重建复用同一条异步链及最终结果。 */
    public void initialize(@NonNull Mode mode, @NonNull List<String> browseIds,
                           int startIndex, long restoredSessionId) {
        if (initialized || initializing || cleared) {
            return;
        }
        initializing = true;
        this.mode = mode;
        this.sessionId = restoredSessionId;
        this.index = Math.max(0, startIndex);
        List<String> ids = new ArrayList<>(browseIds);
        container.content.load(container.executors, () -> {
            if (cleared) {
                return;
            }
            if (mode == Mode.BROWSE) {
                init(mode, ids, index, restoredSessionId);
            } else if (restoredSessionId > 0) {
                // 进程恢复：快照尚未发布不等于会话缺失，必须等 store 加载后查询。
                container.store.read(progress -> {
                    ProgressState.Session session = SessionBuilder.sessionById(progress, restoredSessionId);
                    return session == null ? null : SessionResult.copyOf(session);
                }, result -> {
                    if (cleared) {
                        return;
                    }
                    if (result == null) {
                        buildSession();
                    } else {
                        acceptSession(result, index);
                    }
                });
            } else {
                buildSession();
            }
        });
    }

    /** 更新与落盘沿用 store 的串行 diskIO 约定；callback 不引用任何 Activity。 */
    private void buildSession() {
        container.store.update(progress -> {
            SessionResult result = SessionResult.copyOf(container.sessionBuilder.buildDailySession(
                    progress, container.settings.getDailyGoal()));
            container.store.flushNow();
            // flushNow 入队在前，随后这个 diskIO barrier 确保保存完成再交付结果。
            container.executors.diskIO().execute(() ->
                    container.executors.mainThread().execute(() -> {
                        if (!cleared) {
                            acceptSession(result, 0);
                        }
                    }));
        });
    }

    private void acceptSession(@NonNull SessionResult result, int startIndex) {
        sessionId = result.id();
        if (!result.hasQuiz() || result.newIds().isEmpty()) {
            initializing = false;
            initialized = true;
            effects.setValue(new Event<>(!result.hasQuiz()
                    ? new Effect.Close() : new Effect.GoQuiz(result.id())));
        } else {
            init(Mode.SESSION, result.newIds(), startIndex, result.id());
        }
    }

    @Override
    protected void onCleared() {
        cleared = true;
    }

    /** 内容加载后初始化。先过滤无效 id，再夹 index（防止越界）。 */
    public void init(@NonNull Mode mode, @NonNull List<String> wordIds, int startIndex, long sessionId) {
        this.initializing = false;
        this.initialized = true;
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

    public boolean isInitialized() {
        return initialized;
    }

    public int currentIndex() {
        return index;
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
        if (initialized) {
            publish();
        }
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
