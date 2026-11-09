package com.littlestar.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.littlestar.app.feature.words.ThemesActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public final class ThemeMediaTest {
    @Test
    public void everyThemeBindsItsOwnImageWithoutCoveringEnglish() {
        LittleStarApp app = ApplicationProvider.getApplicationContext();
        app.container().content.ensureLoaded();
        var themes = app.container().content.themes();
        try (ActivityScenario<ThemesActivity> scenario = ActivityScenario.launch(ThemesActivity.class)) {
            for (int i = 0; i < themes.size(); i++) {
                int position = i;
                var theme = themes.get(i);
                waitForRow(scenario, position);
                scenario.onActivity(activity -> {
                    RecyclerView list = activity.findViewById(R.id.themesList);
                    View row = list.findViewHolderForAdapterPosition(position).itemView;
                    assertEquals(theme.nameEn, ((TextView) row.findViewById(R.id.themeNameEn))
                            .getText().toString());
                    assertTrue(row.findViewById(R.id.themeNameEn).isShown());
                    assertEquals(View.GONE, row.findViewById(R.id.themeGlyph).getVisibility());
                    ImageView image = row.findViewById(R.id.themeIllustration);
                    assertTrue(image.getDrawable() instanceof BitmapDrawable);
                    int resource = activity.getResources().getIdentifier(
                            theme.illustration, "drawable", activity.getPackageName());
                    var expected = BitmapFactory.decodeResource(activity.getResources(), resource);
                    assertNotNull(expected);
                    assertTrue(expected.sameAs(((BitmapDrawable) image.getDrawable()).getBitmap()));
                    expected.recycle();
                });
            }
        }
    }

    @Test
    public void missingImageFallsBackToGlyph() {
        LittleStarApp app = ApplicationProvider.getApplicationContext();
        app.container().content.ensureLoaded();
        var theme = app.container().content.themes().get(0);
        String original = theme.illustration;
        try {
            for (String missing : new String[]{null, "ill_placeholder", "ill_theme_missing"}) {
                theme.illustration = missing;
                try (ActivityScenario<ThemesActivity> scenario = ActivityScenario.launch(ThemesActivity.class)) {
                    waitForRow(scenario, 0);
                    scenario.onActivity(activity -> {
                        RecyclerView list = activity.findViewById(R.id.themesList);
                        View row = list.findViewHolderForAdapterPosition(0).itemView;
                        TextView glyph = row.findViewById(R.id.themeGlyph);
                        assertEquals(View.VISIBLE, glyph.getVisibility());
                        assertEquals(theme.glyph, glyph.getText().toString());
                        assertEquals(theme.nameEn, ((TextView) row.findViewById(R.id.themeNameEn))
                                .getText().toString());
                    });
                }
            }
        } finally {
            theme.illustration = original;
        }
    }

    private static void waitForRow(ActivityScenario<ThemesActivity> scenario, int position) {
        AtomicBoolean ready = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.themesList);
                list.scrollToPosition(position);
                var holder = list.findViewHolderForAdapterPosition(position);
                ready.set(holder != null && holder.itemView.getWidth() > 0);
            });
            if (ready.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        assertTrue("Theme row must be laid out", ready.get());
    }
}
