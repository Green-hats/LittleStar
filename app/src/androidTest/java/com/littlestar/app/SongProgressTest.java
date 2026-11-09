package com.littlestar.app;

import android.os.SystemClock;
import android.view.MotionEvent;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.littlestar.app.core.ui.SongProgressView;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class SongProgressTest {
    @Test public void cancelDoesNotSeekAndUpCommitsOnlyOnce() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SongProgressView view = new SongProgressView(ApplicationProvider.getApplicationContext());
            view.layout(0, 0, 400, 60);
            view.setProgress(1_000, 10_000);
            List<Long> seeks = new ArrayList<>();
            view.setOnSeek(seeks::add);
            touch(view, MotionEvent.ACTION_DOWN, 200);
            touch(view, MotionEvent.ACTION_MOVE, 300);
            touch(view, MotionEvent.ACTION_CANCEL, 300);
            assertTrue(seeks.isEmpty());
            // A standalone click reports accessibility/click feedback, not a new seek target.
            view.performClick();
            assertTrue(seeks.isEmpty());
            touch(view, MotionEvent.ACTION_DOWN, 200);
            touch(view, MotionEvent.ACTION_UP, 300);
            assertEquals(1, seeks.size());
            assertTrue(seeks.get(0) > 5_000);
        });
    }

    private static void touch(SongProgressView view, int action, float x) {
        MotionEvent event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                action, x, 30, 0);
        try {
            view.onTouchEvent(event);
        } finally {
            event.recycle();
        }
    }
}
