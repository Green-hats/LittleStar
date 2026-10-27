package com.littlestar.app.feature.culture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.databinding.ActivityCultureDetailBinding;
import com.littlestar.app.feature.player.PlayerActivity;

/** 文化学习第二步：了解内容、预览文本，再主动开始播放。 */
public final class CultureDetailActivity extends ChildActivity {

    private static final String EXTRA_ITEM_ID = "itemId";
    private static final String STATE_PREVIEW = "previewExpanded";
    private ActivityCultureDetailBinding binding;
    private CultureDetailViewModel vm;
    private boolean previewExpanded;

    @NonNull
    public static Intent intent(@NonNull Activity from, @NonNull String itemId) {
        return new Intent(from, CultureDetailActivity.class).putExtra(EXTRA_ITEM_ID, itemId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCultureDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());
        previewExpanded = savedInstanceState != null && savedInstanceState.getBoolean(STATE_PREVIEW);
        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new CultureDetailViewModel(container())))
                .get(CultureDetailViewModel.class);

        binding.btnBack.setOnClickListener(v -> {
            finish();
            Motion.childExit(this);
        });
        binding.btnPreview.setOnClickListener(v -> {
            previewExpanded = !previewExpanded;
            renderPreview();
        });
        binding.btnListen.setOnClickListener(v -> {
            CultureDetailViewModel.UiState s = vm.state().getValue();
            if (s == null || s.item == null || !binding.btnListen.isEnabled()) {
                return;
            }
            binding.btnListen.setEnabled(false);
            startActivity(PlayerActivity.intent(this, s.item.id, s.queue));
            Motion.childEnter(this);
        });
        vm.state().observe(this, this::render);
        container().content.load(container().executors, () -> {
            if (!isFinishing() && !isDestroyed()) {
                vm.init(getIntent().getStringExtra(EXTRA_ITEM_ID));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (vm != null && vm.state().getValue() != null) {
            render(vm.state().getValue());
        }
    }

    private void render(CultureDetailViewModel.UiState s) {
        if (s == null) {
            return;
        }
        boolean valid = s.item != null;
        binding.detailContent.setVisibility(valid ? View.VISIBLE : View.GONE);
        binding.detailStatus.setVisibility(valid ? View.GONE : View.VISIBLE);
        binding.detailStatus.setText(R.string.culture_detail_missing);
        binding.btnListen.setEnabled(valid);
        if (!valid) {
            return;
        }
        binding.detailGlyph.setText(s.item.glyph);
        binding.detailTitle.setText(s.item.titleZh);
        binding.detailSubtitle.setText(getString(R.string.player_subtitle_fmt,
                s.item.subtitle, CultureListActivity.formatDuration(s.item.durationSec)));
        binding.detailSummary.setText(s.item.summary);
        binding.detailFocus.setText(s.item.learningFocus);
        binding.detailTranscript.setText(s.item.transcript);
        renderPreview();
    }

    private void renderPreview() {
        binding.detailTranscript.setVisibility(previewExpanded ? View.VISIBLE : View.GONE);
        binding.previewLabel.setText(previewExpanded
                ? R.string.culture_detail_hide_text : R.string.culture_detail_read_together);
        binding.btnPreview.setContentDescription(getString(previewExpanded
                ? R.string.culture_detail_hide_text : R.string.culture_detail_read_together));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_PREVIEW, previewExpanded);
    }
}
