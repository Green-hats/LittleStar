package com.littlestar.app.feature.culture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.data.content.model.CultureItem;
import com.littlestar.app.databinding.ActivityCultureListBinding;
import com.littlestar.app.databinding.ItemCultureRowBinding;

import java.util.ArrayList;
import java.util.List;

/** 文化列表：选择内容后先查看简介和学习重点，再进入播放器。 */
public final class CultureListActivity extends ChildActivity {

    private static final String EXTRA_CATEGORY_ID = "categoryId";

    @NonNull
    public static Intent intent(@NonNull Activity from, @NonNull String categoryId) {
        Intent i = new Intent(from, CultureListActivity.class);
        i.putExtra(EXTRA_CATEGORY_ID, categoryId);
        return i;
    }

    private ActivityCultureListBinding binding;
    private CultureListViewModel vm;
    private RowAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCultureListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new CultureListViewModel(container())))
                .get(CultureListViewModel.class);

        adapter = new RowAdapter();
        binding.itemList.setLayoutManager(new LinearLayoutManager(this));
        binding.itemList.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> {
            finish();
            Motion.childExit(this);
        });
        vm.state().observe(this, this::render);
        container().content.load(container().executors,
                () -> vm.init(getIntent().getStringExtra(EXTRA_CATEGORY_ID)));
    }

    private void render(CultureListViewModel.UiState s) {
        if (s == null) {
            return;
        }
        binding.categoryGlyph.setText(s.category.glyph);
        binding.categoryName.setText(s.category.nameZh);
        binding.categoryCount.setText(
                getString(R.string.cul_list_header_fmt, s.category.nameEn, s.items.size()));
        adapter.setItems(s.items);
    }

    private void openDetail(int index) {
        CultureListViewModel.UiState s = vm.state().getValue();
        if (s == null || index < 0 || index >= s.items.size()) {
            return;
        }
        startActivity(CultureDetailActivity.intent(this, s.items.get(index).id));
        Motion.childEnter(this);
    }

    private final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {

        private List<CultureItem> items = new ArrayList<>();

        void setItems(@NonNull List<CultureItem> newItems) {
            items = new ArrayList<>(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemCultureRowBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            CultureItem item = items.get(position);
            h.b.rowGlyph.setText(item.glyph);
            h.b.rowTitle.setText(item.titleZh);
            h.b.rowSub.setText(getString(R.string.player_subtitle_fmt, item.subtitle,
                    formatDuration(item.durationSec)));
            h.b.rowBlock.setContentDescription(getString(R.string.culture_detail_open, item.titleZh));
            h.b.btnDetail.setContentDescription(getString(R.string.culture_detail_open, item.titleZh));
            h.b.rowBlock.setOnClickListener(v -> openDetail(h.getBindingAdapterPosition()));
            h.b.btnDetail.setOnClickListener(v -> openDetail(h.getBindingAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemCultureRowBinding b;

            Holder(@NonNull ItemCultureRowBinding binding) {
                super(binding.getRoot());
                b = binding;
            }
        }
    }

    /** m:ss 格式化（列表行用）。 */
    @NonNull
    public static String formatDuration(int seconds) {
        return seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }
}
