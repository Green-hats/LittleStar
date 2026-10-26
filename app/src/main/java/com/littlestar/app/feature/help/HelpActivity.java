package com.littlestar.app.feature.help;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.littlestar.app.BuildConfig;
import com.littlestar.app.R;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.databinding.ActivityHelpBinding;
import com.littlestar.app.feature.home.HomeActivity;

/**
 * 帮助页（A1）：说明提醒与离线策略，提供可现场演示的测试通知。
 * 菜单使用标准 options menu，便于展示「菜单操作」。
 */
public final class HelpActivity extends ChildActivity {

    private ActivityHelpBinding binding;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    sendTestNotificationNow();
                } else {
                    Toast.makeText(this, R.string.help_notification_off, Toast.LENGTH_LONG).show();
                }
            });

    @NonNull
    public static Intent intent(@NonNull Activity from) {
        return new Intent(from, HelpActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHelpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.help_title);
        }
        binding.toolbar.setNavigationContentDescription(R.string.nav_back);

        binding.btnTestNotification.setOnClickListener(v -> sendTestNotification());
        binding.btnAbout.setOnClickListener(v -> showAbout());
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_help, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            Motion.childExit(this);
            return true;
        }
        if (id == R.id.action_test_notification) {
            sendTestNotification();
            return true;
        }
        if (id == R.id.action_about) {
            showAbout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void sendTestNotification() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        sendTestNotificationNow();
    }

    private void sendTestNotificationNow() {
        container().notifier.ensureChannels();
        container().notifier.test(new Intent(this, HomeActivity.class));
        Toast.makeText(this, R.string.help_test_sent, Toast.LENGTH_SHORT).show();
    }

    private void showAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.help_about_title)
                .setMessage(getString(R.string.help_about_message, BuildConfig.VERSION_NAME))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
