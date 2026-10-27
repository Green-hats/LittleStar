package com.littlestar.app.core.ui;

import android.view.View;
import android.content.res.Configuration;
import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;

/** Edge-to-edge 安全区工具。 */
public final class Insets {

    private Insets() {
    }

    /** 窗口取得焦点后，以本页主题同步系统栏，而不是沿用上一窗口的外观。 */
    public static void syncSystemBarAppearance(@NonNull Activity activity) {
        boolean dark = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        var controller = WindowCompat.getInsetsController(activity.getWindow(),
                activity.getWindow().getDecorView());
        if (controller.isAppearanceLightStatusBars() == dark) {
            controller.setAppearanceLightStatusBars(!dark);
        }
        if (controller.isAppearanceLightNavigationBars() == dark) {
            controller.setAppearanceLightNavigationBars(!dark);
        }
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
