package com.littlestar.app.feature.favourites;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.data.content.model.CultureItem;
import com.littlestar.app.data.repo.FavouriteRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 收藏。点红心取消收藏时行不立刻消失（离开页面后再移除），
 * 用 pendingUnfav 集合在内存里标灰。
 */
public final class FavouritesViewModel extends ViewModel {

    public static final class Row {
        public final CultureItem item;
        public final boolean active;            // false = 本次会话内刚被取消（空心，不消失）

        public Row(CultureItem item, boolean active) {
            this.item = item;
            this.active = active;
        }
    }

    public static final class UiState {
        public final List<Row> rows;
        public final FavouriteRepository.Sort sort;

        public UiState(List<Row> rows, FavouriteRepository.Sort sort) {
            this.rows = rows;
            this.sort = sort;
        }

        public boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    private final AppContainer container;
    private final MediatorLiveData<UiState> state = new MediatorLiveData<>();
    private FavouriteRepository.Sort sort = FavouriteRepository.Sort.NEWEST;
    private final List<String> pendingUnfav = new ArrayList<>();
    private boolean attached;
    private List<String> lastIds;

    public FavouritesViewModel(@NonNull AppContainer container) {
        this.container = container;
    }

    public void load() {
        if (attached) {
            return;
        }
        attached = true;
        state.addSource(container.favourites.observeIds(sort), ids -> {
            lastIds = ids;
            publish();
        });
    }

    public void setSort(@NonNull FavouriteRepository.Sort newSort) {
        sort = newSort;
        publish();
    }

    /** 点红心：行不消失，变成空心。 */
    public void toggle(@NonNull String itemId) {
        if (pendingUnfav.contains(itemId)) {
            pendingUnfav.remove(itemId);
        } else {
            pendingUnfav.add(itemId);
        }
        container.favourites.toggle(itemId);
        publish();
    }

    private void publish() {
        if (lastIds == null) {
            return;
        }
        List<String> ids = new ArrayList<>(lastIds);
        // 排序切换在内存做：重新按收藏时间排
        if (sort == FavouriteRepository.Sort.OLDEST) {
            Collections.reverse(ids);   // observeIds 默认 NEWEST；反转为最早
        }
        List<Row> rows = new ArrayList<>();
        for (String id : ids) {
            CultureItem item = container.content.itemById(id);
            if (item != null) {
                rows.add(new Row(item, !pendingUnfav.contains(id)));
            }
        }
        state.setValue(new UiState(Collections.unmodifiableList(rows), sort));
    }

    @NonNull
    public LiveData<UiState> state() {
        return state;
    }
}
