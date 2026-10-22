package com.littlestar.app.feature.culture;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.data.content.model.CultureCategory;
import com.littlestar.app.data.repo.ProgressRepository;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.databinding.ActivityCultureCategoriesBinding;
import com.littlestar.app.feature.favourites.FavouritesActivity;

import java.util.List;

/** 文化分类（§7.7 / §9.7）：MVP 3 类 = 儿歌/故事两块 + 古诗宽卡；收藏行；统一青绿。 */
public final class CultureCategoriesActivity extends ChildActivity {

    private ActivityCultureCategoriesBinding binding;
    private CultureCategoriesViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCultureCategoriesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new CultureCategoriesViewModel(container())))
                .get(CultureCategoriesViewModel.class);

        binding.btnBack.setOnClickListener(v -> {
            finish();
            Motion.childExit(this);
        });
        binding.favRow.setOnClickListener(v -> {
            startActivity(FavouritesActivity.intent(this));
            Motion.childEnter(this);
        });

        vm.categories().observe(this, this::render);
        vm.favouriteCount().observe(this, n ->
                binding.favCount.setText(getString(R.string.fav_count, n)));
        container().content.load(container().executors, () -> vm.load());
    }

    private void render(List<CultureCategoriesViewModel.CategoryItem> items) {
        ProgressState ps = container().store.observe().getValue();
        binding.starCount.setText(String.valueOf(ps == null ? 0 : ProgressRepository.totalStars(ps)));
        for (CultureCategoriesViewModel.CategoryItem item : items) {
            CultureCategory c = item.category;
            switch (c.id) {
                case "songs":
                    bind(c, item.count, binding.glyphSongs, binding.nameSongs, binding.nameSongsEn,
                            binding.countSongs, binding.catSongs);
                    break;
                case "stories":
                    bind(c, item.count, binding.glyphStories, binding.nameStories,
                            binding.nameStoriesEn, binding.countStories, binding.catStories);
                    break;
                case "poems":
                    bind(c, item.count, binding.glyphPoems, binding.namePoems, null,
                            binding.countPoems, binding.catPoems);
                    break;
                default:
                    break;   // MVP 只显示 3 类
            }
        }
    }

    private void bind(CultureCategory c, int count,
                      com.littlestar.app.core.ui.HanziTextView glyph,
                      android.widget.TextView name, @Nullable android.widget.TextView nameEn,
                      android.widget.TextView countView,
                      com.littlestar.app.core.ui.BlockLayout block) {
        glyph.setText(c.glyph);
        name.setText(c.nameZh);
        if (nameEn != null) {
            nameEn.setText(c.nameEn);
        }
        int unitRes = switch (c.id) {
            case "songs" -> R.string.cul_unit_shou;
            case "poems" -> R.string.cul_unit_poems;
            default -> R.string.cul_unit_ge;
        };
        String unit = getString(unitRes);
        countView.setText(getString(R.string.cul_count_fmt, count, unit));
        block.setOnClickListener(v -> {
            startActivity(CultureListActivity.intent(this, c.id));
            Motion.childEnter(this);
        });
    }
}
