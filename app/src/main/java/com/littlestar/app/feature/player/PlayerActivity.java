package com.littlestar.app.feature.player;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;
import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.data.content.model.CultureItem;
import com.littlestar.app.data.content.model.LyricLine;
import com.littlestar.app.databinding.ActivityPlayerBinding;
import com.littlestar.app.feature.culture.CultureListActivity;
import com.littlestar.app.playback.PlaybackService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 播放器（§7.9 / §9.9）：MediaController 连 PlaybackService，不在本页持有 ExoPlayer。
 * 进度 250ms 刷新；歌词当前句黄底、后两句 muted。
 */
public final class PlayerActivity extends ChildActivity {

    private static final String EXTRA_ITEM_ID = "itemId";
    private static final String EXTRA_QUEUE = "queue";
    private static final String STATE_PLAYBACK_STARTED = "playback_started";
    private static final long PROGRESS_TICK_MS = 250L;
    private static final float DISABLED_ALPHA = 0.38f;

    @NonNull
    public static Intent intent(@NonNull Activity from, @NonNull String itemId,
                                @NonNull List<String> queue) {
        Intent i = new Intent(from, PlayerActivity.class);
        i.putExtra(EXTRA_ITEM_ID, itemId);
        i.putStringArrayListExtra(EXTRA_QUEUE, new ArrayList<>(queue));
        return i;
    }

