package com.littlestar.app.core.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.littlestar.app.R;

/**
 * 朗读中的 3 根声波条（§8.5）。{@link #setPlaying(boolean)} 驱动动画；
 * 「减少动态效果」开启时显示静态中等高度。
 */
public final class SoundWaveView extends View {

    private static final int BAR_COUNT = 3;
    private static final long CYCLE_MS = 660L;
    private static final int DEFAULT_WIDTH_DP = 28;
    private static final int DEFAULT_HEIGHT_DP = 24;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean playing;
    private float phase;
    @Nullable
    private ValueAnimator waveAnim;

    public SoundWaveView(@NonNull Context context) {
        this(context, null);
    }

    public SoundWaveView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SoundWaveView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        int color;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SoundWaveView);
        try {
            color = a.getColor(R.styleable.SoundWaveView_barColor,
                    ContextCompat.getColor(context, R.color.ls_blue));
        } finally {
            a.recycle();
        }
        paint.setColor(color);
        setContentDescription(getResources().getString(R.string.ls_speaking_desc));
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean value) {
        if (playing == value) {
            return;
        }
        playing = value;
        if (value && ValueAnimator.areAnimatorsEnabled()) {
            startWave();
        } else {
            stopWave();
            invalidate();
        }
    }

    public void setBarColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    private void startWave() {
        stopWave();
        waveAnim = ValueAnimator.ofFloat(0f, 1f);
        waveAnim.setDuration(CYCLE_MS);
        waveAnim.setRepeatCount(ValueAnimator.INFINITE);
        waveAnim.setInterpolator(new LinearInterpolator());
        waveAnim.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        waveAnim.start();
    }

    private void stopWave() {
        if (waveAnim != null) {
            waveAnim.cancel();
            waveAnim = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        setMeasuredDimension(
                resolveSize((int) (DEFAULT_WIDTH_DP * density), widthMeasureSpec),
                resolveSize((int) (DEFAULT_HEIGHT_DP * density), heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float left = getPaddingLeft();
        float top = getPaddingTop();
        float right = getWidth() - getPaddingRight();
        float bottom = getHeight() - getPaddingBottom();
        float gap = (right - left) / (BAR_COUNT * 2f);
        float barWidth = gap;
        float centerY = (top + bottom) / 2f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float fraction;
            if (playing && waveAnim != null) {
                // 三根条错相 1/3 周期
                float local = (phase + i / (float) BAR_COUNT) % 1f;
                fraction = 0.25f + 0.75f * Math.abs((float) Math.sin(local * Math.PI));
            } else {
                fraction = 0.45f;
            }
            float half = (bottom - top) * fraction / 2f;
            float l = left + i * (barWidth + gap);
            canvas.drawRoundRect(l, centerY - half, l + barWidth, centerY + half,
                    barWidth / 2f, barWidth / 2f, paint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopWave();
        super.onDetachedFromWindow();
    }
}
