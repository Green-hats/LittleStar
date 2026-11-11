package com.littlestar.app.playback;

import androidx.media3.common.Player;

/** 同一离线媒体会话的前台与首页控制。 */
public final class PlaybackControls {
    private PlaybackControls() {
    }

    public static void play(Player player) {
        if (player.getPlaybackState() == Player.STATE_ENDED) {
            player.seekTo(0);
        }
        if (player.getPlaybackState() == Player.STATE_IDLE || player.getPlayerError() != null) {
            player.prepare();
        }
        player.play();
    }

    public static void toggle(Player player) {
        if (player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_ENDED
                && player.getPlayerError() == null) {
            player.pause();
        } else {
            play(player);
        }
    }
}
