package com.littlestar.app.core.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.littlestar.app.R;

/**
 * 播放器进度条（§9.9）：10dp 粗轨道（track 色）+ 已播蓝色 + 黄色方块拖柄（墨线）。
 * 拖动用回调通知，由 PlayerActivity 调 MediaController.seekTo。
 */
public final class SongProgressView extends View {

    /** 拖动回调。 */
    public interface OnSeek {
        void onSeek(long positionMs);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private long positionMs;
    private long durationMs;
    private long playbackPositionMs;
    private boolean dragging;
    @Nullable
    private OnSeek onSeek;

    public SongProgressView(@NonNull Context context) {
        this(context, null);
    }

    public SongProgressView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SongProgressView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.ls_track));
        fillPaint.setColor(ContextCompat.getColor(context, R.color.ls_blue));
        thumbPaint.setColor(ContextCompat.getColor(context, R.color.ls_yellow));
        thumbStroke.setColor(ContextCompat.getColor(context, R.color.ls_stroke));
        thumbStroke.setStyle(Paint.Style.STROKE);
        thumbStroke.setStrokeWidth(
                getResources().getDimensionPixelSize(R.dimen.ls_stroke_chip));
    }

    public void setProgress(long position, long duration) {
        long nextDuration = Math.max(0, duration);
        playbackPositionMs = Math.max(0, Math.min(position, nextDuration));
        if (dragging && durationMs != nextDuration) {
            cancelDrag();
        }
        durationMs = nextDuration;
        if (!dragging) {
            positionMs = playbackPositionMs;
            invalidate();
        }
    }

    public void setOnSeek(@Nullable OnSeek listener) {
        onSeek = listener;
        if (listener == null) {
            cancelDrag();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = Math.max(getResources().getDimensionPixelSize(R.dimen.ls_btn_icon),
                getResources().getDimensionPixelSize(R.dimen.ls_thumb) * 2);
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float thumb = getResources().getDimension(R.dimen.ls_thumb);
        float trackH = getResources().getDimension(R.dimen.ls_track_height);
        float cy = getHeight() / 2f;
        float left = thumb / 2f;
        float right = getWidth() - thumb / 2f;
        float frac = Math.max(0f, Math.min(1f, (float) positionMs / (float) Math.max(1, durationMs)));
        float cx = left + (right - left) * frac;

        // 轨道
        rect.set(left, cy - trackH / 2f, right, cy + trackH / 2f);
        canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, trackPaint);
        // 已播
        rect.set(left, cy - trackH / 2f, cx, cy + trackH / 2f);
        canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, fillPaint);
        // 方块拖柄
        rect.set(cx - thumb / 2f, cy - thumb / 2f, cx + thumb / 2f, cy + thumb / 2f);
        canvas.drawRoundRect(rect, thumb * 0.25f, thumb * 0.25f, thumbPaint);
        canvas.drawRoundRect(rect, thumb * 0.25f, thumb * 0.25f, thumbStroke);
    }

    private void releaseParent() {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
    }

    private void cancelDrag() {
        if (dragging) {
            dragging = false;
            positionMs = playbackPositionMs;
            releaseParent();
            invalidate();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            cancelDrag();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelDrag();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (!isEnabled() || durationMs <= 0 || onSeek == null
                || getWidth() <= getResources().getDimension(R.dimen.ls_thumb)) {
            cancelDrag();
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                dragging = true;
                updateFromX(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) {
                    return false;
                }
                updateFromX(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    return false;
                }
                updateFromX(event.getX());
                dragging = false;
                releaseParent();
                onSeek.onSeek(positionMs);
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelDrag();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void updateFromX(float x) {
        float thumb = getResources().getDimension(R.dimen.ls_thumb);
        float left = thumb / 2f;
        float right = getWidth() - thumb / 2f;
        float frac = Math.max(0f, Math.min(1f, (x - left) / (right - left)));
        positionMs = (long) (frac * durationMs);
        invalidate();
    }
}
