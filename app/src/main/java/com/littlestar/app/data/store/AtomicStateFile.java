package com.littlestar.app.data.store;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 基于 {@link AtomicFile} 的原子写入：先写临时文件，finishWrite 原子替换；
 * 出错 failWrite，旧文件始终完整。
 */
public final class AtomicStateFile implements StateFile {

    private static final String TAG = "AtomicStateFile";

    private final File baseFile;
    private final AtomicFile atomicFile;
    private final File backupFile;

    public AtomicStateFile(@NonNull File baseFile) {
        this.baseFile = baseFile;
        this.atomicFile = new AtomicFile(baseFile);
        this.backupFile = new File(baseFile.getParentFile(), "progress.bak.json");
    }

    @Nullable
    @Override
    public String read() {
        if (!baseFile.exists()) {
            return null;
        }
        try (InputStream in = new FileInputStream(baseFile);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            Log.e(TAG, "read failed", e);
            return null;
        }
    }

    @Override
    public void write(@NonNull String content) {
        FileOutputStream out = null;
        try {
            out = atomicFile.startWrite();
            out.write(content.getBytes(StandardCharsets.UTF_8));
            atomicFile.finishWrite(out);
        } catch (IOException e) {
            Log.e(TAG, "write failed", e);
            if (out != null) {
                atomicFile.failWrite(out);
            }
        }
    }

    @Override
    public void rename(@NonNull String suffix) {
        if (!baseFile.exists()) {
            return;
        }
        File target = new File(baseFile.getParentFile(), baseFile.getName() + suffix);
        if (!baseFile.renameTo(target)) {
            Log.e(TAG, "rename to " + target + " failed");
            return;
        }
        // 再清掉 AtomicFile 可能遗留的 .bak 临时文件（此时 base 已移走，delete 只清临时文件）
        atomicFile.delete();
    }

    @Nullable
    @Override
    public String readBackup() {
        if (!backupFile.exists()) {
            return null;
        }
        try (InputStream in = new FileInputStream(backupFile);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            Log.e(TAG, "readBackup failed", e);
            return null;
        }
    }

    @Override
    public void writeBackup(@NonNull String content) {
        try (FileOutputStream out = new FileOutputStream(backupFile)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.e(TAG, "writeBackup failed", e);
        }
    }
}
