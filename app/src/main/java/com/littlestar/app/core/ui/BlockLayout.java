package com.littlestar.app.core.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.littlestar.app.R;

/** Material surface for cards and compound controls containing child views. */
public class BlockLayout extends FrameLayout {

    @Nullable
    private static Runnable pressSoundHook;

    public static void setPressSoundHook(@Nullable Runnable hook) {
        pressSoundHook = hook;
    }

    private final MaterialShapeDrawable surface;
    private final boolean pressable;
    private boolean lastPressed;

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

        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.BlockLayout, defStyleAttr, defStyleRes);
        try {
            float radius = a.getDimension(R.styleable.BlockLayout_ls_cornerRadius,
                    getResources().getDimension(R.dimen.ls_radius_tile));
            ShapeAppearanceModel corners = ShapeAppearanceModel.builder()
                    .setAllCornerSizes(radius)
                    .build();
            surface = new MaterialShapeDrawable(corners);
            ColorStateList fill = a.getColorStateList(R.styleable.BlockLayout_ls_fill);
            surface.setFillColor(fill != null ? fill
                    : ContextCompat.getColorStateList(context, R.color.ls_card));
            surface.setStroke(a.getDimension(R.styleable.BlockLayout_ls_strokeWidth,
                            getResources().getDimension(R.dimen.ls_surface_stroke)),
                    a.getColor(R.styleable.BlockLayout_ls_strokeColor,
                            ContextCompat.getColor(context, R.color.ls_outline_variant)));

            pressable = a.getBoolean(R.styleable.BlockLayout_ls_pressable, true);
            if (pressable) {
                ColorStateList ripple = a.getColorStateList(R.styleable.BlockLayout_ls_rippleColor);
                if (ripple == null) {
                    ripple = ContextCompat.getColorStateList(context, R.color.ls_ripple);
                }
                MaterialShapeDrawable mask = new MaterialShapeDrawable(corners);
                mask.setFillColor(ContextCompat.getColorStateList(context, android.R.color.white));
                setBackground(new RippleDrawable(ripple, surface, mask));
            } else {
                setBackground(surface);
            }
        } finally {
            a.recycle();
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        boolean pressed = isClickable() && isPressed() && isEnabled();
        if (pressable && pressed && !lastPressed) {
            Runnable hook = pressSoundHook;
            if (hook != null) {
                hook.run();
            }
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        lastPressed = pressed;
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return isClickable() ? Button.class.getName() : super.getAccessibilityClassName();
    }

    public void setFillColor(int color) {
        surface.setFillColor(ColorStateList.valueOf(color));
    }
}
