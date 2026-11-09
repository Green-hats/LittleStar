package com.littlestar.app.core.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.littlestar.app.R;

/**
 * 学会印章（§8.5）：未学 = 描边方印 + 灰色「会」字；已学 = 朱红底 + 米白「会」字。
 * {@link #setLearned(boolean, boolean)} 带动效：缩放 1.3 → 1 + 旋转 −8° → 0，240ms。
 */
public final class StampView extends View {

    private static final long STAMP_MS = 240L;
    private static final float START_SCALE = 1.3f;
    private static final float START_ROTATION = -8f;
    private static final int DEFAULT_SIZE_DP = 72;

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final String stampChar;
    private final float strokeWidth;
    private final float cornerRadius;

    private final int redColor;
    private final int onRedColor;
    private final int mutedColor;

    private boolean learned;
    private float stampFraction = 1f;   // 0 = 动画起点，1 = 落定
    @Nullable
    private ValueAnimator stampAnim;

    public StampView(@NonNull Context context) {
        this(context, null);
    }

    public StampView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StampView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.StampView);
        try {
            learned = a.getBoolean(R.styleable.StampView_learned, false);
        } finally {
            a.recycle();
        }

        stampChar = getResources().getString(R.string.ls_stamp_char);
        strokeWidth = getResources().getDimension(R.dimen.ls_stroke_block);
        cornerRadius = getResources().getDimension(R.dimen.ls_radius_chip);
        redColor = ContextCompat.getColor(context, R.color.ls_red);
        onRedColor = ContextCompat.getColor(context, R.color.ls_on_red);
        mutedColor = ContextCompat.getColor(context, R.color.ls_muted);

        textPaint.setTextAlign(Paint.Align.CENTER);
        Typeface hanzi = ResourcesCompat.getFont(context, R.font.noto_sans_sc_hanzi);
        if (hanzi != null) {
            textPaint.setTypeface(hanzi);
        }
        updateDescription();
    }

    public boolean isLearned() {
        return learned;
    }

    public void setLearned(boolean value) {
        setLearned(value, false);
    }

    /** animate=true 时播盖章动效（尊重「减少动态效果」）。 */
    public void setLearned(boolean value, boolean animate) {
        if (learned == value && stampFraction == 1f) {
            return;
        }
        learned = value;
        updateDescription();
        if (stampAnim != null) {
            stampAnim.cancel();
        }
        if (!value || !animate || !ValueAnimator.areAnimatorsEnabled()) {
            stampFraction = 1f;
            invalidate();
            return;
        }
        stampFraction = 0f;
        stampAnim = ValueAnimator.ofFloat(0f, 1f);
        stampAnim.setDuration(STAMP_MS);
        stampAnim.setInterpolator(new OvershootInterpolator());
        stampAnim.addUpdateListener(a -> {
            stampFraction = (float) a.getAnimatedValue();
            invalidate();
        });
        stampAnim.start();
    }

    private void updateDescription() {
        setContentDescription(getResources().getString(
                learned ? R.string.pr_stamp_marked_desc : R.string.pr_stamp_action_desc));
        setStateDescription(getResources().getString(
                learned ? R.string.pr_stamp_state_marked : R.string.pr_stamp_state_unmarked));
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return "android.widget.Button";
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultSize = (int) (DEFAULT_SIZE_DP * getResources().getDisplayMetrics().density);
        setMeasuredDimension(
                resolveSize(defaultSize, widthMeasureSpec),
                resolveSize(defaultSize, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float half = strokeWidth / 2f;
        rect.set(getPaddingLeft() + half, getPaddingTop() + half,
                w - getPaddingRight() - half, h - getPaddingBottom() - half);

        int save = canvas.save();
        if (learned && stampFraction < 1f) {
            float scale = START_SCALE + (1f - START_SCALE) * stampFraction;
            float rotation = START_ROTATION * (1f - stampFraction);
            canvas.scale(scale, scale, w / 2f, h / 2f);
            canvas.rotate(rotation, w / 2f, h / 2f);
        }

        if (learned) {
            boxPaint.setStyle(Paint.Style.FILL);
            boxPaint.setColor(redColor);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, boxPaint);
            textPaint.setColor(onRedColor);
        } else {
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(strokeWidth);
            boxPaint.setColor(mutedColor);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, boxPaint);
            textPaint.setColor(mutedColor);
        }

        textPaint.setTextSize(rect.height() * 0.52f);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float cy = rect.centerY() - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(stampChar, rect.centerX(), cy, textPaint);
        canvas.restoreToCount(save);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (stampAnim != null) {
            stampAnim.cancel();
        }
        super.onDetachedFromWindow();
    }
}
