package com.littlestar.app.feature.words;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.data.content.ContentRepository;
import com.littlestar.app.data.content.model.Theme;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.store.ProgressState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 主题选择：6 个主题块 + 已学数。 */
public final class ThemesViewModel extends ViewModel {

    /** 一个主题块的数据。 */
    public static final class ThemeItem {
        public final Theme theme;
        public final int learned;       // 已学数 0–6
        public final int total;         // 6

        public ThemeItem(Theme theme, int learned, int total) {
            this.theme = theme;
            this.learned = learned;
            this.total = total;
        }
    }

    private final AppContainer container;
    private final MediatorHolder holder;

    public ThemesViewModel(@NonNull AppContainer container) {
        this.container = container;
        this.holder = new MediatorHolder();
    }

    @NonNull
    public LiveData<List<ThemeItem>> themes() {
        return holder.liveData;
    }

    /** 内容就绪后调用一次。 */
    public void load() {
        holder.attach();
    }

    /** 内部：快照 + 内容 → 主题列表。 */
    private final class MediatorHolder {
        final androidx.lifecycle.MediatorLiveData<List<ThemeItem>> liveData =
                new androidx.lifecycle.MediatorLiveData<>();
        boolean attached;

        void attach() {
            if (attached) {
                return;
            }
            attached = true;
            liveData.addSource(container.progress.observe(), ps -> liveData.setValue(compute(ps)));
        }

        List<ThemeItem> compute(ProgressState ps) {
            if (ps == null) {
                return List.of();
            }
            ContentRepository content = container.content;
            List<ThemeItem> result = new ArrayList<>();
            for (Theme t : content.themes()) {
                int learned = 0;
                for (Word w : t.words) {
                    ProgressState.WordProgress wp = ps.words.get(w.id);
                    if (wp != null && wp.learnedAt != null) {
                        learned++;
                    }
                }
                result.add(new ThemeItem(t, learned, t.words.size()));
            }
            return Collections.unmodifiableList(result);
        }
    }
}
