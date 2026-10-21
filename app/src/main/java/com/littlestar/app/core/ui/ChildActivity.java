package com.littlestar.app.core.ui;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.littlestar.app.AppContainer;
import com.littlestar.app.LittleStarApp;

/**
 * 孩子区基类：edge-to-edge + 计入孩子时长。
 * 子类 setContentView 后调用 Insets.applySystemBarPadding(root)。
 */
public abstract class ChildActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        container().onChildResumed(this);
    }

    @Override
    protected void onPause() {
        container().onChildPaused();
        super.onPause();
    }

    /** 会话中（Flashcard / Quiz）返回 true：到时不打断，等奖励页结束再弹。跨包访问故为 public。 */
    public boolean deferTimeUp() {
        return false;
    }

    protected final AppContainer container() {
        return ((LittleStarApp) getApplication()).container();
    }
}
