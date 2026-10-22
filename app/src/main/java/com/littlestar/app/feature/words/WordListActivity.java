package com.littlestar.app.feature.words;

import android.app.Activity;
import android.content.Intent;
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
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.databinding.ActivityWordListBinding;
import com.littlestar.app.databinding.ItemWordTileBinding;
import com.littlestar.app.feature.flashcard.FlashcardActivity;
import com.littlestar.app.feature.quiz.QuizActivity;

import java.util.ArrayList;
import java.util.List;

/** 词表（§7.3 / §9.3）：6 字网格 + Quiz(THEME) 入口。数据驱动：返回时绿勾自动刷新。 */
public final class WordListActivity extends ChildActivity {

    private static final String EXTRA_THEME_ID = "themeId";

    @NonNull
    public static Intent intent(@NonNull Activity from, @NonNull String themeId) {
        Intent i = new Intent(from, WordListActivity.class);
        i.putExtra(EXTRA_THEME_ID, themeId);
        return i;
    }

    private ActivityWordListBinding binding;
    private WordListViewModel vm;
    private WordAdapter adapter;
    private String themeId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWordListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new WordListViewModel(container())))
                .get(WordListViewModel.class);

        themeId = getIntent().getStringExtra(EXTRA_THEME_ID);

        adapter = new WordAdapter();
        binding.wordGrid.setLayoutManager(new GridLayoutManager(this, 2));
        binding.wordGrid.addItemDecoration(new ThemesActivity.GapDecoration(
                getResources().getDimensionPixelSize(R.dimen.ls_gap)));
        binding.wordGrid.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> {
            finish();
            Motion.childExit(this);
        });
        binding.btnQuiz.setOnClickListener(v -> startThemeQuiz());

        vm.state().observe(this, this::render);
        container().content.load(container().executors, () -> vm.init(themeId));
    }

    private void render(WordListViewModel.UiState s) {
        if (s == null) {
            return;
        }
        binding.listTitle.setText(s.theme.nameEn + " " + s.theme.nameZh);
        binding.listProgress.setSegments(Math.max(1, s.words.size()));
        binding.listProgress.setFilled(s.learned);
        binding.listProgressText.setText(
                getString(R.string.wl_learned, s.learned, s.words.size()));
        adapter.setItems(s.words);
    }

    /** Quiz 入口：建 THEME 会话（立即落盘）→ QuizActivity(THEME)。 */
    private void startThemeQuiz() {
        // 防连点：双击不重复建会话
        if (!binding.btnQuiz.isEnabled()) {
            return;
        }
        binding.btnQuiz.setEnabled(false);
        binding.btnQuiz.postDelayed(() -> binding.btnQuiz.setEnabled(true), 800);
        container().store.update(state -> {
            ProgressState.Session s = container().sessionBuilder.buildThemeSession(state, themeId);
            long id = s.id;
            container().store.flushNow();
            container().executors.mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                startActivity(QuizActivity.themeIntent(this, id));
                Motion.childEnter(this);
            });
        });
    }

    private final class WordAdapter extends RecyclerView.Adapter<WordAdapter.Holder> {

        private List<WordListViewModel.WordItem> items = new ArrayList<>();

        void setItems(@NonNull List<WordListViewModel.WordItem> newItems) {
            items = new ArrayList<>(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemWordTileBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            WordListViewModel.WordItem item = items.get(position);
            h.b.wordGlyph.setText(item.word.hanzi);
            h.b.wordHanzi.setText(item.word.hanzi);
            h.b.wordPinyin.setText(item.word.pinyin);
            h.b.wordEnglish.setText(item.word.english);
            h.b.wordDone.setVisibility(item.learned ? View.VISIBLE : View.GONE);
            h.b.wordTile.setOnClickListener(v -> {
                // 点字块 → Flashcard(BROWSE) 并自动朗读
                List<String> ids = new ArrayList<>();
                for (WordListViewModel.WordItem it : items) {
                    ids.add(it.word.id);
                }
                startActivity(FlashcardActivity.browseIntent(
                        WordListActivity.this, ids, h.getBindingAdapterPosition()));
                Motion.childEnter(WordListActivity.this);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemWordTileBinding b;

            Holder(@NonNull ItemWordTileBinding binding) {
                super(binding.getRoot());
                b = binding;
            }
        }
    }
}
