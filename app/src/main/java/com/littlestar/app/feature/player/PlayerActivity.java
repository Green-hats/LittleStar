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
    private static final long PROGRESS_TICK_MS = 250L;

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

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            MediaController c = controller;
            if (c != null) {
                vm.onProgress(c.getCurrentPosition(),
                        Math.max(0, c.getDuration()), c.isPlaying());
            }
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
            if (c != null) {
                if (c.isPlaying()) {
                    c.pause();
                } else {
                    c.play();
                }
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
        vm.state().observe(this, this::render);

        List<String> queue = getIntent().getStringArrayListExtra(EXTRA_QUEUE);
        String itemId = getIntent().getStringExtra(EXTRA_ITEM_ID);
        container().content.load(container().executors,
                () -> vm.init(itemId, queue != null ? queue : new ArrayList<>()));
    }

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken token = new SessionToken(this,
                new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
            } catch (Exception e) {
                return;   // 连接失败：本页只读态可用，播放键无效（记 DECISIONS）
            }
            controller.addListener(new Player.Listener() {
                @Override
                public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                    if (mediaItem != null) {
                        vm.onTrackChanged(mediaItem.mediaId);
                    }
                }
            });
            startPlaybackIfNeeded();
            handler.post(progressTick);
        }, ContextCompat.getMainExecutor(this));
    }

    /** 队列为空或与本次意图不同才重置（从后台回来不打断正在播的内容）。 */
    private void startPlaybackIfNeeded() {
        MediaController c = controller;
        if (c == null) {
            return;
        }
        PlayerViewModel.UiState s = vm.state().getValue();
        if (s == null) {
            return;
        }
        MediaItem cur = c.getCurrentMediaItem();
        if (cur != null && s.item.id.equals(cur.mediaId) && c.getMediaItemCount() == s.queue.size()) {
            return;   // 已经在播同一首
        }
        List<MediaItem> mediaItems = new ArrayList<>();
        for (String id : s.queue) {
            CultureItem it = container().content.itemById(id);
            if (it != null) {
                mediaItems.add(new MediaItem.Builder()
                        .setMediaId(it.id)
                        .setUri("asset:///" + it.audio)
                        .build());
            }
        }
        c.setMediaItems(mediaItems, s.queueIndex, 0);
        c.prepare();
        c.play();
        playbackStarted = true;
    }

    private void render(PlayerViewModel.UiState s) {
        if (s == null) {
            return;
        }
        if (!playbackStarted) {
            // vm.init 晚于 controller 连接时补一次自动播放（竞态修复）
            startPlaybackIfNeeded();
        }
        binding.playerTitle.setText(s.item.titleZh);
        binding.songTitle.setText(s.item.titleZh);
        binding.coverGlyph.setText(s.item.glyph);
        binding.songSubtitle.setText(getString(R.string.player_subtitle_fmt,
                s.item.subtitle == null ? "" : s.item.subtitle,
                CultureListActivity.formatDuration(s.item.durationSec)));

        binding.playIcon.setImageResource(s.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        binding.btnPlay.setContentDescription(
                getString(s.playing ? R.string.player_desc_pause : R.string.player_desc_play));

        binding.progress.setProgress(s.positionMs, s.durationMs);
        binding.timeCurrent.setText(formatMs(s.positionMs));
        binding.timeTotal.setText(formatMs(s.durationMs));

        renderLyrics(s);
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
    protected void onStop() {
        handler.removeCallbacks(progressTick);
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
            controller = null;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
