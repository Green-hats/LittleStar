package com.littlestar.app.feature.sleep;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.littlestar.app.AppContainer;
import com.littlestar.app.BuildConfig;
import com.littlestar.app.LittleStarApp;
import com.littlestar.app.R;
import com.littlestar.app.core.ui.BlockLayout;
import com.littlestar.app.databinding.SheetSleepTimerBinding;

import java.time.Duration;

/**
 * 睡前定时（§7.11 / §9.11）。BottomSheet，不新增 Activity。
 * 已在计时：显示剩余 + 「取消计时」。debug 构建额外 1 分钟选项（M7 验收用）。
 */
public final class SleepTimerSheet extends BottomSheetDialogFragment {

    private SheetSleepTimerBinding binding;
    private int minutes;
    private boolean finishTrack;
    private BlockLayout[] opts;
    private int[] values;

    @NonNull
    private AppContainer container() {
        return ((LittleStarApp) requireActivity().getApplication()).container();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetSleepTimerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        minutes = container().settings.getSleepDefaultMin();
        finishTrack = container().settings.isSleepFinishTrack();

        binding.opt10Text.setText(getString(R.string.st_minutes, 10));
        binding.opt20Text.setText(getString(R.string.st_minutes, 20));
        binding.opt30Text.setText(getString(R.string.st_minutes, 30));
        if (BuildConfig.DEBUG) {
            // debug 专用 1 分钟选项（M7 验收）
            binding.opt1.setVisibility(View.VISIBLE);
            opts = new BlockLayout[]{binding.opt1, binding.opt10, binding.opt20, binding.opt30};
            values = new int[]{1, 10, 20, 30};
        } else {
            opts = new BlockLayout[]{binding.opt10, binding.opt20, binding.opt30};
            values = new int[]{10, 20, 30};
        }
        for (int i = 0; i < opts.length; i++) {
            int v = values[i];
            opts[i].setOnClickListener(x -> selectMinutes(v));
        }
        selectMinutes(minutes);
        renderSwitch();

        binding.finishSwitch.setOnClickListener(v -> {
            finishTrack = !finishTrack;
            container().settings.setSleepFinishTrack(finishTrack);
            renderSwitch();
        });
        binding.btnStart.setOnClickListener(v -> {
            container().settings.setSleepDefaultMin(minutes);
            container().sleepTimer.start(minutes, finishTrack);
            dismiss();
        });
        binding.btnCancelTimer.setOnClickListener(v -> {
            container().sleepTimer.cancel();
            dismiss();
        });
        binding.btnCancel.setOnClickListener(v -> dismiss());

        // 已在计时 → 只显示剩余 + 取消计时
        renderMode();
        container().sleepTimer.remaining().observe(getViewLifecycleOwner(), left -> renderMode());
    }

    private void renderMode() {
        boolean running = container().sleepTimer.isRunning();
        binding.setupArea.setVisibility(running ? View.GONE : View.VISIBLE);
        binding.runningArea.setVisibility(running ? View.VISIBLE : View.GONE);
        if (running) {
            Duration left = container().sleepTimer.remaining().getValue();
            if (left != null) {
                binding.remainingText.setText(getString(R.string.st_running,
                        left.toMinutes(), left.toSecondsPart()));
            }
        }
    }

    /** 选中 = 黄底；未选 = 卡片底。 */
    private void selectMinutes(int v) {
        minutes = v;
        int yellow = ContextCompat.getColor(requireContext(), R.color.ls_yellow);
        int card = ContextCompat.getColor(requireContext(), R.color.ls_card);
        for (int i = 0; i < opts.length; i++) {
            opts[i].setFillColor(values[i] == v ? yellow : card);
        }
    }

    /** 开关：开 = 绿轨道 + 圆点在右；关 = 灰轨道 + 圆点在左。 */
    private void renderSwitch() {
        binding.finishSwitch.setBackgroundResource(
                finishTrack ? R.drawable.bg_switch_on : R.drawable.bg_switch_off);
        View knob = binding.finishKnob;
        float travel = getResources().getDimension(R.dimen.ls_switch_travel);
        knob.setTranslationX(finishTrack ? travel : 0);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
