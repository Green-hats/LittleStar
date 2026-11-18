package com.littlestar.app.feature.quiz;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.LocaleSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.littlestar.app.R;
import com.littlestar.app.core.audio.SpeechPlayer;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.databinding.ActivityQuizBinding;
import com.littlestar.app.databinding.ViewQuizOptionBinding;
import com.littlestar.app.feature.reward.RewardActivity;

import java.util.Locale;

public final class QuizActivity extends ChildActivity {
    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_SESSION_ID = "sessionId";

    public static Intent sessionIntent(@NonNull Activity from, long sessionId) {
        return intent(from, sessionId, QuizViewModel.Mode.SESSION);
    }

    public static Intent themeIntent(@NonNull Activity from, long sessionId) {
        return intent(from, sessionId, QuizViewModel.Mode.THEME);
    }

    private static Intent intent(Activity from, long id, QuizViewModel.Mode mode) {
        return new Intent(from, QuizActivity.class)
                .putExtra(EXTRA_MODE, mode.name()).putExtra(EXTRA_SESSION_ID, id);
    }

    private ActivityQuizBinding binding;
    private QuizViewModel vm;
    private ViewQuizOptionBinding[] options;
    private SpeechPlayer.FailureListener audioFailureListener;
    private int renderedIndex = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQuizBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        vm = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            @SuppressWarnings("unchecked")
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass, @NonNull CreationExtras extras) {
                return (T) new QuizViewModel(container(), SavedStateHandleSupport.createSavedStateHandle(extras));
            }
        }).get(QuizViewModel.class);

        options = new ViewQuizOptionBinding[]{
                binding.option0, binding.option1, binding.option2, binding.option3};
        for (int i = 0; i < options.length; i++) {
            int index = i;
            options[i].getRoot().setOnClickListener(v -> vm.choose(index));
        }
        container().speech.isPlaying().observe(this, playing -> binding.btnListen.setStateDescription(
                Boolean.TRUE.equals(playing) ? getString(R.string.fc_reading) : null));

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnListen.setOnClickListener(v -> vm.speakCurrent());
        binding.btnRetry.setOnClickListener(v -> vm.retry());
        binding.btnNext.setOnClickListener(v -> vm.next());
        binding.btnAudioRetry.setOnClickListener(v -> {
            binding.audioMissing.setVisibility(View.GONE);
            vm.speakCurrent();
        });
        binding.btnAudioContinue.setOnClickListener(v -> finish());
        audioFailureListener = id -> {
            QuizViewModel.UiState state = vm.state().getValue();
            if (state != null && state.question != null && id.equals(state.question.wordId())) {
                binding.audioMissing.setVisibility(View.VISIBLE);
                binding.audioMissing.announceForAccessibility(getString(R.string.pr_audio_title));
            }
        };
        container().speech.setFailureListener(audioFailureListener);

        vm.state().observe(this, this::render);
        vm.effects().observe(this, event -> {
            QuizViewModel.Effect effect = event.getContentIfNotHandled();
            if (effect instanceof QuizViewModel.Effect.GoReward reward) {
                startActivity(RewardActivity.intent(this, reward.sessionId()));
                finish();
            } else if (effect instanceof QuizViewModel.Effect.PlayWord play) {
                Word word = container().content.wordById(play.wordId());
                if (word != null) {
                    container().speech.play(word);
                }
            }
        });

        long sessionId = getIntent().getLongExtra(EXTRA_SESSION_ID, -1);
        QuizViewModel.Mode mode = QuizViewModel.Mode.THEME.name().equals(getIntent().getStringExtra(EXTRA_MODE))
                ? QuizViewModel.Mode.THEME : QuizViewModel.Mode.SESSION;
        container().content.load(container().executors, () -> {
            if (!isDestroyed()) {
                container().progress.observe().observe(this, snapshot -> {
                    if (snapshot != null && !vm.init(mode, sessionId)) {
                        binding.quizCounter.setText(R.string.sprout_unavailable);
                    }
                });
            }
        });
    }

    private void render(QuizViewModel.UiState state) {
        if (state.question == null) {
            binding.quizCounter.setText(R.string.sprout_unavailable);
            return;
        }
        binding.quizOptions.setVisibility(View.VISIBLE);
        binding.quizCounter.setText(getString(R.string.quiz_counter, state.index + 1, state.total));
        binding.quizProgress.setMax(state.total);
        binding.quizProgress.setProgress(state.index + 1);

        boolean newQuestion = renderedIndex != state.index;
        if (newQuestion) {
            renderedIndex = state.index;
            binding.audioMissing.setVisibility(View.GONE);
            binding.getRoot().scrollTo(0, 0);
        }

        boolean resolved = state.lastAnswerCorrect || state.corrected;
        for (int i = 0; i < options.length; i++) {
            ViewQuizOptionBinding option = options[i];
            if (i >= state.question.options().size()) {
                option.getRoot().setVisibility(View.GONE);
                continue;
            }
            String value = state.question.options().get(i);
            boolean right = resolved && i == state.question.correctIndex();
            boolean wrong = state.answered && (state.retrying ? i == state.retryChosenIndex
                    : !resolved && i == state.chosenIndex);
            option.getRoot().setVisibility(View.VISIBLE);
            option.getRoot().setEnabled(!state.answered || state.retrying);
            option.getRoot().setCardBackgroundColor(ContextCompat.getColor(this,
                    right ? R.color.sprout_dew : wrong ? R.color.sprout_peach : R.color.ls_card));
            option.optionHanzi.setText(value);
            option.optionBadge.setVisibility(right || wrong ? View.VISIBLE : View.GONE);
            option.optionBadge.setImageResource(right ? R.drawable.ic_check : R.drawable.ic_close);
            SpannableString pronunciation = new SpannableString(value);
            pronunciation.setSpan(new LocaleSpan(Locale.SIMPLIFIED_CHINESE),
                    0, value.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            option.getRoot().setContentDescription(pronunciation);
            option.getRoot().setStateDescription(right ? getString(R.string.quiz_option_correct)
                    : wrong ? getString(R.string.quiz_option_wrong) : null);
        }

        binding.bottomFeedback.setVisibility(state.answered ? View.VISIBLE : View.GONE);
        if (state.answered) {
            binding.bottomFeedback.setCardBackgroundColor(ContextCompat.getColor(this,
                    resolved ? R.color.sprout_dew : R.color.sprout_peach));
            binding.feedbackText.setText(state.corrected ? getString(R.string.quiz_retry_correct)
                    : state.lastAnswerCorrect ? getString(R.string.quiz_feedback_correct)
                    : state.retrying ? getString(R.string.quiz_retry_hint)
                    : getString(R.string.quiz_feedback_wrong, state.question.hanzi(), state.question.english()));
            binding.btnRetry.setVisibility(!resolved && !state.retrying ? View.VISIBLE : View.GONE);
            binding.btnNext.setVisibility(resolved ? View.VISIBLE : View.GONE);
            binding.btnNext.setText(state.isLast ? R.string.sprout_finish : R.string.sprout_next);
            if (!state.retrying) {
                binding.bottomFeedback.post(() -> binding.bottomFeedback.requestRectangleOnScreen(
                        new android.graphics.Rect(0, 0, binding.bottomFeedback.getWidth(),
                                binding.bottomFeedback.getHeight())));
            }
        }
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
