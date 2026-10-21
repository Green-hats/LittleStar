package com.littlestar.app.util;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.littlestar.app.data.content.ContentRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/** 从真实 assets 目录加载内容的 ContentRepository（单测用）。 */
public final class TestContent {

    private TestContent() {
    }

    @NonNull
    public static ContentRepository load() {
        File assets = new File("src/main/assets/content");
        ContentRepository repo = new ContentRepository(
                path -> {
                    InputStream in = new FileInputStream(new File(assets, path));
                    return in;
                },
                new Gson());
        repo.ensureLoaded();
        return repo;
    }
}
