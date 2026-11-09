package com.littlestar.app.data.content;

import com.littlestar.app.data.content.model.CultureItem;
import com.littlestar.app.data.content.model.LyricLine;

/** Keeps each teaching line with its pronunciation and English meaning. */
public final class CultureReadingText {
    private CultureReadingText() {
    }

    public static String format(CultureItem item) {
        if (item.lyrics != null && !item.lyrics.isEmpty()) {
            StringBuilder text = new StringBuilder();
            for (LyricLine line : item.lyrics) {
                if (text.length() > 0) {
                    text.append("\n\n");
                }
                text.append(line.hanzi).append('\n')
                        .append(line.pinyin).append('\n').append(line.english);
            }
            return text.toString();
        }
        String chinese = item.transcript == null ? "" : item.transcript;
        if (item.transcriptEn == null || item.transcriptEn.isBlank()) {
            return chinese;
        }
        return chinese.isBlank() ? item.transcriptEn : chinese + "\n\n" + item.transcriptEn;
    }
}
