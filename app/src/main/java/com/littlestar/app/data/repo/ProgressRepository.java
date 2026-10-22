package com.littlestar.app.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.littlestar.app.core.time.AppClock;
import com.littlestar.app.data.content.ContentRepository;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.data.store.ProgressStore;
import com.littlestar.app.domain.ReviewScheduler;

import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * 学习进度入口：页面 / ViewModel 只通过它访问，不直接拿 ProgressStore / ProgressState。
 * 修改方法内部投递到 diskIO 线程。
 */
public final class ProgressRepository {

    private final ProgressStore store;
    private final ContentRepository content;
    private final AppClock clock;

    public ProgressRepository(@NonNull ProgressStore store, @NonNull ContentRepository content,
                              @NonNull AppClock clock) {
        this.store = store;
        this.content = content;
        this.clock = clock;
    }

    /** 整体快照（派生用）。 */
    @NonNull
    public LiveData<ProgressState> observe() {
        return store.observe();
    }

    /** 某个主题已学数。 */
    @NonNull
    public LiveData<Integer> observeThemeLearned(@NonNull String themeId) {
        return Transformations.map(store.observe(), state -> countThemeLearned(state, themeId));
    }

    /** 纯函数：主题 6 个字里 learnedAt != null 的个数。 */
    public int countThemeLearned(@NonNull ProgressState state, @NonNull String themeId) {
        if (!content.isLoaded()) {
            return 0;   // 内容未就绪：先返回 0，等 load 回调后再算（避免主线程文件 IO）
        }
        int n = 0;
        for (com.littlestar.app.data.content.model.Word w : content.wordsOfTheme(themeId)) {
            ProgressState.WordProgress wp = state.words.get(w.id);
            if (wp != null && wp.learnedAt != null) {
                n++;
            }
        }
        return n;
    }

    /** 累计星星 = sessions 里 stars 求和。 */
    public static int totalStars(@NonNull ProgressState state) {
        int total = 0;
        for (ProgressState.Session s : state.sessions) {
            total += s.stars;
        }
        return total;
    }

    /** 某天学会字数 = words 里 learnedAt 落在当天的个数。 */
    public static int learnedOn(@NonNull ProgressState state, @NonNull LocalDate date, @NonNull AppClock clock) {
        long dayStart = date.atStartOfDay(clock.zone()).toInstant().toEpochMilli();
        long dayEnd = date.plusDays(1).atStartOfDay(clock.zone()).toInstant().toEpochMilli();
        int n = 0;
        for (ProgressState.WordProgress wp : state.words.values()) {
            if (wp.learnedAt != null && wp.learnedAt >= dayStart && wp.learnedAt < dayEnd) {
                n++;
            }
        }
        return n;
    }

    /** 点印章 / 手动学会。 */
    public void markLearned(@NonNull String wordId) {
        Instant now = clock.now();
        LocalDate today = clock.today();
        store.update(state -> {
            ProgressState.WordProgress wp =
                    state.words.computeIfAbsent(wordId, k -> new ProgressState.WordProgress());
            ReviewScheduler.markLearned(wp, now, today);
        });
    }

    /** 在指定会话里记一题答案，并更新该字进度（同一个 update 里完成，相当于事务）。 */
    public void recordAnswer(long sessionId, @NonNull String wordId, @NonNull String chosenHanzi,
                             boolean correct, boolean firstTry) {
        Instant now = clock.now();
        LocalDate today = clock.today();
        store.update(state -> {
            ProgressState.Session s = findSession(state, sessionId);
            if (s != null) {
                ProgressState.Answer a = new ProgressState.Answer();
                a.wordId = wordId;
                a.chosen = chosenHanzi;
                a.correct = correct;
                a.firstTry = firstTry;
                a.at = now.toEpochMilli();
                s.answers.add(a);
                if (correct) {
                    s.correct++;
                }
            }
            ProgressState.WordProgress wp =
                    state.words.computeIfAbsent(wordId, k -> new ProgressState.WordProgress());
            ReviewScheduler.onQuizAnswer(wp, correct && firstTry, now, today);
        });
    }

    /** 写星星 + 完成时间；只在 completedAt 为空时写一次（Reward 页调用后 flushNow）。 */
    public void completeSession(long sessionId, int stars) {
        long now = clock.now().toEpochMilli();
        store.update(state -> {
            ProgressState.Session s = findSession(state, sessionId);
            if (s != null && s.completedAt == null) {
                s.completedAt = now;
                s.stars = stars;
            }
        });
        store.flushNow();
    }

    /** 读一次（diskIO 读 → 主线程回调）。 */
    public <T> void read(@NonNull java.util.function.Function<ProgressState, T> query,
                         @NonNull Consumer<T> onMain) {
        store.read(query, onMain);
    }

    /** 在 diskIO 线程改 state（建会话等复杂修改走这里）。 */
    public void update(@NonNull Consumer<ProgressState> mutation) {
        store.update(mutation);
    }

    public void flushNow() {
        store.flushNow();
    }

    @Nullable
    private static ProgressState.Session findSession(@NonNull ProgressState state, long id) {
        for (ProgressState.Session s : state.sessions) {
            if (s.id == id) {
                return s;
            }
        }
        return null;
    }
}
