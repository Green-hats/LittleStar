package com.littlestar.app.feature.reward;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.LocaleSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.littlestar.app.R;
import com.littlestar.app.core.audio.SpeechPlayer;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.databinding.ActivityRewardBinding;
import com.littlestar.app.databinding.ViewPracticedWordBinding;
import com.littlestar.app.feature.review.RoundReviewActivity;

import java.util.Locale;

public final class RewardActivity extends ChildActivity {
    private static final String EXTRA_SESSION_ID = "sessionId";
    private SpeechPlayer.FailureListener audioFailureListener;
    private View playingWord;

    @NonNull
    public static Intent intent(@NonNull Activity from, long sessionId) {
        return new Intent(from, RewardActivity.class).putExtra(EXTRA_SESSION_ID, sessionId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityRewardBinding binding = ActivityRewardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());
        long sessionId = getIntent().getLongExtra(EXTRA_SESSION_ID, -1);
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnDone.setOnClickListener(v -> finish());
        binding.btnReview.setOnClickListener(v -> startActivity(RoundReviewActivity.intent(this, sessionId)));
        audioFailureListener = id -> binding.rewardAudioMissing.setVisibility(View.VISIBLE);
        container().speech.setFailureListener(audioFailureListener);
        container().speech.isPlaying().observe(this, playing -> {
            if (playingWord != null) {
                playingWord.setStateDescription(Boolean.TRUE.equals(playing)
                        ? getString(R.string.fc_reading) : null);
            }
        });

        RewardViewModel vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new RewardViewModel(container()))).get(RewardViewModel.class);
        vm.state().observe(this, s -> {
            int practiced = s.words.size();
            binding.rewardSubtitle.setText(practiced == 0 ? getString(R.string.reward_subtitle_review)
                    : getResources().getQuantityString(R.plurals.reward_subtitle, practiced, practiced));
            binding.rewardScore.setText(getString(R.string.sprout_first_try, s.correct, s.total));
            int pending = Math.max(0, s.total - s.correct);
            binding.rewardPractice.setText(pending == 0 ? getString(R.string.sprout_no_revisit)
                    : getResources().getQuantityString(R.plurals.sprout_practice_count, pending, pending));
            binding.btnReview.setEnabled(s.total > 0);
            binding.practicedSection.setVisibility(s.words.isEmpty() ? View.GONE : View.VISIBLE);
            binding.practicedWords.removeAllViews();
            for (RewardViewModel.PracticedWord result : s.words) {
                ViewPracticedWordBinding row = ViewPracticedWordBinding.inflate(
                        getLayoutInflater(), binding.practicedWords, false);
                String hanzi = result.word().hanzi;
                row.btnReplayWord.setText(hanzi);
                String replayLabel = getString(R.string.reward_replay_word, hanzi);
                SpannableString label = new SpannableString(replayLabel);
                int start = replayLabel.indexOf(hanzi);
                label.setSpan(new LocaleSpan(Locale.SIMPLIFIED_CHINESE), start,
                        start + hanzi.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                row.btnReplayWord.setContentDescription(label);
                row.practicedResult.setText(switch (result.result()) {
                    case INDEPENDENT -> R.string.reward_word_independent;
                    case WITH_CLUE -> R.string.reward_word_with_clue;
                    case WITH_CLUE_THEN_INDEPENDENT -> R.string.reward_word_retested;
                    case CORRECTED -> R.string.reward_word_corrected;
                    case REVISIT -> R.string.reward_word_revisit;
                });
                row.btnReplayWord.setOnClickListener(v -> {
                    if (playingWord != null && playingWord != v) {
                        playingWord.setStateDescription(null);
                    }
                    playingWord = v;
                    binding.rewardAudioMissing.setVisibility(View.GONE);
                    container().speech.play(result.word());
                });
                binding.practicedWords.addView(row.getRoot());
            }
        });

        container().content.load(container().executors, () -> {
            if (!isDestroyed()) {
                container().progress.observe().observe(this, snapshot -> {
                    if (snapshot != null && !vm.init(sessionId)) {
                        binding.rewardSubtitle.setText(R.string.sprout_unavailable);
                    }
                });
            }
        });
    }

    @Override
    protected void onPause() {
        container().speech.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        container().speech.clearFailureListener(audioFailureListener);
        super.onDestroy();
    }
}
