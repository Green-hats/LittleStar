package com.littlestar.app.feature.parent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;

import com.littlestar.app.R;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.core.ui.ParentActivity;
import com.littlestar.app.databinding.ActivityParentGateBinding;
import com.littlestar.app.feature.settings.SettingsActivity;

/**
 * 家长验证（D31：PIN 登录取代算术题）。
 * 已设 PIN → 输入验证，错了抖动 + 清空，不锁定；
 * 未设 PIN（旧数据/跳过引导）→ 首次进入创建并再次确认（4 位数字）。
 * 通过 → Settings 并 finish 自己。
 */
public final class ParentGateActivity extends ParentActivity {

    @androidx.annotation.NonNull
    public static Intent intent(@androidx.annotation.NonNull Activity from) {
        return new Intent(from, ParentGateActivity.class);
    }

    private ActivityParentGateBinding binding;
    private boolean createMode;
    // Keep the unconfirmed PIN in memory only; recreation safely restarts setup.
    private String pendingPin;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityParentGateBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        createMode = !container().settings.isPinSet();
        binding.questionText.setText(createMode
                ? R.string.pg_pin_create : R.string.pg_pin_prompt);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnConfirm.setOnClickListener(v -> submit());
        binding.answerInput.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
    }

    private void submit() {
        String input = binding.answerInput.getText().toString().trim();
        if (!input.matches("[0-9]{4}")) {
            Motion.shake(binding.answerInput);
            binding.answerInput.setText("");
            return;
        }
        if (createMode) {
            if (pendingPin == null) {
                pendingPin = input;
                binding.answerInput.setText("");
                binding.answerInput.setError(null);
                binding.questionText.setText(R.string.peer_settings_pin_confirm);
                binding.questionText.announceForAccessibility(
                        getString(R.string.peer_settings_pin_confirm));
            } else if (pendingPin.equals(input)) {
                container().settings.setParentPin(input);
                pendingPin = null;
                dispatch();
            } else {
                pendingPin = null;
                binding.questionText.setText(R.string.pg_pin_create);
                binding.answerInput.setText("");
                binding.answerInput.setError(getString(R.string.peer_settings_pin_mismatch));
                Motion.shake(binding.answerInput);
            }
        } else if (input.equals(container().settings.getParentPin())) {
            dispatch();
        } else {
            // 错了：抖动 + 清空，不锁定
            Motion.shake(binding.answerInput);
            binding.answerInput.setText("");
        }
    }

    /** 通过后只进入设置页。 */
    private void dispatch() {
        startActivity(new Intent(this, SettingsActivity.class));
        Motion.parentFade(this);
        finish();
    }
}
