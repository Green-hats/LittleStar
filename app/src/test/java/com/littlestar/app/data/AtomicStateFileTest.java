package com.littlestar.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.core.util.AtomicFile;

import com.google.gson.Gson;
import com.littlestar.app.AppExecutors;
import com.littlestar.app.data.store.AtomicStateFile;
import com.littlestar.app.data.store.ProgressStore;
import com.littlestar.app.util.FakeClock;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;

public final class AtomicStateFileTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void writeThenRead_returnsCommittedContent() {
        AtomicStateFile file = new AtomicStateFile(new File(tmp.getRoot(), "progress.json"));
        file.write("{\"nextSessionId\":42}");

        assertEquals("{\"nextSessionId\":42}", file.read());
    }

    @Test
    public void missingMain_restoresLegacyAtomicBackup() throws Exception {
        File base = new File(tmp.getRoot(), "progress.json");
        File backup = new File(tmp.getRoot(), "progress.json.bak");
        Files.writeString(backup.toPath(), "{\"nextSessionId\":42}");

        AtomicStateFile file = new AtomicStateFile(base);
        assertEquals("{\"nextSessionId\":42}", file.read());
        assertTrue(base.exists());
        assertFalse(backup.exists());
    }

    @Test
    public void legacyAtomicBackup_takesPriorityOverUncommittedMain() throws Exception {
        File base = new File(tmp.getRoot(), "progress.json");
        Files.writeString(base.toPath(), "{ incomplete");
        Files.writeString(new File(tmp.getRoot(), "progress.json.bak").toPath(),
                "{\"nextSessionId\":42}");

        assertEquals("{\"nextSessionId\":42}", new AtomicStateFile(base).read());
    }

    @Test
    public void interruptedWrite_readsCommittedContentAndDiscardsPartialFile() throws Exception {
        File base = new File(tmp.getRoot(), "progress.json");
        AtomicStateFile file = new AtomicStateFile(base);
        file.write("{\"nextSessionId\":42}");
        try (FileOutputStream out = new AtomicFile(base).startWrite()) {
            out.write("{ incomplete".getBytes(StandardCharsets.UTF_8));
        }
        File partial = new File(tmp.getRoot(), "progress.json.new");
        assertTrue(partial.exists());

        assertEquals("{\"nextSessionId\":42}", file.read());
        assertFalse(partial.exists());
    }

    @Test
    public void interruptedFirstWrite_doesNotPromotePartialContent() throws Exception {
        File base = new File(tmp.getRoot(), "progress.json");
        try (FileOutputStream out = new AtomicFile(base).startWrite()) {
            out.write("{ incomplete".getBytes(StandardCharsets.UTF_8));
        }

        assertNull(new AtomicStateFile(base).read());
        assertFalse(base.exists());
    }

    @Test
    public void failedWrite_keepsCommittedContent() throws Exception {
        File base = new File(tmp.getRoot(), "progress.json");
        AtomicStateFile file = new AtomicStateFile(base);
        file.write("{\"nextSessionId\":42}");
        AtomicFile writer = new AtomicFile(base);
        FileOutputStream out = writer.startWrite();
        out.write("{ incomplete".getBytes(StandardCharsets.UTF_8));
        writer.failWrite(out);

        assertEquals("{\"nextSessionId\":42}", file.read());
        assertFalse(new File(tmp.getRoot(), "progress.json.new").exists());
    }

    @Test
    public void progressStore_recoversAtomicBackupBeforeOlderDailyBackup() throws Exception {
        File base = new File(tmp.getRoot(), "progress.json");
        Files.writeString(new File(tmp.getRoot(), "progress.json.bak").toPath(),
                "{\"schemaVersion\":3,\"nextSessionId\":42,"
                        + "\"words\":{\"mao\":{\"learnedAt\":123,\"box\":2}}}");
        AtomicStateFile file = new AtomicStateFile(base);
        file.writeBackup("{\"schemaVersion\":3,\"nextSessionId\":17}");

        ProgressStore store = new ProgressStore(file, new Gson(),
                new FakeClock(LocalDateTime.of(2026, 10, 21, 8, 0)),
                new AppExecutors(Runnable::run, Runnable::run));
        store.ensureLoaded();
        store.read(state -> state, state -> {
            assertEquals(42, state.nextSessionId);
            assertEquals(Long.valueOf(123), state.words.get("mao").learnedAt);
            assertEquals(2, state.words.get("mao").box);
        });

        ProgressStore reloaded = new ProgressStore(file, new Gson(),
                new FakeClock(LocalDateTime.of(2026, 10, 21, 8, 0)),
                new AppExecutors(Runnable::run, Runnable::run));
        reloaded.ensureLoaded();
        reloaded.read(state -> state.nextSessionId, id -> assertEquals(42L, id.longValue()));
    }
}
