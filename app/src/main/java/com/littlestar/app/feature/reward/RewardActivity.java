package com.littlestar.app.feature.reward;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.littlestar.app.R;
import com.littlestar.app.core.audio.SfxPlayer;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.databinding.ActivityRewardBinding;
import com.littlestar.app.feature.review.RoundReviewActivity;

public final class RewardActivity extends ChildActivity {
    private static final String EXTRA_SESSION_ID = "sessionId";

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

        RewardViewModel vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new RewardViewModel(container()))).get(RewardViewModel.class);
        vm.state().observe(this, s -> {
            binding.rewardSubtitle.setText(s.newWords == 0 ? getString(R.string.reward_subtitle_review)
                    : getString(R.string.reward_subtitle, s.newWords));
            binding.rewardScore.setText(getString(R.string.sprout_first_try, s.correct, s.total));
            int pending = Math.max(0, s.total - s.correct);
            binding.rewardPractice.setText(pending == 0 ? getString(R.string.sprout_no_revisit)
                    : getResources().getQuantityString(R.plurals.sprout_practice_count, pending, pending));
            binding.btnReview.setEnabled(s.total > 0);
        });
        vm.effects().observe(this, event -> {
            if (event.getContentIfNotHandled() != null) {
                container().sfx.play(SfxPlayer.Sfx.CORRECT);
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
}
