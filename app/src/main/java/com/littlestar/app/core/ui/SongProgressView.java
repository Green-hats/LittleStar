package com.littlestar.app.core.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.slider.Slider;

/** Material slider that commits a touch seek on release and restores playback on cancellation. */
public final class SongProgressView extends Slider {
    public interface OnSeek {
        void onSeek(long positionMs);
    }

    private long durationMs;
    private long playbackPositionMs;
    private boolean touching;
    private boolean releasing;
    @Nullable
    private OnSeek onSeek;

    public SongProgressView(@NonNull Context context) {
        this(context, null);
    }

    public SongProgressView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLabelFormatter(value -> DateUtils.formatElapsedTime(Math.round(value / 1000f)));
        addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && !touching && onSeek != null && durationMs > 0) {
                onSeek.onSeek(Math.round(value));
            }
        });
        addOnSliderTouchListener(new OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {}

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                if (releasing && onSeek != null) {
                    onSeek.onSeek(Math.round(slider.getValue()));
                }
            }
        });
    }

    public void setProgress(long position, long duration) {
        long nextDuration = Math.max(0, duration);
        if (nextDuration != durationMs) {
            cancelTouch();
            durationMs = nextDuration;
            setValueTo(Math.max(1, durationMs));
        }
        playbackPositionMs = Math.max(0, Math.min(position, durationMs));
        if (!touching) {
            setValue(playbackPositionMs);
        }
    }

    public void setOnSeek(@Nullable OnSeek listener) {
        if (listener == null) {
            cancelTouch();
        }
        onSeek = listener;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled) {
            cancelTouch();
        }
        super.setEnabled(enabled);
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelTouch();
        super.onDetachedFromWindow();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility") // Slider exposes accessibility range actions.
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (!isEnabled() || durationMs <= 0 || onSeek == null
                || getTrackWidth() <= 0 || getParent() == null) {
            cancelTouch();
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touching = true;
        } else if (!touching) {
            return false;
        }
        releasing = action == MotionEvent.ACTION_UP;
        boolean handled = super.onTouchEvent(event);
        if (releasing || action == MotionEvent.ACTION_CANCEL) {
            finishTouch(action == MotionEvent.ACTION_CANCEL);
        }
        return handled;
    }

    private void cancelTouch() {
        if (!touching) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        try {
            releasing = false;
            super.onTouchEvent(cancel);
        } finally {
            cancel.recycle();
            finishTouch(true);
        }
    }

    private void finishTouch(boolean restorePlayback) {
        touching = false;
        releasing = false;
        if (restorePlayback) {
            setValue(playbackPositionMs);
        }
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
    }
}
