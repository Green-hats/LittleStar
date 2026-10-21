package com.littlestar.app.feature.parent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;

import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.core.ui.ParentActivity;
import com.littlestar.app.databinding.ActivityParentGateBinding;
import com.littlestar.app.domain.ParentChallenge;

import java.util.Random;

/**
 * 家长验证（§7.12 / §9.12）：两位数进位加法 / 一位数乘法。
 * 答错抖动 + 清空 + 换新题，不锁定；答对 → target 并 finish 自己。
 */
public final class ParentGateActivity extends ParentActivity {

    /** 验证后跳转目标（extra）。TimeUpActivity(M7) 也引用这些常量。 */
    public static final String EXTRA_TARGET = "target";
    public static final String TARGET_DASHBOARD = "dashboard";
    public static final String TARGET_TIME = "time";

    @androidx.annotation.NonNull
    public static Intent intent(@androidx.annotation.NonNull Activity from,
                                @androidx.annotation.NonNull String target) {
        Intent i = new Intent(from, ParentGateActivity.class);
        i.putExtra(EXTRA_TARGET, target);
        return i;
    }

    private ActivityParentGateBinding binding;
    private ParentChallenge challenge;
    private ParentChallenge.Challenge current;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityParentGateBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        challenge = new ParentChallenge(new Random());
        nextQuestion();

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

    private void nextQuestion() {
        current = challenge.next();
        binding.questionText.setText(current.questionText());
    }

    private void submit() {
        String input = binding.answerInput.getText().toString();
        if (ParentChallenge.check(input, current)) {
            dispatch();
        } else {
            // 答错：抖动 + 清空 + 换新题，不锁定
            Motion.shake(binding.answerInput);
            binding.answerInput.setText("");
            nextQuestion();
        }
    }

    /** 答对 → target（默认 Dashboard），finish 自己（§5.3）。 */
    private void dispatch() {
        String target = getIntent().getStringExtra(EXTRA_TARGET);
        Class<? extends Activity> dest;
        if (TARGET_TIME.equals(target)) {
            dest = ParentTimeActivity.class;
        } else {
            dest = ParentDashboardActivity.class;
        }
        startActivity(new Intent(this, dest));
        Motion.parentFade(this);
        finish();
    }
}
