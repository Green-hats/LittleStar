package com.littlestar.app.feature.reward;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.core.lifecycle.Event;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.domain.SessionBuilder;

public final class RewardViewModel extends ViewModel {
    public static final class UiState {
        public final int newWords;
        public final int correct;
        public final int total;

        UiState(int newWords, int correct, int total) {
            this.newWords = newWords;
            this.correct = correct;
            this.total = total;
        }
    }

    private final AppContainer container;
    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> effects = new MutableLiveData<>();

    public RewardViewModel(AppContainer container) {
        this.container = container;
    }

    public boolean init(long sessionId) {
        if (state.getValue() != null) {
            return true;
        }
        ProgressState snapshot = container.progress.observe().getValue();
        ProgressState.Session session = snapshot == null ? null : SessionBuilder.sessionById(snapshot, sessionId);
        if (session == null) {
            return false;
        }
        // Wait for the completion snapshot so the summary and review agree.
        if (session.completedAt == null) {
            container.progress.completeSession(sessionId);
            return true;
        }
        state.setValue(new UiState(session.newWordIds.size(), session.correct, session.total));
        effects.setValue(new Event<>(true));
        return true;
    }

    public LiveData<UiState> state() {
        return state;
    }

    public LiveData<Event<Boolean>> effects() {
        return effects;
    }
}
