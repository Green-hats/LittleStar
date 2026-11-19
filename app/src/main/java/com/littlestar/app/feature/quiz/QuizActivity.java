package com.littlestar.app.feature.quiz;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.LocaleSpan;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.OneShotPreDrawListener;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.google.android.material.R.attr;
import com.google.android.material.color.MaterialColors;
import com.littlestar.app.R;
import com.littlestar.app.core.audio.SpeechPlayer;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.databinding.ActivityQuizBinding;
import com.littlestar.app.databinding.ViewQuizOptionBinding;
import com.littlestar.app.feature.reward.RewardActivity;

import java.util.List;
import java.util.Locale;

public final class QuizActivity extends ChildActivity {
    private static final String EXTRA_SESSION_ID = "sessionId";

    public static Intent intent(@NonNull Activity from, long sessionId) {
        return new Intent(from, QuizActivity.class).putExtra(EXTRA_SESSION_ID, sessionId);
    }

    private ActivityQuizBinding binding;
    private QuizViewModel vm;
    private ViewQuizOptionBinding[] options;
    private SpeechPlayer.FailureListener audioFailureListener;
    private int renderedIndex = -1;
    private boolean renderedRetest;
    private List<String> renderedOptions = List.of();

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
                return (T) new QuizViewModel(container());
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
        container().content.load(container().executors, () -> {
            if (!isDestroyed()) {
                container().progress.observe().observe(this, snapshot -> {
                    if (snapshot != null && !vm.init(sessionId)) {
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
        binding.quizCounter.setText(state.retest ? getString(R.string.quiz_retest_title)
                : getString(R.string.quiz_counter, state.index + 1, state.total));
        binding.quizProgress.setMax(state.total);
        binding.quizProgress.setProgress(state.index + 1);

        boolean questionChanged = renderedIndex != state.index || renderedRetest != state.retest;
        boolean movePromptFocus = renderedIndex >= 0 && questionChanged;
        boolean newQuestion = questionChanged || !renderedOptions.equals(state.question.options());
        if (newQuestion) {
            renderedIndex = state.index;
            renderedRetest = state.retest;
            renderedOptions = state.question.options();
            binding.audioMissing.setVisibility(View.GONE);
            binding.getRoot().scrollTo(0, 0);
        }

        binding.shapeClue.setVisibility(state.showingHint ? View.VISIBLE : View.GONE);
        if (state.showingHint) {
            CharacterClue clue = CharacterClue.forHanzi(state.question.hanzi());
            binding.clueShape.setVisibility(clue == null ? View.GONE : View.VISIBLE);
            binding.clueShape.setText(clue == null ? null : clue.shape());
            binding.clueText.setText(clue == null ? R.string.quiz_clue_fallback : clue.description());
        }
        boolean correct = state.lastAnswerCorrect || state.corrected;
        int success = MaterialColors.getColor(binding.bottomFeedback, attr.colorOnSecondaryContainer);
        int successBackground = MaterialColors.getColor(binding.bottomFeedback, attr.colorSecondaryContainer);
        int retry = MaterialColors.getColor(binding.bottomFeedback, attr.colorOnErrorContainer);
        int retryBackground = MaterialColors.getColor(binding.bottomFeedback, attr.colorErrorContainer);
        int surface = MaterialColors.getColor(binding.bottomFeedback, attr.colorSurface);
        int onSurface = MaterialColors.getColor(binding.bottomFeedback, attr.colorOnSurface);
        int outline = MaterialColors.getColor(binding.bottomFeedback, attr.colorOutline);
        for (int i = 0; i < options.length; i++) {
            ViewQuizOptionBinding option = options[i];
            if (i >= state.question.options().size()) {
                option.getRoot().setVisibility(View.GONE);
                continue;
            }
            String value = state.question.options().get(i);
            boolean right = correct && i == state.question.correctIndex();
            int wrongIndex = state.showingHint ? state.retryChosenIndex : state.chosenIndex;
            boolean wrong = state.answered && !correct && i == wrongIndex;
            option.getRoot().setVisibility(View.VISIBLE);
            option.getRoot().setEnabled(!state.answered || state.retrying);
            int foreground = right ? success : wrong ? retry : onSurface;
            int background = right ? successBackground : wrong ? retryBackground : surface;
            option.getRoot().setCardBackgroundColor(background);
            option.getRoot().setStrokeColor(right || wrong ? foreground : outline);
            option.getRoot().setStrokeWidth(getResources().getDimensionPixelSize(
                    right || wrong ? R.dimen.quiz_outcome_outline : R.dimen.quiz_option_outline));
            option.optionHanzi.setText(value);
            option.optionHanzi.setTextColor(foreground);
            option.optionBadge.setVisibility(right || wrong ? View.VISIBLE : View.GONE);
            option.optionBadge.setImageResource(right ? R.drawable.ic_check : R.drawable.ic_close);
            option.optionBadge.setImageTintList(ColorStateList.valueOf(foreground));
            SpannableString pronunciation = new SpannableString(value);
            pronunciation.setSpan(new LocaleSpan(Locale.SIMPLIFIED_CHINESE),
                    0, value.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            option.getRoot().setContentDescription(pronunciation);
            option.getRoot().setStateDescription(right ? getString(R.string.quiz_option_correct)
                    : wrong ? getString(R.string.quiz_option_wrong) : null);
        }

        boolean feedback = state.answered && !state.retrying;
        binding.bottomFeedback.setVisibility(feedback ? View.VISIBLE : View.GONE);
        if (feedback) {
            binding.bottomFeedback.setCardBackgroundColor(correct ? successBackground : retryBackground);
            binding.feedbackText.setTextColor(correct ? success : retry);
            binding.feedbackText.setText(state.corrected ? getString(R.string.quiz_retry_correct)
                    : state.lastAnswerCorrect ? getString(R.string.quiz_feedback_correct)
                    : state.retest ? getString(R.string.quiz_retest_wrong)
                    : state.showingHint ? getString(R.string.quiz_clue_wrong)
                    : getString(R.string.quiz_feedback_wrong));
            binding.btnRetry.setVisibility(state.canAdvance() ? View.GONE : View.VISIBLE);
            binding.btnRetry.setText(state.showingHint ? R.string.sprout_try_again : R.string.quiz_see_clue);
            binding.btnRetry.setIconResource(state.showingHint ? R.drawable.ic_retry : R.drawable.ic_clue);
            binding.btnNext.setVisibility(state.canAdvance() ? View.VISIBLE : View.GONE);
            binding.btnNext.setText(state.isLast ? R.string.sprout_finish : R.string.sprout_next);
            binding.bottomFeedback.post(() -> binding.bottomFeedback.requestRectangleOnScreen(
                    new android.graphics.Rect(0, 0, binding.bottomFeedback.getWidth(),
                            binding.bottomFeedback.getHeight())));
        }
        if (movePromptFocus && getSystemService(AccessibilityManager.class).isTouchExplorationEnabled()) {
            OneShotPreDrawListener.add(binding.questionHint, () -> binding.questionHint.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
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
