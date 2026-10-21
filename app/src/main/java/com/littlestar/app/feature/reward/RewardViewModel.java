package com.littlestar.app.feature.reward;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.core.lifecycle.Event;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.domain.SessionBuilder;
import com.littlestar.app.domain.StarCalculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 奖励页：读 session 算星星，写库只写一次（completedAt 为空才写）。 */
public final class RewardViewModel extends ViewModel {

    public sealed interface Effect {
        record Close() implements Effect {
        }

        record PlayAgain() implements Effect {
        }

        record StarSfx(int index) implements Effect {
        }

        record PraiseSfx() implements Effect {
        }

        /** init 时连续发射的多个事件打包成一批（LiveData 在观察者未激活时只保留最后一个值）。 */
        record Batch(@NonNull List<Effect> effects) implements Effect {
        }
    }

    public static final class UiState {
        public final int stars;
        public final int starSlots;             // = newWords.size（1–3）
        public final List<Word> learnedWords;   // 本轮新字
        public final boolean showStars;         // THEME 隐藏
        public final int correct;
        public final int total;

        public UiState(int stars, int starSlots, List<Word> learnedWords,
                       boolean showStars, int correct, int total) {
            this.stars = stars;
            this.starSlots = starSlots;
            this.learnedWords = learnedWords;
            this.showStars = showStars;
            this.correct = correct;
            this.total = total;
        }
    }

    private final AppContainer container;
    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<Effect>> effects = new MutableLiveData<>();

    private boolean initialized;
    private boolean written;

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
        int stars = StarCalculator.compute(s.kind, s.newWordIds, s.answers);
        // 写库只写一次
        if (!written && s.completedAt == null) {
            written = true;
            container.progress.completeSession(sessionId, stars);
        }
        List<Word> learned = new ArrayList<>();
        for (String id : s.newWordIds) {
            Word w = container.content.wordById(id);
            if (w != null) {
                learned.add(w);
            }
        }
        boolean showStars = !SessionBuilder.KIND_THEME.equals(s.kind);
        state.setValue(new UiState(stars, Math.max(1, s.newWordIds.size()),
                Collections.unmodifiableList(learned), showStars, s.correct, s.total));
        if (showStars) {
            List<Effect> batch = new ArrayList<>();
            for (int i = 0; i < stars; i++) {
                batch.add(new Effect.StarSfx(i));
            }
            batch.add(new Effect.PraiseSfx());
            effects.setValue(new Event<>(new Effect.Batch(batch)));
        }
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
