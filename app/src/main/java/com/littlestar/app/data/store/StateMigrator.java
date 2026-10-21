package com.littlestar.app.data.store;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

/**
 * progress.json 版本升级：在 Gson 反序列化之前对 JsonObject 逐级迁移（1→2→3…）。
 * v1 时为空实现，但结构要在。每次改 ProgressState 结构都要加一步迁移和对应单测。
 */
public final class StateMigrator {

    private StateMigrator() {
    }

    /** 把 raw 从 fromVersion 逐级迁到 {@link ProgressStore#CURRENT_VERSION}。 */
    @NonNull
    public static JsonObject migrate(@NonNull JsonObject raw, int fromVersion) {
        int version = fromVersion;
        // 未来版本：
        // if (version == 1) { raw = migrate1To2(raw); version = 2; }
        return raw;
    }
}
