package com.littlestar.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.littlestar.app.AppExecutors;
import com.littlestar.app.data.repo.ProgressRepository;
import com.littlestar.app.data.store.AtomicStateFile;
import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.data.store.ProgressStore;
import com.littlestar.app.data.store.StateFile;
import com.littlestar.app.util.FakeClock;
import com.littlestar.app.util.TestContent;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Real disk/callback executors; LiveData dispatch is synchronous, not an Android main looper. */
public final class ProgressStoreAsyncTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    @Rule
    public InstantTaskExecutorRule liveData = new InstantTaskExecutorRule();

    private final Gson gson = new Gson();
    private final FakeClock clock = new FakeClock(LocalDateTime.of(2026, 10, 21, 8, 0));
    private final AtomicReference<Thread> diskThread = new AtomicReference<>();
    private final AtomicReference<Thread> callbackThread = new AtomicReference<>();
    private ExecutorService disk;
    private ExecutorService callbacks;
    private RecordingStateFile file;

    @Before
    public void setUp() {
        disk = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "test-progress-disk");
            diskThread.set(thread);
            return thread;
        });
        callbacks = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "test-progress-callback");
            callbackThread.set(thread);
            return thread;
        });
        file = new RecordingStateFile(new File(tmp.getRoot(), "progress.json"));
    }

    @After
    public void tearDown() throws Exception {
        disk.shutdown();
        callbacks.shutdown();
        try {
            assertTrue(disk.awaitTermination(5, TimeUnit.SECONDS));
            assertTrue(callbacks.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            disk.shutdownNow();
            callbacks.shutdownNow();
        }
    }

    private ProgressStore newStore(long debounceMs) {
        return new ProgressStore(file, gson, clock, new AppExecutors(disk, callbacks), debounceMs);
    }

    @Test
    public void queuedRecoveryAnswersAndCompletion_flushBeforeDebounceAndKeepSnapshots() throws Exception {
        Files.writeString(new File(tmp.getRoot(), "progress.bak.json").toPath(), """
                {"schemaVersion":3,"nextSessionId":8,
                 "words":{"mao":{"learnedAt":123,"box":2}},
                 "sessions":[{"id":7,"date":"2026-10-21","kind":"DAILY","total":3,
                   "quizWordIds":["mao","gou","niao"]}]}
                """);
        ProgressStore store = newStore(60_000);
        List<ProgressState> snapshots = new CopyOnWriteArrayList<>();
        Observer<ProgressState> observer = snapshots::add;
        store.observe().observeForever(observer);
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> gate = disk.submit(() -> {
            ready.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        });
        try {
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            store.loadAsync();
            var content = TestContent.load();
            ProgressRepository progress = new ProgressRepository(store, content, clock);
            for (String id : List.of("mao", "gou", "niao")) {
                progress.recordAnswer(7, id, content.wordById(id).hanzi, true, true);
            }
            progress.completeSession(7);
            CompletableFuture<ProgressState> result = new CompletableFuture<>();
            AtomicReference<Thread> callbackUsed = new AtomicReference<>();
            store.read(state -> state.deepCopy(gson), state -> {
                callbackUsed.set(Thread.currentThread());
                result.complete(state);
            });
            release.countDown();
            gate.get(5, TimeUnit.SECONDS);
            ProgressState state = result.get(5, TimeUnit.SECONDS);
            assertSame(callbackThread.get(), callbackUsed.get());
            assertEquals(3, state.sessions.get(0).answers.size());
            assertEquals(3, state.sessions.get(0).correct);
            assertEquals(Long.valueOf(clock.now().toEpochMilli()), state.sessions.get(0).completedAt);
            assertEquals(Long.valueOf(123), state.words.get("mao").learnedAt);
            assertTrue(snapshots.get(0).sessions.get(0).answers.isEmpty());
            assertTrue(snapshots.get(0).sessions.get(0).completedAt == null);

            String persisted = disk.submit(file::read).get(5, TimeUnit.SECONDS);
            assertEquals(JsonParser.parseString(gson.toJson(state)), JsonParser.parseString(persisted));
            ProgressStore reloaded = newStore(60_000);
            ProgressState restored = disk.submit(() -> reloaded.readBlocking(s -> s.deepCopy(gson)))
                    .get(5, TimeUnit.SECONDS);
            assertEquals(JsonParser.parseString(persisted), JsonParser.parseString(gson.toJson(restored)));
            assertEquals(Set.of(diskThread.get()), file.accessThreads);
        } finally {
            release.countDown();
            store.observe().removeObserver(observer);
        }
    }

    @Test
    public void debouncedSave_persistsLastQueuedMutationWithoutFlush() throws Exception {
        ProgressStore store = newStore(30);
        store.loadAsync();
        for (int i = 0; i < 100; i++) {
            store.update(state -> state.nextSessionId++);
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        String persisted;
        do {
            persisted = file.writes.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            assertNotNull("Timed out waiting for the final debounced write", persisted);
        } while (JsonParser.parseString(persisted).getAsJsonObject()
                .get("nextSessionId").getAsLong() != 101);

        ProgressStore reloaded = newStore(60_000);
        long restored = disk.submit(() -> reloaded.readBlocking(state -> state.nextSessionId))
                .get(5, TimeUnit.SECONDS);
        assertEquals(101, restored);
        assertEquals(Set.of(diskThread.get()), file.accessThreads);
    }

    @Test
    public void concurrentCallers_mutationsAreSerializedBeforeFlush() throws Exception {
        ProgressStore store = newStore(60_000);
        store.loadAsync();
        ExecutorService callers = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                tasks.add(() -> {
                    for (int j = 0; j < 25; j++) {
                        store.update(state -> state.nextSessionId++);
                    }
                    return null;
                });
            }
            for (Future<Void> caller : callers.invokeAll(tasks, 5, TimeUnit.SECONDS)) {
                caller.get(5, TimeUnit.SECONDS);
            }
        } finally {
            callers.shutdownNow();
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
        }
        store.flushNow();
        String persisted = disk.submit(file::read).get(5, TimeUnit.SECONDS);
        assertEquals(101, JsonParser.parseString(persisted).getAsJsonObject()
                .get("nextSessionId").getAsLong());
        assertEquals(Set.of(diskThread.get()), file.accessThreads);
    }

    private static final class RecordingStateFile implements StateFile {
        private final AtomicStateFile delegate;
        private final Set<Thread> accessThreads = ConcurrentHashMap.newKeySet();
        private final BlockingQueue<String> writes = new LinkedBlockingQueue<>();

        private RecordingStateFile(File base) {
            delegate = new AtomicStateFile(base);
        }

        @Override
        public String read() {
            accessThreads.add(Thread.currentThread());
            return delegate.read();
        }

        @Override
        public void write(String content) {
            accessThreads.add(Thread.currentThread());
            delegate.write(content);
            writes.add(content);
        }

        @Override
        public void rename(String suffix) {
            accessThreads.add(Thread.currentThread());
            delegate.rename(suffix);
        }

        @Override
        public String readBackup() {
            accessThreads.add(Thread.currentThread());
            return delegate.readBackup();
        }

        @Override
        public void writeBackup(String content) {
            accessThreads.add(Thread.currentThread());
            delegate.writeBackup(content);
        }
    }
}
