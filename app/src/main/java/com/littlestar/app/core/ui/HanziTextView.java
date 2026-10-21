package com.littlestar.app.core.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.res.ResourcesCompat;

import com.littlestar.app.R;

import java.util.Locale;

/**
 * 教学汉字 TextView（§8.5）：固定 textLocale=zh-CN（保证中文字形，不会回退到日文字形）、
 * 打包的汉字字体、includeFontPadding=false。
 */
public final class HanziTextView extends AppCompatTextView {

    public HanziTextView(@NonNull Context context) {
        this(context, null);
    }

    public HanziTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HanziTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setTextLocale(Locale.SIMPLIFIED_CHINESE);
        setIncludeFontPadding(false);
        Typeface hanzi = ResourcesCompat.getFont(context, R.font.noto_sans_sc_hanzi);
        if (hanzi != null) {
            setTypeface(hanzi);
        }
    }
}
