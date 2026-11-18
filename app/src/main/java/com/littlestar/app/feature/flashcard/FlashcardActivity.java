package com.littlestar.app.feature.flashcard;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.littlestar.app.R;
import com.littlestar.app.core.audio.SpeechPlayer;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.ContentImage;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.databinding.ActivityFlashcardBinding;
import com.littlestar.app.feature.quiz.QuizActivity;

import java.util.ArrayList;
import java.util.List;

public final class FlashcardActivity extends ChildActivity {
    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_WORD_IDS = "wordIds";
    private static final String EXTRA_START_INDEX = "startIndex";
    private static final String EXTRA_SESSION_ID = "sessionId";
    private static final String STATE_SESSION_ID = "state_session_id";
    private static final String STATE_CURRENT_INDEX = "state_current_index";

    private ActivityFlashcardBinding binding;
    private FlashcardViewModel vm;
    private ContentImage illustration;
    private String renderedWordId;
    private SpeechPlayer.FailureListener audioFailureListener;
    private boolean landscape;

    @NonNull
    public static Intent sessionIntent(@NonNull Activity from) {
        return new Intent(from, FlashcardActivity.class)
                .putExtra(EXTRA_MODE, FlashcardViewModel.Mode.SESSION.name());
    }

    @NonNull
    public static Intent browseIntent(@NonNull Activity from, @NonNull List<String> wordIds, int startIndex) {
        return new Intent(from, FlashcardActivity.class)
                .putExtra(EXTRA_MODE, FlashcardViewModel.Mode.BROWSE.name())
                .putStringArrayListExtra(EXTRA_WORD_IDS, new ArrayList<>(wordIds))
                .putExtra(EXTRA_START_INDEX, startIndex);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFlashcardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());
        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        binding.flashcardScroll.setVisibility(landscape ? View.GONE : View.VISIBLE);
        binding.rotationGate.setVisibility(landscape ? View.VISIBLE : View.GONE);
        binding.btnBack.setOnClickListener(v -> finish());
        binding.rotationBack.setOnClickListener(v -> finish());
        if (landscape) {
            return;
        }

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new FlashcardViewModel(container())))
                .get(FlashcardViewModel.class);
        illustration = new ContentImage(binding.pageCard.pageIllustration, container().executors,
                binding.pageCard.pageGlyph);
        binding.pageCard.btnListen.setOnClickListener(v -> speakCurrent());
        binding.btnNext.setOnClickListener(v -> vm.next());
        binding.stamp.setOnClickListener(v -> {
            if (vm.stamp()) {
                binding.stamp.setEnabled(false);
            }
        });
        binding.btnAudioContinue.setOnClickListener(v -> binding.audioMissing.setVisibility(View.GONE));
        binding.btnAudioRetry.setOnClickListener(v -> {
            binding.audioMissing.setVisibility(View.GONE);
            speakCurrent();
        });
        audioFailureListener = wordId -> {
            if (wordId.equals(renderedWordId)) {
                binding.audioMissing.setVisibility(View.VISIBLE);
                binding.audioMissing.announceForAccessibility(getString(R.string.pr_audio_title));
            }
        };

        vm.state().observe(this, this::render);
        vm.effects().observe(this, event -> {
            FlashcardViewModel.Effect effect = event.getContentIfNotHandled();
            if (effect instanceof FlashcardViewModel.Effect.GoQuiz quiz) {
                startActivity(QuizActivity.sessionIntent(this, quiz.sessionId()));
                finish();
            } else if (effect instanceof FlashcardViewModel.Effect.Close) {
                finish();
            }
        });
        container().progress.observe().observe(this, ignored -> vm.refresh());
        container().speech.isPlaying().observe(this, playing -> binding.pageCard.btnListen
                .setStateDescription(Boolean.TRUE.equals(playing) ? getString(R.string.fc_reading) : null));

        long sessionId = savedInstanceState == null ? getIntent().getLongExtra(EXTRA_SESSION_ID, -1)
                : savedInstanceState.getLong(STATE_SESSION_ID, -1);
        int startIndex = savedInstanceState == null ? getIntent().getIntExtra(EXTRA_START_INDEX, 0)
                : savedInstanceState.getInt(STATE_CURRENT_INDEX, 0);
        List<String> ids = getIntent().getStringArrayListExtra(EXTRA_WORD_IDS);
        FlashcardViewModel.Mode mode =
                FlashcardViewModel.Mode.BROWSE.name().equals(getIntent().getStringExtra(EXTRA_MODE))
                ? FlashcardViewModel.Mode.BROWSE : FlashcardViewModel.Mode.SESSION;
        vm.initialize(mode, ids == null ? List.of() : ids, startIndex, sessionId);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (vm != null) {
            outState.putLong(STATE_SESSION_ID, vm.sessionId());
            outState.putInt(STATE_CURRENT_INDEX, vm.currentIndex());
        }
    }

    private void render(FlashcardViewModel.UiState state) {
        Word word = state.current();
        if (word == null) {
            binding.cardCounter.setText(R.string.sprout_unavailable);
            return;
        }
        binding.pageCard.getRoot().setVisibility(View.VISIBLE);
        binding.cardCounter.setText(getString(R.string.sprout_word_counter, state.index + 1, state.words.size()));
        binding.cardProgress.setMax(state.words.size());
        binding.cardProgress.setProgress(state.index + 1);
        if (!word.id.equals(renderedWordId)) {
            renderedWordId = word.id;
            binding.audioMissing.setVisibility(View.GONE);
            binding.pageCard.pageHanzi.setText(word.hanzi);
            binding.pageCard.pagePinyin.setText(word.pinyin);
            binding.pageCard.pageEnglish.setText(word.english);
            illustration.bind(word.image);
            binding.flashcardScroll.scrollTo(0, 0);
            if (getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                speakCurrent();
            }
        }
        boolean browsing = state.mode == FlashcardViewModel.Mode.BROWSE;
        binding.stamp.setVisibility(browsing ? View.VISIBLE : View.GONE);
        binding.stamp.setEnabled(!state.currentLearned);
        binding.stamp.setText(state.currentLearned ? R.string.sprout_learned : R.string.sprout_mark);
        binding.btnNext.setEnabled(true);
        binding.btnNext.setText(!state.isLast ? R.string.sprout_next
                : browsing ? R.string.sprout_back : R.string.sprout_start_practice);
    }

    private void speakCurrent() {
        FlashcardViewModel.UiState state = vm.state().getValue();
        if (state != null && state.current() != null) {
            container().speech.play(state.current());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!landscape) {
            container().speech.setFailureListener(audioFailureListener);
            speakCurrent();
        }
    }

    @Override
    protected void onPause() {
        if (!landscape) {
            container().speech.clearFailureListener(audioFailureListener);
            container().speech.stop();
        }
        super.onPause();
    }
}
