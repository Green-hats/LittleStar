package com.littlestar.app.core.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import androidx.annotation.NonNull;

import com.littlestar.app.R;

import java.util.EnumMap;
import java.util.Map;

/**
 * 短音效播放器（§8.9）：SoundPool maxStreams=4，启动时预加载 4 个 < 1s 音效。
 * 音量跟随媒体音量；{@code soundEnabled} 为 false 时不播放（家长区 / 设置关声音）。
 */
public final class SfxPlayer {

    /** 音效清单。 */
    public enum Sfx {
        TAP,
        FLIP,
        CORRECT,
        WRONG
    }

    /** 声音开关（SettingsStore.isSoundEnabled 的方法引用）。 */
    public interface SoundEnabled {
        boolean get();
    }

    private final SoundPool pool;
    private final SoundEnabled soundEnabled;
    private final Map<Sfx, Integer> soundIds = new EnumMap<>(Sfx.class);

    public SfxPlayer(@NonNull Context context, @NonNull SoundEnabled enabled) {
        soundEnabled = enabled;
        pool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        soundIds.put(Sfx.TAP, pool.load(context, R.raw.sfx_tap, 1));
        soundIds.put(Sfx.FLIP, pool.load(context, R.raw.sfx_flip, 1));
        soundIds.put(Sfx.CORRECT, pool.load(context, R.raw.sfx_correct, 1));
        soundIds.put(Sfx.WRONG, pool.load(context, R.raw.sfx_wrong, 1));
    }

    /** 声音关闭时不播放；加载未完成时 SoundPool 静默忽略。 */
    public void play(@NonNull Sfx sfx) {
        if (!soundEnabled.get()) {
            return;
        }
        Integer id = soundIds.get(sfx);
        if (id != null) {
            pool.play(id, 1f, 1f, 1, 0, 1f);
        }
    }

    public void release() {
        pool.release();
    }
}
