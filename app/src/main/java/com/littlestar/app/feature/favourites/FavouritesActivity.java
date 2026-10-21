package com.littlestar.app.feature.favourites;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.data.content.model.CultureItem;
import com.littlestar.app.data.repo.FavouriteRepository;
import com.littlestar.app.databinding.ActivityFavouritesBinding;
import com.littlestar.app.databinding.ItemCultureRowBinding;
import com.littlestar.app.feature.culture.CultureCategoriesActivity;
import com.littlestar.app.feature.culture.CultureListActivity;
import com.littlestar.app.feature.player.PlayerActivity;

import java.util.ArrayList;
import java.util.List;

/** 收藏（§7.10 / §9.10）：列表 + Sort；红心取消后行变空心不消失；空状态有出口。 */
public final class FavouritesActivity extends ChildActivity {

    @NonNull
    public static Intent intent(@NonNull Activity from) {
        return new Intent(from, FavouritesActivity.class);
    }

    private ActivityFavouritesBinding binding;
    private FavouritesViewModel vm;
    private FavAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFavouritesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new FavouritesViewModel(container())))
                .get(FavouritesViewModel.class);

        adapter = new FavAdapter();
        binding.favList.setLayoutManager(new LinearLayoutManager(this));
        binding.favList.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> {
            finish();
            Motion.childExit(this);
        });
        binding.btnSort.setOnClickListener(v -> {
            FavouritesViewModel.UiState s = vm.state().getValue();
            vm.setSort(s != null && s.sort == FavouriteRepository.Sort.NEWEST
                    ? FavouriteRepository.Sort.OLDEST : FavouriteRepository.Sort.NEWEST);
        });
        binding.btnGoListen.setOnClickListener(v -> {
            // 空状态出口：回文化分类（栈里没有就开一个）
            Intent i = new Intent(this, CultureCategoriesActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            Motion.childExit(this);
            finish();
        });

        vm.state().observe(this, this::render);
        container().content.load(container().executors, () -> vm.load());
    }

    private void render(FavouritesViewModel.UiState s) {
        if (s == null) {
            return;
        }
        binding.btnSort.setText(s.sort == FavouriteRepository.Sort.NEWEST
                ? R.string.fav_sort_new : R.string.fav_sort_old);
        binding.favList.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
        binding.emptyState.setVisibility(s.isEmpty() ? View.VISIBLE : View.GONE);
        binding.favCount.setText(getString(R.string.fav_count, s.rows.size()));
        adapter.setRows(s.rows);
    }

    private final class FavAdapter extends RecyclerView.Adapter<FavAdapter.Holder> {

        private List<FavouritesViewModel.Row> rows = new ArrayList<>();

        void setRows(@NonNull List<FavouritesViewModel.Row> newRows) {
            rows = new ArrayList<>(newRows);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemCultureRowBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            FavouritesViewModel.Row row = rows.get(position);
            CultureItem item = row.item;
            h.b.rowGlyph.setText(item.glyph);
            h.b.rowTitle.setText(item.titleZh);
            h.b.rowSub.setText(getString(R.string.player_subtitle_fmt, item.subtitle,
                    CultureListActivity.formatDuration(item.durationSec)));
            h.b.btnPlay.setVisibility(View.GONE);
            h.b.btnHeart.setVisibility(View.VISIBLE);
            h.b.btnHeart.setColorFilter(ContextCompat.getColor(FavouritesActivity.this,
                    row.active ? R.color.ls_red : R.color.ls_muted));
            h.b.btnHeart.setOnClickListener(v -> vm.toggle(item.id));
            h.b.rowBlock.setOnClickListener(v -> {
                List<String> queue = new ArrayList<>();
                for (FavouritesViewModel.Row r : rows) {
                    queue.add(r.item.id);
                }
                startActivity(PlayerActivity.intent(FavouritesActivity.this, item.id, queue));
                Motion.childEnter(FavouritesActivity.this);
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemCultureRowBinding b;

            Holder(@NonNull ItemCultureRowBinding binding) {
                super(binding.getRoot());
                b = binding;
            }
        }
    }
}
