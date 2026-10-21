package com.littlestar.app.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.littlestar.app.data.store.StateFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** 单测用的普通文件 StateFile（临时目录），可统计写入次数。 */
public final class FileStateFile implements StateFile {

    private final File file;
    private final File backup;
    public int writeCount;

    public FileStateFile(@NonNull File dir) {
        this.file = new File(dir, "progress.json");
        this.backup = new File(dir, "progress.bak.json");
    }

    @Nullable
    @Override
    public String read() {
        return readFile(file);
    }

    @Override
    public void write(@NonNull String content) {
        writeCount++;
        try {
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void rename(@NonNull String suffix) {
        if (!file.exists()) {
            return;
        }
        try {
            Files.move(file.toPath(), new File(file.getParentFile(), file.getName() + suffix).toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public String readBackup() {
        return readFile(backup);
    }

    @Override
    public void writeBackup(@NonNull String content) {
        try {
            Files.write(backup.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    private static String readFile(File f) {
        if (!f.exists()) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
