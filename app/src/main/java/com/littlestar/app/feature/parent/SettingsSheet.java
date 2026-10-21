package com.littlestar.app.feature.parent;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.littlestar.app.AppContainer;
import com.littlestar.app.BuildConfig;
import com.littlestar.app.LittleStarApp;
import com.littlestar.app.R;
import com.littlestar.app.databinding.SheetParentSettingsBinding;

/** 家长设置（§7.25 / §9.13）：BottomSheet。重置用 MaterialAlertDialogBuilder 二次确认。 */
public final class SettingsSheet extends BottomSheetDialogFragment {

    private SheetParentSettingsBinding binding;

    @NonNull
    private AppContainer container() {
        return ((LittleStarApp) requireActivity().getApplication()).container();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetParentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.soundSwitch.setChecked(container().settings.isSoundEnabled());
        binding.englishSwitch.setChecked(container().settings.isEnglishHint());
        binding.soundSwitch.setOnCheckedChangeListener((b, on) ->
                container().settings.setSoundEnabled(on));
        binding.englishSwitch.setOnCheckedChangeListener((b, on) ->
                container().settings.setEnglishHint(on));
        binding.versionText.setText(getString(R.string.ss_version_fmt, BuildConfig.VERSION_NAME));

        binding.resetRow.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ss_reset_title)
                .setMessage(R.string.ss_reset_msg)
                .setPositiveButton(R.string.ss_reset_confirm, (d, which) -> resetProgress())
                .setNegativeButton(R.string.ss_cancel, null)
                .show());
    }

    /** 清空全部学习数据（含星星）并落盘。 */
    private void resetProgress() {
        container().store.update(state -> {
            state.words.clear();
            state.sessions.clear();
            state.dailyPlans.clear();
            state.usage.clear();
            state.favourites.clear();
            state.nextSessionId = 1;
        });
        container().store.flushNow();
        dismiss();
        View root = requireActivity().findViewById(android.R.id.content);
        Snackbar.make(root, R.string.ss_reset_done, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
