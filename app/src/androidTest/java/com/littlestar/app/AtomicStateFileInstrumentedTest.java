package com.littlestar.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.littlestar.app.data.store.AtomicStateFile;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/** AtomicStateFile 在真机上的写入 / 读取 / 改名 / 备份。 */
@RunWith(AndroidJUnit4.class)
public final class AtomicStateFileInstrumentedTest {

    @Test
    public void writeReadRenameBackup() {
        File dir = ApplicationProvider.getApplicationContext().getFilesDir();
        File f = new File(dir, "test-state-" + System.nanoTime() + ".json");
        AtomicStateFile sf = new AtomicStateFile(f);

        assertNull(sf.read());

        sf.write("{\"a\":1}");
        assertEquals("{\"a\":1}", sf.read());

        sf.write("{\"a\":2}");
        assertEquals("{\"a\":2}", sf.read());

        sf.writeBackup("{\"a\":2}");
        assertEquals("{\"a\":2}", sf.readBackup());

        sf.rename(".corrupt-test");
        assertNull(sf.read());
        assertTrue(new File(dir, f.getName() + ".corrupt-test").exists());

        // 清理
        new File(dir, f.getName() + ".corrupt-test").delete();
        new File(dir, "progress.bak.json").delete();
    }
}
