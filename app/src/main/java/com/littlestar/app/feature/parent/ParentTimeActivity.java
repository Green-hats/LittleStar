package com.littlestar.app.feature.parent;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.littlestar.app.R;
import com.littlestar.app.core.ui.BlockLayout;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.core.ui.ParentActivity;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.databinding.ActivityParentTimeBinding;

/** 使用时长（§7.14 / §9.14）。选项即选即改（内存），保存才写 SettingsStore。 */
public final class ParentTimeActivity extends ParentActivity {

    private static final int[] LIMITS = {15, 30, 45, 60};

    private ActivityParentTimeBinding binding;
    private BlockLayout[] opts;
    private int selected;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityParentTimeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        opts = new BlockLayout[]{binding.opt15, binding.opt30, binding.opt45, binding.opt60};
        for (int i = 0; i < opts.length; i++) {
            int v = LIMITS[i];
            ((TextView) opts[i].getChildAt(0)).setText(getString(R.string.pt_minutes_fmt, v));
            opts[i].setOnClickListener(x -> select(v));
        }
        selected = container().settings.getDailyLimitMin();
        select(selected);
        binding.restSwitch.setChecked(container().settings.isRestReminder());

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCancel.setOnClickListener(v -> finish());
        binding.btnSave.setOnClickListener(v -> {
            container().settings.setDailyLimitMin(selected);
            container().settings.setRestReminder(binding.restSwitch.isChecked());
            container().screenTime.checkNow();
            Snackbar.make(binding.getRoot(), R.string.pt_saved, Snackbar.LENGTH_SHORT).show();
            binding.getRoot().postDelayed(this::finish, 600);
        });
        binding.btnExtra.setOnClickListener(v -> {
            container().usage.grantExtraMinutes(10);
            container().screenTime.checkNow();
            Snackbar.make(binding.getRoot(), R.string.pt_saved, Snackbar.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 「今天已达上限」才显示加时行
        container().usage.loadToday(this::renderExtraRow);
    }

    private void renderExtraRow(ProgressState.DailyUsage today) {
        int limitMin = container().settings.getDailyLimitMin();
        boolean reached = today != null
                && today.childSeconds >= limitMin * 60L
                && today.extraMinutesGranted <= 0;
        binding.extraRow.setVisibility(reached ? View.VISIBLE : View.GONE);
    }

    /** 选中 = 黄底（评审 P1-4）；勾选在视觉上由黄底承担，与选项块一致。 */
    private void select(int minutes) {
        selected = minutes;
        int yellow = ContextCompat.getColor(this, R.color.ls_yellow);
        int card = ContextCompat.getColor(this, R.color.ls_card);
        for (int i = 0; i < opts.length; i++) {
            opts[i].setFillColor(LIMITS[i] == minutes ? yellow : card);
        }
    }
}
