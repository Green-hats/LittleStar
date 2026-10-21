package com.littlestar.app.playback;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.session.MediaSessionService;

import com.littlestar.app.LittleStarApp;
import com.littlestar.app.feature.home.HomeActivity;
import com.littlestar.app.notify.Notifier;

import java.time.Instant;

/**
 * 长音频播放服务（§10.2）：后台播放 + 系统媒体通知（Media3 默认）+ 睡前定时。
 * 定时到点：finishTrack=true → 播完当前音轨再停；false → 3 秒淡出后停。
 * 停止后发「故事讲完啦，晚安」通知，stopSelf()，清 deadline。
 */
public final class PlaybackService extends MediaSessionService {

    @Nullable
    private MediaSession session;
    @Nullable
    private ExoPlayer player;
    private final Handler main = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable stopRunnable;
    @Nullable
    private Observer<Instant> deadlineObserver;
    @Nullable
    private SleepTimerController sleepTimer;

    @Override
    public void onCreate() {
        super.onCreate();
        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        session = new MediaSession.Builder(this, player).build();

        sleepTimer = ((LittleStarApp) getApplication()).container().sleepTimer;
        deadlineObserver = this::onDeadlineChanged;
        sleepTimer.deadline().observeForever(deadlineObserver);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                // 「播完这首再停」：当前音轨自然结束（setPauseAtEndOfMediaItems 生效）后进入停止流程
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    finishSleepStop();
                }
            }
        });
    }

    private void onDeadlineChanged(@Nullable Instant deadline) {
        if (stopRunnable != null) {
            main.removeCallbacks(stopRunnable);
            stopRunnable = null;
        }
        if (deadline == null || player == null) {
            return;
        }
        long delay = Math.max(0, deadline.toEpochMilli() - System.currentTimeMillis());
        stopRunnable = this::onSleepDeadlineReached;
        main.postDelayed(stopRunnable, delay);
    }

    private void onSleepDeadlineReached() {
        if (player == null || sleepTimer == null) {
            return;
        }
        if (!player.isPlaying()) {
            // 队列在到点前已播完：不会再有 END_OF_MEDIA_ITEM 事件，直接进停止流程
            finishSleepStop();
            return;
        }
        if (sleepTimer.isFinishTrack()) {
            // 播完当前这首再停
            player.setPauseAtEndOfMediaItems(true);
        } else {
            fadeOutAndStop();
        }
    }

    /** 100ms 降一档音量，3 秒淡出后 pause，再恢复音量。 */
    private void fadeOutAndStop() {
        final int steps = 30;
        final int[] i = {0};
        Runnable fade = new Runnable() {
            @Override
            public void run() {
                if (player == null) {
                    return;
                }
                i[0]++;
                player.setVolume(Math.max(0f, 1f - (float) i[0] / steps));
                if (i[0] < steps) {
                    main.postDelayed(this, 100);
                } else {
                    player.pause();
                    player.setVolume(1f);
                    finishSleepStop();
                }
            }
        };
        main.post(fade);
    }

    private void finishSleepStop() {
        if (player != null) {
            player.pause();
            player.setPauseAtEndOfMediaItems(false);
        }
        if (sleepTimer != null) {
            sleepTimer.cancel();
        }
        Notifier notifier = ((LittleStarApp) getApplication()).container().notifier;
        notifier.ensureChannels();
        notifier.bedtime(new Intent(this, HomeActivity.class));
        stopSelf();
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        // App 被划掉：没在播放就停服务
        if (player != null && !player.isPlaying()) {
            stopSelf();
        }
    }

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onDestroy() {
        if (stopRunnable != null) {
            main.removeCallbacks(stopRunnable);
        }
        if (sleepTimer != null && deadlineObserver != null) {
            sleepTimer.deadline().removeObserver(deadlineObserver);
        }
        if (session != null) {
            session.release();
            session = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
