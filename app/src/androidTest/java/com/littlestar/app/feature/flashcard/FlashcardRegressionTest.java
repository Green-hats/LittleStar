package com.littlestar.app.feature.flashcard;

import static org.junit.Assert.*;

import android.content.Intent;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.viewpager2.widget.ViewPager2;

import com.google.gson.Gson;
import com.littlestar.app.AppContainer;
import com.littlestar.app.LittleStarApp;
import com.littlestar.app.R;
import com.littlestar.app.data.store.ProgressState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** 持久化设备回归；只在隔离的测试安装运行，结束后还原原有进度。 */
@RunWith(AndroidJUnit4.class)
public final class FlashcardRegressionTest {
    private AppContainer container;
    private LittleStarApp app;
    private ProgressState original;
    private List<String> ids;

    @Before
    public void prepare() throws Exception {
        app = (LittleStarApp) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        container = app.container();
        original = read(progress -> progress.deepCopy(new Gson()));
        CountDownLatch ready = new CountDownLatch(1);
        container.executors.diskIO().execute(() -> {
            container.content.ensureLoaded();
            ids = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                ids.add(container.content.wordsInOrder().get(i).id);
            }
            ready.countDown();
        });
        await(ready);
        container.store.update(progress -> {
            progress.words.clear();
            progress.sessions.clear();
            progress.dailyPlans.clear();
            progress.nextSessionId = 1;
            ProgressState.DailyPlan plan = new ProgressState.DailyPlan();
            plan.goal = 3;
            plan.newWordIds.addAll(ids);
            progress.dailyPlans.put(container.clock.today().toString(), plan);
        });
        read(progress -> progress.sessions.size()); // 串行 diskIO barrier
    }

    @After
    public void restore() throws Exception {
        if (original == null) return;
        container.store.update(progress -> {
            progress.words = original.words;
            progress.dailyPlans = original.dailyPlans;
            progress.sessions = original.sessions;
            progress.nextSessionId = original.nextSessionId;
            progress.usage = original.usage;
        });
        container.store.flushNow();
        read(progress -> progress.sessions.size());
    }

    @Test
    public void sessionRecreateRetainsIdCardsAndIndexWithoutNewSession() throws Exception {
        try (ActivityScenario<FlashcardActivity> scenario = ActivityScenario.launch(sessionIntent())) {
            FlashcardViewModel before = awaitCards(scenario);
            long id = before.sessionId();
            scenario.onActivity(activity -> before.onPageSelected(1));
            scenario.recreate();
            FlashcardViewModel after = awaitCards(scenario);
            assertSame(before, after);
            assertEquals(id, after.sessionId());
            assertEquals(1, after.currentIndex());
            assertEquals(ids, wordIds(after));
            scenario.onActivity(activity -> assertEquals(1,
                    ((ViewPager2) activity.findViewById(R.id.pager)).getCurrentItem()));
            assertEquals(Integer.valueOf(1), read(progress -> progress.sessions.size()));
            assertEquals(Long.valueOf(id), read(progress -> progress.sessions.get(0).id));
        }
    }

    @Test
    public void browseRecreateRetainsCardsAndIndexWithoutCreatingSession() throws Exception {
        Intent intent = new Intent(app, FlashcardActivity.class)
                .putExtra("mode", "BROWSE")
                .putStringArrayListExtra("wordIds", new ArrayList<>(ids))
                .putExtra("startIndex", 1);
        try (ActivityScenario<FlashcardActivity> scenario = ActivityScenario.launch(intent)) {
            FlashcardViewModel before = awaitCards(scenario);
            scenario.onActivity(activity -> before.onPageSelected(2));
            scenario.recreate();
            FlashcardViewModel after = awaitCards(scenario);
            assertSame(before, after);
            assertEquals(-1, after.sessionId());
            assertEquals(2, after.currentIndex());
            assertEquals(ids, wordIds(after));
            scenario.onActivity(activity -> assertEquals(2,
                    ((ViewPager2) activity.findViewById(R.id.pager)).getCurrentItem()));
            assertEquals(Integer.valueOf(0), read(progress -> progress.sessions.size()));
        }
    }

    @Test
    public void recreateWhileInitializationIsQueuedCreatesExactlyOneSession() throws Exception {
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch buildBlocked = new CountDownLatch(1);
        CountDownLatch releaseBuild = new CountDownLatch(1);
        container.executors.diskIO().execute(() -> {
            blocked.countDown();
            try {
                if (!release.await(30, TimeUnit.SECONDS)) throw new AssertionError("disk gate timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        });
        await(blocked);
        try (ActivityScenario<FlashcardActivity> scenario = ActivityScenario.launch(sessionIntent())) {
            // content load 已入队：在它之后、store.update 之前再设 gate，精确覆盖建会话在途。
            container.executors.diskIO().execute(() -> {
                buildBlocked.countDown();
                try {
                    if (!releaseBuild.await(30, TimeUnit.SECONDS)) throw new AssertionError("build gate timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            });
            release.countDown();
            await(buildBlocked);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicReference<FlashcardViewModel> before = new AtomicReference<>();
            scenario.onActivity(activity -> {
                before.set(vm(activity));
                assertFalse(before.get().isInitialized());
                assertEquals(-1, before.get().sessionId());
            });
            scenario.recreate();
            releaseBuild.countDown();
            FlashcardViewModel after = awaitCards(scenario);
            assertSame(before.get(), after);
            assertTrue(after.sessionId() > 0);
            // read 入队在所有初始化任务之后，亦能捕获延后创建的重复会话。
            assertEquals(Integer.valueOf(1), read(progress -> progress.sessions.size()));
            assertEquals(Long.valueOf(2), read(progress -> progress.nextSessionId));
            scenario.recreate();
            assertEquals(after.sessionId(), awaitCards(scenario).sessionId());
            assertEquals(Integer.valueOf(1), read(progress -> progress.sessions.size()));
        } finally {
            release.countDown();
            releaseBuild.countDown();
        }
    }

    @Test
    public void freshViewModelRestoresKnownSessionEvenWhenSnapshotIsNull() throws Exception {
        long id;
        try (ActivityScenario<FlashcardActivity> scenario = ActivityScenario.launch(sessionIntent())) {
            id = awaitCards(scenario).sessionId();
        }
        AtomicReference<FlashcardViewModel> fresh = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            // 模拟进程恢复时 store 尚未发布首份快照；不能据此判定 session 缺失。
            ((MutableLiveData<ProgressState>) container.store.observe()).setValue(null);
            fresh.set(new FlashcardViewModel(container));
            fresh.get().initialize(FlashcardViewModel.Mode.SESSION, List.of(), 1, id);
        });
        awaitCards(fresh.get());
        assertEquals(id, fresh.get().sessionId());
        assertEquals(1, fresh.get().currentIndex());
        assertEquals(ids, wordIds(fresh.get()));
        assertEquals(Integer.valueOf(1), read(progress -> progress.sessions.size()));
    }

    private Intent sessionIntent() {
        return new Intent(app, FlashcardActivity.class).putExtra("mode", "SESSION");
    }

    private static FlashcardViewModel vm(FlashcardActivity activity) {
        return new ViewModelProvider(activity).get(FlashcardViewModel.class);
    }

    private FlashcardViewModel awaitCards(ActivityScenario<FlashcardActivity> scenario) throws Exception {
        AtomicReference<FlashcardViewModel> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(vm(activity)));
        awaitCards(result.get());
        return result.get();
    }

    private void awaitCards(FlashcardViewModel vm) throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Observer<FlashcardViewModel.UiState> observer = state -> {
            if (state != null && !state.words.isEmpty()) ready.countDown();
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> vm.state().observeForever(observer));
        try {
            await(ready);
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> vm.state().removeObserver(observer));
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static List<String> wordIds(FlashcardViewModel vm) {
        List<String> result = new ArrayList<>();
        for (var word : vm.state().getValue().words) result.add(word.id);
        return result;
    }

    private <T> T read(Function<ProgressState, T> query) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        container.store.read(query, value -> {
            result.set(value);
            done.countDown();
        });
        await(done);
        return result.get();
    }

    private static void await(CountDownLatch latch) throws Exception {
        assertTrue("asynchronous work timed out", latch.await(15, TimeUnit.SECONDS));
    }
}
