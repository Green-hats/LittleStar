package com.littlestar.app.feature.timeup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.littlestar.app.LittleStarApp;
import com.littlestar.app.R;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.databinding.ActivityTimeUpBinding;

/**
 * 休息页（§9.17）：
 * REST —— 20 秒倒计时，期间「好的」不可点、屏蔽返回；结束 finish 回原页。
 * LIMIT —— 没有倒计时；「好的」= finishAffinity（不拦截返回键，返回键等价「好的」）；
 *          底部「家长」行 → ParentGate(target=ParentTime)。
 * 都不计入孩子时长（不继承 ChildActivity）。
 */
public final class TimeUpActivity extends AppCompatActivity {

    public enum Kind {REST, LIMIT}

    private static final String EXTRA_KIND = "kind";
    private static final long REST_SECONDS = 20;

    public static void start(@NonNull Context context, @NonNull Kind kind) {
        Intent intent = new Intent(context, TimeUpActivity.class);
        intent.putExtra(EXTRA_KIND, kind.name());
        if (kind == Kind.LIMIT) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
        context.startActivity(intent);
    }

    private ActivityTimeUpBinding binding;
    private Kind kind = Kind.REST;
    @Nullable
    private CountDownTimer timer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        kind = Kind.valueOf(getIntent().getStringExtra(EXTRA_KIND) == null
                ? Kind.REST.name() : getIntent().getStringExtra(EXTRA_KIND));
        binding = ActivityTimeUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        // 休息 / 到限时暂停播放（§9.17）
        startService(new Intent(this, com.littlestar.app.playback.PlaybackService.class)
                .setAction(com.littlestar.app.playback.PlaybackService.ACTION_PAUSE));

        if (kind == Kind.REST) {
            setupRest();
        } else {
            setupLimit();
        }
    }

    private void setupRest() {
        binding.title.setText(R.string.timeup_rest_title);
        binding.subtitle.setVisibility(View.VISIBLE);
        binding.countdown.setVisibility(View.VISIBLE);
        binding.parentEntry.setVisibility(View.GONE);
        binding.btnOk.setEnabled(false);

        // 倒计时期间屏蔽返回
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 倒计时期间不响应返回
            }
        });

        int usedMin = 0;
        // 读今日已用分钟（异步；到时再填副标题）
        ((LittleStarApp) getApplication()).container().usage.loadToday(u ->
                binding.subtitle.setText(getString(R.string.timeup_rest_subtitle, u.childSeconds / 60)));

        timer = new CountDownTimer(REST_SECONDS * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                binding.countdownText.setText(
                        getString(R.string.timeup_rest_countdown, millisUntilFinished / 1000 + 1));
            }

            @Override
            public void onFinish() {
                binding.countdownText.setText(R.string.timeup_rest_done);
                binding.btnOk.setEnabled(true);
            }
        }.start();

        binding.btnOk.setOnClickListener(v -> finish());
    }

    private void setupLimit() {
        binding.title.setText(R.string.timeup_limit_title);
        binding.subtitle.setVisibility(View.GONE);
        binding.countdown.setVisibility(View.GONE);
        binding.parentEntry.setVisibility(View.VISIBLE);
        binding.btnOk.setEnabled(true);

        // 返回键等价「好的」：不拦截，把孩子困住是不允许的（§5.3）
        binding.btnOk.setOnClickListener(v -> finishAffinity());
        binding.parentEntry.setOnClickListener(v -> {
            Intent gate = new Intent(this,
                    com.littlestar.app.feature.parent.ParentGateActivity.class);
            gate.putExtra(com.littlestar.app.feature.parent.ParentGateActivity.EXTRA_TARGET,
                    com.littlestar.app.feature.parent.ParentGateActivity.TARGET_TIME);
            startActivity(gate);
        });
    }

    @Override
    protected void onDestroy() {
        if (timer != null) {
            timer.cancel();
        }
        super.onDestroy();
    }
}
