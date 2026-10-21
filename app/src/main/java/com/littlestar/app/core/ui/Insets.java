package com.littlestar.app.core.ui;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Edge-to-edge 安全区工具。 */
public final class Insets {

    private Insets() {
    }

    /** 把 systemBars 的 inset 加到根布局 padding 上（保留原 padding）。 */
    public static void applySystemBarPadding(@NonNull View root) {
        int pl = root.getPaddingLeft();
        int pt = root.getPaddingTop();
        int pr = root.getPaddingRight();
        int pb = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            androidx.core.graphics.Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(pl + bars.left, pt + bars.top, pr + bars.right, pb + bars.bottom);
            return windowInsets;
        });
    }
}
