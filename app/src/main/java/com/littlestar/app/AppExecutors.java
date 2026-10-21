package com.littlestar.app;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 磁盘单线程 + 主线程。ProgressState 只在 diskIO 线程上读改，天然无并发问题。
 */
public final class AppExecutors {

    private final Executor diskIO;
    private final Executor mainThread;

    public AppExecutors() {
        this(Executors.newSingleThreadExecutor(r -> new Thread(r, "ls-disk")),
                new Handler(Looper.getMainLooper())::post);
    }

    /** 测试用：传 {@code Runnable::run} 得到同步执行器。 */
    public AppExecutors(@NonNull Executor diskIO, @NonNull Executor mainThread) {
        this.diskIO = diskIO;
        this.mainThread = mainThread;
    }

    @NonNull
    public Executor diskIO() {
        return diskIO;
    }

    @NonNull
    public Executor mainThread() {
        return mainThread;
    }
}
