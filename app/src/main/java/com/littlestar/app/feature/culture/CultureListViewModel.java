package com.littlestar.app.feature.culture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.data.content.model.CultureCategory;
import com.littlestar.app.data.content.model.CultureItem;

import java.util.Collections;
import java.util.List;

/** 文化列表：一个分类的全部条目。 */
public final class CultureListViewModel extends ViewModel {

    public static final class UiState {
        public final CultureCategory category;
        public final List<CultureItem> items;

        public UiState(CultureCategory category, List<CultureItem> items) {
            this.category = category;
            this.items = items;
        }
    }

    private final AppContainer container;
    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    private boolean loaded;

    public CultureListViewModel(@NonNull AppContainer container) {
        this.container = container;
    }

    /** 内容就绪后调用一次。 */
    public void init(@NonNull String categoryId) {
        if (loaded) {
            return;
        }
        loaded = true;
        CultureCategory category = null;
        for (CultureCategory c : container.content.categories()) {
            if (c.id.equals(categoryId)) {
                category = c;
                break;
            }
        }
        if (category == null) {
            return;
        }
        state.setValue(new UiState(category,
                Collections.unmodifiableList(container.content.itemsOfCategory(categoryId))));
    }

    @Nullable
    public CultureItem item(@NonNull String id) {
        return container.content.itemById(id);
    }

    @NonNull
    public LiveData<UiState> state() {
        return state;
    }
}
