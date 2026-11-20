package com.littlestar.app.core.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

/** Uses Material button styling with LittleStar's existing tap sound and haptic feedback. */
public final class LearningButton extends MaterialButton {
    private boolean lastPressed;

    public LearningButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        boolean pressed = isClickable() && isPressed() && isEnabled();
        if (pressed && !lastPressed) {
            BlockLayout.playPressFeedback(this);
        }
        lastPressed = pressed;
    }
}
