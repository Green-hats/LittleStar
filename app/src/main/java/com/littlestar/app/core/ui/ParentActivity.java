package com.littlestar.app.core.ui;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.littlestar.app.AppContainer;
import com.littlestar.app.LittleStarApp;

/**
 * 家长任务配置基类：Theme.LittleStar.Parent；
 * 退到后台超过 60 秒，回来时跳回 ParentGate 重新验证。
 */
public abstract class ParentActivity extends AppCompatActivity {

    private static final long REAUTH_AFTER_MS = 60_000;

    private long stoppedAt;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > REAUTH_AFTER_MS) {
            onReauthRequired();
        }
    }

    @Override
    protected void onStop() {
        stoppedAt = System.currentTimeMillis();
        super.onStop();
    }

    /** 后台超时后需要重新验证（§5.3）：跳回 ParentGate 并关掉当前家长页。 */
    protected void onReauthRequired() {
        startActivity(com.littlestar.app.feature.parent.ParentGateActivity.intent(this));
        finish();
    }

    protected final AppContainer container() {
        return ((LittleStarApp) getApplication()).container();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Insets.syncSystemBarAppearance(this);
        }
    }
}
