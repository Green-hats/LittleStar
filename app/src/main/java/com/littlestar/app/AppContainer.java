package com.littlestar.app;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.littlestar.app.core.screentime.ScreenTimeTracker;
import com.littlestar.app.core.time.AppClock;
import com.littlestar.app.core.time.SystemAppClock;
import com.littlestar.app.data.prefs.SettingsStore;
import com.littlestar.app.data.store.AtomicStateFile;
import com.littlestar.app.data.store.ProgressStore;

import java.io.File;

/**
 * 手写依赖容器。字段按里程碑逐步补齐；构造顺序即依赖顺序。
 */
public final class AppContainer {

    public final AppExecutors executors = new AppExecutors();
    public final AppClock clock = new SystemAppClock();

    public final ProgressStore store;
    public final SettingsStore settings;
    public final ScreenTimeTracker screenTime;

    public AppContainer(@NonNull Application app) {
        Gson gson = new Gson();
        store = new ProgressStore(
                new AtomicStateFile(new File(app.getFilesDir(), "progress.json")), gson, clock, executors);
        settings = new SettingsStore(app.getSharedPreferences("settings", Context.MODE_PRIVATE));
        screenTime = new ScreenTimeTracker(clock, new Handler(Looper.getMainLooper()));
        store.loadAsync();   // 后台线程读入 progress.json
    }

    /** ChildActivity.onResume 回调（M7 接入 ScreenTimeTracker 计时）。 */
    public void onChildResumed(@NonNull com.littlestar.app.core.ui.ChildActivity activity) {
        screenTime.onChildResumed(activity);
    }

    /** ChildActivity.onPause 回调。 */
    public void onChildPaused() {
        screenTime.onChildPaused();
    }
}
