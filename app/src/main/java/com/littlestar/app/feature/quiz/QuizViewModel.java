package com.littlestar.app.feature.quiz;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.core.lifecycle.Event;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.domain.QuizGenerator;
import com.littlestar.app.domain.SessionBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 测验。一页一题；题目顺序在会话创建时写进 session.quizWordIds（恢复时按它重建，不重新随机）。
 * 选项顺序按 (session.id, word.id) 确定性种子生成，确保进程重建后四选项位置稳定不变。
 */
public final class QuizViewModel extends ViewModel {

    public enum Mode {SESSION, THEME}

    public sealed interface Effect {
        record GoReward(long sessionId) implements Effect {
        }

        record PlayWord(@NonNull String wordId) implements Effect {
        }

        record AnswerSfx(boolean correct) implements Effect {
        }
    }

    /** 单题 UI 状态。 */
    public static final class UiState {
        public final int index;                 // 当前题（0 起）
        public final int total;
        @Nullable
        public final QuizGenerator.QuizQuestion question;
        public final boolean answered;
        public final boolean lastAnswerCorrect;
        public final int chosenIndex;           // -1 = 未答
        public final boolean isLast;
        public final boolean showEnglishHint;

        public UiState(int index, int total, @Nullable QuizGenerator.QuizQuestion question,
                       boolean answered, boolean lastAnswerCorrect, int chosenIndex,
                       boolean isLast, boolean showEnglishHint) {
            this.index = index;
            this.total = total;
            this.question = question;
            this.answered = answered;
            this.lastAnswerCorrect = lastAnswerCorrect;
            this.chosenIndex = chosenIndex;
            this.isLast = isLast;
            this.showEnglishHint = showEnglishHint;
        }
    }

    private static final String KEY_INDEX = "quiz_index";

    private final AppContainer container;
    private final SavedStateHandle savedState;

    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<Effect>> effects = new MutableLiveData<>();

    private Mode mode = Mode.SESSION;
    private long sessionId = -1;
    private List<QuizGenerator.QuizQuestion> questions = new ArrayList<>();
    /** 刚作答的结果（store 快照到达前 UI 立即反馈用）。 */
    private boolean justAnswered;
    private boolean justAnsweredCorrect;
    private int justChosenIndex = -1;

    private boolean initialized;

    public QuizViewModel(@NonNull AppContainer container, @NonNull SavedStateHandle savedState) {
        this.container = container;
        this.savedState = savedState;
    }

    /** Activity 在 content + store 就绪后调用；会话还没读到时返回 false，稍后重试。 */
    public boolean init(@NonNull Mode mode, long sessionId) {
        if (initialized) {
            return true;
        }
        ProgressState ps = container.store.observe().getValue();
        ProgressState.Session session = ps == null ? null : SessionBuilder.sessionById(ps, sessionId);
        if (session == null) {
            return false;
        }
        initialized = true;
        this.mode = mode;
        this.sessionId = sessionId;
        for (int qIdx = 0; qIdx < session.quizWordIds.size(); qIdx++) {
            String id = session.quizWordIds.get(qIdx);
            Word w = container.content.wordById(id);
            if (w != null) {
                long seed = session.id * 10007L + (long) qIdx * 31L + id.hashCode();
                QuizGenerator seededGen = new QuizGenerator(container.content, new Random(seed));
                questions.add(seededGen.generate(w));
            }
        }
        publish(session);
        if (!restoreAnswered(session)) {
            speakCurrent();
        }
        return true;
    }

    private int index() {
        Integer i = savedState.get(KEY_INDEX);
        return i == null ? 0 : i;
    }

    @Nullable
    private ProgressState.Session currentSession() {
        ProgressState ps = container.store.observe().getValue();
        return ps == null ? null : SessionBuilder.sessionById(ps, sessionId);
    }

    /** 进程恢复：当前题若已有答案，恢复已答状态（含选中项，标 clay/mint）。返回是否恢复为已答。 */
    private boolean restoreAnswered(@Nullable ProgressState.Session session) {
        if (session == null) {
            return false;
        }
        int idx = index();
        if (idx < session.answers.size()) {
            ProgressState.Answer a = session.answers.get(idx);
            justAnswered = true;
            justAnsweredCorrect = a.correct;
            QuizGenerator.QuizQuestion q = idx < questions.size() ? questions.get(idx) : null;
            if (q != null) {
                justChosenIndex = q.options().indexOf(a.chosen);
            }
            publish(session);
            return true;
        }
        return false;
    }

    private void publish(@Nullable ProgressState.Session session) {
        int idx = index();
        QuizGenerator.QuizQuestion q = idx < questions.size() ? questions.get(idx) : null;
        boolean answered = justAnswered;
        boolean correct = justAnsweredCorrect;
        int chosen = justChosenIndex;
        if (!answered && session != null && q != null && idx < session.answers.size()) {
            ProgressState.Answer a = session.answers.get(idx);
            answered = true;
            correct = a.correct;
            chosen = q.options().indexOf(a.chosen);
        }
        state.setValue(new UiState(idx, questions.size(), q, answered, correct, chosen,
                idx == questions.size() - 1, container.settings.isEnglishHint()));
    }

    /** 点选项。立即锁定全部选项并判定（每题一次作答，点即锁定，本次即 firstTry）。 */
    public void choose(int optionIndex) {
        UiState s = state.getValue();
        if (s == null || s.question == null || s.answered) {
            return;
        }
        QuizGenerator.QuizQuestion q = s.question;
        boolean correct = optionIndex == q.correctIndex();
        container.progress.recordAnswer(sessionId, q.wordId(), q.options().get(optionIndex), correct, true);
        justAnswered = true;
        justAnsweredCorrect = correct;
        justChosenIndex = optionIndex;
        publish(currentSession());
        effects.setValue(new Event<>(new Effect.AnswerSfx(correct)));
        if (!correct) {
            effects.setValue(new Event<>(new Effect.PlayWord(q.wordId())));   // 答错后再朗读正确字
        }
    }

    /** NEXT / 完成。 */
    public void next() {
        UiState s = state.getValue();
        if (s == null || !s.answered) {
            return;
        }
        if (s.isLast) {
            effects.setValue(new Event<>(new Effect.GoReward(sessionId)));
            return;
        }
        savedState.set(KEY_INDEX, s.index + 1);
        justAnswered = false;
        justChosenIndex = -1;
        publish(currentSession());
        speakCurrent();
    }

    /** 自动读题 / 点「听」重听。 */
    public void speakCurrent() {
        UiState s = state.getValue();
        if (s != null && s.question != null) {
            effects.setValue(new Event<>(new Effect.PlayWord(s.question.wordId())));
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
}
