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
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
    }

    protected final AppContainer container() {
        return ((LittleStarApp) getApplication()).container();
    }
}
