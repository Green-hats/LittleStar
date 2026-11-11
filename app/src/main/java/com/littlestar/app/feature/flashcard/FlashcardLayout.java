package com.littlestar.app.feature.flashcard;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.littlestar.app.R;
import com.littlestar.app.databinding.ActivityFlashcardBinding;

/** 依实际视口先重排，再逐步缩放；到达可读和触控下限后才允许整页滚动。 */
final class FlashcardLayout implements ViewTreeObserver.OnGlobalLayoutListener {
    private final ActivityFlashcardBinding binding;
    private int width;
    private int height;
    private boolean compact;
    private float scale = 1f;

    FlashcardLayout(ActivityFlashcardBinding binding) {
        this.binding = binding;
        binding.flashcardRoot.getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    void reset() {
        width = 0;
        binding.flashcardRoot.requestLayout();
    }

    boolean isLandscape() {
        return binding.getRoot().getWidth() > binding.getRoot().getHeight();
    }

    @Override
    public void onGlobalLayout() {
        var viewport = (ConstraintLayout.LayoutParams) binding.flashcardScroll.getLayoutParams();
        int maximumWidth = dimension(isLandscape()
                ? R.dimen.ls_flashcard_landscape_max_width : R.dimen.ls_content_max_width);
        if (viewport.matchConstraintMaxWidth != maximumWidth) {
            viewport.matchConstraintMaxWidth = maximumWidth;
            binding.flashcardScroll.setLayoutParams(viewport);
            return;
        }
        int availableWidth = binding.flashcardScroll.getWidth();
        int availableHeight = binding.flashcardScroll.getHeight();
        if (availableWidth <= 0 || availableHeight <= 0) {
            return;
        }
        if (width != availableWidth || height != availableHeight) {
            width = availableWidth;
            height = availableHeight;
            compact = isLandscape();
            scale = 1f;
            apply();
            return;
        }
        int needed = binding.flashcardWorkspace.getBottom() + binding.flashcardRoot.getPaddingBottom();
        if (needed <= height) {
            return;
        }
        if (!compact) {
            compact = true;
            apply();
        } else if (scale > .6f) {
            float shortage = 1f - height / (float) needed;
            scale = Math.max(.6f, scale - Math.max(.05f, shortage * scale));
            apply();
        }
    }

    private int dimension(int id) {
        return binding.getRoot().getResources().getDimensionPixelSize(id);
    }

    private int scaled(int id, int minimum) {
        return Math.max(dimension(minimum), Math.round(dimension(id) * scale));
    }

    private void textSize(TextView view, int maximum, int minimum) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(
                dimension(minimum), dimension(maximum) * scale));
    }

    private void apply() {
        var card = binding.pageCard;
        boolean landscape = isLandscape();
        int gap = scaled(compact ? R.dimen.ls_gap_sm : R.dimen.ls_gap, R.dimen.ls_flashcard_gap_min);
        int margin = scaled(compact ? R.dimen.ls_gap : R.dimen.ls_flashcard_margin, R.dimen.ls_gap_sm);
        int touch = scaled(R.dimen.ls_btn_primary, R.dimen.ls_btn_icon);
        binding.flashcardRoot.setPadding(margin, margin, margin, margin);
        card.cardBody.setPadding(gap, gap, gap, gap);
        binding.flashcardWorkspace.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        card.pageMain.setOrientation(compact ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        var image = (LinearLayout.LayoutParams) card.illustrationFrame.getLayoutParams();
        image.width = scaled(compact ? R.dimen.ls_flashcard_image_compact : R.dimen.ls_flashcard_image,
                R.dimen.ls_flashcard_image_min);
        if (landscape) {
            image.width = Math.max(dimension(R.dimen.ls_flashcard_image_min),
                    Math.min(dimension(R.dimen.ls_flashcard_image_landscape_max), Math.round(width * .16f * scale)));
        }
        image.height = image.width;
        image.gravity = compact ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL;
        image.setMarginEnd(compact ? gap : 0);
        card.illustrationFrame.setLayoutParams(image);
        var labels = (LinearLayout.LayoutParams) card.wordLabels.getLayoutParams();
        labels.width = compact ? 0 : ViewGroup.LayoutParams.MATCH_PARENT;
        labels.weight = compact ? 1f : 0f;
        labels.topMargin = compact ? 0 : gap;
        card.wordLabels.setLayoutParams(labels);

        var cardParams = (LinearLayout.LayoutParams) card.getRoot().getLayoutParams();
        cardParams.width = landscape ? 0 : ViewGroup.LayoutParams.MATCH_PARENT;
        cardParams.weight = landscape ? 1f : 0f;
        card.getRoot().setLayoutParams(cardParams);
        var footer = (LinearLayout.LayoutParams) binding.flashcardFooter.getLayoutParams();
        footer.width = landscape ? Math.max(dimension(R.dimen.ls_flashcard_sidebar_min),
                Math.min(dimension(R.dimen.ls_flashcard_sidebar_max), Math.round(width * .28f)))
                : ViewGroup.LayoutParams.MATCH_PARENT;
        footer.topMargin = landscape ? 0 : gap;
        footer.setMarginStart(landscape ? gap : 0);
        binding.flashcardFooter.setLayoutParams(footer);
        binding.flashcardFooter.setOrientation(landscape ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);

        ViewGroup audioParent = (ViewGroup) card.audioRow.getParent();
        ViewGroup target = landscape ? binding.flashcardFooter : card.cardBody;
        if (audioParent != target) {
            audioParent.removeView(card.audioRow);
            target.addView(card.audioRow, landscape ? 0 : target.getChildCount());
        }
        if (landscape) {
            card.soundWave.setPlaying(false);
            card.soundWave.setVisibility(View.GONE);
            card.readingLabel.setVisibility(View.GONE);
        }
        for (View action : new View[]{binding.stamp, binding.btnNext}) {
            var params = (LinearLayout.LayoutParams) action.getLayoutParams();
            params.width = landscape ? ViewGroup.LayoutParams.MATCH_PARENT : 0;
            params.height = landscape ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
            params.weight = landscape ? 0f : action == binding.stamp ? 1f : 1.5f;
            params.topMargin = landscape ? gap : 0;
            params.setMarginStart(!landscape && action == binding.btnNext ? gap : 0);
            action.setMinimumHeight(touch);
            action.setLayoutParams(params);
        }
        for (View view : new View[]{binding.cardProgress, binding.flashcardWorkspace, card.audioRow}) {
            var params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            params.topMargin = landscape && view == card.audioRow ? 0 : gap;
            view.setLayoutParams(params);
        }
        int audioTouch = scaled(R.dimen.ls_btn_secondary, R.dimen.ls_btn_icon);
        card.btnListen.setMinimumHeight(audioTouch);
        card.btnListen.getChildAt(0).setMinimumHeight(audioTouch);
        textSize(card.pageHanzi, R.dimen.ls_flashcard_hanzi_max, R.dimen.ls_flashcard_hanzi_min);
        textSize(card.pagePinyin, R.dimen.ls_flashcard_pinyin_max, R.dimen.ls_flashcard_pinyin_min);
        textSize(card.pageEnglish, R.dimen.ls_flashcard_english_max, R.dimen.ls_flashcard_english_min);
        textSize(card.listenLabel, R.dimen.ls_flashcard_english_max, R.dimen.ls_flashcard_english_min);
        textSize(binding.stampLabel, R.dimen.ls_flashcard_english_max, R.dimen.ls_flashcard_english_min);
        textSize(binding.cardTitle, R.dimen.ls_flashcard_title_max, R.dimen.ls_flashcard_title_min);
        textSize(binding.cardCounter, R.dimen.ls_flashcard_title_max, R.dimen.ls_flashcard_title_min);
        textSize(binding.btnNextText, R.dimen.ls_flashcard_button_max, R.dimen.ls_flashcard_button_min);
    }

    void close() {
        binding.flashcardRoot.getViewTreeObserver().removeOnGlobalLayoutListener(this);
    }
}
