package com.littlestar.app.data.prefs;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * SharedPreferences 封装：学习任务与应用配置。每个键一个 getter / setter，写入用 apply()。
 */
public final class SettingsStore {

    /** 键名集中在这里。 */
    public static final class Keys {
        public static final String DAILY_GOAL = "daily_goal";
        public static final String REMINDER_TIME = "reminder_time";     // HH:mm
        public static final String REMINDER_DAYS = "reminder_days";     // ISO 星期，逗号分隔
        public static final String SLEEP_DEFAULT_MIN = "sleep_default_min";
        public static final String SLEEP_FINISH_TRACK = "sleep_finish_track";
        public static final String ONBOARDED = "onboarded";
        public static final String NOTIF_RATIONALE_SHOWN = "notif_rationale_shown";
        public static final String SOUND_ENABLED = "sound_enabled";
        public static final String ENGLISH_HINT = "english_hint";
        public static final String PARENT_PIN = "parent_pin";

        private Keys() {
        }
    }

    private final SharedPreferences prefs;

    public SettingsStore(@NonNull SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public int getDailyGoal() {
        return prefs.getInt(Keys.DAILY_GOAL, 3);
    }

    public void setDailyGoal(int goal) {
        prefs.edit().putInt(Keys.DAILY_GOAL, goal).apply();
    }

    @NonNull
    public String getReminderTime() {
        String v = prefs.getString(Keys.REMINDER_TIME, "19:30");
        return v == null ? "19:30" : v;
    }

    public void setReminderTime(@NonNull String hhmm) {
        prefs.edit().putString(Keys.REMINDER_TIME, hhmm).apply();
    }

    /** ISO 星期集合（1=周一 … 7=周日）。默认周一到周五。 */
    @NonNull
    public Set<Integer> getReminderDays() {
        String raw = prefs.getString(Keys.REMINDER_DAYS, "1,2,3,4,5");
        Set<Integer> days = new HashSet<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                try {
                    days.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignored) {
                    // 忽略坏数据
                }
            }
        }
        return Collections.unmodifiableSet(days);
    }

    public void setReminderDays(@NonNull Set<Integer> days) {
        StringBuilder sb = new StringBuilder();
        for (int d = 1; d <= 7; d++) {
            if (days.contains(d)) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(d);
            }
        }
        prefs.edit().putString(Keys.REMINDER_DAYS, sb.toString()).apply();
    }

    public int getSleepDefaultMin() {
        return prefs.getInt(Keys.SLEEP_DEFAULT_MIN, 20);
    }

    public void setSleepDefaultMin(int minutes) {
        prefs.edit().putInt(Keys.SLEEP_DEFAULT_MIN, minutes).apply();
    }

    public boolean isSleepFinishTrack() {
        return prefs.getBoolean(Keys.SLEEP_FINISH_TRACK, true);
    }

    public void setSleepFinishTrack(boolean finishTrack) {
        prefs.edit().putBoolean(Keys.SLEEP_FINISH_TRACK, finishTrack).apply();
    }

    public boolean isOnboarded() {
        return prefs.getBoolean(Keys.ONBOARDED, false);
    }

    public void setOnboarded(boolean onboarded) {
        prefs.edit().putBoolean(Keys.ONBOARDED, onboarded).apply();
    }

    public boolean isNotifRationaleShown() {
        return prefs.getBoolean(Keys.NOTIF_RATIONALE_SHOWN, false);
    }

    public void setNotifRationaleShown(boolean shown) {
        prefs.edit().putBoolean(Keys.NOTIF_RATIONALE_SHOWN, shown).apply();
    }

    public boolean isSoundEnabled() {
        return prefs.getBoolean(Keys.SOUND_ENABLED, true);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(Keys.SOUND_ENABLED, enabled).apply();
    }

    /** debug 开关：测验题目卡显示英文提示（默认关）。 */
    public boolean isEnglishHint() {
        return prefs.getBoolean(Keys.ENGLISH_HINT, false);
    }

    public void setEnglishHint(boolean show) {
        prefs.edit().putBoolean(Keys.ENGLISH_HINT, show).apply();
    }

    /** 家长密码（D31：PIN 登录取代算术验证，引导时设置）。 */
    public boolean isPinSet() {
        String pin = prefs.getString(Keys.PARENT_PIN, "");
        return pin != null && pin.length() == 4;
    }

    @NonNull
    public String getParentPin() {
        String pin = prefs.getString(Keys.PARENT_PIN, "");
        return pin == null ? "" : pin;
    }

    public void setParentPin(@NonNull String pin) {
        prefs.edit().putString(Keys.PARENT_PIN, pin).apply();
    }

    /**
     * 观察某个键的变化。监听器由返回的 LiveData 强引用持有（active 期间注册），不会被 GC。
     */
    @NonNull
    public LiveData<Integer> observeInt(@NonNull String key, int defaultValue) {
        return new PrefsLiveData<>(key, () -> prefs.getInt(key, defaultValue));
    }

    private final class PrefsLiveData<T> extends MutableLiveData<T> {
        private final String key;
        private final java.util.function.Supplier<T> reader;
        private final SharedPreferences.OnSharedPreferenceChangeListener listener;

        PrefsLiveData(String key, java.util.function.Supplier<T> reader) {
            this.key = key;
            this.reader = reader;
            this.listener = (p, changedKey) -> {
                if (key.equals(changedKey)) {
                    setValue(reader.get());
                }
            };
        }

        @Override
        protected void onActive() {
            setValue(reader.get());
            prefs.registerOnSharedPreferenceChangeListener(listener);
        }

        @Override
        protected void onInactive() {
            prefs.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }
}
