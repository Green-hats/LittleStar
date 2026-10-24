package com.littlestar.app.feature.debug;

import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.littlestar.app.R;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.databinding.ActivityComponentGalleryBinding;

/**
 * M1 组件画廊（仅 debug 构建）：一页展示全部组件。
 * 顶部三个切换：浅色 / 深色 / 1.3 倍字体（1.3 倍只作用于本页）。
 * 另有一个自动演示按下态的硬影块。
 */
public final class ComponentGalleryActivity extends AppCompatActivity {

    private static final long PRESS_DEMO_MS = 1200L;
    private static final float LARGE_FONT_SCALE = 1.3f;

    /** 1.3 倍字体开关（仅 gallery 进程内有效）。 */
    private static boolean largeFont;

    private static final int[] ICONS = {
            R.drawable.ic_back, R.drawable.ic_chevron, R.drawable.ic_play, R.drawable.ic_pause,
            R.drawable.ic_prev, R.drawable.ic_next, R.drawable.ic_speaker,
            R.drawable.ic_check, R.drawable.ic_close, R.drawable.ic_lock,
            R.drawable.ic_clock, R.drawable.ic_calendar, R.drawable.ic_history,
            R.drawable.ic_quiz, R.drawable.ic_music, R.drawable.ic_book, R.drawable.ic_scroll
    };
    private static final String[] ICON_NAMES = {
            "back", "chevron", "play", "pause", "prev", "next", "speaker",
            "check", "close", "lock", "clock", "calendar", "history",
            "quiz", "music", "book", "scroll"
    };
    private static final int ICONS_PER_ROW = 5;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean demoPressed;
    private final Runnable pressDemo = new Runnable() {
        @Override
        public void run() {
            demoPressed = !demoPressed;
            if (binding != null) {
                binding.demoPressBlock.setPressed(demoPressed);
            }
            handler.postDelayed(this, PRESS_DEMO_MS);
        }
    };

    @Nullable
    private ActivityComponentGalleryBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (largeFont) {
            // 只对本页模拟 1.3 倍字体
            Configuration config = new Configuration(getResources().getConfiguration());
            config.fontScale = LARGE_FONT_SCALE;
            applyOverrideConfiguration(config);
        }
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityComponentGalleryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        binding.btnLight.setOnClickListener(
                v -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO));
        binding.btnDark.setOnClickListener(
                v -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES));
        binding.btnFont.setOnClickListener(v -> {
            largeFont = !largeFont;
            recreate();
        });

        binding.soundWave.setPlaying(true);
        binding.barChart.setData(new int[]{5, 12, 0, 8, 20, 3, 15});
        binding.stampLearned.setOnClickListener(
                v -> binding.stampLearned.setLearned(true, true));

        buildIconGrid();
        handler.postDelayed(pressDemo, PRESS_DEMO_MS);
    }

    /** 20 个图标按 5 列排进 iconGrid，每个图标下标注名字。 */
    private void buildIconGrid() {
        ActivityComponentGalleryBinding b = binding;
        if (b == null) {
            return;
        }
        int iconSize = getResources().getDimensionPixelSize(R.dimen.ls_icon);
        int gap = getResources().getDimensionPixelSize(R.dimen.ls_gap_sm);
        for (int row = 0; row < (ICONS.length + ICONS_PER_ROW - 1) / ICONS_PER_ROW; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < ICONS_PER_ROW; col++) {
                int index = row * ICONS_PER_ROW + col;
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                if (index < ICONS.length) {
                    ImageView icon = new ImageView(this);
                    icon.setImageResource(ICONS[index]);
                    icon.setContentDescription(ICON_NAMES[index]);
                    LinearLayout.LayoutParams iconLp =
                            new LinearLayout.LayoutParams(iconSize, iconSize);
                    cell.addView(icon, iconLp);
                    TextView name = new TextView(this);
                    name.setText(ICON_NAMES[index]);
                    name.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                            captionTextSize());
                    name.setTextColor(captionColor());
                    name.setTypeface(Typeface.DEFAULT);
                    cell.addView(name);
                }
                LinearLayout.LayoutParams cellLp =
                        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                cellLp.setMargins(0, 0, 0, gap);
                rowLayout.addView(cell, cellLp);
            }
            b.iconGrid.addView(rowLayout);
        }
    }

    private float captionTextSize() {
        android.content.res.TypedArray a = obtainStyledAttributes(
                R.style.TextAppearance_LS_Caption, new int[]{android.R.attr.textSize});
        try {
            return a.getDimension(0, 12f * getResources().getDisplayMetrics().scaledDensity);
        } finally {
            a.recycle();
        }
    }

    private int captionColor() {
        return androidx.core.content.ContextCompat.getColor(this, R.color.ls_muted);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        binding = null;
        super.onDestroy();
    }
}
