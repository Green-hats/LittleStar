package com.littlestar.app.feature.flashcard;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.content.ContextCompat;
import com.littlestar.app.core.ui.ContentImage;

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
import com.littlestar.app.databinding.ActivityFlashcardBinding;
import com.littlestar.app.feature.quiz.QuizActivity;

import java.util.ArrayList;
import java.util.List;

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
    private ContentImage illustration;
    private FlashcardLayout layout;
    private String renderedWordId;

    private FlashcardViewModel.Mode mode = FlashcardViewModel.Mode.SESSION;
    private long sessionId = -1;
    private int restoredIndex = -1;
    private boolean speaking;
    private SpeechPlayer.FailureListener audioFailureListener;

    private static final String STATE_SESSION_ID = "state_session_id";
    private static final String STATE_CURRENT_INDEX = "state_current_index";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFlashcardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());
        binding.flashcardScroll.setVisibility(needsPortrait() ? View.GONE : View.VISIBLE);
        binding.rotationGate.setVisibility(needsPortrait() ? View.VISIBLE : View.GONE);
        layout = new FlashcardLayout(binding);

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

        illustration = new ContentImage(binding.pageCard.pageIllustration, container().executors,
                binding.pageCard.pageGlyph);
        binding.pageCard.btnListen.setOnClickListener(v -> speakCurrent());

        binding.btnBack.setOnClickListener(v -> {
            finish();
            Motion.childExit(this);
        });
        binding.btnNext.setOnClickListener(v -> {
            if (!needsPortrait()) {
                vm.next();
            }
        });
        binding.stamp.setOnClickListener(v -> {
            if (!needsPortrait() && vm.stamp()) {
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
                    if (!needsPortrait() && current != null && current.current() != null
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
        if (needsPortrait() || s == null || s.words.isEmpty()) {
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
                ? theme.nameEn
                : getString(R.string.fc_session_title));
        binding.cardCounter.setText(getString(R.string.fc_counter, s.index + 1, s.words.size()));
        binding.cardProgress.setSegments(s.words.size());
        binding.cardProgress.setFilled(s.index + 1);

        if (!cur.id.equals(renderedWordId)) {
            boolean first = renderedWordId == null;
            renderedWordId = cur.id;
            binding.pageCard.pageHanzi.setText(cur.hanzi);
            binding.pageCard.pagePinyin.setText(cur.pinyin);
            binding.pageCard.pageEnglish.setText(cur.english);
            binding.pageCard.pageGlyph.setText(cur.hanzi);
            illustration.bind(cur.image);
            layout.reset();
            binding.flashcardScroll.scrollTo(0, 0);
            if (!first) {
                container().sfx.play(SfxPlayer.Sfx.FLIP);
            }
            speakCurrent();
        }

        binding.stamp.setFillColor(ContextCompat.getColor(this,
                s.currentLearned ? R.color.ls_mint : R.color.ls_card));
        binding.stamp.setContentDescription(getString(s.currentLearned
                ? R.string.pr_stamp_marked_desc : R.string.pr_stamp_action_desc));
        binding.stamp.setStateDescription(getString(s.currentLearned
                ? R.string.pr_stamp_state_marked : R.string.pr_stamp_state_unmarked));
        binding.stampLabel.setText(s.currentLearned
                ? R.string.pr_stamp_marked : R.string.pr_stamp_optional);
        binding.btnNextText.setText(!s.isLast ? R.string.fc_next
                : s.mode == FlashcardViewModel.Mode.SESSION
                ? R.string.fc_start_quiz : R.string.fc_back_words);
        updateReading();
    }

    private void speakCurrent() {
        if (needsPortrait()) {
            return;
        }
        FlashcardViewModel.UiState s = vm.state().getValue();
        if (s != null && s.current() != null) {
            container().speech.play(s.current());
        }
    }

    /** 朗读中：当前页的声波 + 「朗读中」（§7.23，不改布局）。 */
    private void updateReading() {
        boolean showStatus = speaking && !needsPortrait();
        binding.pageCard.soundWave.setPlaying(showStatus);
        binding.pageCard.soundWave.setVisibility(showStatus ? View.VISIBLE : View.GONE);
        binding.pageCard.readingLabel.setVisibility(showStatus ? View.VISIBLE : View.GONE);
        binding.pageCard.btnListen.setStateDescription(speaking ? getString(R.string.fc_reading) : null);
    }

    private void onEffect(Event<FlashcardViewModel.Effect> event) {
        if (needsPortrait()) {
            return;
        }
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

    private boolean needsPortrait() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @Override
    protected void onPause() {
        container().speech.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (layout != null) {
            layout.close();
        }
        if (audioFailureListener != null) {
            container().speech.clearFailureListener(audioFailureListener);
        }
        super.onDestroy();
    }

}
