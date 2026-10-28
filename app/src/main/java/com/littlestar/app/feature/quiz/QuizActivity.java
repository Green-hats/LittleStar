package com.littlestar.app.feature.quiz;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.littlestar.app.R;
import com.littlestar.app.core.audio.SfxPlayer;
import com.littlestar.app.core.lifecycle.Event;
import com.littlestar.app.core.ui.BlockLayout;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.databinding.ActivityQuizBinding;
import com.littlestar.app.databinding.ViewQuizOptionBinding;
import com.littlestar.app.feature.reward.RewardActivity;

/**
 * 测验（§7.5）：一页一题，不用 ViewPager。点选项即锁定判定；
 * 答对 mint+勾，答错 clay+墨色× 并亮出正确项。答后反馈条 + NEXT 从下滑入。
 */
public final class QuizActivity extends ChildActivity {

    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_SESSION_ID = "sessionId";

    @NonNull
    public static Intent sessionIntent(@NonNull Activity from, long sessionId) {
        Intent i = new Intent(from, QuizActivity.class);
        i.putExtra(EXTRA_MODE, QuizViewModel.Mode.SESSION.name());
        i.putExtra(EXTRA_SESSION_ID, sessionId);
        return i;
    }

    /** 词表 Quiz 入口：创建 THEME 会话。 */
    @NonNull
    public static Intent themeIntent(@NonNull Activity from, long sessionId) {
        Intent i = new Intent(from, QuizActivity.class);
        i.putExtra(EXTRA_MODE, QuizViewModel.Mode.THEME.name());
        i.putExtra(EXTRA_SESSION_ID, sessionId);
        return i;
    }

    private ActivityQuizBinding binding;
    private QuizViewModel vm;
    private ViewQuizOptionBinding[] options;
    private BlockLayout[] optionBlocks;

