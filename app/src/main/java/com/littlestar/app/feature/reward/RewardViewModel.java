package com.littlestar.app.feature.reward;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.core.lifecycle.Event;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.domain.SessionBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 学习完成页：展示本轮新字，完成时间只记录一次。 */
public final class RewardViewModel extends ViewModel {

    public sealed interface Effect {
        record Close() implements Effect {
        }

        record PlayAgain() implements Effect {
        }

        record PraiseSfx() implements Effect {
        }
    }

    public static final class UiState {
        public final List<Word> learnedWords;   // 本轮新字

        public UiState(@NonNull List<Word> learnedWords) {
            this.learnedWords = learnedWords;
        }
    }

    private final AppContainer container;
    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<Effect>> effects = new MutableLiveData<>();

    private boolean initialized;

    public RewardViewModel(@NonNull AppContainer container) {
        this.container = container;
    }

    /** 读 session；数据未就绪返回 false 稍后重试。 */
    public boolean init(long sessionId) {
        if (initialized) {
            return true;
        }
        ProgressState ps = container.store.observe().getValue();
        ProgressState.Session s = ps == null ? null : SessionBuilder.sessionById(ps, sessionId);
        if (s == null) {
            return false;
        }
        initialized = true;
        if (s.completedAt == null) {
            container.progress.completeSession(sessionId);
        }
        List<Word> learned = new ArrayList<>();
        for (String id : s.newWordIds) {
            Word w = container.content.wordById(id);
            if (w != null) {
                learned.add(w);
            }
        }
        state.setValue(new UiState(Collections.unmodifiableList(learned)));
        effects.setValue(new Event<>(new Effect.PraiseSfx()));
        return true;
    }

    public void finish() {
        effects.setValue(new Event<>(new Effect.Close()));
    }

    public void playAgain() {
        effects.setValue(new Event<>(new Effect.PlayAgain()));
    }

    @NonNull
    public LiveData<UiState> state() {
        return state;
    }

    @NonNull
    public LiveData<Event<Effect>> effects() {
        return effects;
    }
}
