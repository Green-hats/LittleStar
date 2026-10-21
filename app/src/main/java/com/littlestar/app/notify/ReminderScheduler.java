package com.littlestar.app.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.littlestar.app.core.time.AppClock;
import com.littlestar.app.data.prefs.SettingsStore;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * 每日提醒调度（§10.4）：AlarmManager.setWindow（非精确，10 分钟窗口）。
 * 不申请 SCHEDULE_EXACT_ALARM。
 */
public final class ReminderScheduler {

    private static final long WINDOW_MS = 10 * 60_000;

    private final Context app;
    private final SettingsStore settings;
    private final AppClock clock;
    private final AlarmManager alarmManager;

    public ReminderScheduler(@NonNull Context context, @NonNull SettingsStore settings,
                             @NonNull AppClock clock) {
        this.app = context.getApplicationContext();
        this.settings = settings;
        this.clock = clock;
        this.alarmManager = app.getSystemService(AlarmManager.class);
    }

    /** 重排：BOOT_COMPLETED / TIMEZONE_CHANGED / TIME_SET / Plan 保存 / App 启动。 */
    public void reschedule() {
        PendingIntent pi = pendingIntent();
        alarmManager.cancel(pi);
        long triggerAt = nextTriggerMillis();
        if (triggerAt > 0) {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_MS, pi);
        }
    }

    /** 从今天开始找下一个在 reminder_days 里、且时间还没过的日子。 */
    public long nextTriggerMillis() {
        Set<Integer> days = settings.getReminderDays();
        if (days.isEmpty()) {
            return -1;
        }
        LocalTime time;
        try {
            time = LocalTime.parse(settings.getReminderTime());
        } catch (RuntimeException e) {
            time = LocalTime.of(19, 30);
        }
        LocalDate today = clock.today();
        LocalDateTime now = LocalDateTime.of(today, clock.localTime());
        for (int d = 0; d < 8; d++) {
            LocalDate date = today.plusDays(d);
            int isoDow = date.getDayOfWeek().getValue();
            if (!days.contains(isoDow)) {
                continue;
            }
            LocalDateTime candidate = LocalDateTime.of(date, time);
            if (candidate.isAfter(now)) {
                return ZonedDateTime.of(candidate, clock.zone()).toInstant().toEpochMilli();
            }
        }
        return -1;
    }

    @NonNull
    private PendingIntent pendingIntent() {
        Intent intent = new Intent(app, ReminderReceiver.class);
        return PendingIntent.getBroadcast(app, ID_REMINDER, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static final int ID_REMINDER = Notifier.ID_REMINDER;
}
