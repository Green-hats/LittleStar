package com.littlestar.app.data.store;

import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * progress.json 的完整内容。只在 diskIO 线程上读改。
 * Gson 模型：普通类 + public 字段 + 无参构造函数，集合字段有默认值。
 */
public final class ProgressState {

    public int schemaVersion = ProgressStore.CURRENT_VERSION;
    /** key = wordId；没学过的字不在 map 里 */
    public Map<String, WordProgress> words = new HashMap<>();
    /** key = ISO 日期 */
    public Map<String, DailyPlan> dailyPlans = new HashMap<>();
    public List<Session> sessions = new ArrayList<>();
    public long nextSessionId = 1;
    /** key = ISO 日期 */
    public Map<String, DailyUsage> usage = new HashMap<>();

    public static final class WordProgress {
        @Nullable
        public Long learnedAt;      // 首次「学会」的时间（印章或测验答对）
        public int box;             // Leitner 盒 0–5
        @Nullable
        public String dueDate;      // ISO 日期，下次复习日
        @Nullable
        public Long lastSeenAt;
        public int correctCount;
        public int wrongCount;
    }

    public static final class DailyPlan {
        public int goal;
        public List<String> newWordIds = new ArrayList<>();
    }

    public static final class Session {
        public long id;
        public String date;
        public String kind;         // DAILY / REVIEW / THEME
        @Nullable
        public String themeId;
        public List<String> newWordIds = new ArrayList<>();
        public List<String> reviewWordIds = new ArrayList<>();
        public List<String> quizWordIds = new ArrayList<>();
        public long startedAt;
        @Nullable
        public Long completedAt;
        public int correct;
        public int total;
        public List<Answer> answers = new ArrayList<>();
    }

    public static final class Answer {
        public String wordId;
        public String chosen;
        public boolean correct;
        public boolean firstTry;
        public long at;
    }

    public static final class DailyUsage {
        public int childSeconds;
        public int extraMinutesGranted;
    }

    /** 深拷贝，用于发布给 UI 的只读快照。 */
    public ProgressState deepCopy(Gson gson) {
        return gson.fromJson(gson.toJson(this), ProgressState.class);
    }
}
