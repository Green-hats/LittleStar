package com.littlestar.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.littlestar.app.core.audio.SfxPlayer;
import com.littlestar.app.core.ui.BlockLayout;
import com.littlestar.app.core.ui.SongProgressView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class CoreUiRegressionTest {

    private Context themed;

    @Before
    public void setUp() {
        themed = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
                R.style.Theme_LittleStar_Child);
    }

    @Test
    public void blockPress_firesOnEachEnabledPressEdge() {
        AppContainer container =
                ((LittleStarApp) ApplicationProvider.getApplicationContext()).container();
        AtomicInteger taps = new AtomicInteger();
        try {
            onMain(() -> BlockLayout.setPressSoundHook(taps::incrementAndGet));
            onMain(() -> {
                BlockLayout block = new BlockLayout(themed);
                block.setClickable(true);
                block.setPressed(true);
                block.setSelected(true);
                block.setPressed(false);
                block.setPressed(true);
                assertEquals(2, taps.get());
            });
            onMain(() -> {
                BlockLayout block = new BlockLayout(themed);
                block.setClickable(true);
                block.setEnabled(false);
                block.setPressed(true);
                block.setPressed(false);
                assertEquals(2, taps.get());
            });
        } finally {
            onMain(() -> BlockLayout.setPressSoundHook(
                    () -> container.sfx.play(SfxPlayer.Sfx.TAP)));
        }
    }

    @Test
    public void blockPress_quickReleaseLeavesNoPendingAnimation() throws Exception {
        AtomicReference<BlockLayout> ref = new AtomicReference<>();
        onMain(() -> {
            BlockLayout block = new BlockLayout(themed);
            block.setClickable(true);
            block.setPressed(true);
            block.setPressed(false);
            ref.set(block);
        });
        SystemClock.sleep(260);
        onMain(() -> assertEquals(0f, pressFraction(ref.get()), 0.001f));
    }

    @Test
    public void blockShadowOffset_adjustsReservedPadding() {
        float newOffset = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2,
                themed.getResources().getDisplayMetrics());
        onMain(() -> {
            BlockLayout block = new BlockLayout(themed);
            float oldOffset = block.getShadowOffset();
            int left = block.getPaddingLeft();
            int top = block.getPaddingTop();
            int right = block.getPaddingRight();
            int bottom = block.getPaddingBottom();
            int delta = (int) newOffset - (int) oldOffset;
            block.setShadowOffset(newOffset);
            assertEquals(left, block.getPaddingLeft());
            assertEquals(top, block.getPaddingTop());
            assertEquals(right + delta, block.getPaddingRight());
            assertEquals(bottom + delta, block.getPaddingBottom());
            int settledRight = block.getPaddingRight();
            int settledBottom = block.getPaddingBottom();
            block.setShadowOffset(newOffset);
            assertEquals(settledRight, block.getPaddingRight());
            assertEquals(settledBottom, block.getPaddingBottom());
        });
    }

    @Test
    public void blockAccessibility_classIsButtonOnlyWhenClickable() {
        onMain(() -> {
            BlockLayout block = new BlockLayout(themed);
            block.setClickable(true);
            assertEquals(Button.class.getName(), block.getAccessibilityClassName());
            block.setClickable(false);
            assertNotEquals(Button.class.getName(), block.getAccessibilityClassName());
        });
    }

    @Test
    public void progressCancel_restoresPlaybackPositionAndReleasesParent() throws Exception {
        AtomicReference<SongProgressView> viewRef = new AtomicReference<>();
        SeekLog log = new SeekLog();
        onMain(() -> {
            InterceptFrame parent = new InterceptFrame(themed);
            SongProgressView view = progress(parent, 300);
            view.setOnSeek(log);
            view.setProgress(2000, 10000);
            long now = SystemClock.uptimeMillis();
            assertTrue(touch(view, now, MotionEvent.ACTION_DOWN, 150));
            assertTrue(parent.disallow);
            touch(view, now, MotionEvent.ACTION_MOVE, 200);
            view.setProgress(3000, 10000);
            touch(view, now, MotionEvent.ACTION_CANCEL, 0);
            assertFalse(parent.disallow);
            viewRef.set(view);
        });
        assertTrue(log.values.isEmpty());
        assertEquals(3000, longField(viewRef.get(), "positionMs"));
    }

    @Test
    public void progressUp_seeksOnceAndReleasesParent() {
        SeekLog log = new SeekLog();
        onMain(() -> {
            InterceptFrame parent = new InterceptFrame(themed);
            SongProgressView view = progress(parent, 300);
            view.setOnSeek(log);
            view.setProgress(0, 10000);
            long now = SystemClock.uptimeMillis();
            float right = view.getWidth()
                    - view.getResources().getDimension(R.dimen.ls_thumb) / 2f;
            touch(view, now, MotionEvent.ACTION_DOWN, 0);
            assertTrue(parent.disallow);
            touch(view, now, MotionEvent.ACTION_MOVE, 150);
            assertTrue(touch(view, now, MotionEvent.ACTION_UP, right));
            assertFalse(parent.disallow);
        });
        assertEquals(List.of(10000L), log.values);
    }

    @Test
    public void progressDisabledOrEmpty_ignoresTouchesAndCancelsDrag() {
        SeekLog log = new SeekLog();
        onMain(() -> {
            InterceptFrame parent = new InterceptFrame(themed);
            SongProgressView view = progress(parent, 300);
            view.setOnSeek(log);
            view.setProgress(0, 0);
            long now = SystemClock.uptimeMillis();
            assertFalse(touch(view, now, MotionEvent.ACTION_DOWN, 150));
            view.setProgress(500, 10000);
            view.setEnabled(false);
            assertFalse(touch(view, now, MotionEvent.ACTION_DOWN, 150));
            view.setEnabled(true);
            assertTrue(touch(view, now, MotionEvent.ACTION_DOWN, 150));
            assertTrue(parent.disallow);
            view.setEnabled(false);
            assertFalse(parent.disallow);
            touch(view, now, MotionEvent.ACTION_UP, 150);
            assertTrue(log.values.isEmpty());
        });
    }

    @Test
    public void progressTooNarrow_ignoresTouches() {
        SeekLog log = new SeekLog();
        onMain(() -> {
            InterceptFrame parent = new InterceptFrame(themed);
            int thumb = themed.getResources().getDimensionPixelSize(R.dimen.ls_thumb);
            SongProgressView view = progress(parent, thumb);
            view.setOnSeek(log);
            view.setProgress(0, 10000);
            long now = SystemClock.uptimeMillis();
            assertFalse(touch(view, now, MotionEvent.ACTION_DOWN, 0));
            assertTrue(log.values.isEmpty());
        });
    }

    @Test
    public void progressSetProgress_clampsAndMeasuresMinHeight() throws Exception {
        AtomicReference<SongProgressView> ref = new AtomicReference<>();
        onMain(() -> {
            SongProgressView view = new SongProgressView(themed);
            view.setProgress(-5, 10000);
            ref.set(view);
        });
        assertEquals(0, longField(ref.get(), "positionMs"));
        onMain(() -> ref.get().setProgress(99999, 10000));
        assertEquals(10000, longField(ref.get(), "positionMs"));
        onMain(() -> ref.get().setProgress(-5, -3));
        assertEquals(0, longField(ref.get(), "positionMs"));
        onMain(() -> {
            SongProgressView view = ref.get();
            view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            assertTrue(view.getMeasuredHeight()
                    >= themed.getResources().getDimensionPixelSize(R.dimen.ls_btn_icon));
            assertTrue(view.performClick());
        });
    }

    private static SongProgressView progress(FrameLayout parent, int widthPx) {
        SongProgressView view = new SongProgressView(parent.getContext());
        parent.addView(view);
        int height = parent.getResources().getDimensionPixelSize(R.dimen.ls_btn_icon);
        view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, widthPx, height);
        return view;
    }

    private static boolean touch(SongProgressView view, long now, int action, float x) {
        MotionEvent event = MotionEvent.obtain(now, now, action, x, 0, 0);
        try {
            return view.onTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static float pressFraction(BlockLayout block) {
        try {
            Field field = BlockLayout.class.getDeclaredField("pressFraction");
            field.setAccessible(true);
            return field.getFloat(block);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static long longField(SongProgressView target, String name) throws Exception {
        Field field = SongProgressView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getLong(target);
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static final class InterceptFrame extends FrameLayout {
        boolean disallow;

        InterceptFrame(Context context) {
            super(context);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            disallow = disallowIntercept;
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    private static final class SeekLog implements SongProgressView.OnSeek {
        final List<Long> values = new ArrayList<>();

        @Override
        public void onSeek(long positionMs) {
            values.add(positionMs);
        }
    }
}
