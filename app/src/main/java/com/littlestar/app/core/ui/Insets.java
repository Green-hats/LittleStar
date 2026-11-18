package com.littlestar.app.core.ui;

import android.app.Activity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;

/** Edge-to-edge 安全区工具。 */
public final class Insets {

    private Insets() {
    }

    /** 窗口取得焦点后，以本页主题同步系统栏，而不是沿用上一窗口的外观。 */
    public static void syncSystemBarAppearance(@NonNull Activity activity) {
        var controller = WindowCompat.getInsetsController(activity.getWindow(),
                activity.getWindow().getDecorView());
        if (!controller.isAppearanceLightStatusBars()) {
            controller.setAppearanceLightStatusBars(true);
        }
        if (!controller.isAppearanceLightNavigationBars()) {
            controller.setAppearanceLightNavigationBars(true);
        }
    }

    /** 把 systemBars 的 inset 加到根布局 padding 上（保留原 padding）。 */
    public static void applySystemBarPadding(@NonNull View root) {
        applyPadding(root, null);
    }

    public static void applyFormPadding(@NonNull View root, @NonNull View controls) {
        applyPadding(root, controls);
        controls.addOnLayoutChangeListener((view, left, top, right, bottom,
                                            oldLeft, oldTop, oldRight, oldBottom) -> {
            if (top != oldTop || bottom != oldBottom) {
                root.post(() -> revealFormControls(root, controls));
            }
        });
    }

    private static void applyPadding(@NonNull View root, @Nullable View controls) {
        int pl = root.getPaddingLeft();
        int pt = root.getPaddingTop();
        int pr = root.getPaddingRight();
        int pb = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            int bars = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();
            var safe = windowInsets.getInsets(controls == null ? bars : bars | WindowInsetsCompat.Type.ime());
            int bottom = pb + safe.bottom;
            boolean reveal = controls != null && v.getPaddingBottom() != bottom
                    && windowInsets.isVisible(WindowInsetsCompat.Type.ime());
            v.setPadding(pl + safe.left, pt + safe.top, pr + safe.right, bottom);
            if (reveal) {
                v.post(() -> revealFormControls(v, controls));
            }
            return windowInsets;
        });
    }

    private static void revealFormControls(@NonNull View root, @NonNull View controls) {
        var insets = ViewCompat.getRootWindowInsets(root);
        if (insets == null || !insets.isVisible(WindowInsetsCompat.Type.ime())) {
            return;
        }
        int[] rootLocation = new int[2];
        int[] fieldLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        controls.getLocationOnScreen(fieldLocation);
        int visibleBottom = rootLocation[1] + root.getHeight() - root.getPaddingBottom();
        int covered = fieldLocation[1] + controls.getHeight() - visibleBottom;
        if (covered > 0) {
            root.scrollBy(0, covered);
        }
    }
}
