package com.littlestar.app.core.ui;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.littlestar.app.AppContainer;
import com.littlestar.app.LittleStarApp;

/**
 * 家长任务配置基类：Theme.LittleStar.Parent；
 * 退到后台超过 60 秒，回来时跳回 ParentGate 重新验证。
 */
public abstract class ParentActivity extends AppCompatActivity {

    private ParentAccessViewModel access;
    private boolean restoredInstance;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        access = new ViewModelProvider(this).get(ParentAccessViewModel.class);
        restoredInstance = savedInstanceState != null;
        EdgeToEdge.enable(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (access.requiresReauth(restoredInstance, container().clock.elapsedRealtimeMillis())) {
            onReauthRequired();
        }
    }

    @Override
    protected void onStop() {
        access.onStopped(container().clock.elapsedRealtimeMillis());
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
