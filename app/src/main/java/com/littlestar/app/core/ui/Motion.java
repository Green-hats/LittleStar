package com.littlestar.app.core.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;

import com.littlestar.app.R;

/** 动效集合（§8.6）。全部先检查 areAnimatorsEnabled，关闭时改为淡入或直接切换。 */
public final class Motion {

    private Motion() {
    }

    public static boolean animatorsEnabled() {
        return ValueAnimator.areAnimatorsEnabled();
    }

    /** 答错抖动：translationX 0, −4, 4, −4, 4, 0（dp），200ms。 */
    public static void shake(@NonNull View v) {
        if (!animatorsEnabled()) {
            return;
        }
        float d = v.getResources().getDisplayMetrics().density * 4f;
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, -d, d, -d, d, 0f)
                .setDuration(200)
                .start();
    }

    /** 勾图标 scale 0 → 1，300ms。 */
    public static void popIn(@NonNull View v) {
        if (!animatorsEnabled()) {
            v.setVisibility(View.VISIBLE);
            return;
        }
        v.setVisibility(View.VISIBLE);
        v.setScaleX(0f);
        v.setScaleY(0f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(300)
                .setInterpolator(new OvershootInterpolator()).start();
    }

    /** 盖章：缩放 1.3 → 1 + 旋转 −8° → 0，240ms。 */
    public static void stamp(@NonNull View v) {
        if (!animatorsEnabled()) {
            return;
        }
        v.setScaleX(1.3f);
        v.setScaleY(1.3f);
        v.setRotation(-8f);
        v.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(240)
                .setInterpolator(new OvershootInterpolator(1.2f)).start();
    }

    /** 从下方滑入（反馈条 + NEXT）。 */
    public static void slideUpIn(@NonNull View v) {
        if (!animatorsEnabled()) {
            v.setVisibility(View.VISIBLE);
            return;
        }
        v.setVisibility(View.VISIBLE);
        v.setTranslationY(v.getResources().getDisplayMetrics().density * 48f);
        v.setAlpha(0f);
        v.animate().translationY(0f).alpha(1f).setDuration(220).start();
    }

    /** 孩子区页面切换：右进左出 260ms。 */
    public static void childEnter(@NonNull Activity activity) {
        activity.overridePendingTransition(R.anim.ls_slide_in_right, R.anim.ls_slide_out_left);
    }

    /** 孩子区返回：左进右出。 */
    public static void childExit(@NonNull Activity activity) {
        activity.overridePendingTransition(R.anim.ls_slide_in_left, R.anim.ls_slide_out_right);
    }

    /** 家长区：淡入 180ms。 */
    public static void parentFade(@NonNull Activity activity) {
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
