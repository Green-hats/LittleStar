package com.littlestar.app.feature.parent;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.core.ui.ParentActivity;
import com.littlestar.app.databinding.ActivityParentRecordsBinding;
import com.littlestar.app.databinding.ItemRecordRowBinding;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 学习记录（§7.16 / §9.16）：汇总卡 + 按天列表 + Sort + 空态 + 隐私说明。 */
public final class ParentRecordsActivity extends ParentActivity {

    private ActivityParentRecordsBinding binding;
    private ParentRecordsViewModel vm;
    private RecordAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityParentRecordsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new ParentRecordsViewModel(container())))
                .get(ParentRecordsViewModel.class);

        adapter = new RecordAdapter();
        binding.recordsList.setLayoutManager(new LinearLayoutManager(this));
        binding.recordsList.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSort.setOnClickListener(v -> {
            ParentRecordsViewModel.UiState s = vm.state().getValue();
            vm.setSort(s != null && s.sort == ParentRecordsViewModel.Sort.NEWEST
                    ? ParentRecordsViewModel.Sort.OLDEST : ParentRecordsViewModel.Sort.NEWEST);
        });
        binding.btnGoHome.setOnClickListener(v -> {
            finish();
            Motion.parentFade(this);
        });

        vm.state().observe(this, this::render);
        vm.load();
    }

    private void render(ParentRecordsViewModel.UiState s) {
        if (s == null) {
            return;
        }
        binding.btnSort.setText(s.sort == ParentRecordsViewModel.Sort.NEWEST
                ? R.string.fav_sort_new : R.string.fav_sort_old);
        boolean empty = s.days.isEmpty();
        binding.recordsList.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.summaryCard.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);

        binding.weekSummary.setText(getString(R.string.pr_week_fmt, s.weekLearned, s.weekQuizCount));
        int diff = s.weekLearned - s.lastWeekLearned;
        binding.weekCompare.setText(diff > 0
                ? getString(R.string.pr_compare_more, diff)
                : diff < 0 ? getString(R.string.pr_compare_less, -diff)
                        : getString(R.string.pr_compare_same));
        adapter.setDays(s.days);
    }

    private final class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.Holder> {

        private List<ParentRecordsViewModel.DayRecord> days = new ArrayList<>();

        void setDays(@NonNull List<ParentRecordsViewModel.DayRecord> newDays) {
            days = new ArrayList<>(newDays);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemRecordRowBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            ParentRecordsViewModel.DayRecord d = days.get(position);
            String[] weekdays = getResources().getStringArray(R.array.weekday_short);
            h.b.recordDate.setText(d.date.format(DateTimeFormatter.ofPattern("M月d日"))
                    + " " + weekdays[d.date.getDayOfWeek().getValue() - 1]);
            h.b.recordDetail.setText(getString(R.string.pr_row_fmt,
                    d.learnedCount, d.quizCorrect, d.quizTotal, d.minutes));
        }

        @Override
        public int getItemCount() {
            return days.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemRecordRowBinding b;

            Holder(@NonNull ItemRecordRowBinding binding) {
                super(binding.getRoot());
                b = binding;
            }
        }
    }
}
