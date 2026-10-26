package com.littlestar.app.domain;

import androidx.annotation.NonNull;

import java.time.LocalTime;

/**
 * 首页默认模式：06:00–16:59 字；17:00–05:59 文。
 * 手动切换只存在 ViewModel 内存里，不写 SharedPreferences。
 */
public final class HomeModeResolver {

    public enum Mode {WORDS, CULTURE}

    private HomeModeResolver() {
    }

    @NonNull
    public static Mode resolve(@NonNull LocalTime time) {
        int hour = time.getHour();
        return (hour >= 6 && hour < 17) ? Mode.WORDS : Mode.CULTURE;
    }
}
