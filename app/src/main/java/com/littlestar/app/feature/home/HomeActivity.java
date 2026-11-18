package com.littlestar.app.feature.home;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;
import androidx.lifecycle.ViewModelProvider;

import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.databinding.ActivityHomeBinding;
import com.littlestar.app.feature.culture.CultureCategoriesActivity;
import com.littlestar.app.feature.flashcard.FlashcardActivity;
import com.littlestar.app.feature.parent.ParentGateActivity;
import com.littlestar.app.feature.words.ThemesActivity;

public final class HomeActivity extends ChildActivity {
    private ActivityHomeBinding binding;
    private HomeViewModel vm;
    private boolean openingLesson;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());
        getLifecycle().addObserver(new HomePlaybackControls(this, binding));

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new HomeViewModel(container()))).get(HomeViewModel.class);
        vm.state().observe(this, this::render);

        binding.btnStart.setOnClickListener(v -> {
            if (!openingLesson) {
                openingLesson = true;
                binding.btnStart.setEnabled(false);
                startActivity(FlashcardActivity.sessionIntent(this));
            }
        });
        binding.moduleWords.setOnClickListener(v -> startActivity(new Intent(this, ThemesActivity.class)));
        binding.moduleCulture.setOnClickListener(v -> startActivity(new Intent(this, CultureCategoriesActivity.class)));
        binding.parentEntry.setOnClickListener(v -> startActivity(ParentGateActivity.intent(this)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        openingLesson = false;
        vm.refresh();
    }

    private void render(HomeViewModel.UiState state) {
        binding.btnStart.setEnabled(!state.loading && !openingLesson && state.nextWord != null);
        if (state.loading) {
            return;
        }
        binding.todayProgressText.setText(getString(R.string.sprout_today_goal, state.learnedToday, state.goal));
        binding.todayProgress.setMax(Math.max(1, state.goal));
        binding.todayProgress.setProgress(state.isDailyComplete() ? Math.max(1, state.goal) : state.learnedToday);
        binding.btnStart.setText(state.isDailyComplete() ? R.string.sprout_review : R.string.sprout_start);
    }
}
