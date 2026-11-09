package com.littlestar.app.feature.flashcard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.littlestar.app.R;
import com.littlestar.app.core.audio.SfxPlayer;
import com.littlestar.app.core.audio.SpeechPlayer;
import com.littlestar.app.core.lifecycle.Event;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.data.content.model.Theme;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.databinding.ActivityFlashcardBinding;
import com.littlestar.app.databinding.PageFlashcardBinding;
import com.littlestar.app.feature.quiz.QuizActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 识字卡（§7.4）。SESSION：进入时用 SessionBuilder 建会话（REVIEW 会话无新字 → 直接进 Quiz）。
 * BROWSE：词表浏览（M4）。翻页 sfx FLIP + 自动朗读；音频缺失走页面内对话框（§7.26）。
 */
public final class FlashcardActivity extends ChildActivity {

    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_WORD_IDS = "wordIds";
    private static final String EXTRA_START_INDEX = "startIndex";
    private static final String EXTRA_SESSION_ID = "sessionId";

    /** 首页 START 入口：会话在 content 就绪后由本页创建。 */
    @NonNull
    public static Intent sessionIntent(@NonNull Activity from) {
        Intent i = new Intent(from, FlashcardActivity.class);
        i.putExtra(EXTRA_MODE, FlashcardViewModel.Mode.SESSION.name());
        return i;
    }

    /** 词表浏览入口（M4 用）。 */
    @NonNull
    public static Intent browseIntent(@NonNull Activity from, @NonNull List<String> wordIds, int startIndex) {
        Intent i = new Intent(from, FlashcardActivity.class);
        i.putExtra(EXTRA_MODE, FlashcardViewModel.Mode.BROWSE.name());
        i.putStringArrayListExtra(EXTRA_WORD_IDS, new ArrayList<>(wordIds));
        i.putExtra(EXTRA_START_INDEX, startIndex);
        return i;
    }

    private ActivityFlashcardBinding binding;
    private FlashcardViewModel vm;
    private CardAdapter adapter;

    private FlashcardViewModel.Mode mode = FlashcardViewModel.Mode.SESSION;
    private long sessionId = -1;
    private int restoredIndex = -1;
    private boolean speaking;
    private SpeechPlayer.FailureListener audioFailureListener;
    /** 页面滑动来源标记：next() 按钮触发的翻页不重复朗读（render 里已处理）。 */
    private boolean firstPageSpoken;

