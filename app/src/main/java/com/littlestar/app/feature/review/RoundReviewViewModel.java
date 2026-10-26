package com.littlestar.app.feature.review;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.domain.SessionBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 本轮回顾：只读 {@link ProgressState.Session#answers}，不修改进度。
 * 用 sessionId 从会话里取答案，目标页显示用户选了什么、正确答案是什么。
 */
public final class RoundReviewViewModel extends ViewModel {

    public static final class ReviewItem {
        public final Word word;
        public final String chosen;
        public final boolean correct;

        public ReviewItem(@NonNull Word word, @NonNull String chosen, boolean correct) {
            this.word = word;
            this.chosen = chosen;
            this.correct = correct;
        }
    }

    public static final class UiState {
        public final List<ReviewItem> items;
        public final int correct;
        public final int total;

        public UiState(@NonNull List<ReviewItem> items, int correct, int total) {
            this.items = items;
            this.correct = correct;
            this.total = total;
        }
    }

    private final AppContainer container;
    private final MediatorLiveData<UiState> state = new MediatorLiveData<>();
    private long sessionId = -1;
    private boolean attached;

    public RoundReviewViewModel(@NonNull AppContainer container) {
        this.container = container;
    }

    /** 绑定会话 id；store 快照到达后自动计算。 */
    public void init(long sessionId) {
        this.sessionId = sessionId;
        if (attached) {
            return;
        }
        attached = true;
        state.addSource(container.store.observe(), ps -> {
            if (ps == null) {
                return;
            }
            state.setValue(compute(ps));
        });
    }

    @NonNull
    private UiState compute(@NonNull ProgressState ps) {
        ProgressState.Session session = SessionBuilder.sessionById(ps, sessionId);
        if (session == null) {
            return new UiState(Collections.emptyList(), 0, 0);
        }
        List<ReviewItem> items = new ArrayList<>();
        for (ProgressState.Answer answer : session.answers) {
            Word word = container.content.wordById(answer.wordId);
            if (word == null) {
                continue;
            }
            String chosen = answer.chosen == null ? "" : answer.chosen;
            items.add(new ReviewItem(word, chosen, answer.correct));
        }
        int total = session.total > 0 ? session.total : items.size();
        return new UiState(Collections.unmodifiableList(items), session.correct, total);
    }

    @NonNull
    public LiveData<UiState> state() {
        return state;
    }
}
