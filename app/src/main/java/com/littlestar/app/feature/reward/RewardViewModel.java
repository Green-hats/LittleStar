package com.littlestar.app.feature.reward;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.repo.ProgressRepository;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.domain.SessionBuilder;

import java.util.ArrayList;
import java.util.List;

public final class RewardViewModel extends ViewModel {
    enum Result { INDEPENDENT, WITH_CLUE, WITH_CLUE_THEN_INDEPENDENT, CORRECTED, REVISIT }

    record PracticedWord(Word word, Result result) {}

    public static final class UiState {
        public final int correct;
        public final int total;
        final List<PracticedWord> words;

        UiState(int correct, int total, List<PracticedWord> words) {
            this.correct = correct;
            this.total = total;
            this.words = words;
        }
    }

    private final AppContainer container;
    private final MutableLiveData<UiState> state = new MutableLiveData<>();

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
        List<PracticedWord> words = new ArrayList<>();
        for (String id : session.quizWordIds) {
            Word word = container.content.wordById(id);
            ProgressState.Answer answer = ProgressRepository.answerFor(session, id);
            if (word == null || answer == null) {
                continue;
            }
            Result result;
            if (answer.correct && answer.firstTry) {
                result = Result.INDEPENDENT;
            } else if (session.correctedWordIds.contains(id) && session.hintAttempts.containsKey(id)) {
                result = Boolean.TRUE.equals(session.retestResults.get(id))
                        ? Result.WITH_CLUE_THEN_INDEPENDENT : Result.WITH_CLUE;
            } else if (session.correctedWordIds.contains(id) || answer.correct) {
                result = Result.CORRECTED;
            } else {
                result = Result.REVISIT;
            }
            words.add(new PracticedWord(word, result));
        }
        state.setValue(new UiState(session.correct, session.total, List.copyOf(words)));
        return true;
    }

    public LiveData<UiState> state() {
        return state;
    }

}
