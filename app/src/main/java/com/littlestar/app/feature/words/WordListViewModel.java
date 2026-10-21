package com.littlestar.app.feature.words;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.data.content.ContentRepository;
import com.littlestar.app.data.content.model.Theme;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.store.ProgressState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 词表：主题 6 字 + 已学状态；数据一改自动刷新（返回时绿勾立刻更新）。 */
public final class WordListViewModel extends ViewModel {

    public static final class WordItem {
        public final Word word;
        public final boolean learned;

        public WordItem(Word word, boolean learned) {
            this.word = word;
            this.learned = learned;
        }
    }

    public static final class UiState {
        public final Theme theme;
        public final List<WordItem> words;
        public final int learned;

        public UiState(Theme theme, List<WordItem> words, int learned) {
            this.theme = theme;
            this.words = words;
            this.learned = learned;
        }
    }

    private final AppContainer container;
    private final MediatorLiveData<UiState> state = new MediatorLiveData<>();
    private String themeId;
    private boolean attached;

    public WordListViewModel(@NonNull AppContainer container) {
        this.container = container;
    }

    /** 内容就绪后调用一次。 */
    public void init(@NonNull String themeId) {
        this.themeId = themeId;
        if (attached) {
            return;
        }
        attached = true;
        state.addSource(container.progress.observe(), ps -> state.setValue(compute(ps)));
    }

    private UiState compute(ProgressState ps) {
        if (ps == null) {
            return null;
        }
        ContentRepository content = container.content;
        Theme theme = null;
        for (Theme t : content.themes()) {
            if (t.id.equals(themeId)) {
                theme = t;
                break;
            }
        }
        if (theme == null) {
            return null;
        }
        List<WordItem> words = new ArrayList<>();
        int learned = 0;
        for (Word w : theme.words) {
            ProgressState.WordProgress wp = ps.words.get(w.id);
            boolean l = wp != null && wp.learnedAt != null;
            if (l) {
                learned++;
            }
            words.add(new WordItem(w, l));
        }
        return new UiState(theme, Collections.unmodifiableList(words), learned);
    }

    @NonNull
    public LiveData<UiState> state() {
        return state;
    }
}
