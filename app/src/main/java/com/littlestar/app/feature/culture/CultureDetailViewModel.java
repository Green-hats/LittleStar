package com.littlestar.app.feature.culture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.data.content.model.CultureItem;

import java.util.ArrayList;
import java.util.List;

/** 内容详情与同分类播放队列；仅保存内容 ID，重建时重新查询本地内容库。 */
public final class CultureDetailViewModel extends ViewModel {

    public static final class UiState {
        @Nullable public final CultureItem item;
        @NonNull public final List<String> queue;

        UiState(@Nullable CultureItem item, @NonNull List<String> queue) {
            this.item = item;
            this.queue = List.copyOf(queue);
        }
    }

    private final AppContainer container;
    private final MutableLiveData<UiState> state = new MutableLiveData<>();

    public CultureDetailViewModel(@NonNull AppContainer container) {
        this.container = container;
    }

    /** 必须在 content.load 完成后调用。无效 ID 显示不可播放的空状态。 */
    public void init(@Nullable String itemId) {
        CultureItem item = container.content.itemById(itemId);
        List<String> queue = new ArrayList<>();
        if (item != null) {
            for (CultureItem sibling : container.content.itemsOfCategory(item.categoryId)) {
                queue.add(sibling.id);
            }
        }
        state.setValue(new UiState(item, queue));
    }

    @NonNull
    public LiveData<UiState> state() {
        return state;
    }
}