    private ActivityPlayerBinding binding;
    private PlayerViewModel vm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    private MediaController controller;
    @Nullable
    private ListenableFuture<MediaController> controllerFuture;
    /** 本次进入是否已把播放队列交给控制器（防止 vm/controller 谁先就绪的竞态导致不自动播）。 */
    private boolean playbackStarted;
    private boolean controllerInitialized;

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            syncControllerState();
            handler.postDelayed(this, PROGRESS_TICK_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new PlayerViewModel(container())))
                .get(PlayerViewModel.class);

        binding.btnBack.setOnClickListener(v -> {
            finish();
            Motion.childExit(this);
        });
        binding.btnPlay.setOnClickListener(v -> {
            MediaController c = controller;
            if (c != null && c.getMediaItemCount() > 0) {
                if (c.getPlayWhenReady() && c.getPlaybackState() != Player.STATE_ENDED
                        && c.getPlayerError() == null) {
                    c.pause();
                } else {
                    if (c.getPlaybackState() == Player.STATE_ENDED) {
                        c.seekTo(0);
                    }
                    if (c.getPlaybackState() == Player.STATE_IDLE || c.getPlayerError() != null) {
                        c.prepare();
                    }
                    c.play();
                }
                syncControllerState();
            }
        });
        binding.btnPrev.setOnClickListener(v -> {
            MediaController c = controller;
            if (c != null) {
                c.seekToPreviousMediaItem();
            }
        });
        binding.btnNext.setOnClickListener(v -> {
            MediaController c = controller;
            if (c != null) {
                c.seekToNextMediaItem();
            }
        });
        binding.progress.setOnSeek(pos -> {
            MediaController c = controller;
            if (c != null) {
                c.seekTo(pos);
            }
        });
        playbackStarted = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_PLAYBACK_STARTED);
        vm.state().observe(this, this::render);
        updateControls();

        List<String> queue = getIntent().getStringArrayListExtra(EXTRA_QUEUE);
        String itemId = getIntent().getStringExtra(EXTRA_ITEM_ID);
        container().content.load(container().executors, () -> {
            if (!isDestroyed()) {
                if (itemId == null || container().content.itemById(itemId) == null) {
                    finish();
                } else {
                    vm.init(itemId, queue != null ? queue : new ArrayList<>());
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        controllerInitialized = false;
        SessionToken token = new SessionToken(this,
                new ComponentName(this, PlaybackService.class));
        ListenableFuture<MediaController> future =
                new MediaController.Builder(this, token).buildAsync();
        controllerFuture = future;
        future.addListener(() -> {
            if (controllerFuture != future || isFinishing() || isDestroyed()) {
                return;
            }
            try {
                controller = future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                updateControls();
                return;
            } catch (java.util.concurrent.ExecutionException
                    | java.util.concurrent.CancellationException e) {
                updateControls();
                return;   // 连接失败：本页只读态可用，播放键无效（记 DECISIONS）
            }
            controller.addListener(new Player.Listener() {
                @Override
                public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
                    if (controllerInitialized) {
                        syncControllerState();
                    }
                }
            });
            startPlaybackIfNeeded();
            updateControls();
            handler.removeCallbacks(progressTick);
            handler.post(progressTick);
        }, ContextCompat.getMainExecutor(this));
    }

    /** 队列为空或与本次意图不同才重置（从后台回来不打断正在播的内容）。 */
    private void startPlaybackIfNeeded() {
        MediaController c = controller;
        PlayerViewModel.UiState s = vm.state().getValue();
        if (controllerInitialized || c == null || !c.isConnected() || s == null) {
            return;
        }
        controllerInitialized = true;
        MediaItem cur = c.getCurrentMediaItem();
        List<String> activeQueue = new ArrayList<>();
        for (int i = 0; i < c.getMediaItemCount(); i++) {
            activeQueue.add(c.getMediaItemAt(i).mediaId);
        }
        if (vm.canKeepPlayback(activeQueue, cur == null ? null : cur.mediaId, playbackStarted)) {
            playbackStarted = true;
            syncControllerState();
            return;   // 保留同一队列的当前曲目、进度和暂停状态
        }
        boolean resume = playbackStarted;
        playbackStarted = true;
        List<MediaItem> mediaItems = new ArrayList<>();
        for (String id : s.queue) {
            CultureItem it = container().content.itemById(id);
            if (it != null) {
                mediaItems.add(new MediaItem.Builder()
                        .setMediaId(it.id)
                        .setUri("asset:///" + it.audio)
                        .setMediaMetadata(new MediaMetadata.Builder()
                                .setTitle(it.titleEn)
                                .setDisplayTitle(it.titleEn)
                                .setSubtitle(it.titleZh)
                                .setArtist(it.subtitleEn)
                                .build())
                        .build());
            }
        }
        c.setMediaItems(mediaItems, s.queueIndex, resume ? s.positionMs : 0);
        c.prepare();
        c.setPlayWhenReady(!resume || s.playing);
        syncControllerState();
    }

    private void syncControllerState() {
        MediaController c = controller;
        if (c != null && c.isConnected() && controllerInitialized
                && vm.state().getValue() != null) {
            MediaItem current = c.getCurrentMediaItem();
            if (current != null) {
                vm.onTrackChanged(current.mediaId);
            }
            vm.onProgress(Math.max(0, c.getCurrentPosition()), Math.max(0, c.getDuration()),
                    c.getPlayWhenReady() && c.getPlaybackState() != Player.STATE_ENDED
                            && c.getPlayerError() == null);
        }
        updateControls();
    }

    private void updateControls() {
        MediaController c = controller;
        boolean ready = c != null && c.isConnected() && controllerInitialized
                && c.getMediaItemCount() > 0;
        setControlEnabled(binding.btnPlay, ready);
        updateTrackControls();
        setControlEnabled(binding.progress,
                ready && c.isCurrentMediaItemSeekable() && c.getDuration() > 0);
    }

    private static void setControlEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : DISABLED_ALPHA);
    }

    /** Show whether a neighbouring item actually exists in the current category queue. */
    private void updateTrackControls() {
        MediaController c = controller;
        boolean ready = c != null && c.isConnected() && controllerInitialized
                && c.getMediaItemCount() > 0;
        boolean previous = ready
                && c.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                && c.hasPreviousMediaItem();
        boolean next = ready
                && c.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                && c.hasNextMediaItem();
        float unavailableAlpha = getResources().getFraction(
                R.fraction.player_unavailable_alpha, 1, 1);
        binding.btnPrev.setEnabled(previous);
        binding.btnNext.setEnabled(next);
        binding.btnPrev.setAlpha(previous ? 1f : unavailableAlpha);
        binding.btnNext.setAlpha(next ? 1f : unavailableAlpha);
        binding.btnPrev.setContentDescription(getString(previous
                ? R.string.player_desc_prev : R.string.player_desc_prev_unavailable));
        binding.btnNext.setContentDescription(getString(next
                ? R.string.player_desc_next : R.string.player_desc_next_unavailable));
    }

    private void render(PlayerViewModel.UiState s) {
        if (s == null) {
            return;
        }
        if (!controllerInitialized) {
            // vm.init 晚于 controller 连接时补一次自动播放（竞态修复）
            startPlaybackIfNeeded();
            s = vm.state().getValue();
        }
        binding.playerTitle.setText(s.item.titleEn);
        binding.songTitle.setText(s.item.titleZh);
        binding.coverGlyph.setText(s.item.glyph);
        binding.songSubtitle.setText(getString(R.string.player_subtitle_fmt,
                s.item.subtitleEn == null ? "" : s.item.subtitleEn,
                CultureListActivity.formatDuration(s.item.durationSec)));

        binding.playerSummary.setText(s.item.summary);
        binding.playLabel.setText(s.playing ? R.string.player_desc_pause : R.string.player_desc_play);
        binding.playIcon.setImageResource(s.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        binding.btnPlay.setContentDescription(
                getString(s.playing ? R.string.player_desc_pause : R.string.player_desc_play));

        binding.progress.setProgress(s.positionMs, s.durationMs);
        binding.timeCurrent.setText(formatMs(s.positionMs));
        binding.timeTotal.setText(formatMs(s.durationMs));

        renderLyrics(s);
        updateControls();
    }

    /** 歌词：当前句黄底高亮，后两句 muted；无歌词隐藏卡（§9.9）。 */
    private void renderLyrics(PlayerViewModel.UiState s) {
        if (s.item.lyrics == null || s.item.lyrics.isEmpty() || s.lyricIndex < 0) {
            binding.lyricsCard.setVisibility(View.GONE);
            return;
        }
        binding.lyricsCard.setVisibility(View.VISIBLE);
        List<LyricLine> lyrics = s.item.lyrics;
        int i = s.lyricIndex;
        bindLyric(lyrics, i, binding.lyricCurrent, binding.lyricCurrentPinyin,
                binding.lyricCurrentEnglish);
        bindLyric(lyrics, i + 1, binding.lyricNext1, binding.lyricNext1Pinyin, null);
        bindLyric(lyrics, i + 2, binding.lyricNext2, binding.lyricNext2Pinyin, null);
    }

    private void bindLyric(List<LyricLine> lyrics, int index,
                           android.widget.TextView hanzi, android.widget.TextView pinyin,
                           @Nullable android.widget.TextView english) {
        if (index < lyrics.size()) {
            LyricLine line = lyrics.get(index);
            hanzi.setText(line.hanzi);
            pinyin.setText(line.pinyin);
            pinyin.setVisibility(View.VISIBLE);
            if (english != null) {
                english.setText(line.english);
                english.setVisibility(View.VISIBLE);
            }
        } else {
            hanzi.setText("");
            pinyin.setVisibility(View.GONE);
            if (english != null) {
                english.setVisibility(View.GONE);
            }
        }
    }

    @NonNull
    private static String formatMs(long ms) {
        if (ms <= 0) {
            return "0:00";
        }
        long totalSec = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", totalSec / 60, totalSec % 60);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(STATE_PLAYBACK_STARTED, playbackStarted);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(progressTick);
        ListenableFuture<MediaController> future = controllerFuture;
        controllerFuture = null;
        controller = null;
        controllerInitialized = false;
        updateControls();
        if (future != null) {
            MediaController.releaseFuture(future);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
