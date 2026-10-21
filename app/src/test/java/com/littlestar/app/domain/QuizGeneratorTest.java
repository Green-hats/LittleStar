package com.littlestar.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.littlestar.app.data.content.ContentRepository;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.util.TestContent;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class QuizGeneratorTest {

    @Test
    public void distractorsExcludeAnswer_noDuplicates() {
        ContentRepository content = TestContent.load();
        QuizGenerator gen = new QuizGenerator(content, new Random(7));
        for (Word w : content.wordsInOrder()) {
            QuizGenerator.QuizQuestion q = gen.generate(w);
            assertEquals(4, q.options().size());
            assertEquals(new HashSet<>(q.options()).size(), 4);   // 不重复
            assertFalse("干扰项含答案", q.options().subList(0, q.correctIndex()).contains(q.hanzi())
                    || q.options().subList(q.correctIndex() + 1, 4).contains(q.hanzi()));
            assertEquals(q.hanzi(), q.options().get(q.correctIndex()));
        }
    }

    /** 正确答案位置均匀分布：1000 次，每个位置 20%–30%。 */
    @Test
    public void correctPosition_uniform() {
        ContentRepository content = TestContent.load();
        QuizGenerator gen = new QuizGenerator(content, new Random(20261021));
        Word w = content.wordById("mao");
        int[] counts = new int[4];
        for (int i = 0; i < 1000; i++) {
            counts[gen.generate(w).correctIndex()]++;
        }
        for (int i = 0; i < 4; i++) {
            assertTrue("位置 " + i + " 占比 " + counts[i] / 10.0 + "%",
                    counts[i] >= 200 && counts[i] <= 300);
        }
    }

    /** 同主题不够 3 个干扰项时跨主题补（人为构造 distractors 为空的情况测同主题补）。 */
    @Test
    public void fillsFromSameThemeWhenNoDistractors() {
        ContentRepository content = TestContent.load();
        Word w = content.wordById("yi");   // distractors=["二","三","四"] 已有 3 个
        // 构造一个无 distractors 的副本语义：直接用 distractors 为空的新 Word
        Word bare = new Word();
        bare.id = "yi";
        bare.hanzi = "一";
        bare.pinyin = "yī";
        bare.english = "one";
        bare.image = "ill_placeholder";
        bare.audio = "audio/words/yi.ogg";
        QuizGenerator gen = new QuizGenerator(content, new Random(3));
        QuizGenerator.QuizQuestion q = gen.generate(bare);
        assertEquals(4, q.options().size());
        List<String> sameTheme = List.of("一", "二", "三", "四", "五", "六");
        long fromTheme = q.options().stream().filter(sameTheme::contains).count();
        assertEquals(4, fromTheme);   // 全部来自数字主题
    }
}
