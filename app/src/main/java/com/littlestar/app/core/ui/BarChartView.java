package com.littlestar.app.core.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.littlestar.app.R;

import java.util.Locale;

/**
 * 最近 7 天分钟数柱状图（§8.5）：今天用 ls_red，其余 ls_ink 40% 透明度；
 * 柱顶写分钟数。Canvas 绘制，不引图表库。数据最后一项是今天。
 */
public final class BarChartView extends View {

    private static final int DEFAULT_DAYS = 7;
    private static final float OTHER_ALPHA = 0.4f;
    private static final int DEFAULT_HEIGHT_DP = 120;
    private static final float MIN_BAR_FRACTION = 0.04f;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final int todayColor;
    private final int otherColor;
    private final float textHeight;
    private final float gap;

    private int days = DEFAULT_DAYS;
    @NonNull
    private int[] minutes = new int[0];

    public BarChartView(@NonNull Context context) {
        this(context, null);
    }

    public BarChartView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BarChartView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BarChartView);
        try {
            days = Math.max(1, a.getInt(R.styleable.BarChartView_days, DEFAULT_DAYS));
        } finally {
            a.recycle();
        }

        todayColor = ContextCompat.getColor(context, R.color.ls_red);
        otherColor = ContextCompat.getColor(context, R.color.ls_ink);
        gap = getResources().getDimension(R.dimen.ls_gap_sm);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(ContextCompat.getColor(context, R.color.ls_muted));
        // 字号跟随 TextAppearance.LS.Caption，不写死
        TypedArray ta = context.obtainStyledAttributes(
                R.style.TextAppearance_LS_Caption, new int[]{android.R.attr.textSize});
        try {
            textPaint.setTextSize(ta.getDimension(0,
                    getResources().getDisplayMetrics().scaledDensity * 12f));
        } finally {
            ta.recycle();
        }
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        textHeight = fm.descent - fm.ascent;

        setContentDescription(getResources().getString(R.string.ls_bar_chart_desc));
    }

    /** 传 7 个值，最后一项是今天；不足或超出会被裁剪 / 补零。 */
    public void setData(@Nullable int[] values) {
        int[] copy = new int[days];
        if (values != null) {
            for (int i = 0; i < days; i++) {
                int srcIndex = values.length - days + i;
                copy[i] = srcIndex >= 0 ? Math.max(0, values[srcIndex]) : 0;
            }
        }
        minutes = copy;
        invalidate();
    }

    @NonNull
    public int[] getData() {
        return minutes.clone();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int defaultHeight = (int) (DEFAULT_HEIGHT_DP * getResources().getDisplayMetrics().density);
        int height = resolveSize(defaultHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int n = days;
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float top = getPaddingTop();
        float bottom = getHeight() - getPaddingBottom();

        int max = 1;
        for (int v : minutes) {
            max = Math.max(max, v);
        }

        float barWidth = (right - left - gap * (n - 1)) / n;
        float barTopLimit = top + textHeight + gap / 2f;
        float barAreaHeight = bottom - barTopLimit;
        float radius = Math.min(barWidth / 2f, gap);

        for (int i = 0; i < n; i++) {
            int value = i < minutes.length ? minutes[i] : 0;
            float fraction = value == 0 ? MIN_BAR_FRACTION
                    : Math.max(MIN_BAR_FRACTION, (float) value / max);
            float l = left + i * (barWidth + gap);
            float barTop = bottom - barAreaHeight * fraction;
            boolean today = i == n - 1;
            if (today) {
                barPaint.setColor(todayColor);
                barPaint.setAlpha(255);
            } else {
                barPaint.setColor(otherColor);
                barPaint.setAlpha((int) (255 * OTHER_ALPHA));
            }
            rect.set(l, barTop, l + barWidth, bottom);
            canvas.drawRoundRect(rect, radius, radius, barPaint);

            // 柱顶分钟数
            float textY = barTop - gap / 2f - textPaint.getFontMetrics().descent;
            canvas.drawText(String.format(Locale.ROOT, "%d", value),
                    l + barWidth / 2f, textY, textPaint);
        }
    }
}
