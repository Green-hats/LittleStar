package com.littlestar.app.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.littlestar.app.AppContainer;
import com.littlestar.app.LittleStarApp;
import com.littlestar.app.feature.home.HomeActivity;

import java.time.LocalDate;

/**
 * 每日提醒触发：读今日计划算剩余字数（为 0 时当天不发），发通知，排下一次。
 * goAsync + diskIO 线程，10 秒内 finish。
 */
public final class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        PendingResult pr = goAsync();
        AppContainer container = ((LittleStarApp) context.getApplicationContext()).container();
        container.executors.diskIO().execute(() -> {
            try {
                container.content.ensureLoaded();
                int goal = container.settings.getDailyGoal();
                LocalDate today = container.clock.today();
                int remaining = container.store.<Integer>readBlocking(state -> {
                    com.littlestar.app.data.store.ProgressState.DailyPlan plan =
                            state.dailyPlans.get(today.toString());
                    int total = plan == null ? goal : plan.newWordIds.size();
                    int learned = 0;
                    if (plan != null) {
                        for (String id : plan.newWordIds) {
                            com.littlestar.app.data.store.ProgressState.WordProgress wp =
                                    state.words.get(id);
                            if (wp != null && wp.learnedAt != null) {
                                learned++;
                            }
                        }
                    }
                    return Math.max(0, total - learned);
                });
                if (remaining > 0) {
                    container.notifier.ensureChannels();
                    Intent tap = new Intent(context, HomeActivity.class);
                    container.notifier.reminder(remaining, tap);
                }
            } finally {
                // 无论是否发通知都排下一次
                container.reminders.reschedule();
                pr.finish();
            }
        });
    }
}
