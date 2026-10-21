package com.littlestar.app.core.ui;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.littlestar.app.AppContainer;
import com.littlestar.app.LittleStarApp;

/**
 * 家长区基类：Theme.LittleStar.Parent；不计入孩子时长；
 * 退到后台超过 60 秒，回来时跳回 ParentGate 重新验证。
 */
public abstract class ParentActivity extends AppCompatActivity {

    private static final long REAUTH_AFTER_MS = 60_000;

    private long stoppedAt;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
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

    /** 后台超时后需要重新验证：由 M6 接入 ParentGate。默认只 finish。 */
    protected void onReauthRequired() {
        finish();
    }

    protected final AppContainer container() {
        return ((LittleStarApp) getApplication()).container();
    }
}
