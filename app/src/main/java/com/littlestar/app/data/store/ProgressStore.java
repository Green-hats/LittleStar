package com.littlestar.app.data.store;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.littlestar.app.AppExecutors;
import com.littlestar.app.core.time.AppClock;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 学习数据的唯一入口：内存中的 {@link ProgressState} + progress.json。
 * state 只在 diskIO 线程上读改；修改后发布深拷贝快照，2 秒防抖保存。
 */
public final class ProgressStore {

    public static final int CURRENT_VERSION = 1;
    private static final String TAG = "ProgressStore";
    private static final long SAVE_DEBOUNCE_MS = 2_000;
    /** sessions 只保留最近 180 天；dailyPlans 只保留最近 30 天 */
    private static final int KEEP_SESSION_DAYS = 180;
    private static final int KEEP_PLAN_DAYS = 30;

    private final StateFile file;
    private final Gson gson;
    private final AppClock clock;
    private final AppExecutors executors;
    private final long saveDebounceMs;

    private ProgressState state;                                 // 只在 diskIO 线程访问
    private boolean loaded;
    private String lastBackupDate;                               // 每天只备份一次
    private final MutableLiveData<ProgressState> snapshot = new MutableLiveData<>();
    private final ScheduledExecutorService saver = Executors.newSingleThreadScheduledExecutor();
    @Nullable
    private ScheduledFuture<?> pendingSave;

    public ProgressStore(@NonNull StateFile file, @NonNull Gson gson,
                         @NonNull AppClock clock, @NonNull AppExecutors executors) {
        this(file, gson, clock, executors, SAVE_DEBOUNCE_MS);
    }

    /** 测试用：自定义防抖时长。 */
    public ProgressStore(@NonNull StateFile file, @NonNull Gson gson,
                         @NonNull AppClock clock, @NonNull AppExecutors executors,
                         long saveDebounceMs) {
        this.file = file;
        this.gson = gson;
        this.clock = clock;
        this.executors = executors;
        this.saveDebounceMs = saveDebounceMs;
    }

    /** App 启动时调用。读文件 → 迁移 → 清理过期数据 → 发布快照。 */
    public void loadAsync() {
        executors.diskIO().execute(this::loadBlocking);
    }

    /** 观察快照（UI 用）。快照是深拷贝，UI 改它不会影响真实状态。 */
    @NonNull
    public LiveData<ProgressState> observe() {
        return snapshot;
    }

    /** 在 diskIO 线程上读一次，结果回调到主线程（Receiver、Tracker 用）。 */
    public <T> void read(@NonNull Function<ProgressState, T> query, @NonNull Consumer<T> onMain) {
        executors.diskIO().execute(() -> {
            ensureLoaded();
            T result = query.apply(state);
            executors.mainThread().execute(() -> onMain.accept(result));
        });
    }

    /** 同步读一次。只能在 diskIO 线程（如 BroadcastReceiver 的 goAsync 任务）调用。 */
    public <T> T readBlocking(@NonNull Function<ProgressState, T> query) {
        ensureLoaded();
        return query.apply(state);
    }

    /** 所有修改的唯一入口：diskIO 线程执行 → 发布新快照 → 2 秒防抖后保存。 */
    public void update(@NonNull Consumer<ProgressState> mutation) {
        executors.diskIO().execute(() -> {
            ensureLoaded();
            mutation.accept(state);
            snapshot.postValue(state.deepCopy(gson));
            scheduleSave();
        });
    }

    /** 立即保存（App 退到后台、会话完成、Reward 写星星、家长延长时间时调用）。 */
    public void flushNow() {
        executors.diskIO().execute(() -> {
            ensureLoaded();
            writeBlocking();
        });
    }

    /** 测试用：同步确保已加载。只能在 diskIO 线程或测试线程调用。 */
    public void ensureLoaded() {
        if (!loaded) {
            loadBlocking();
        }
    }

    // ---------- 内部：全部只在 diskIO 线程执行 ----------

    private void loadBlocking() {
        ProgressState loadedState = null;
        String raw = file.read();
        if (raw != null) {
            loadedState = parse(raw, false);
        }
        if (loadedState == null && raw != null) {
            // 主文件损坏：改名保留现场，尝试备份
            file.rename(".corrupt-" + clock.now().toEpochMilli() + ".json");
            String backup = file.readBackup();
            if (backup != null) {
                loadedState = parse(backup, true);
            }
            if (loadedState == null) {
                Log.e(TAG, "progress.json and backup both corrupt, starting fresh");
            }
        }
        state = loadedState != null ? loadedState : new ProgressState();
        loaded = true;
        cleanupExpired(state);
        snapshot.postValue(state.deepCopy(gson));
        writeBlocking();
        backupOncePerDay();
    }

    @Nullable
    private ProgressState parse(@NonNull String raw, boolean fromBackup) {
        try {
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            int version = obj.has("schemaVersion") ? obj.get("schemaVersion").getAsInt() : 1;
            JsonObject migrated = StateMigrator.migrate(obj, version);
            return gson.fromJson(migrated, ProgressState.class);
        } catch (RuntimeException e) {
            Log.e(TAG, "parse failed (backup=" + fromBackup + ")", e);
            return null;
        }
    }

    /** 清理过期数据：sessions 留 180 天，dailyPlans 留 30 天，usage 全留。 */
    private void cleanupExpired(@NonNull ProgressState s) {
        LocalDate today = clock.today();
        String sessionCutoff = today.minusDays(KEEP_SESSION_DAYS).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String planCutoff = today.minusDays(KEEP_PLAN_DAYS).format(DateTimeFormatter.ISO_LOCAL_DATE);
        s.sessions.removeIf(session -> session.date != null && session.date.compareTo(sessionCutoff) < 0);
        s.dailyPlans.keySet().removeIf(date -> date.compareTo(planCutoff) < 0);
    }

    private void scheduleSave() {
        if (pendingSave != null) {
            pendingSave.cancel(false);
        }
        pendingSave = saver.schedule(
                () -> executors.diskIO().execute(this::writeBlocking),
                saveDebounceMs, TimeUnit.MILLISECONDS);
    }

    private void writeBlocking() {
        if (state == null) {
            return;
        }
        file.write(gson.toJson(state));
    }

    /** 每天第一次成功加载后，把当前文件复制为 progress.bak.json（只保留 1 份）。 */
    private void backupOncePerDay() {
        String today = clock.today().format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (!today.equals(lastBackupDate)) {
            lastBackupDate = today;
            file.writeBackup(gson.toJson(state));
        }
    }
}
