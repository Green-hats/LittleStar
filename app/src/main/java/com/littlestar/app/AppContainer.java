package com.littlestar.app;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.littlestar.app.core.audio.SfxPlayer;
import com.littlestar.app.core.time.AppClock;
import com.littlestar.app.core.time.SystemAppClock;
import com.littlestar.app.data.content.ContentRepository;
import com.littlestar.app.data.prefs.SettingsStore;
import com.littlestar.app.data.repo.ProgressRepository;
import com.littlestar.app.data.store.AtomicStateFile;
import com.littlestar.app.data.store.ProgressStore;
import com.littlestar.app.core.audio.SpeechPlayer;
import com.littlestar.app.domain.ReviewScheduler;
import com.littlestar.app.domain.SessionBuilder;
import com.littlestar.app.notify.Notifier;
import com.littlestar.app.notify.ReminderScheduler;

import java.io.File;
import java.util.Random;

/**
 * 手写依赖容器。字段按里程碑逐步补齐；构造顺序即依赖顺序。
 */
public final class AppContainer {

    public final AppExecutors executors = new AppExecutors();
    public final AppClock clock = new SystemAppClock();

    public final ProgressStore store;
    public final SettingsStore settings;
    public final ContentRepository content;
    public final ProgressRepository progress;
    public final ReviewScheduler scheduler;
    public final SessionBuilder sessionBuilder;
    public final SfxPlayer sfx;
    public final SpeechPlayer speech;
    public final Notifier notifier;
    public final ReminderScheduler reminders;

    public AppContainer(@NonNull Application app) {
        Gson gson = new Gson();
        store = new ProgressStore(
                new AtomicStateFile(new File(app.getFilesDir(), "progress.json")), gson, clock, executors);
        settings = new SettingsStore(app.getSharedPreferences("settings", Context.MODE_PRIVATE));
        content = new ContentRepository(app.getAssets(), gson);
        progress = new ProgressRepository(store, content, clock);
        scheduler = new ReviewScheduler();
        sessionBuilder = new SessionBuilder(content, scheduler, clock, new Random());
        sfx = new SfxPlayer(app, settings::isSoundEnabled);
        speech = new SpeechPlayer(app);
        notifier = new Notifier(app);
        reminders = new ReminderScheduler(app, settings, clock);
        store.loadAsync();   // 后台线程读入 progress.json
    }
}
