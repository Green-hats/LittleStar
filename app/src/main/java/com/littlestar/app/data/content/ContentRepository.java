package com.littlestar.app.data.content;

import android.content.res.AssetManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.littlestar.app.AppExecutors;
import com.littlestar.app.data.content.model.CultureCategory;
import com.littlestar.app.data.content.model.CultureItem;
import com.littlestar.app.data.content.model.Theme;
import com.littlestar.app.data.content.model.Word;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 只读内容库：assets/content/*.json，首次访问在 diskIO 线程解析并缓存。
 * 解析后转成不可变列表并建 Map 索引。
 */
public final class ContentRepository {

    private static final String TAG = "ContentRepository";

    /** 打开内容文件的抽象：正式用 AssetManager，单测用文件系统。 */
    public interface Source {
        @NonNull
        InputStream open(@NonNull String path) throws IOException;
    }

    private static final class WordsFile {
        int version;
        List<Theme> themes = new ArrayList<>();
    }

    private static final class CultureFile {
        int version;
        List<CultureCategory> categories = new ArrayList<>();
        List<CultureItem> items = new ArrayList<>();
    }

    private final Source source;
    private final Gson gson;

    // 解析后的不可变缓存（volatile：加载完成后对 UI 线程可见）
    private volatile List<Theme> themes;
    private volatile List<Word> wordsInOrder;
    private volatile Map<String, Word> wordIndex;
    private volatile Map<String, String> wordTheme;
    private volatile List<CultureCategory> categories;
    private volatile Map<String, List<CultureItem>> itemsByCategory;
    private volatile Map<String, CultureItem> itemIndex;

    public ContentRepository(@NonNull AssetManager assets, @NonNull Gson gson) {
        this(path -> assets.open("content/" + path), gson);
    }

    public ContentRepository(@NonNull Source source, @NonNull Gson gson) {
        this.source = source;
        this.gson = gson;
    }

    /** 在 diskIO 线程解析（若未解析），完成后回调到主线程。 */
    public void load(@NonNull AppExecutors executors, @NonNull Runnable onMain) {
        executors.diskIO().execute(() -> {
            ensureLoaded();
            executors.mainThread().execute(onMain);
        });
    }

    /** 是否已解析（不会触发加载；主线程安全）。 */
    public boolean isLoaded() {
        return themes != null;
    }

    /** 同步确保已解析。只能在 diskIO 线程或测试线程调用。 */
    public synchronized void ensureLoaded() {
        if (themes != null) {
            return;
        }
        WordsFile wordsFile = read("words.json", WordsFile.class);
        CultureFile cultureFile = read("culture.json", CultureFile.class);

        List<Theme> themeList = new ArrayList<>(wordsFile.themes);
        themeList.sort(Comparator.comparingInt(t -> t.order));

        List<Word> words = new ArrayList<>();
        Map<String, Word> wIndex = new HashMap<>();
        Map<String, String> wTheme = new HashMap<>();
        for (Theme t : themeList) {
            for (Word w : t.words) {
                if (wIndex.put(w.id, w) != null) {
                    Log.w(TAG, "duplicate word id: " + w.id);
                }
                wTheme.put(w.id, t.id);
                words.add(w);
            }
        }

        List<CultureCategory> cats = new ArrayList<>(cultureFile.categories);
        cats.sort(Comparator.comparingInt(c -> c.order));
        Map<String, List<CultureItem>> byCat = new HashMap<>();
        Map<String, CultureItem> iIndex = new HashMap<>();
        for (CultureItem it : cultureFile.items) {
            byCat.computeIfAbsent(it.categoryId, k -> new ArrayList<>()).add(it);
            if (iIndex.put(it.id, it) != null) {
                Log.w(TAG, "duplicate culture id: " + it.id);
            }
        }
        Map<String, List<CultureItem>> byCatImmutable = new HashMap<>();
        for (Map.Entry<String, List<CultureItem>> e : byCat.entrySet()) {
            byCatImmutable.put(e.getKey(), List.copyOf(e.getValue()));
        }

        this.themes = List.copyOf(themeList);
        this.wordsInOrder = List.copyOf(words);
        this.wordIndex = Collections.unmodifiableMap(wIndex);
        this.wordTheme = Collections.unmodifiableMap(wTheme);
        this.categories = List.copyOf(cats);
        this.itemsByCategory = Collections.unmodifiableMap(byCatImmutable);
        this.itemIndex = Collections.unmodifiableMap(iIndex);
    }

