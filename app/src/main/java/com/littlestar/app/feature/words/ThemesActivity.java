package com.littlestar.app.feature.words;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.databinding.ActivityThemesBinding;
import com.littlestar.app.databinding.ItemThemeBinding;

import java.util.ArrayList;
import java.util.List;

/** 主题选择（§7.2 / §9.2）：2 列 6 块；点块进词表。 */
public final class ThemesActivity extends ChildActivity {

    private ActivityThemesBinding binding;
    private ThemesViewModel vm;
    private ThemeAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityThemesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new ThemesViewModel(container())))
                .get(ThemesViewModel.class);

        adapter = new ThemeAdapter();
        binding.themesList.setLayoutManager(new GridLayoutManager(this, 2));
        binding.themesList.addItemDecoration(new GapDecoration(
                getResources().getDimensionPixelSize(R.dimen.ls_gap)));
        binding.themesList.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> {
            finish();
            Motion.childExit(this);
        });

        vm.themes().observe(this, this::render);
        container().content.load(container().executors, () -> vm.load());
    }

    private void render(List<ThemesViewModel.ThemeItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        adapter.setItems(items);
        int stars = 0;
        com.littlestar.app.data.store.ProgressState ps = container().store.observe().getValue();
        if (ps != null) {
            stars = com.littlestar.app.data.repo.ProgressRepository.totalStars(ps);
        }
        binding.starCount.setText(String.valueOf(stars));
    }

    /** 网格横向间距（纵向由 item 的 marginBottom 提供）。 */
    static final class GapDecoration extends RecyclerView.ItemDecoration {
        private final int gap;

        GapDecoration(int gap) {
            this.gap = gap;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int pos = parent.getChildAdapterPosition(view);
            if (pos % 2 == 0) {
                outRect.right = gap / 2;
            } else {
                outRect.left = gap / 2;
            }
        }
    }

    private final class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.Holder> {

        private List<ThemesViewModel.ThemeItem> items = new ArrayList<>();

        void setItems(@NonNull List<ThemesViewModel.ThemeItem> newItems) {
            items = new ArrayList<>(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemThemeBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            ThemesViewModel.ThemeItem item = items.get(position);
            h.b.themeGlyph.setText(item.theme.glyph);
            h.b.badgeText.setText(String.valueOf(item.theme.order));
            h.b.badgeNum.setContentDescription(
                    getString(R.string.themes_badge_desc, item.theme.order));
            boolean done = item.total > 0 && item.learned >= item.total;
            h.b.badgeNum.setVisibility(done ? View.GONE : View.VISIBLE);
            h.b.badgeDone.setVisibility(done ? View.VISIBLE : View.GONE);
            h.b.themeNameZh.setText(item.theme.nameZh);
            h.b.themeNameEn.setText(item.theme.nameEn);
            h.b.themeCount.setText(getString(R.string.themes_words_count, item.total));
            h.b.themeLearned.setText(getString(R.string.themes_learned, item.learned, item.total));
            h.b.themeTile.setOnClickListener(v -> {
                startActivity(WordListActivity.intent(ThemesActivity.this, item.theme.id));
                Motion.childEnter(ThemesActivity.this);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemThemeBinding b;

            Holder(@NonNull ItemThemeBinding binding) {
                super(binding.getRoot());
                b = binding;
            }
        }
    }
}
