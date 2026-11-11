package com.littlestar.app.feature.home;

import android.content.SharedPreferences;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;
import com.google.gson.Gson;
import com.littlestar.app.AppExecutors;
import com.littlestar.app.data.content.ContentRepository;
import com.littlestar.app.data.prefs.SettingsStore;
import com.littlestar.app.data.repo.ProgressRepository;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.data.store.ProgressStore;
import com.littlestar.app.domain.ReviewScheduler;
import com.littlestar.app.domain.SessionBuilder;
import com.littlestar.app.util.FakeClock;
import com.littlestar.app.util.FileStateFile;
import com.littlestar.app.util.TestContent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import static org.junit.Assert.*;

public final class HomeViewModelTest {
    @Rule public InstantTaskExecutorRule liveData = new InstantTaskExecutorRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private final Map<String, Object> prefs = new HashMap<>();
    private final FakeClock clock = new FakeClock(LocalDateTime.of(2026, 11, 7, 8, 0));
    private final ContentRepository content = TestContent.load();
    private final AppExecutors executors = new AppExecutors(Runnable::run, Runnable::run);
    private final SessionBuilder builder = new SessionBuilder(content, new ReviewScheduler(), clock,
            new Random(42));
    private ProgressStore store;
    private SettingsStore settings;

    @Before public void setup() throws Exception {
        prefs.put(SettingsStore.Keys.DAILY_GOAL, 5);
        prefs.put(SettingsStore.Keys.ONBOARDED, true);
        settings = new SettingsStore((SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("get")) {
                        return prefs.getOrDefault(args[0], args[1]);
                    }
                    throw new UnsupportedOperationException(method.getName());
                }));
        store = new ProgressStore(new FileStateFile(tmp.newFolder()), new Gson(), clock, executors, 60_000);
        store.ensureLoaded();
    }

    private void learnFirst(int count) {
        store.update(state -> {
            for (var word : content.wordsInOrder().subList(0, count)) {
                var wp = state.words.computeIfAbsent(word.id, key -> new ProgressState.WordProgress());
                ReviewScheduler.markLearned(wp, clock.now(), clock.today());
            }
        });
    }

    private void observe(java.util.function.Consumer<HomeViewModel> test) {
        var vm = new HomeViewModel(settings, content,
                new ProgressRepository(store, content, clock), builder, executors);
        Observer<HomeViewModel.UiState> observer = state -> {};
        vm.state().observeForever(observer);
        try {
            test.accept(vm);
        } finally {
            vm.state().removeObserver(observer);
        }
    }

    @Test public void eveningKeepsTheSameDailyPractice() {
        observe(vm -> {
            var morning = vm.state().getValue();
            clock.advance(Duration.ofHours(12));
            vm.refresh();
            var evening = vm.state().getValue();
            assertNotNull(evening.nextWord);
            assertEquals(morning.nextWord.id, evening.nextWord.id);
            assertEquals(morning.goal, evening.goal);
            assertEquals(morning.learnedToday, evening.learnedToday);
            assertFalse(evening.isDailyComplete());
        });
    }

    @Test public void lastWordCompletesActualPlanWithoutRewritingGoal() {
        int size = content.wordsInOrder().size();
        learnFirst(size - 1);
        store.update(state -> builder.todayPlan(state, 5));
        observe(vm -> {
            assertEquals(1, vm.state().getValue().goal);
            assertFalse(vm.state().getValue().isDailyComplete());
            learnFirst(size);
            var ui = vm.state().getValue();
            assertEquals(1, ui.learnedToday);
            assertEquals(1, ui.goal);
            assertTrue(ui.allDone);
            assertTrue(ui.isDailyComplete());
            assertEquals(5, settings.getDailyGoal());
            assertEquals(5, (int) store.readBlocking(state ->
                    state.dailyPlans.get(clock.today().toString()).goal));
        });
    }

    @Test public void allLearnedWithoutPlan_isCompleteAndReadOnly() {
        learnFirst(content.wordsInOrder().size());
        observe(vm -> {
            assertTrue(vm.state().getValue().allDone);
            assertTrue(vm.state().getValue().isDailyComplete());
            assertEquals(0, vm.state().getValue().goal);
            assertNotNull(vm.state().getValue().nextWord);
            vm.refresh();
            assertTrue(store.readBlocking(state -> state.dailyPlans.isEmpty()));
        });
    }

    @Test public void ordinaryCompletionAndGoalChangeKeepTodayFrozen() {
        store.update(state -> builder.todayPlan(state, 3));
        observe(vm -> {
            assertEquals(3, vm.state().getValue().goal);
            learnFirst(3);
            assertFalse(vm.state().getValue().allDone);
            assertTrue(vm.state().getValue().isDailyComplete());
            prefs.put(SettingsStore.Keys.DAILY_GOAL, 8);
            vm.refresh();
            assertEquals(3, vm.state().getValue().goal);
            clock.advance(Duration.ofDays(1));
            vm.refresh();
            assertEquals(8, vm.state().getValue().goal);
            assertFalse(vm.state().getValue().isDailyComplete());
            assertEquals(1, (int) store.readBlocking(state -> state.dailyPlans.size()));
        });
    }
}
