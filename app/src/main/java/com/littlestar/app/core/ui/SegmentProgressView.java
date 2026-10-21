package com.littlestar.app.core.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.littlestar.app.R;

/**
 * 分段进度条（§8.5）：段宽撑满可用宽度；段数 > 8 时退化为单条进度条。
 * contentDescription 自动设为「已学 k / n」。
 */
public final class SegmentProgressView extends View {

    private static final int MAX_SEGMENTS = 8;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int segments;
    private int filled;
    private int fillColor;
    private int trackColor;
    private final float gap;
    private final float defaultHeight;

    public SegmentProgressView(@NonNull Context context) {
        this(context, null);
    }

    public SegmentProgressView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SegmentProgressView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        gap = getResources().getDimension(R.dimen.ls_gap_sm);
        defaultHeight = getResources().getDimension(R.dimen.ls_gap_sm);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SegmentProgressView);
        try {
            segments = Math.max(1, a.getInt(R.styleable.SegmentProgressView_segments, 6));
            filled = a.getInt(R.styleable.SegmentProgressView_filled, 0);
            fillColor = a.getColor(R.styleable.SegmentProgressView_fillColor,
                    ContextCompat.getColor(context, R.color.ls_red));
            trackColor = a.getColor(R.styleable.SegmentProgressView_trackColor,
                    ContextCompat.getColor(context, R.color.ls_track));
        } finally {
            a.recycle();
        }
        updateDescription();
    }

    public void setSegments(int count) {
        segments = Math.max(1, count);
        updateDescription();
        invalidate();
        requestLayout();
    }

    public void setFilled(int count) {
        filled = Math.max(0, Math.min(count, segments));
        updateDescription();
        invalidate();
    }

    public int getSegments() {
        return segments;
    }

    public int getFilled() {
        return filled;
    }

    public void setFillColor(int color) {
        fillColor = color;
        invalidate();
    }

    public void setTrackColor(int color) {
        trackColor = color;
        invalidate();
    }

    private void updateDescription() {
        setContentDescription(getContext().getString(
                R.string.ls_seg_progress_desc, filled, segments));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int height;
        if (heightMode == MeasureSpec.EXACTLY) {
            height = MeasureSpec.getSize(heightMeasureSpec);
        } else {
            height = (int) (defaultHeight + getPaddingTop() + getPaddingBottom());
            if (heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
            }
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float top = getPaddingTop();
        float bottom = getHeight() - getPaddingBottom();
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float radius = (bottom - top) / 2f;

        if (segments > MAX_SEGMENTS) {
            // 退化为单条进度条
            paint.setColor(trackColor);
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
            float fraction = segments == 0 ? 0f : (float) filled / segments;
            if (fraction > 0f) {
                paint.setColor(fillColor);
                canvas.drawRoundRect(left, top,
                        left + (right - left) * fraction, bottom, radius, radius, paint);
            }
            return;
        }

        float segWidth = (right - left - gap * (segments - 1)) / segments;
        for (int i = 0; i < segments; i++) {
            paint.setColor(i < filled ? fillColor : trackColor);
            float l = left + i * (segWidth + gap);
            canvas.drawRoundRect(l, top, l + segWidth, bottom, radius, radius, paint);
        }
    }
}
