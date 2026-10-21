package com.littlestar.app.core.lifecycle;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.function.Supplier;

/** 通用 ViewModel 工厂：{@code ViewModelFactory.of(() -> new HomeViewModel(container))}。 */
public final class ViewModelFactory {

    private ViewModelFactory() {
    }

    @NonNull
    public static ViewModelProvider.Factory of(@NonNull Supplier<? extends ViewModel> supplier) {
        return new ViewModelProvider.Factory() {
            @NonNull
            @Override
            @SuppressWarnings("unchecked")
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return (T) supplier.get();
            }
        };
    }
}