    private QuizViewModel.Mode mode = QuizViewModel.Mode.SESSION;
    private long sessionId = -1;
    /** 已滑入反馈条的题号（-1 = 当前题还没滑入）。 */
    private int feedbackShownFor = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQuizBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        // SavedStateHandle 从 CreationExtras 取（进程被杀后回到同一题）
        vm = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            @SuppressWarnings("unchecked")
            public <T extends androidx.lifecycle.ViewModel> T create(@NonNull Class<T> modelClass,
                    @NonNull CreationExtras extras) {
                SavedStateHandle handle = SavedStateHandleSupport.createSavedStateHandle(extras);
                return (T) new QuizViewModel(container(), handle);
            }
        }).get(QuizViewModel.class);

        String m = getIntent().getStringExtra(EXTRA_MODE);
        if (m != null) {
            mode = QuizViewModel.Mode.valueOf(m);
        }
        sessionId = getIntent().getLongExtra(EXTRA_SESSION_ID, -1);

        options = new ViewQuizOptionBinding[]{
                ViewQuizOptionBinding.bind(findViewById(R.id.option0)),
                ViewQuizOptionBinding.bind(findViewById(R.id.option1)),
                ViewQuizOptionBinding.bind(findViewById(R.id.option2)),
                ViewQuizOptionBinding.bind(findViewById(R.id.option3))};
        optionBlocks = new BlockLayout[options.length];
        for (int i = 0; i < options.length; i++) {
            optionBlocks[i] = options[i].optionBlock;
            int index = i;
            optionBlocks[i].setOnClickListener(v -> vm.choose(index));
        }

        binding.btnBack.setOnClickListener(v -> {
            finish();
            Motion.childExit(this);
        });
        binding.btnListen.setOnClickListener(v -> vm.speakCurrent());
        binding.questionCard.setOnClickListener(v -> vm.speakCurrent());
        binding.btnNext.setOnClickListener(v -> vm.next());

        vm.state().observe(this, this::render);
        vm.effects().observe(this, this::onEffect);

        // 内容就绪 + 会话可读后才 init（会话由 Flashcard 落盘，快照可能稍晚到）
        container().content.load(container().executors, this::tryInit);
    }

    private void tryInit() {
        if (vm.init(mode, sessionId)) {
            return;
        }
        container().store.observe().observe(this, new Observer<ProgressState>() {
            @Override
            public void onChanged(ProgressState ps) {
                if (vm.init(mode, sessionId)) {
                    container().store.observe().removeObserver(this);
                }
            }
        });
    }

    private void render(QuizViewModel.UiState s) {
        if (s == null || s.question == null) {
            return;
        }
        binding.quizCounter.setText(getString(R.string.quiz_counter, s.index + 1, s.total));
        binding.quizProgress.setSegments(Math.max(1, s.total));
        binding.quizProgress.setFilled(s.index + 1);

        // 英文提示默认隐藏（settings.isEnglishHint）
        binding.englishHint.setVisibility(s.showEnglishHint ? View.VISIBLE : View.GONE);
        binding.englishHint.setText(s.question.english());

        int card = ContextCompat.getColor(this, R.color.ls_card);
        int mint = ContextCompat.getColor(this, R.color.ls_mint);
        int clay = ContextCompat.getColor(this, R.color.ls_clay);
        int ink = ContextCompat.getColor(this, R.color.ls_ink);
        int muted = ContextCompat.getColor(this, R.color.ls_muted);
        int onMint = ContextCompat.getColor(this, R.color.ls_on_mint);
        int onClay = ContextCompat.getColor(this, R.color.ls_on_clay);

        for (int i = 0; i < options.length; i++) {
            ViewQuizOptionBinding ob = options[i];
            String hanzi = i < s.question.options().size() ? s.question.options().get(i) : "";
            ob.optionHanzi.setText(hanzi);
            Word w = container().content.wordByHanzi(hanzi);
            ob.optionPinyin.setText(w != null ? w.pinyin : "");
            ob.optionBadge.setVisibility(View.GONE);

            if (!s.answered) {
                optionBlocks[i].setFillColor(card);
                ob.optionHanzi.setTextColor(ink);
                ob.optionPinyin.setTextColor(muted);
            } else if (i == s.question.correctIndex()) {
                optionBlocks[i].setFillColor(mint);
                ob.optionHanzi.setTextColor(onMint);
                ob.optionPinyin.setTextColor(onMint);
                showBadge(ob, true);
            } else if (i == s.chosenIndex) {
                optionBlocks[i].setFillColor(clay);
                ob.optionHanzi.setTextColor(onClay);
                ob.optionPinyin.setTextColor(onClay);
                showBadge(ob, false);
            } else {
                optionBlocks[i].setFillColor(card);
                ob.optionHanzi.setTextColor(ink);
                ob.optionPinyin.setTextColor(muted);
            }
        }

        // 反馈条 + NEXT（答后滑入一次）
        if (s.answered) {
            binding.feedbackBar.setFillColor(s.lastAnswerCorrect ? mint : clay);
            binding.feedbackText.setText(s.lastAnswerCorrect
                    ? getString(R.string.quiz_feedback_correct)
                    : getString(R.string.quiz_feedback_wrong, s.question.hanzi()));
            binding.feedbackText.setTextColor(s.lastAnswerCorrect ? onMint : onClay);
            binding.feedbackIcon.setImageResource(
                    s.lastAnswerCorrect ? R.drawable.ic_check : R.drawable.ic_close);
            binding.feedbackIcon.setColorFilter(s.lastAnswerCorrect ? onMint : onClay);
            binding.btnNextText.setText(s.isLast ? R.string.quiz_done : R.string.quiz_next);
            if (feedbackShownFor != s.index) {
                feedbackShownFor = s.index;
                Motion.slideUpIn(binding.bottomFeedback);
            }
        } else {
            binding.bottomFeedback.setVisibility(View.GONE);
            feedbackShownFor = -1;
        }
    }

    private void showBadge(ViewQuizOptionBinding ob, boolean correct) {
        ob.optionBadge.setImageResource(correct ? R.drawable.ic_check : R.drawable.ic_close);
        ob.optionBadge.setBackgroundResource(
                correct ? R.drawable.bg_badge_green : R.drawable.bg_badge_ink);
        ob.optionBadge.setColorFilter(ContextCompat.getColor(this,
                correct ? R.color.ls_on_green : R.color.ls_card));
        Motion.popIn(ob.optionBadge);
    }

    private void onEffect(Event<QuizViewModel.Effect> event) {
        QuizViewModel.Effect e = event.getContentIfNotHandled();
        if (e instanceof QuizViewModel.Effect.GoReward go) {
            startActivity(RewardActivity.intent(this, go.sessionId()));
            Motion.childEnter(this);
            finish();
        } else if (e instanceof QuizViewModel.Effect.PlayWord play) {
            Word w = container().content.wordById(play.wordId());
            if (w != null) {
                container().speech.play(w);
            }
        } else if (e instanceof QuizViewModel.Effect.AnswerSfx sfx) {
            container().sfx.play(sfx.correct() ? SfxPlayer.Sfx.CORRECT : SfxPlayer.Sfx.WRONG);
            if (!sfx.correct()) {
                QuizViewModel.UiState s = vm.state().getValue();
                if (s != null && s.chosenIndex >= 0 && s.chosenIndex < optionBlocks.length) {
                    Motion.shake(optionBlocks[s.chosenIndex]);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        container().speech.stop();
        super.onDestroy();
    }
}
