package com.littlestar.app.feature.quiz;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.core.lifecycle.Event;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.repo.ProgressRepository;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.domain.QuizGenerator;
import com.littlestar.app.domain.QuizGenerator.QuizQuestion;
import com.littlestar.app.domain.SessionBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class QuizViewModel extends ViewModel {

    public sealed interface Effect {
        record GoReward(long sessionId) implements Effect {}
        record PlayWord(@NonNull String wordId) implements Effect {}
    }

    public static final class UiState {
        public final int index;
        public final int total;
        @Nullable public final QuizQuestion question;
        public final boolean answered;
        public final boolean lastAnswerCorrect;
        public final int chosenIndex;
        public final boolean isLast;
        public final boolean retrying;
        public final boolean corrected;
        public final int retryChosenIndex;
        public final boolean retest;
        public final boolean showingHint;

        UiState(int index, int total, @Nullable QuizQuestion question, boolean answered,
                boolean lastAnswerCorrect, int chosenIndex, boolean isLast, boolean retrying,
                boolean corrected, int retryChosenIndex, boolean retest, boolean showingHint) {
            this.index = index;
            this.total = total;
            this.question = question;
            this.answered = answered;
            this.lastAnswerCorrect = lastAnswerCorrect;
            this.chosenIndex = chosenIndex;
            this.isLast = isLast;
            this.retrying = retrying;
            this.corrected = corrected;
            this.retryChosenIndex = retryChosenIndex;
            this.retest = retest;
            this.showingHint = showingHint;
        }

        public boolean canAdvance() {
            return lastAnswerCorrect || corrected || (retest && answered);
        }
    }

    private final AppContainer container;
    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<Effect>> effects = new MutableLiveData<>();
    private final List<QuizQuestion> questions = new ArrayList<>();
    private ProgressState.Session session;
    private int retryMissIndex = -1;
    private Integer retestChosenIndex;
    private boolean completing;

    public QuizViewModel(@NonNull AppContainer container) {
        this.container = container;
    }

    public boolean init(long sessionId) {
        if (session != null) {
            return true;
        }
        ProgressState ps = container.store.observe().getValue();
        ProgressState.Session source = ps == null ? null : SessionBuilder.sessionById(ps, sessionId);
        if (source == null) {
            return false;
        }
        session = new ProgressState.Session();
        session.id = source.id;
        session.practiceIndex = source.practiceIndex;
        session.practiceRetestWordId = source.practiceRetestWordId;
        session.answers.addAll(source.answers);
        session.correctedWordIds.addAll(source.correctedWordIds);
        session.hintAttempts.putAll(source.hintAttempts);
        session.retestResults.putAll(source.retestResults);
        for (int i = 0; i < source.quizWordIds.size(); i++) {
            String id = source.quizWordIds.get(i);
            Word word = container.content.wordById(id);
            if (word != null) {
                long seed = sessionId * 10007L + i * 31L + id.hashCode();
                questions.add(new QuizGenerator(container.content, new Random(seed)).generate(word));
            }
        }
        session.practiceIndex = Math.max(0, Math.min(session.practiceIndex, questions.size() - 1));
        container.progress.savePracticePosition(session.id, session.practiceIndex, session.practiceRetestWordId);
        showPrompt();
        return true;
    }

    @Nullable
    private QuizQuestion baseQuestion() {
        if (session.practiceRetestWordId != null) {
            for (QuizQuestion q : questions) {
                if (q.wordId().equals(session.practiceRetestWordId)) {
                    return q;
                }
            }
        }
        return questions.isEmpty() ? null : questions.get(session.practiceIndex);
    }

    private void publish() {
        int idx = session.practiceIndex;
        QuizQuestion q = baseQuestion();
        String id = q == null ? "" : q.wordId();
        boolean retest = id.equals(session.practiceRetestWordId);
        ProgressState.Answer first = ProgressRepository.answerFor(session, id);
        int attempts = session.hintAttempts.getOrDefault(id, 0);
        boolean corrected = !retest && (session.correctedWordIds.contains(id)
                || (first != null && first.correct && !first.firstTry));
        boolean showingHint = !retest && attempts > 0 && !corrected;
        boolean answered = retest ? session.retestResults.containsKey(id) : first != null;
        boolean correct = retest ? Boolean.TRUE.equals(session.retestResults.get(id))
                : first != null && first.correct && first.firstTry;
        if (q != null && (attempts > 0 || retest)) {
            q = q.reordered(attempts + (retest ? 1 : 0));
        }
        int chosen = q == null ? -1 : retest ? retestChosenIndex == null ? -1 : retestChosenIndex
                : first == null ? -1 : q.options().indexOf(first.chosen);
        state.setValue(new UiState(idx, questions.size(), q, answered, correct, chosen,
                idx == questions.size() - 1 && pendingRetest() == null,
                showingHint && retryMissIndex == -1, corrected, retryMissIndex, retest, showingHint));
    }

    public void choose(int optionIndex) {
        UiState s = state.getValue();
        if (s == null || s.question == null || optionIndex < 0 || completing
                || optionIndex >= s.question.options().size() || (s.answered && !s.retrying)) {
            return;
        }
        QuizQuestion q = s.question;
        boolean correct = optionIndex == q.correctIndex();
        if (s.retest) {
            retestChosenIndex = optionIndex;
            session.retestResults.put(q.wordId(), correct);
            container.progress.recordRetest(session.id, q.wordId(), correct);
        } else if (s.retrying) {
            if (correct) {
                session.correctedWordIds.add(q.wordId());
                container.progress.recordCorrection(session.id, q.wordId());
            } else {
                retryMissIndex = optionIndex;
            }
        } else {
            ProgressState.Answer first = new ProgressState.Answer();
            first.wordId = q.wordId();
            first.chosen = q.options().get(optionIndex);
            first.correct = correct;
            first.firstTry = true;
            session.answers.add(first);
            container.progress.recordAnswer(session.id, first.wordId, first.chosen, correct, true);
        }
        publish();
    }

    public void retry() {
        UiState s = state.getValue();
        if (s == null || s.question == null || !s.answered || s.retest
                || s.canAdvance() || s.retrying) {
            return;
        }
        String id = s.question.wordId();
        int attempt = session.hintAttempts.getOrDefault(id, 0) + 1;
        session.hintAttempts.put(id, attempt);
        retryMissIndex = -1;
        container.progress.recordHintAttempt(session.id, id, attempt);
        showPrompt();
    }

    @Nullable
    private String pendingRetest() {
        int i = session.practiceIndex;
        if (i > 0 && needsRetest(questions.get(i - 1).wordId())) {
            return questions.get(i - 1).wordId();
        }
        if (i == questions.size() - 1) {
            for (QuizQuestion q : questions) {
                if (needsRetest(q.wordId())) {
                    return q.wordId();
                }
            }
        }
        return null;
    }

    private boolean needsRetest(String id) {
        return session.hintAttempts.containsKey(id) && session.correctedWordIds.contains(id)
                && !session.retestResults.containsKey(id);
    }

    public void next() {
        UiState s = state.getValue();
        if (s == null || !s.canAdvance() || completing) {
            return;
        }
        if (s.isLast) {
            completing = true;
            container.progress.completeSession(session.id);
            effects.setValue(new Event<>(new Effect.GoReward(session.id)));
            return;
        }
        String retestId = pendingRetest();
        if (retestId == null) {
            session.practiceIndex++;
        }
        session.practiceRetestWordId = retestId;
        retryMissIndex = -1;
        retestChosenIndex = null;
        container.progress.savePracticePosition(session.id, session.practiceIndex, retestId);
        showPrompt();
    }

    private void showPrompt() {
        publish();
        UiState s = state.getValue();
        if (s != null && (!s.answered || s.retrying)) {
            speakCurrent();
        }
    }

    public void speakCurrent() {
        UiState s = state.getValue();
        if (s != null && s.question != null) {
            effects.setValue(new Event<>(new Effect.PlayWord(s.question.wordId())));
        }
    }

    @NonNull public LiveData<UiState> state() {
        return state;
    }

    @NonNull public LiveData<Event<Effect>> effects() {
        return effects;
    }
}
