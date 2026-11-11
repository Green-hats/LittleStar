package com.littlestar.app.feature.home;

import android.content.ComponentName;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;
import com.littlestar.app.R;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.databinding.ActivityHomeBinding;
import com.littlestar.app.feature.player.PlayerActivity;
import com.littlestar.app.playback.PlaybackControls;
import com.littlestar.app.playback.PlaybackService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/** 只连接已存在的播放服务，首页不自行创建空媒体会话。 */
final class HomePlaybackControls implements DefaultLifecycleObserver {
    private final HomeActivity home;
    private final ActivityHomeBinding binding;
    private ListenableFuture<MediaController> future;
    private MediaController controller;

    HomePlaybackControls(HomeActivity home, ActivityHomeBinding binding) {
        this.home = home;
        this.binding = binding;
        binding.nowPlayingOpen.setOnClickListener(v -> {
            MediaController c = controller;
            MediaItem item = c == null ? null : c.getCurrentMediaItem();
            if (item == null) {
                return;
            }
            List<String> queue = new ArrayList<>();
            for (int i = 0; i < c.getMediaItemCount(); i++) {
                queue.add(c.getMediaItemAt(i).mediaId);
            }
            home.startActivity(PlayerActivity.resumeIntent(home, item.mediaId, queue));
            Motion.childEnter(home);
        });
        binding.nowPlayingToggle.setOnClickListener(v -> {
            if (controller != null && controller.getMediaItemCount() > 0) {
                PlaybackControls.toggle(controller);
                render();
            }
        });
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        if (!PlaybackService.isRunning()) {
            binding.nowPlaying.setVisibility(View.GONE);
            return;
        }
        ListenableFuture<MediaController> request = new MediaController.Builder(home,
                new SessionToken(home, new ComponentName(home, PlaybackService.class))).buildAsync();
        future = request;
        request.addListener(() -> {
            if (request != future) {
                return;
            }
            try {
                controller = request.get();
                controller.addListener(new Player.Listener() {
                    @Override
                    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
                        render();
                    }
                });
                render();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | CancellationException e) {
                binding.nowPlaying.setVisibility(View.GONE);
            }
        }, ContextCompat.getMainExecutor(home));
    }

    private void render() {
        MediaController c = controller;
        MediaItem item = c == null || !c.isConnected() ? null : c.getCurrentMediaItem();
        binding.nowPlaying.setVisibility(item == null ? View.GONE : View.VISIBLE);
        if (item == null) {
            return;
        }
        boolean playing = c.getPlayWhenReady() && c.getPlaybackState() != Player.STATE_ENDED
                && c.getPlayerError() == null;
        CharSequence title = item.mediaMetadata.title;
        binding.nowPlayingTitle.setText(title == null ? home.getString(R.string.player_heading) : title);
        binding.nowPlayingState.setText(playing ? R.string.home_audio_playing : R.string.home_audio_paused);
        binding.nowPlayingOpen.setContentDescription(home.getString(R.string.home_audio_open, title));
        binding.nowPlayingIcon.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        binding.nowPlayingToggle.setContentDescription(home.getString(
                playing ? R.string.player_desc_pause : R.string.player_desc_play));
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        controller = null;
        if (future != null) {
            MediaController.releaseFuture(future);
            future = null;
        }
    }
}
