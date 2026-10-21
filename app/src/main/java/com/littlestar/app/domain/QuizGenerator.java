package com.littlestar.app.domain;

import androidx.annotation.NonNull;

import com.littlestar.app.data.content.ContentRepository;
import com.littlestar.app.data.content.model.Word;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 出题（§7.4）：听音 + 看图 → 四选一选字。
 * 干扰项优先 word.distractors；不够 3 个从同主题补；再不够从其他主题补。
 */
public final class QuizGenerator {

    /** 一道题（record：不可变值对象）。 */
    public record QuizQuestion(@NonNull String wordId,
                               @NonNull String hanzi,
                               @NonNull String pinyin,
                               @NonNull String english,
                               @NonNull String image,
                               @NonNull List<String> options,
                               int correctIndex) {
    }

    private final ContentRepository content;
    private final Random random;

    public QuizGenerator(@NonNull ContentRepository content, @NonNull Random random) {
        this.content = content;
        this.random = random;
    }

    /** 为一个字出一道四选一题。 */
    @NonNull
    public QuizQuestion generate(@NonNull Word word) {
        content.ensureLoaded();
        Set<String> distractors = new LinkedHashSet<>();
        for (String d : word.distractors) {
            if (!d.equals(word.hanzi)) {
                distractors.add(d);
            }
        }
        if (distractors.size() < 3) {
            String themeId = content.themeIdOf(word.id);
            if (themeId != null) {
                List<Word> sameTheme = new ArrayList<>(content.wordsOfTheme(themeId));
                Collections.shuffle(sameTheme, random);
                for (Word w : sameTheme) {
                    if (distractors.size() >= 3) {
                        break;
                    }
                    if (!w.hanzi.equals(word.hanzi)) {
                        distractors.add(w.hanzi);
                    }
                }
            }
        }
        if (distractors.size() < 3) {
            List<Word> all = new ArrayList<>(content.wordsInOrder());
            Collections.shuffle(all, random);
            for (Word w : all) {
                if (distractors.size() >= 3) {
                    break;
                }
                if (!w.hanzi.equals(word.hanzi)) {
                    distractors.add(w.hanzi);
                }
            }
        }

        List<String> picked = new ArrayList<>(distractors).subList(0, 3);
        List<String> options = new ArrayList<>(picked);
        options.add(word.hanzi);
        Collections.shuffle(options, random);
        int correctIndex = options.indexOf(word.hanzi);
        return new QuizQuestion(word.id, word.hanzi, word.pinyin, word.english,
                word.image, List.copyOf(options), correctIndex);
    }
}
