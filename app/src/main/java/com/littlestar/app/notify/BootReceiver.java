package com.littlestar.app.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.littlestar.app.LittleStarApp;

/** 重启 / 时区变化 / 时间变化后重排每日提醒。 */
public final class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_TIMEZONE_CHANGED:
            case Intent.ACTION_TIME_CHANGED:
                ((LittleStarApp) context.getApplicationContext()).container().reminders.reschedule();
                break;
            default:
                break;
        }
    }
}
