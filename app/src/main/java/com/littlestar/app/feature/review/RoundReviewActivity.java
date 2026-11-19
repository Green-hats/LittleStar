package com.littlestar.app.feature.review;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

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
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.databinding.ActivityRoundReviewBinding;
import com.littlestar.app.databinding.ItemReviewAnswerBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 本轮回顾（A1）：Reward 通过 sessionId 进入，逐题显示所选与正确答案。
 * 只读，不写进度；没有答题记录时显示空状态。
 */
public final class RoundReviewActivity extends ChildActivity {

    private static final String EXTRA_SESSION_ID = "sessionId";

    @NonNull
    public static Intent intent(@NonNull Activity from, long sessionId) {
        Intent i = new Intent(from, RoundReviewActivity.class);
        i.putExtra(EXTRA_SESSION_ID, sessionId);
        return i;
    }

    private ActivityRoundReviewBinding binding;
    private RoundReviewViewModel vm;
    private ReviewAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRoundReviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Insets.applySystemBarPadding(binding.getRoot());

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new RoundReviewViewModel(container())))
                .get(RoundReviewViewModel.class);

        adapter = new ReviewAdapter();
        binding.answerList.setLayoutManager(new LinearLayoutManager(this));
        binding.answerList.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> {
            finish();
            Motion.childExit(this);
        });

        vm.state().observe(this, this::render);
        // The restored task may open this Activity in a fresh process. Resolve
        // answer word IDs only after bundled content is loaded.
        container().content.load(container().executors,
                () -> vm.init(getIntent().getLongExtra(EXTRA_SESSION_ID, -1)));
    }

    private void render(RoundReviewViewModel.UiState s) {
        if (s == null) {
            return;
        }
        boolean empty = s.items.isEmpty();
        binding.reviewSummary.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.answerList.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.reviewSummary.setText(getString(R.string.sprout_first_try, s.correct, s.total));
        adapter.setItems(s.items);
    }

    @Override
    protected void onPause() {
        container().speech.stop();
        super.onPause();
    }

    private final class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.Holder> {

        private List<RoundReviewViewModel.ReviewItem> items = new ArrayList<>();

        void setItems(@NonNull List<RoundReviewViewModel.ReviewItem> newItems) {
            items = new ArrayList<>(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemReviewAnswerBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            RoundReviewViewModel.ReviewItem item = items.get(position);
            Word word = item.word;

            h.b.rowHanzi.setText(word.hanzi);
            h.b.rowPinyin.setText(word.pinyin);
            h.b.rowEnglish.setText(word.english);

            String chosen = item.chosen.isEmpty()
                    ? getString(R.string.review_no_answer) : item.chosen;
            h.b.rowAnswer.setText(item.correct
                    ? getString(R.string.review_correct)
                    : getString(R.string.review_wrong, chosen, word.hanzi));
            ImageView status = h.b.rowStatus;
            int outcomeColor = getColor(item.correct ? R.color.sprout_fern : R.color.sprout_on_rose);
            h.b.rowAnswer.setTextColor(outcomeColor);
            status.setImageResource(item.correct ? R.drawable.ic_check : R.drawable.ic_close);
            status.setColorFilter(outcomeColor);
            status.setContentDescription(getString(item.correct
                    ? R.string.review_correct_desc : R.string.review_wrong_desc));

            h.b.btnSpeak.setContentDescription(getString(R.string.review_play_desc, word.hanzi));
            h.b.btnSpeak.setOnClickListener(v -> container().speech.play(word));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemReviewAnswerBinding b;

            Holder(@NonNull ItemReviewAnswerBinding binding) {
                super(binding.getRoot());
                b = binding;
            }
        }
    }
}
