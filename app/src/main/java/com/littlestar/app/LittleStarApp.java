package com.littlestar.app;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.littlestar.app.core.audio.SfxPlayer;
import com.littlestar.app.core.ui.BlockLayout;

/** Application 入口：持有 {@link AppContainer}。 */
public final class LittleStarApp extends Application {

    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
        // debug 构建开启 StrictMode（容器建好后开启：getFilesDir 等框架调用不算违规）（§15）
        if (BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(new android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads().detectDiskWrites().penaltyLog().build());
        }
        // 硬影块按下音效（BlockLayout 不直接依赖 AppContainer）
        BlockLayout.setPressSoundHook(() -> container.sfx.play(SfxPlayer.Sfx.TAP));
        // 通知渠道 + 每日提醒重排（§10.4：App 启动是重排时机之一）
        container.notifier.ensureChannels();
        container.reminders.reschedule();
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
