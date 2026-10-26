package com.littlestar.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.littlestar.app.data.content.ContentRepository;
import com.littlestar.app.data.content.model.Theme;
import com.littlestar.app.data.content.model.Word;
import com.littlestar.app.util.TestContent;

import org.junit.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 解析真实 words.json / culture.json。 */
public final class ContentRepositoryTest {

    @Test
    public void parsesRealContent_idsUnique() {
        ContentRepository repo = TestContent.load();
        List<Theme> themes = repo.themes();
        assertEquals(6, themes.size());
        Set<String> ids = new HashSet<>();
        for (Theme t : themes) {
            assertEquals(6, t.words.size());
            for (Word w : t.words) {
                assertTrue("重复 id: " + w.id, ids.add(w.id));
                assertNotNull(w.hanzi);
                assertNotNull(w.pinyin);
                assertEquals(t.id, repo.themeIdOf(w.id));
            }
        }
        assertEquals(36, ids.size());
        assertEquals(3, repo.mvpCategories().size());
        assertEquals(11, repo.itemsOfCategory("songs").size()
                + repo.itemsOfCategory("stories").size()
                + repo.itemsOfCategory("poems").size());
        assertNotNull(repo.itemById("xiaoxingxing"));
    }

    @Test
    public void featuredToday_matchesDateRange() {
        ContentRepository repo = TestContent.load();
        assertTrue(repo.featuredToday(LocalDate.of(2026, 10, 21)).isEmpty());   // MVP 无 featured
    }
}
