package com.littlestar.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.littlestar.app.data.content.model.CultureItem;
import com.littlestar.app.feature.culture.CultureCategoriesActivity;
import com.littlestar.app.feature.culture.CultureDetailActivity;
import com.littlestar.app.feature.culture.CultureListActivity;
import com.littlestar.app.feature.home.HomeActivity;
import com.littlestar.app.feature.player.PlayerActivity;
import com.littlestar.app.playback.PlaybackService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public final class LanguageSupportTest {
    private Context context;
    private AppContainer container;
    private boolean onboarded;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        container = ((LittleStarApp) context).container();
        container.content.ensureLoaded();
        onboarded = container.settings.isOnboarded();
        container.settings.setOnboarded(true);
    }

    @After
    public void tearDown() {
        container.settings.setOnboarded(onboarded);
        context.stopService(new Intent(context, PlaybackService.class));
    }

    @Test
    public void homeHasEnglishPracticeAndDistinctBrowsingEntries() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            waitForText(scenario, R.id.moduleWordsTitle, "Browse words");
            AtomicBoolean hasMeaning = new AtomicBoolean();
            long deadline = SystemClock.uptimeMillis() + 5000;
            do {
                scenario.onActivity(activity -> {
                    TextView english = activity.findViewById(R.id.heroEnglish);
                    TextView hanzi = activity.findViewById(R.id.heroHanzi);
                    for (var word : container.content.wordsInOrder()) {
                        if (english.isShown() && word.english.contentEquals(english.getText())
                                && word.hanzi.contentEquals(hanzi.getText())) {
                            hasMeaning.set(true);
                        }
                    }
                });
                if (hasMeaning.get()) break;
                SystemClock.sleep(50);
            } while (SystemClock.uptimeMillis() < deadline);
            assertTrue("Daily practice needs an English meaning", hasMeaning.get());
            onView(withId(R.id.moduleWordsIcon)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.moduleCultureIcon)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.moduleCultureTitle)).check(matches(withText("Listen & read")));
        }
    }

    @Test
    public void everyCultureCategoryShowsEnglishTitlesAndOriginals() {
        for (var category : container.content.mvpCategories()) {
            try (ActivityScenario<CultureListActivity> scenario = ActivityScenario.launch(
                    new Intent(context, CultureListActivity.class).putExtra("categoryId", category.id))) {
                waitForText(scenario, R.id.categoryName, category.nameEn);
                var items = container.content.itemsOfCategory(category.id);
                for (int index = 0; index < items.size(); index++) {
                    int position = index;
                    scenario.onActivity(activity -> ((RecyclerView) activity.findViewById(R.id.itemList))
                            .scrollToPosition(position));
                    onView(withText(items.get(index).titleEn)).check(matches(isDisplayed()));
                    onView(withText(items.get(index).titleZh)).check(matches(isDisplayed()));
                }
            }
        }
    }

    @Test
    public void allDetailsKeepEnglishMeaningInExpandedReading() {
        for (var category : container.content.mvpCategories()) {
            for (CultureItem item : container.content.itemsOfCategory(category.id)) {
                try (ActivityScenario<CultureDetailActivity> scenario = ActivityScenario.launch(
                        new Intent(context, CultureDetailActivity.class).putExtra("itemId", item.id))) {
                    waitForText(scenario, R.id.detailTitleEn, item.titleEn);
                    onView(withId(R.id.detailTitleEn)).check(matches(isDisplayed()));
                    onView(withId(R.id.previewLabel)).check(matches(
                            withText(R.string.culture_detail_read_together)));
                    onView(withId(R.id.btnPreview)).perform(scrollTo(), click());
                    waitForText(scenario, R.id.previewLabel,
                            context.getString(R.string.culture_detail_hide_text));
                    scenario.onActivity(activity -> {
                        TextView text = activity.findViewById(R.id.detailTranscript);
                        assertEquals(View.VISIBLE, text.getVisibility());
                        String preview = text.getText().toString();
                        if (item.lyrics.isEmpty()) {
                            assertTrue(preview.contains(item.transcript));
                            assertTrue(preview.contains(item.transcriptEn));
                        } else {
                            for (var line : item.lyrics) {
                                assertTrue(preview.contains(line.hanzi + "\n" + line.pinyin + "\n" + line.english));
                            }
                        }
                    });
                }
            }
        }
    }

    @Test
    public void storyPlayerHasVisibleEnglishWithoutTimedLyrics() {
        CultureItem item = container.content.itemById("changebenyue");
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                new Intent(context, PlayerActivity.class).putExtra("itemId", item.id))) {
            waitForText(scenario, R.id.playerTitle, item.titleEn);
            onView(withId(R.id.playerTitle)).check(matches(isDisplayed()));
            onView(withId(R.id.songTitle)).check(matches(withText(item.titleZh)));
            onView(withId(R.id.playerSummary)).check(matches(withText(item.summary)));
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.lyricsCard).getVisibility());
                assertTrue(((TextView) activity.findViewById(R.id.playLabel)).getText().length() > 0);
            });
        }
    }

    @Test
    public void navigationLabelsAndDecorativeGlyphsFit() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            waitForText(scenario, R.id.moduleWordsTitle, "Browse words");
            scenario.onActivity(activity -> {
                assertWholeWord(activity.findViewById(R.id.moduleWordsTitle));
                assertWholeWord(activity.findViewById(R.id.moduleCultureTitle));
            });
        }
        try (ActivityScenario<CultureCategoriesActivity> scenario =
                     ActivityScenario.launch(CultureCategoriesActivity.class)) {
            waitForText(scenario, R.id.nameSongsEn, "Songs");
            scenario.onActivity(activity -> {
                assertWholeWord(activity.findViewById(R.id.nameSongsEn));
                assertWholeWord(activity.findViewById(R.id.nameStoriesEn));
                assertWholeWord(activity.findViewById(R.id.namePoemsEn));
                assertEquals(View.VISIBLE, activity.findViewById(R.id.iconSongs).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.iconStories).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.iconPoems).getVisibility());
            });
        }
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                new Intent(context, PlayerActivity.class).putExtra("itemId", "changebenyue"))) {
            waitForText(scenario, R.id.playerTitle, container.content.itemById("changebenyue").titleEn);
            scenario.onActivity(activity -> {
                assertWholeWord(activity.findViewById(R.id.previousLabel));
                assertWholeWord(activity.findViewById(R.id.playLabel));
                assertWholeWord(activity.findViewById(R.id.nextLabel));
            });
        }
    }

    private static void assertAbove(ViewGroup parent, View first, View second) {
        Rect firstBounds = new Rect();
        Rect secondBounds = new Rect();
        first.getDrawingRect(firstBounds);
        second.getDrawingRect(secondBounds);
        parent.offsetDescendantRectToMyCoords(first, firstBounds);
        parent.offsetDescendantRectToMyCoords(second, secondBounds);
        assertTrue("Category titles must not cover glyphs", firstBounds.bottom <= secondBounds.top);
    }

    private static void assertWholeWord(TextView view) {
        assertTrue("Control label needs text", view.length() > 0);
        assertEquals("Do not split control words", 1, view.getLineCount());
        assertEquals("Do not truncate control words", 0, view.getLayout().getEllipsisCount(0));
        assertTrue("Control word must fit its width", view.getPaint().measureText(view.getText().toString())
                <= view.getWidth() - view.getCompoundPaddingLeft() - view.getCompoundPaddingRight() + 1);
    }

    private static void assertGlyphFits(TextView view) {
        Rect ink = new Rect();
        String text = view.getText().toString();
        assertTrue("Decorative glyph needs text", !text.isBlank());
        view.getPaint().getTextBounds(text, 0, text.length(), ink);
        assertTrue("Glyph top must fit", view.getBaseline() + ink.top >= view.getPaddingTop());
        assertTrue("Glyph bottom must fit", view.getBaseline() + ink.bottom
                <= view.getHeight() - view.getPaddingBottom());
    }

    private static <T extends Activity> void waitForText(ActivityScenario<T> scenario,
                                                       int id, String expected) {
        AtomicBoolean ready = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            scenario.onActivity(activity -> {
                TextView view = activity.findViewById(id);
                ready.set(view != null && expected.contentEquals(view.getText())
                        && view.getWidth() > 0 && view.getLayout() != null);
            });
            if (ready.get()) {
                return;
            }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("English text did not load: " + expected);
    }
}
