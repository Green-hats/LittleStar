package com.littlestar.app.feature.culture;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.littlestar.app.AppContainer;
import com.littlestar.app.data.content.model.CultureCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 文化分类：MVP 只显示 mvp=true 的 3 类（2 + 1 宽卡布局由界面决定）。 */
public final class CultureCategoriesViewModel extends ViewModel {

    public static final class CategoryItem {
        public final CultureCategory category;
        public final int count;

        public CategoryItem(CultureCategory category, int count) {
            this.category = category;
            this.count = count;
        }
    }

    private final AppContainer container;
    private final MediatorLiveData<List<CategoryItem>> categories = new MediatorLiveData<>();
    private boolean attached;

    public CultureCategoriesViewModel(@NonNull AppContainer container) {
        this.container = container;
    }

    /** 内容就绪后调用一次。 */
    public void load() {
        if (attached) {
            return;
        }
        attached = true;
        List<CategoryItem> result = new ArrayList<>();
        for (CultureCategory c : container.content.mvpCategories()) {
            result.add(new CategoryItem(c, container.content.itemsOfCategory(c.id).size()));
        }
        categories.setValue(Collections.unmodifiableList(result));
    }

    @NonNull
    public LiveData<List<CategoryItem>> categories() {
        return categories;
    }

    /** 收藏数（顶栏星星位旁的小计数可选；当前用于收藏行角标）。 */
    @NonNull
    public LiveData<Integer> favouriteCount() {
        return androidx.lifecycle.Transformations.map(container.store.observe(),
                ps -> ps == null ? 0 : ps.favourites.size());
    }
}
