package com.littlestar.app.data.repo;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.littlestar.app.core.time.AppClock;
import com.littlestar.app.data.store.ProgressStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 收藏入口。 */
public final class FavouriteRepository {

    public enum Sort {NEWEST, OLDEST}

    private final ProgressStore store;
    private final AppClock clock;

    public FavouriteRepository(@NonNull ProgressStore store, @NonNull AppClock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** 切换收藏，返回现在是否已收藏（回调主线程）。 */
    public void toggle(@NonNull String itemId) {
        long now = clock.now().toEpochMilli();
        store.update(state -> {
            if (state.favourites.remove(itemId) == null) {
                state.favourites.put(itemId, now);
            }
        });
    }

    /** 收藏列表（itemId → 时间），按排序返回 id 列表。 */
    @NonNull
    public LiveData<List<String>> observeIds(@NonNull Sort sort) {
        return Transformations.map(store.observe(), state -> {
            List<Map.Entry<String, Long>> entries = new ArrayList<>(state.favourites.entrySet());
            entries.sort((a, b) -> sort == Sort.NEWEST
                    ? Long.compare(b.getValue(), a.getValue())
                    : Long.compare(a.getValue(), b.getValue()));
            List<String> ids = new ArrayList<>(entries.size());
            for (Map.Entry<String, Long> e : entries) {
                ids.add(e.getKey());
            }
            return Collections.unmodifiableList(ids);
        });
    }

    @NonNull
    public LiveData<Boolean> observeIsFavourite(@NonNull String itemId) {
        return Transformations.map(store.observe(), state -> state.favourites.containsKey(itemId));
    }
}