    private <T> T read(@NonNull String path, @NonNull Class<T> type) {
        try (InputStream in = source.open(path);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, type);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("内容解析失败: " + path, e);
        }
    }

    // ---------- 查询（调用前须 ensureLoaded；UI 里通过 load() 回调使用） ----------

    @NonNull
    public List<Theme> themes() {
        return requireLoaded(themes);
    }

    /** 按主题顺序展开的全部字。 */
    @NonNull
    public List<Word> wordsInOrder() {
        return requireLoaded(wordsInOrder);
    }

    @Nullable
    public Word wordById(@Nullable String id) {
        return id == null ? null : requireLoaded(wordIndex).get(id);
    }

    @Nullable
    public String themeIdOf(@Nullable String wordId) {
        return wordId == null ? null : requireLoaded(wordTheme).get(wordId);
    }

    /** 按 id 查主题（M3 加：Flashcard 顶栏显示主题名）。 */
    @Nullable
    public Theme themeById(@Nullable String themeId) {
        if (themeId == null) {
            return null;
        }
        for (Theme t : requireLoaded(themes)) {
            if (t.id.equals(themeId)) {
                return t;
            }
        }
        return null;
    }

    /** 按汉字查字（M3 加：Quiz 选项只有汉字串，需要反查拼音）。 */
    @Nullable
    public Word wordByHanzi(@Nullable String hanzi) {
        if (hanzi == null) {
            return null;
        }
        for (Word w : requireLoaded(wordsInOrder)) {
            if (hanzi.equals(w.hanzi)) {
                return w;
            }
        }
        return null;
    }

    @NonNull
    public List<Word> wordsOfTheme(@NonNull String themeId) {
        for (Theme t : requireLoaded(themes)) {
            if (t.id.equals(themeId)) {
                return Collections.unmodifiableList(t.words);
            }
        }
        return List.of();
    }

    @NonNull
    public List<CultureCategory> categories() {
        return requireLoaded(categories);
    }

    /** MVP 分类（mvp=true）。 */
    @NonNull
    public List<CultureCategory> mvpCategories() {
        List<CultureCategory> result = new ArrayList<>();
        for (CultureCategory c : requireLoaded(categories)) {
            if (c.mvp) {
                result.add(c);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @NonNull
    public List<CultureItem> itemsOfCategory(@NonNull String categoryId) {
        List<CultureItem> list = requireLoaded(itemsByCategory).get(categoryId);
        return list == null ? List.of() : list;
    }

    @Nullable
    public CultureItem itemById(@Nullable String id) {
        return id == null ? null : requireLoaded(itemIndex).get(id);
    }

    /** 今天 featured 区间内的条目（节日置顶用）。 */
    @NonNull
    public List<CultureItem> featuredToday(@NonNull LocalDate today) {
        String iso = today.toString();
        List<CultureItem> result = new ArrayList<>();
        for (Map.Entry<String, CultureItem> e : requireLoaded(itemIndex).entrySet()) {
            for (CultureItem.DateRange r : e.getValue().featured) {
                if (r.from != null && r.to != null && iso.compareTo(r.from) >= 0 && iso.compareTo(r.to) <= 0) {
                    result.add(e.getValue());
                    break;
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    @NonNull
    private static <T> T requireLoaded(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("ContentRepository 尚未加载，先调 load() / ensureLoaded()");
        }
        return value;
    }
}
