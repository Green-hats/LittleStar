package com.littlestar.app.notify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.littlestar.app.R;

/**
 * 通知出口（§10.3）。学习提醒与晚安通知 + Media3 自己的媒体通知。
 * 文案进 strings.xml；小图标 ic_stat_panda。
 */
public final class Notifier {

    public static final String CHANNEL_REMINDER = "reminder";
    public static final String CHANNEL_BEDTIME = "bedtime";

    public static final int ID_REMINDER = 1001;
    public static final int ID_BEDTIME = 1003;

    private final Context app;
    private final NotificationManager nm;

    public Notifier(@NonNull Context context) {
        this.app = context.getApplicationContext();
        this.nm = app.getSystemService(NotificationManager.class);
    }

    /** App 启动时建渠道（幂等）。 */
    public void ensureChannels() {
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_REMINDER,
                app.getString(R.string.notif_channel_reminder), NotificationManager.IMPORTANCE_DEFAULT));
        // 升级时清理已删除的休息提醒渠道及遗留通知。
        nm.cancel(1002);
        nm.deleteNotificationChannel("rest");
        NotificationChannel bedtime = new NotificationChannel(CHANNEL_BEDTIME,
                app.getString(R.string.notif_channel_bedtime), NotificationManager.IMPORTANCE_LOW);
        bedtime.setSound(null, null);
        nm.createNotificationChannel(bedtime);
    }

    /** 每日学习提醒：「该学汉字啦：今天还有 N 个字」。 */
    public void reminder(int remainingWords, @NonNull Intent tapIntent) {
        String text = app.getString(R.string.notif_reminder_text, remainingWords);
        notify(ID_REMINDER, CHANNEL_REMINDER, app.getString(R.string.notif_reminder_title), text, tapIntent);
    }

    /** 睡前故事结束：「故事讲完啦，晚安」。 */
    public void bedtime(@NonNull Intent tapIntent) {
        notify(ID_BEDTIME, CHANNEL_BEDTIME, app.getString(R.string.notif_bedtime_title),
                app.getString(R.string.notif_bedtime_text), tapIntent);
    }

    private void notify(int id, @NonNull String channel, @NonNull String title, @NonNull String text,
                        @NonNull Intent tapIntent) {
        PendingIntent pi = PendingIntent.getActivity(app, id, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(app, channel)
                .setSmallIcon(R.drawable.ic_stat_panda)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(id, n);
    }
}
