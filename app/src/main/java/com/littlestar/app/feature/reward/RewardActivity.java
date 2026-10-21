package com.littlestar.app.feature.reward;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.littlestar.app.R;
import com.littlestar.app.core.audio.SfxPlayer;
import com.littlestar.app.core.lifecycle.Event;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.BlockLayout;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.HanziTextView;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.databinding.ActivityRewardBinding;
import com.littlestar.app.feature.flashcard.FlashcardActivity;

import java.io.IOException;
import java.util.Random;

/**
 * 奖励页（§7.6）：黄色舞台 + 熊猫 + 星星逐颗弹（250ms 间隔）+ 本轮字块。
 * confetti 最多 12 个、只在舞台内播一次；「太棒了」提示音 assets/audio/prompts/great.ogg。
 * 返回键 = 完成（回 Home）。
 */
public final class RewardActivity extends ChildActivity {

    private static final String EXTRA_SESSION_ID = "sessionId";
    private static final long STAR_INTERVAL_MS = 250L;
    private static final int CONFETTI_COUNT = 12;

    @NonNull
    public static Intent intent(@NonNull Activity from, long sessionId) {
        Intent i = new Intent(from, RewardActivity.class);
        i.putExtra(EXTRA_SESSION_ID, sessionId);
        return i;
    }

