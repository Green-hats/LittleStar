package com.littlestar.app.core.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.littlestar.app.R;

/**
 * 步进器（§8.5）：− / 数字 / +，范围可配；到边界时按钮变灰并禁用。
 * 按钮用 {@link BlockLayout}（Icon 样式），整个控件横向排列、垂直居中。
 */
public final class StepperView extends LinearLayout {

    /** 值变化回调。 */
    public interface OnValueChangeListener {
        void onValueChanged(int value);
    }

    private static final float DISABLED_ALPHA = 0.38f;

    private final BlockLayout minusButton;
    private final BlockLayout plusButton;
    private final TextView valueText;

    private int minValue;
    private int maxValue;
    private int value;
    @Nullable
    private OnValueChangeListener listener;

    public StepperView(@NonNull Context context) {
        this(context, null);
    }

    public StepperView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StepperView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.StepperView);
        try {
            minValue = a.getInt(R.styleable.StepperView_minValue, 1);
            maxValue = a.getInt(R.styleable.StepperView_maxValue, 10);
            value = a.getInt(R.styleable.StepperView_value, 3);
        } finally {
            a.recycle();
        }
        value = Math.max(minValue, Math.min(value, maxValue));

        int buttonSize = getResources().getDimensionPixelSize(R.dimen.ls_btn_icon);
        int gap = getResources().getDimensionPixelSize(R.dimen.ls_gap);

        minusButton = createButton(context, getResources().getString(R.string.ls_stepper_minus),
                getResources().getString(R.string.ls_stepper_decrease_desc));
        plusButton = createButton(context, getResources().getString(R.string.ls_stepper_plus),
                getResources().getString(R.string.ls_stepper_increase_desc));

        valueText = new TextView(context);
        valueText.setGravity(Gravity.CENTER);
        valueText.setTextAppearance(R.style.TextAppearance_LS_Title);

        LayoutParams minusLp = new LayoutParams(buttonSize, buttonSize);
        LayoutParams plusLp = new LayoutParams(buttonSize, buttonSize);
        LayoutParams textLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMargins(gap, 0, gap, 0);

        addView(minusButton, minusLp);
        addView(valueText, textLp);
        addView(plusButton, plusLp);

        minusButton.setOnClickListener(v -> setValue(value - 1));
        plusButton.setOnClickListener(v -> setValue(value + 1));

        refresh();
    }

    @NonNull
    private BlockLayout createButton(@NonNull Context context, @NonNull String label,
            @NonNull String description) {
        BlockLayout button = new BlockLayout(context, null, 0, R.style.Widget_LS_Block_Icon);
        TextView text = new TextView(context);
        text.setText(label);
        text.setGravity(Gravity.CENTER);
        text.setTextAppearance(R.style.TextAppearance_LS_Title);
        text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        button.addView(text, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        button.setContentDescription(description);
        return button;
    }

    public void setValue(int newValue) {
        int clamped = Math.max(minValue, Math.min(newValue, maxValue));
        if (clamped == value) {
            return;
        }
        value = clamped;
        refresh();
        if (listener != null) {
            listener.onValueChanged(value);
        }
    }

    public int getValue() {
        return value;
    }

    public void setRange(int min, int max) {
        minValue = min;
        maxValue = max;
        value = Math.max(min, Math.min(value, max));
        refresh();
    }

    public void setOnValueChangeListener(@Nullable OnValueChangeListener l) {
        listener = l;
    }

    private void refresh() {
        valueText.setText(String.valueOf(value));
        boolean canMinus = value > minValue;
        boolean canPlus = value < maxValue;
        minusButton.setEnabled(canMinus);
        plusButton.setEnabled(canPlus);
        // 到边界变灰
        minusButton.setAlpha(canMinus ? 1f : DISABLED_ALPHA);
        plusButton.setAlpha(canPlus ? 1f : DISABLED_ALPHA);
    }
}