    private static final String STATE_SESSION_ID = "state_session_id";
    private static final String STATE_CURRENT_INDEX = "state_current_index";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFlashcardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        // 进程被杀或重建：恢复已建会话与当前字卡索引，不覆盖为 -1，也不重复 buildSession
        if (savedInstanceState != null) {
            sessionId = savedInstanceState.getLong(STATE_SESSION_ID, -1);
            restoredIndex = savedInstanceState.getInt(STATE_CURRENT_INDEX, -1);
        }

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new FlashcardViewModel(container())))
                .get(FlashcardViewModel.class);

        String m = getIntent().getStringExtra(EXTRA_MODE);
        if (m != null) {
            mode = FlashcardViewModel.Mode.valueOf(m);
        }
        if (sessionId < 0) {
            sessionId = getIntent().getLongExtra(EXTRA_SESSION_ID, -1);
        }
        if (sessionId < 0 && vm.sessionId() > 0) {
            sessionId = vm.sessionId();
        }

        adapter = new CardAdapter();
        binding.pager.setAdapter(adapter);
        binding.pager.setPageTransformer((page, position) -> {
            page.setTranslationX(-position * page.getWidth() * 0.08f);
            page.setRotation(position * 2f);   // §8.6 翻卡 ±2°
        });
        binding.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                vm.onPageSelected(position);
                container().sfx.play(SfxPlayer.Sfx.FLIP);
                speakCurrent();
                updateReading();
            }
        });

        binding.btnBack.setOnClickListener(v -> {
            finish();
            Motion.childExit(this);
        });
        binding.btnNext.setOnClickListener(v -> vm.next());
        binding.stamp.setOnClickListener(v -> {
            if (vm.stamp()) {
                // 盖章动效 + 「嗒」（已学再点不撤销、不重播）
                binding.stamp.setLearned(true, true);
                container().sfx.play(SfxPlayer.Sfx.TAP);
                vm.refresh();
            }
        });
        binding.btnAudioContinue.setOnClickListener(v -> binding.audioMissing.setVisibility(View.GONE));
        binding.btnAudioRetry.setOnClickListener(v -> {
            binding.audioMissing.setVisibility(View.GONE);
            speakCurrent();
        });

        // 音频缺失 / 损坏 → 页面内对话框（孩子区不用系统 AlertDialog）
        audioFailureListener = new SpeechPlayer.FailureListener() {
            @Override
            public void onPlayFailed(@NonNull String wordId) {
                runOnUiThread(() -> {
                    FlashcardViewModel.UiState current = vm.state().getValue();
                    if (current != null && current.current() != null
                            && wordId.equals(current.current().id)) {
                        binding.audioMissing.setVisibility(View.VISIBLE);
                        binding.audioMissing.announceForAccessibility(getString(R.string.pr_audio_title));
                    }
                });
            }
        };
        container().speech.setFailureListener(audioFailureListener);

        vm.state().observe(this, this::render);
        vm.effects().observe(this, this::onEffect);
        // 学会状态变化（印章写库是异步的）→ 刷新印章显示
        container().store.observe().observe(this, ps -> vm.refresh());
        container().speech.isPlaying().observe(this, playing -> {
            speaking = Boolean.TRUE.equals(playing);
            updateReading();
        });

        // 异步工作与结果归 retained VM 所有；重建只重新订阅，不重建会话。
        int targetIndex = restoredIndex >= 0
                ? restoredIndex : getIntent().getIntExtra(EXTRA_START_INDEX, 0);
        List<String> ids = getIntent().getStringArrayListExtra(EXTRA_WORD_IDS);
        vm.initialize(mode, ids != null ? ids : new ArrayList<>(), targetIndex, sessionId);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // VM 是权威来源：异步完成后 Activity 字段可能仍是旧入口值。
        outState.putLong(STATE_SESSION_ID, vm != null ? vm.sessionId() : sessionId);
        outState.putInt(STATE_CURRENT_INDEX, vm != null ? vm.currentIndex() : Math.max(0, restoredIndex));
    }

    private void render(FlashcardViewModel.UiState s) {
        if (s == null || s.words.isEmpty()) {
            return;
        }
        Word cur = s.current();
        if (cur == null) {
            return;
        }
        // 顶栏：主题名 + k/N
        String themeId = container().content.themeIdOf(cur.id);
        Theme theme = themeId == null ? null : container().content.themeById(themeId);
        binding.cardTitle.setText(theme != null
                ? theme.nameEn + " " + theme.nameZh
                : getString(R.string.fc_session_title));
        binding.cardCounter.setText(getString(R.string.fc_counter, s.index + 1, s.words.size()));
        binding.cardProgress.setSegments(s.words.size());
        binding.cardProgress.setFilled(s.index + 1);

        adapter.setWords(s.words, learnedSet());
        if (binding.pager.getCurrentItem() != s.index) {
            binding.pager.setCurrentItem(s.index, false);
        } else if (!firstPageSpoken) {
            firstPageSpoken = true;
            speakCurrent();
        }

        binding.stamp.setLearned(s.currentLearned);
        binding.stampLabel.setText(s.currentLearned
                ? R.string.pr_stamp_marked : R.string.pr_stamp_optional);
        binding.btnNextText.setText(s.isLast ? R.string.fc_done : R.string.fc_next);
        updateReading();
    }

    /** 当前已学集合（从 store 快照读，供 adapter 标印章）。 */
    @NonNull
    private Set<String> learnedSet() {
        Set<String> set = new HashSet<>();
        ProgressState ps = container().store.observe().getValue();
        if (ps != null) {
            for (java.util.Map.Entry<String, ProgressState.WordProgress> e : ps.words.entrySet()) {
                if (e.getValue().learnedAt != null) {
                    set.add(e.getKey());
                }
            }
        }
        return set;
    }

    private void speakCurrent() {
        FlashcardViewModel.UiState s = vm.state().getValue();
        if (s != null && s.current() != null) {
            container().speech.play(s.current());
        }
    }

    /** 朗读中：当前页的声波 + 「朗读中」（§7.23，不改布局）。 */
    private void updateReading() {
        CardAdapter.Holder h = currentHolder();
        if (h == null) {
            return;
        }
        h.binding.soundWave.setPlaying(speaking);
        h.binding.soundWave.setVisibility(speaking ? View.VISIBLE : View.GONE);
        h.binding.readingLabel.setVisibility(speaking ? View.VISIBLE : View.GONE);
    }

    @Nullable
    private CardAdapter.Holder currentHolder() {
        ViewGroup rv = (ViewGroup) binding.pager.getChildAt(0);
        if (!(rv instanceof RecyclerView)) {
            return null;
        }
        RecyclerView.ViewHolder vh =
                ((RecyclerView) rv).findViewHolderForAdapterPosition(binding.pager.getCurrentItem());
        return vh instanceof CardAdapter.Holder ? (CardAdapter.Holder) vh : null;
    }

    private void onEffect(Event<FlashcardViewModel.Effect> event) {
        FlashcardViewModel.Effect e = event.getContentIfNotHandled();
        if (e instanceof FlashcardViewModel.Effect.GoQuiz goQuiz) {
            startActivity(QuizActivity.sessionIntent(this, goQuiz.sessionId()));
            Motion.childEnter(this);
            finish();
        } else if (e instanceof FlashcardViewModel.Effect.Close) {
            finish();
            Motion.childExit(this);
        }
    }

    @Override
    protected void onPause() {
        container().speech.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (audioFailureListener != null) {
            container().speech.clearFailureListener(audioFailureListener);
        }
        super.onDestroy();
    }

    /** 卡页 adapter：每页一张主角卡。绑定专属词义配图并隐藏重复字形占位。 */
    private final class CardAdapter extends RecyclerView.Adapter<CardAdapter.Holder> {

        private List<Word> words = new ArrayList<>();
        private Set<String> learned = new HashSet<>();

        void setWords(@NonNull List<Word> newWords, @NonNull Set<String> learnedIds) {
            if (words.equals(newWords) && learned.equals(learnedIds)) {
                return;
            }
            words = new ArrayList<>(newWords);
            learned = new HashSet<>(learnedIds);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            PageFlashcardBinding b = PageFlashcardBinding.inflate(getLayoutInflater(), parent, false);
            return new Holder(b);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Word w = words.get(position);
            int resId = w.image == null ? 0
                    : getResources().getIdentifier(w.image, "drawable", getPackageName());
            boolean hasDedicatedIllustration = resId != 0 && !"ill_placeholder".equals(w.image);
            if (hasDedicatedIllustration) {
                h.binding.pageIllustration.setImageResource(resId);
                h.binding.pageGlyph.setVisibility(View.GONE);
            } else {
                h.binding.pageIllustration.setImageResource(R.drawable.ill_placeholder);
                h.binding.pageGlyph.setVisibility(View.VISIBLE);
                h.binding.pageGlyph.setText(w.hanzi);
            }
            h.binding.pageHanzi.setText(w.hanzi);
            h.binding.pagePinyin.setText(w.pinyin);
            h.binding.pageEnglish.setText(w.english);
            h.binding.btnListen.setOnClickListener(v -> container().speech.play(w));
            h.binding.pageCard.setOnClickListener(v -> container().speech.play(w));
            if (position == binding.pager.getCurrentItem()) {
                updateReading();
            }
        }

        @Override
        public int getItemCount() {
            return words.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final PageFlashcardBinding binding;

            Holder(@NonNull PageFlashcardBinding b) {
                super(b.getRoot());
                binding = b;
            }
        }
    }
}