    private ActivityRewardBinding binding;
    private RewardViewModel vm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private long sessionId = -1;
    private boolean confettiPlayed;
    @Nullable
    private MediaPlayer praisePlayer;
    private ImageView[] stars;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRewardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new RewardViewModel(container())))
                .get(RewardViewModel.class);
        sessionId = getIntent().getLongExtra(EXTRA_SESSION_ID, -1);
        stars = new ImageView[]{binding.star0, binding.star1, binding.star2};

        binding.btnDone.setOnClickListener(v -> vm.finish());
        binding.btnAgain.setOnClickListener(v -> vm.playAgain());
        // 返回键 = 完成（回 Home）
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                vm.finish();
            }
        });

        vm.state().observe(this, this::render);
        vm.effects().observe(this, this::onEffect);

        // 会话可读后才 init（Reward 写库只写一次，由 ViewModel 保证）
        tryInit();
    }

    private void tryInit() {
        if (vm.init(sessionId)) {
            return;
        }
        container().store.observe().observe(this, new Observer<ProgressState>() {
            @Override
            public void onChanged(ProgressState ps) {
                if (vm.init(sessionId)) {
                    container().store.observe().removeObserver(this);
                }
            }
        });
    }

    private void render(RewardViewModel.UiState s) {
        if (s == null) {
            return;
        }
        binding.starsRow.setVisibility(s.showStars ? View.VISIBLE : View.GONE);
        // 星星槽位 = 本轮新字数（1–3）
        for (int i = 0; i < stars.length; i++) {
            stars[i].setVisibility(i < s.starSlots ? View.VISIBLE : View.GONE);
        }
        binding.rewardSubtitle.setText(s.learnedWords.isEmpty()
                ? getString(R.string.reward_subtitle_review)
                : getString(R.string.reward_subtitle, s.learnedWords.size()));

        // 本轮字块
        binding.wordsRow.removeAllViews();
        int chip = getResources().getDimensionPixelSize(R.dimen.ls_word_chip);
        int gap = getResources().getDimensionPixelSize(R.dimen.ls_gap);
        for (int i = 0; i < s.learnedWords.size(); i++) {
            Word w = s.learnedWords.get(i);
            BlockLayout block = new BlockLayout(this);
            block.setClickable(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(chip, chip);
            if (i > 0) {
                lp.setMarginStart(gap);
            }
            block.setLayoutParams(lp);
            HanziTextView text = new HanziTextView(this);
            text.setText(w.hanzi);
            text.setTextAppearance(R.style.TextAppearance_LS_HanziTile);
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            tlp.gravity = Gravity.CENTER;
            text.setLayoutParams(tlp);
            block.addView(text);
            binding.wordsRow.addView(block);
        }
    }

    private void onEffect(Event<RewardViewModel.Effect> event) {
        RewardViewModel.Effect e = event.getContentIfNotHandled();
        if (e instanceof RewardViewModel.Effect.Batch batch) {
            for (RewardViewModel.Effect each : batch.effects()) {
                handle(each);
            }
        } else if (e != null) {
            handle(e);
        }
    }

    private void handle(@NonNull RewardViewModel.Effect e) {
        if (e instanceof RewardViewModel.Effect.Close) {
            // §10.1：会话中到时不打断，奖励页结束时检查一次（defer 兜底）
            container().screenTime.checkNow();
            finish();
            Motion.childExit(this);
        } else if (e instanceof RewardViewModel.Effect.PlayAgain) {
            // 新会话由 Flashcard(SESSION) 进入时创建
            startActivity(FlashcardActivity.sessionIntent(this));
            Motion.childEnter(this);
            finish();
        } else if (e instanceof RewardViewModel.Effect.StarSfx star) {
            handler.postDelayed(() -> popStar(star.index()), star.index() * STAR_INTERVAL_MS);
        } else if (e instanceof RewardViewModel.Effect.PraiseSfx) {
            handler.postDelayed(this::playPraise, 0);
            playConfettiOnce();
        }
    }

    private void popStar(int index) {
        if (index < 0 || index >= stars.length || stars[index].getVisibility() != View.VISIBLE) {
            return;
        }
        stars[index].setImageTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.ls_yellow)));
        Motion.popStar(stars[index]);
        container().sfx.play(SfxPlayer.Sfx.STAR);
    }

    /** 「太棒了」提示音（assets/audio/prompts/great.ogg）。 */
    private void playPraise() {
        if (!container().settings.isSoundEnabled()) {
            return;
        }
        releasePraise();
        try {
            AssetFileDescriptor afd = getAssets().openFd("audio/prompts/great.ogg");
            praisePlayer = new MediaPlayer();
            praisePlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            praisePlayer.setOnCompletionListener(MediaPlayer::release);
            praisePlayer.prepare();
            praisePlayer.start();
        } catch (IOException | RuntimeException ex) {
            // 提示音缺失不阻塞奖励页
            releasePraise();
        }
    }

    /** confetti：最多 12 个小色块，只在黄色舞台内播一次。 */
    private void playConfettiOnce() {
        if (confettiPlayed || !Motion.animatorsEnabled()) {
            return;
        }
        confettiPlayed = true;
        FrameLayout layer = binding.confettiLayer;
        layer.post(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            int w = layer.getWidth();
            int h = layer.getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            int size = getResources().getDimensionPixelSize(R.dimen.ls_confetti);
            int[] colors = {R.color.ls_red, R.color.ls_blue, R.color.ls_green,
                    R.color.ls_on_red, R.color.ls_mint, R.color.ls_clay};
            for (int i = 0; i < CONFETTI_COUNT; i++) {
                View v = new View(this);
                v.setBackgroundColor(ContextCompat.getColor(this, colors[i % colors.length]));
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
                lp.leftMargin = random.nextInt(Math.max(1, w - size));
                v.setLayoutParams(lp);
                v.setTranslationY(-size * 2f);
                layer.addView(v);
                v.animate()
                        .translationY(h * (0.4f + random.nextFloat() * 0.6f))
                        .rotation(random.nextFloat() * 360f - 180f)
                        .alpha(0f)
                        .setDuration(700 + random.nextInt(400))
                        .setStartDelay(random.nextInt(300))
                        .withEndAction(() -> layer.removeView(v))
                        .start();
            }
        });
    }

    private void releasePraise() {
        if (praisePlayer != null) {
            praisePlayer.release();
            praisePlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releasePraise();
        super.onDestroy();
    }
}
