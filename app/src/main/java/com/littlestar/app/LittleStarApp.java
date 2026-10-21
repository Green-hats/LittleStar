package com.littlestar.app;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

/** Application 入口：持有 {@link AppContainer}。 */
public final class LittleStarApp extends Application {

    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStop(@NonNull LifecycleOwner owner) {
                // 退到后台立刻落盘
                container.store.flushNow();
            }
        });
    }

    @NonNull
    public AppContainer container() {
        return container;
    }
}
