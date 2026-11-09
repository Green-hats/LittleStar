package com.littlestar.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.littlestar.app.data.content.CultureReadingText;
import com.littlestar.app.data.content.model.CultureItem;
import com.littlestar.app.data.content.model.LyricLine;

import org.junit.Test;

import java.util.List;

public final class CultureReadingTextTest {
    @Test
    public void lyricsKeepChinesePinyinAndEnglishTogetherInOrder() {
        CultureItem item = new CultureItem();
        item.lyrics = List.of(line("鹅鹅鹅", "é é é", "Goose, goose, goose"),
                line("曲项向天歌", "qū xiàng xiàng tiān gē", "Bent neck sings to the sky"));
        assertEquals("鹅鹅鹅\né é é\nGoose, goose, goose\n\n"
                        + "曲项向天歌\nqū xiàng xiàng tiān gē\nBent neck sings to the sky",
                CultureReadingText.format(item));
    }

    @Test
    public void storyKeepsBothSummariesWithoutInventingTimedLyrics() {
        CultureItem item = new CultureItem();
        item.transcript = "故事梗概（非逐字字幕）\n\n乌龟一直没有停下。";
        item.transcriptEn = "Story summary (not word-for-word subtitles)\n\nThe tortoise keeps going.";
        assertEquals(item.transcript + "\n\n" + item.transcriptEn, CultureReadingText.format(item));
        assertEquals(0, item.lyrics.size());
    }

    @Test
    public void missingOptionalTextDoesNotPrintNull() {
        CultureItem item = new CultureItem();
        item.transcript = "原文";
        assertEquals(item.transcript, CultureReadingText.format(item));
        assertFalse(CultureReadingText.format(item).contains("null"));
    }

    @Test
    public void missingChineseTextKeepsAvailableEnglish() {
        CultureItem item = new CultureItem();
        item.transcriptEn = "A story to read together.";
        assertEquals(item.transcriptEn, CultureReadingText.format(item));
    }

    private static LyricLine line(String hanzi, String pinyin, String english) {
        LyricLine line = new LyricLine();
        line.hanzi = hanzi;
        line.pinyin = pinyin;
        line.english = english;
        return line;
    }
}
