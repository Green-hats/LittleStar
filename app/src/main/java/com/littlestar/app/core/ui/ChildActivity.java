package com.littlestar.app.core.ui;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.littlestar.app.AppContainer;
import com.littlestar.app.LittleStarApp;

/**
 * 孩子区基类：edge-to-edge 与容器访问。
 * 子类 setContentView 后调用 Insets.applySystemBarPadding(root)。
 */
public abstract class ChildActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // AppCompat 先应用本页深浅色配置，再选择系统栏图标颜色。
        EdgeToEdge.enable(this);
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
