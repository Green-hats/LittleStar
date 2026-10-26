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
 * 通知出口（§10.3）。每日学习提醒；媒体通知由 Media3 管理。
 * 文案进 strings.xml；小图标 ic_stat_panda。
 */
public final class Notifier {

    public static final String CHANNEL_REMINDER = "reminder";

    public static final int ID_REMINDER = 1001;

    /** 帮助页「发送测试通知」专用；与每日提醒分开，避免覆盖。 */
    public static final int ID_TEST = 1099;

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
    }

    /** 每日学习提醒：「该学汉字啦：今天还有 N 个字」。 */
    public void reminder(int remainingWords, @NonNull Intent tapIntent) {
        String text = app.getString(R.string.notif_reminder_text, remainingWords);
        notify(ID_REMINDER, CHANNEL_REMINDER, app.getString(R.string.notif_reminder_title), text, tapIntent);
    }

    /** 帮助页的测试通知：立即展示，用于确认通知权限和渠道正常。 */
    public void test(@NonNull Intent tapIntent) {
        notify(ID_TEST, CHANNEL_REMINDER,
                app.getString(R.string.notif_test_title),
                app.getString(R.string.notif_test_text), tapIntent);
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
