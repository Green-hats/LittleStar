package com.littlestar.app.data.store;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

/**
 * progress.json 版本升级：在 Gson 反序列化之前对 JsonObject 逐级迁移（1→2→3…）。
 * 每次改 ProgressState 结构都要加一步迁移和对应单测。
 */
public final class StateMigrator {

    private StateMigrator() {
    }

    /** 把 raw 从 fromVersion 逐级迁到 {@link ProgressStore#CURRENT_VERSION}。 */
    @NonNull
    public static JsonObject migrate(@NonNull JsonObject raw, int fromVersion) {
        int version = fromVersion;
        if (version == 1) {
            // v2 删除星星奖励字段；保留字进度、每日计划与会话答题记录。
            JsonElement sessions = raw.get("sessions");
            if (sessions != null && sessions.isJsonArray()) {
                for (JsonElement session : sessions.getAsJsonArray()) {
                    if (session.isJsonObject()) {
                        session.getAsJsonObject().remove("stars");
                    }
                }
            }
            raw.addProperty("schemaVersion", 2);
            version = 2;
        }
        if (version == 2) {
            // v3 移除收藏；识字进度、每日计划及作答记录保持原样。
            raw.remove("favourites");
            raw.addProperty("schemaVersion", 3);
        }
        return raw;
    }
}
