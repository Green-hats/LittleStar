package com.littlestar.app.core.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.littlestar.app.R;

/**
 * 硬影块（工程文档 §8.4）：墨线 + 填充 + 右下硬影，全部在 dispatchDraw 里画，
 * 不用 layer-list / elevation。按下时内容下沉 {@code shadowOffset - pressRemain}，
 * 影子不动，按下后剩 2dp。
 *
 * <p>按下音效通过 {@link #setPressSoundHook(Runnable)} 静态注入（LittleStarApp.onCreate），
 * BlockLayout 不直接依赖 AppContainer。
 */
public class BlockLayout extends FrameLayout {

    private static final long PRESS_IN_MS = 100L;
    private static final long PRESS_OUT_MS = 180L;
    private static final float OVERSHOOT_TENSION = 1.2f;

    @Nullable
    private static Runnable pressSoundHook;

    /** 在 Application.onCreate 里注入按下音效（如 Sfx.TAP）。 */
    public static void setPressSoundHook(@Nullable Runnable hook) {
        pressSoundHook = hook;
    }

    @NonNull
    private ColorStateList fill;
    private int strokeColor;
    private int shadowColor;
    private float strokeWidth;
    private float shadowOffset;
    private float cornerRadius;
    private final float pressRemain;
    private final boolean pressable;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float pressFraction;          // 0 = 抬起，1 = 按下
    @Nullable
    private ValueAnimator pressAnim;

    public BlockLayout(@NonNull Context context) {
        this(context, null);
    }

    public BlockLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.blockLayoutStyle);
    }

    public BlockLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public BlockLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        int defaultStroke = resolveThemeColor(context, R.attr.lsStroke);
        int defaultShadow = resolveThemeColor(context, R.attr.lsShadow);
        pressRemain = getResources().getDimension(R.dimen.ls_press_remain);

        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.BlockLayout, defStyleAttr, defStyleRes);
        try {
            ColorStateList csl = a.getColorStateList(R.styleable.BlockLayout_ls_fill);
            fill = csl != null ? csl : ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.ls_card));
            strokeColor = a.getColor(R.styleable.BlockLayout_ls_strokeColor, defaultStroke);
            shadowColor = a.getColor(R.styleable.BlockLayout_ls_shadowColor, defaultShadow);
            strokeWidth = a.getDimension(R.styleable.BlockLayout_ls_strokeWidth,
                    getResources().getDimension(R.dimen.ls_stroke_block));
            shadowOffset = a.getDimension(R.styleable.BlockLayout_ls_shadowOffset,
                    getResources().getDimension(R.dimen.ls_shadow_block));
            cornerRadius = a.getDimension(R.styleable.BlockLayout_ls_cornerRadius,
                    getResources().getDimension(R.dimen.ls_radius_block));
            pressable = a.getBoolean(R.styleable.BlockLayout_ls_pressable, isClickable());
        } finally {
            a.recycle();
        }

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(strokeWidth);
        strokePaint.setColor(strokeColor);
        fillPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(shadowColor);

        setWillNotDraw(false);
        setClipToPadding(false);
        // 预留影子空间：内容区不包含右下角的影子
        setPadding(getPaddingLeft(), getPaddingTop(),
                getPaddingRight() + (int) shadowOffset,
                getPaddingBottom() + (int) shadowOffset);
        setElevation(0f);
    }

    private static int resolveThemeColor(@NonNull Context context, int attr) {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(attr, tv, true);
        return ContextCompat.getColor(context, tv.resourceId);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        float travel = (shadowOffset - pressRemain) * pressFraction;
        float w = getWidth() - shadowOffset;
        float h = getHeight() - shadowOffset;
        float half = strokeWidth / 2f;
        float r = cornerRadius;
        fillPaint.setColor(fill.getColorForState(getDrawableState(), fill.getDefaultColor()));
        // 1. 影子：固定在 (offset, offset)，不随按下移动
        canvas.drawRoundRect(shadowOffset, shadowOffset,
                w + shadowOffset, h + shadowOffset, r, r, shadowPaint);
        // 2. 块本体 + 子 View 整体平移 travel
        int save = canvas.save();
        canvas.translate(travel, travel);
        canvas.drawRoundRect(half, half, w - half, h - half, r, r, fillPaint);
        canvas.drawRoundRect(half, half, w - half, h - half, r, r, strokePaint);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    protected void drawableStateChanged() {
        boolean wasPressed = isPressed();
        super.drawableStateChanged();
        boolean pressed = isPressed();
        if (pressable && pressed != wasPressed) {
            if (pressed) {
                Runnable hook = pressSoundHook;
                if (hook != null) {
                    hook.run();
                }
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            animatePress(pressed ? 1f : 0f);
        }
        invalidate();   // 选中态可能改变填充色
    }

    private void animatePress(float target) {
        if (pressFraction == target) {
            return;
        }
        if (pressAnim != null) {
            pressAnim.cancel();
        }
        // 减少动态效果：直接跳变
        if (!ValueAnimator.areAnimatorsEnabled()) {
            pressFraction = target;
            invalidate();
            return;
        }
        pressAnim = ValueAnimator.ofFloat(pressFraction, target);
        pressAnim.setDuration(target == 1f ? PRESS_IN_MS : PRESS_OUT_MS);
        pressAnim.setInterpolator(target == 1f
                ? new DecelerateInterpolator()
                : new OvershootInterpolator(OVERSHOOT_TENSION));
        pressAnim.addUpdateListener(a -> {
            pressFraction = (float) a.getAnimatedValue();
            invalidate();
        });
        pressAnim.start();
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return Button.class.getName();
    }

    public void setFill(@NonNull ColorStateList colors) {
        fill = colors;
        invalidate();
    }

    public void setFillColor(int color) {
        setFill(ColorStateList.valueOf(color));
    }

    @NonNull
    public ColorStateList getFill() {
        return fill;
    }

    public void setStrokeColor(int color) {
        strokeColor = color;
        strokePaint.setColor(color);
        invalidate();
    }

    public void setShadowColor(int color) {
        shadowColor = color;
        shadowPaint.setColor(color);
        invalidate();
    }

    public void setShadowOffset(float offset) {
        shadowOffset = offset;
        requestLayout();
        invalidate();
    }

    public float getShadowOffset() {
        return shadowOffset;
    }
}
