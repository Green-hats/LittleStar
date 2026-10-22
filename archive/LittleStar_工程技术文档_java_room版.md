# LittleStar 工程技术文档

> 版本 1.0 · 2026-10-21
> 依据：[LittleStar_设计书_v3.md](LittleStar_设计书_v3.md)（视觉与交互）+ [LittleStar_设计评审_v3.md](LittleStar_设计评审_v3.md)（修订意见）
> 读者：负责实现的 coding agent / 开发者。本文是**施工图**：照着做就能从空目录做出可验收的 APK。
> **开发语言：Java 17。** 工程中不允许出现 Kotlin 源文件、Kotlin 插件或 `-ktx` 依赖。（Kotlin 版旧稿归档在 `archive/LittleStar_工程技术文档_kotlin版.md`，仅供参考，不再维护。）

---

## 0. 给 Agent 的工作守则（先读这一节）

### 0.1 文档优先级

遇到冲突时，按以下顺序取舍：

1. 本文 **§14 设计冲突裁决表**（已经替你做好的决定）
2. 本文其余章节（工程规格）
3. 设计书 v3 的文字规格
4. 设计书 v3 的设计图（`assets/screens/*.png`）——**只看布局和比例，色值和尺寸以文字为准**

### 0.2 硬性规则

- **一次只做一个里程碑**（§13）。做完跑完该里程碑的「完成标准」再进下一个，不要并行铺开。
- **不要申请 `INTERNET` 权限。** 全部内容离线内置。
- **颜色、尺寸、字号、文案不写死。** 颜色只在 `colors.xml` 里出现十六进制；尺寸只在 `dimens.xml`；文案只在 `strings.xml`。布局和 Java 代码里出现 `#` 色值或 `"中文"` 字面量都算缺陷（内容 JSON 除外）。
- **孩子区不出现系统对话框**（AlertDialog、Toast）。反馈一律用页面内状态、动效和声音。家长区可以用 Snackbar 和 MaterialTimePicker。
- **每完成一屏**，用模拟器截图，和对应的设计图并排对比；截图存到 `docs/screenshots/<milestone>/`。
- **不确定时不要发明需求。** 在 `docs/DECISIONS.md` 记一条「问题 / 采用的做法 / 理由」，然后按最保守的做法继续。
- 每个里程碑结束时提交一次 git commit，提交信息用 `M<n>: <摘要>`。
- **Java 编码约定：** 4 空格缩进；类默认 `final`（需要继承的除外）；字段 `private final` 优先；不用 Lombok；不在 `onCreate` 里做磁盘 IO；匿名内部类持有 Activity 时注意泄漏（Handler 回调用静态内部类 + `WeakReference`，或在 `onDestroy` 里 `removeCallbacksAndMessages(null)`）。

### 0.3 名词表

| 名词 | 含义 |
|---|---|
| 孩子区 Child | 首页、字、文、播放器、收藏、奖励、休息页。受每日时长限制计时 |
| 家长区 Parent | 验证后进入的 5 个页面。不计入孩子时长 |
| 字 Words | 识字模块：主题 → 词表 → 识字卡 → 测验 → 奖励 |
| 文 Culture | 文化模块：分类 → 列表 → 播放器 |
| 会话 Session | 首页 START 开始的一轮：识字卡 → 测验 → 奖励 |
| 每日目标 N | 家长设的每天新字数（1–10，默认 3） |
| 块 Block | 带墨线 + 硬影的组件（`BlockLayout`） |

---

## 1. 项目范围

### 1.1 MVP（必须交付）

| 模块 | 内容 |
|---|---|
| 字 | 5 个主题 × 6 个字 = 30 字（动物 / 水果 / 颜色 / 数字 / 家人）；Nature 自然作为第 6 个主题，**时间允许就做**（首页示例「月」来自 Nature；如果不做，首页示例换成已有主题的字） |
| 文 | 3 类：儿歌 / 故事 / 古诗，每类 3–4 条 |
| 家长 | 验证、概览、时长、计划、记录 |
| 系统 | 每日时长限制、休息页、每日提醒通知、睡前定时、深色模式、1.3 倍字体 |

### 1.2 增强项（MVP 之后）

成语、节日两类文化；Nature 主题；平板布局；家长「设置」页（声音 / 双语提示 / 重置进度）。

### 1.3 不做

账号、云同步、网络请求、内购、广告、统计 SDK、推送服务。

---

## 2. 技术选型

| 项 | 选择 | 理由 |
|---|---|---|
| 语言 | **Java 17**（源码与目标兼容级别均为 17） | 团队要求。工程里**不出现 `.kt` 文件，不应用 `kotlin-android` 插件** |
| 构建脚本 | **Groovy DSL**（`build.gradle`、`settings.gradle`） | 保持全工程无 Kotlin；版本仍用 `libs.versions.toml` 管理 |
| UI | **XML View + ViewBinding** | 设计书 §9.2 按 XML 资源对照；「每块都能用 XML 画」。不用 Compose（Compose 只支持 Kotlin） |
| 页面组织 | **多 Activity**（16 个，见 §5） | 设计书按 Activity 规划；每屏边界清楚，方便逐屏验收 |
| 架构 | MVVM：`ViewModel` + **`LiveData`** + Repository | Java 下最成熟的组合；Room 直接返回 `LiveData` |
| 线程 | **`AppExecutors`**（磁盘单线程 + 主线程 Handler） | 代替协程；所有数据库读写走 `diskIO()` |
| 依赖注入 | **手写 `AppContainer`**（不用 Hilt） | 项目小；减少注解处理和构建时间 |
| 数据库 | Room（`annotationProcessor`，不用 KSP） | 学习进度、会话、时长、收藏 |
| 设置 | **SharedPreferences**，封装在 `SettingsStore` | DataStore 在 Java 里要配 RxJava，不划算；设置项少，SharedPreferences 足够 |
| 内容 | `assets/content/*.json` + **Gson** | 内容和代码分离，便于加字；POJO 映射简单 |
| 空安全 | `androidx.annotation.@NonNull / @Nullable` 标注所有公开方法的参数和返回值 | 弥补 Java 没有空安全；开启 Lint nullability 检查 |
| 长音频 | **Media3 ExoPlayer + MediaSessionService** | 后台播放、通知控制、睡前定时 |
| 短音效 | `SoundPool` | 「嗒」「叮」等 < 1s 音效，低延迟 |
| 字音 | 预录 `.ogg`，用一个单独的 ExoPlayer 实例播放 | 离线、发音标准；TTS 只做开发期兜底 |
| 时间 | `java.time` + 可注入的 `Clock` | minSdk 26 原生可用；方便测试 |
| 提醒 | `AlarmManager.setWindow`（非精确）+ `BOOT_COMPLETED` 重排 | 不需要精确闹钟权限 |
| 测试 | JUnit4、`androidx.arch.core:core-testing`（`InstantTaskExecutorRule`）、Room in-memory、Espresso；Mockito 只在必要时用 | |

**SDK：** `minSdk 26`、`targetSdk 35`、`compileSdk 35`。JDK 17。
**依赖版本：** 用 `gradle/libs.versions.toml` 管理，取写代码时 AndroidX / Media3 的最新稳定版。不要用 alpha/beta。

需要的库（**全部用非 `-ktx` 版本**）：`androidx.core:core`、`appcompat`、`activity`、`material`（M3）、`constraintlayout`、`recyclerview`、`viewpager2`、`lifecycle-viewmodel`、`lifecycle-livedata`、`lifecycle-process`、`room-runtime` + `room-compiler`（`annotationProcessor`）、`media3-exoplayer`、`media3-session`、`core-splashscreen`、`com.google.code.gson:gson`、`androidx.annotation`。

`app/build.gradle` 要点：

```groovy
plugins { id 'com.android.application' }   // 不要 org.jetbrains.kotlin.android

android {
    namespace 'com.littlestar.app'
    compileSdk 35
    defaultConfig {
        applicationId 'com.littlestar.app'
        minSdk 26
        targetSdk 35
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += ['room.schemaLocation': "$projectDir/schemas".toString()]
            }
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    buildFeatures { viewBinding true; buildConfig true }
    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}

dependencies {
    implementation libs.room.runtime
    annotationProcessor libs.room.compiler
    // ……其余见上表
}
```

**Java 17 语言特性的使用边界：**

- `record` 只用于 `domain/` 里的不可变值对象（如 `QuizQuestion`、`SessionPlan`、`HomeMode`）。**Room 实体和 Gson 模型不用 record**，用普通类 + public 字段，避免注解处理器和反射的兼容问题。
- `switch` 表达式、`var`（仅局部变量且类型一目了然时）、文本块可以用。
- 不用 `Optional` 做字段或参数；可空值用 `@Nullable` 标注。
- 集合对外暴露时用 `Collections.unmodifiableList`，或返回 `List.copyOf(...)`。
- 不引入 RxJava、Guava（Media3 会传递依赖 Guava 的 `ListenableFuture`，只在连接 `MediaController` 时用）。

---

## 3. 工程目录

```
LittleStar/
├─ settings.gradle
├─ build.gradle
├─ gradle/libs.versions.toml
├─ docs/
│  ├─ DECISIONS.md               ← agent 自己记录的决定
│  └─ screenshots/M3/...         ← 每屏验收截图
├─ tools/
│  ├─ contrast_check.py          ← 读 colors.xml 复算对比度（§8.1）
│  ├─ validate_content.py        ← 校验 JSON 与资源文件一一对应（§6.4）
│  ├─ svg_to_vector.md           ← 插画转 VectorDrawable 的步骤
│  └─ make_placeholder_audio.sh  ← 开发期用 macOS `say` 生成占位字音
└─ app/
   ├─ build.gradle
   ├─ proguard-rules.pro
   ├─ schemas/                    ← Room 导出的 schema（提交到 git）
   └─ src/
      ├─ main/
      │  ├─ AndroidManifest.xml
      │  ├─ assets/
      │  │  ├─ content/words.json
      │  │  ├─ content/culture.json
      │  │  └─ audio/
      │  │     ├─ words/yue.ogg ...      ← 字音
      │  │     ├─ culture/xiaoxingxing.ogg ...
      │  │     └─ prompts/great.ogg ...  ← 「太棒了」等提示音
      │  ├─ res/
      │  │  ├─ raw/sfx_tap.ogg sfx_flip.ogg sfx_correct.ogg sfx_wrong.ogg sfx_star.ogg
      │  │  ├─ font/poppins_*.ttf  noto_sans_sc_hanzi.otf(子集)  littlestar_latin.xml
      │  │  ├─ drawable/ic_*.xml  ill_*.xml  ic_panda.xml  ic_panda_sleep.xml
      │  │  ├─ layout/ ...
      │  │  ├─ values/ colors.xml dimens.xml strings.xml themes.xml styles_*.xml attrs.xml
      │  │  ├─ values-night/ colors.xml themes.xml
      │  │  └─ values-sw600dp/ dimens.xml
      │  └─ java/com/littlestar/app/
      │     ├─ LittleStarApp.java
      │     ├─ AppContainer.java
      │     ├─ AppExecutors.java
      │     ├─ core/
      │     │  ├─ time/AppClock.java  SystemAppClock.java
      │     │  ├─ lifecycle/Event.java  ViewModelFactory.java
      │     │  ├─ audio/SfxPlayer.java  SpeechPlayer.java
      │     │  ├─ ui/BlockLayout.java  SegmentProgressView.java  StepperView.java  BarChartView.java
      │     │  ├─ ui/ChildActivity.java  ParentActivity.java  Motion.java  Insets.java
      │     │  └─ screentime/ScreenTimeTracker.java
      │     ├─ data/
      │     │  ├─ content/model/Theme.java  Word.java  CultureCategory.java  CultureItem.java  LyricLine.java
      │     │  ├─ content/ContentRepository.java
      │     │  ├─ db/AppDatabase.java  entities/  dao/
      │     │  ├─ prefs/SettingsStore.java
      │     │  └─ repo/ProgressRepository.java  UsageRepository.java  FavouriteRepository.java
      │     ├─ domain/
      │     │  ├─ ReviewScheduler.java     ← 间隔复习
      │     │  ├─ SessionBuilder.java      ← 组一轮会话
      │     │  ├─ QuizGenerator.java       ← 出题 + 干扰项
      │     │  ├─ StarCalculator.java
      │     │  ├─ HomeModeResolver.java    ← 首页字/文时间规则
      │     │  ├─ ParentChallenge.java
      │     │  └─ ScreenTimePolicy.java    ← 时长判断（纯逻辑，便于测试）
      │     ├─ feature/
      │     │  ├─ home/  words/  flashcard/  quiz/  reward/
      │     │  ├─ culture/  player/  favourites/  sleep/
      │     │  ├─ parent/  timeup/
      │     │  └─ debug/ComponentGalleryActivity.java   ← 仅 debug 构建
      │     ├─ playback/PlaybackService.java  SleepTimerController.java
      │     └─ notify/Notifier.java  ReminderScheduler.java  ReminderReceiver.java  BootReceiver.java
      ├─ debug/   ← ComponentGalleryActivity 的 manifest 条目
      ├─ test/    ← JVM 单测
      └─ androidTest/
```

包名 `com.littlestar.app`，applicationId 相同。

---

## 4. 架构

```
Activity (View + ViewBinding)
   │  observe LiveData<UiState> / LiveData<Event<Effect>>；调用 ViewModel 方法
   ▼
ViewModel ── 只依赖 Repository / domain，不持有 Context
   ▼
Repository ── ContentRepository（JSON，内存缓存）
           ── ProgressRepository / UsageRepository / FavouriteRepository（Room）
           ── SettingsStore（SharedPreferences）
   │  耗时操作 → AppExecutors.diskIO()；结果 postValue 回主线程
   ▼
domain/ ── 纯 Java，无 android.* 依赖，100% JVM 单测
```

### 4.1 规则

- 每屏一个 `XxxUiState`（**不可变类**：final 字段 + 构造函数 + `withXxx()` 拷贝方法；也可以用 `record`）和一个 `XxxViewModel`。
- ViewModel 通过通用工厂创建，从 `((LittleStarApp) getApplication()).container()` 取依赖：
  ```java
  HomeViewModel vm = new ViewModelProvider(this,
          ViewModelFactory.of(() -> new HomeViewModel(container()))).get(HomeViewModel.class);
  ```
- ViewModel 内部持有 `MutableLiveData<XxxUiState>`，只对外暴露 `LiveData`。主线程用 `setValue`，后台线程用 `postValue`。
- Activity 里 `vm.state().observe(this, this::render)`，`render(state)` 是**唯一**改 UI 的地方。
- 一次性事件（跳页、播放音效）用 `LiveData<Event<Effect>>`，`Event` 是只能取一次内容的包装类（`getContentIfNotHandled()`），防止旋转后重复跳页。
- 多个数据源合并用 `MediatorLiveData`；派生用 `Transformations.map / switchMap`。
- **禁止在主线程访问 Room**（不要开 `allowMainThreadQueries`）。查询用返回 `LiveData` 的 DAO；一次性读写用 `executors.diskIO().execute(...)`。
- 回调接口统一用 `java.util.function.Consumer<T>`，不自己定义一堆 Callback 接口。
- 页面之间只传 **ID 和模式**（Intent extras），不传对象。进程被杀后可以从 extras 重建。
- `domain/` 里的类构造函数只接收纯数据和 `AppClock`，方便 JVM 单测。

### 4.2 AppContainer

```java
public final class AppContainer {
    public final AppExecutors executors = new AppExecutors();
    public final AppClock clock = new SystemAppClock();
    public final AppDatabase db;
    public final SettingsStore settings;
    public final ContentRepository content;          // 首次访问时在 diskIO 线程解析并缓存
    public final ProgressRepository progress;
    public final UsageRepository usage;
    public final FavouriteRepository favourites;
    public final ReviewScheduler scheduler;
    public final SessionBuilder sessionBuilder;
    public final SfxPlayer sfx;
    public final SpeechPlayer speech;
    public final ScreenTimeTracker screenTime;
    public final ReminderScheduler reminders;
    public final SleepTimerController sleepTimer;

    public AppContainer(@NonNull Application app) {
        db = Room.databaseBuilder(app, AppDatabase.class, "littlestar.db").build();
        settings = new SettingsStore(app.getSharedPreferences("settings", Context.MODE_PRIVATE));
        content = new ContentRepository(app.getAssets(), new Gson());
        progress = new ProgressRepository(db, content, clock, executors);
        usage = new UsageRepository(db, clock, executors);
        favourites = new FavouriteRepository(db, executors);
        scheduler = new ReviewScheduler();
        sessionBuilder = new SessionBuilder(content, progress, scheduler, settings, clock, new Random());
        sfx = new SfxPlayer(app);
        speech = new SpeechPlayer(app);
        screenTime = new ScreenTimeTracker(usage, settings, clock, new Handler(Looper.getMainLooper()));
        reminders = new ReminderScheduler(app, settings, clock);
        sleepTimer = new SleepTimerController(clock);
    }
}

public final class LittleStarApp extends Application {
    private AppContainer container;
    @Override public void onCreate() { super.onCreate(); container = new AppContainer(this); }
    @NonNull public AppContainer container() { return container; }
}
```

### 4.2.1 AppExecutors

```java
public final class AppExecutors {
    private final Executor diskIO = Executors.newSingleThreadExecutor(r -> new Thread(r, "ls-disk"));
    private final Executor mainThread = new Handler(Looper.getMainLooper())::post;
    public Executor diskIO() { return diskIO; }
    public Executor mainThread() { return mainThread; }
}
```

- 磁盘线程**单线程**：保证同一个字的进度更新按顺序执行，不需要额外加锁。
- 测试里用 `Runnable::run` 构造一个同步版 `AppExecutors`（给构造函数加一个测试用重载）。

### 4.3 AppClock

```java
public interface AppClock {
    @NonNull Instant now();
    @NonNull ZoneId zone();
    default @NonNull LocalDate today()      { return now().atZone(zone()).toLocalDate(); }
    default @NonNull LocalTime localTime()  { return now().atZone(zone()).toLocalTime(); }
}
```

（注意：`LocalDate.ofInstant` 是 Java 9 API，Android 上要 API 34 才有，**不要用**；统一写 `instant.atZone(zone).toLocalDate()`。）

**所有时间相关逻辑都必须通过 `AppClock`**，不准直接调 `System.currentTimeMillis()` 或 `LocalDate.now()`。测试用 `FakeClock`。

---

## 5. 页面清单与导航

### 5.1 16 个 Activity + 1 个 BottomSheet

| # | 类 | 区 | 设计书 | 设计图 | 入参（extras） |
|---|---|---|---|---|---|
| 1 | `HomeActivity`（LAUNCHER，SplashScreen API） | 孩子 | 7.1 | home_word / home_culture | — |
| 2 | `ThemesActivity` | 孩子 | 7.2 | words_themes | — |
| 3 | `WordListActivity` | 孩子 | 7.3 | words_list | `themeId` |
| 4 | `FlashcardActivity` | 孩子 | 7.4 | flashcard / flashcard_learned | `mode`(SESSION/BROWSE)、`wordIds`、`startIndex`、`sessionId?` |
| 5 | `QuizActivity` | 孩子 | 7.5 | quiz_* | `mode`(SESSION/THEME)、`sessionId?`、`themeId?` |
| 6 | `RewardActivity` | 孩子 | 7.6 | reward | `sessionId` |
| 7 | `CultureCategoriesActivity` | 孩子 | 7.7 | culture_categories | — |
| 8 | `CultureListActivity` | 孩子 | 7.8 | culture_stories | `categoryId` |
| 9 | `PlayerActivity` | 孩子 | 7.9 | player | `itemId`、`queue: String[]` |
| 10 | `FavouritesActivity` | 孩子 | 7.10 | favourites / favourites_empty | — |
| 11 | `ParentGateActivity` | 家长 | 7.12 | parent_gate | `target`（进入后跳转的页面，默认 Dashboard） |
| 12 | `ParentDashboardActivity` | 家长 | 7.13 | parent_dashboard | — |
| 13 | `ParentTimeActivity` | 家长 | 7.14 | parent_time | — |
| 14 | `ParentPlanActivity` | 家长 | 7.15 | parent_plan | — |
| 15 | `ParentRecordsActivity` | 家长 | 7.16 | parent_records | — |
| 16 | `TimeUpActivity` | 系统 | 7.17 | time_up | `kind`(REST/LIMIT) |
| — | `SleepTimerSheet`（BottomSheetDialogFragment） | 孩子 | 7.11 | sleep_timer | 由 CultureList / Player 打开 |

额外：`ComponentGalleryActivity` 只放在 `src/debug/`，release 不打包。

### 5.2 导航图

```
Home ──START──▶ Flashcard(SESSION) ──完成──▶ Quiz(SESSION) ──完成──▶ Reward ──完成──▶ Home
  │                                                            └─再来一次──▶ Flashcard(SESSION,新会话)
  ├──字模块──▶ Themes ──▶ WordList ──点字──▶ Flashcard(BROWSE)
  │                          └──Quiz──▶ Quiz(THEME) ──▶ Reward(无星)
  ├──文模块──▶ CultureCategories ──▶ CultureList ──▶ Player
  │                 └──收藏──▶ Favourites ──▶ Player
  ├──LISTEN(文态)──▶ Player
  └──家长入口──▶ ParentGate ──▶ Dashboard ──▶ Time / Plan / Records
任意孩子页 ──(时长到)──▶ TimeUp
```

### 5.3 返回栈规则

- 会话链 `Flashcard → Quiz → Reward`：前进时 `finish()` 前一页，所以 Reward 按返回直接回 Home。会话中途按返回：**直接退出到 Home，不弹确认**；已标记学会的字保留，会话记为未完成。
- Reward「完成」：`finish()`，回到栈里的 Home。
- 家长区：Gate 通过后 `finish()` 自己再启动 Dashboard。从家长区返回到孩子区后，下次再进必须重新验证。家长区退到后台超过 60 秒，回来时跳回 Gate。
- `TimeUpActivity(LIMIT)`：`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`；系统返回键等价于「好的」（`finishAffinity()`）。**不要拦截返回键把孩子困住。**
- `TimeUpActivity(REST)`：普通启动，倒计时期间屏蔽返回（`OnBackPressedCallback`），结束后 `finish()` 回原页。

### 5.4 Activity 基类

```java
public abstract class ChildActivity extends AppCompatActivity {
    @Override protected void onCreate(@Nullable Bundle b) {
        EdgeToEdge.enable(this);                 // androidx.activity.EdgeToEdge
        super.onCreate(b);
        // 子类 setContentView 后调用 Insets.applySystemBarPadding(binding.getRoot())（见 §8.7）
    }
    @Override protected void onResume()  { super.onResume(); container().screenTime.onChildResumed(this); }
    @Override protected void onPause()   { container().screenTime.onChildPaused(); super.onPause(); }
    /** 会话中（Flashcard / Quiz）返回 true：到时不打断，等奖励页结束再弹。 */
    protected boolean deferTimeUp() { return false; }
    protected final AppContainer container() { return ((LittleStarApp) getApplication()).container(); }
}

public abstract class ParentActivity extends AppCompatActivity {
    // 主题 Theme.LittleStar.Parent；不计时；
    // onStop 记录时间戳，onStart 时若后台超过 60 秒则跳回 ParentGateActivity 并 finish()
}
```

`TimeUpActivity` 继承 `AppCompatActivity`，不计时。

---

## 6. 内容与数据

### 6.1 words.json

```json
{
  "version": 1,
  "themes": [
    {
      "id": "animals",
      "order": 1,
      "nameZh": "动物",
      "nameEn": "Animals",
      "glyph": "动",
      "illustration": "ill_theme_animals",
      "words": [
        {
          "id": "mao",
          "hanzi": "猫",
          "pinyin": "māo",
          "english": "cat",
          "image": "ill_word_mao",
          "audio": "audio/words/mao.ogg",
          "distractors": ["狗", "鸟", "鱼"]
        }
      ]
    }
  ]
}
```

- `id` 全局唯一，用无声调拼音；同音字加后缀，如 `shi_4_shi`。
- `image` 是 drawable 资源名，运行时用 `resources.getIdentifier` 查一次并缓存；查不到用 `ill_placeholder`，并在 debug 下打 `Log.w`。
- `distractors` 可选；没写就由 `QuizGenerator` 从同主题自动取（§7.4）。
- **Gson 模型**放在 `data/content/model/`：普通类、public 字段、字段名与 JSON 键一致（不需要 `@SerializedName`）；集合字段默认初始化为空列表，防止 JSON 缺字段时出现 null。解析后在 `ContentRepository` 里转成不可变列表并建 `Map<String, Word>` 索引。
- `proguard-rules.pro` 必须加：`-keep class com.littlestar.app.data.content.model.** { *; }`，否则 R8 混淆字段名后 Gson 解析全为空。

**MVP 主题与字（建议，agent 可以直接用）：**

| 主题 | 6 个字 |
|---|---|
| 动物 Animals | 猫 狗 鸟 鱼 马 牛 |
| 水果 Fruits | 果 瓜 桃 梨 枣 李 |
| 颜色 Colors | 红 黄 蓝 绿 白 黑 |
| 数字 Numbers | 一 二 三 四 五 六 |
| 家人 Family | 爸 妈 哥 姐 弟 妹 |
| 自然 Nature（增强） | 月 山 水 火 木 星 |

### 6.2 culture.json

```json
{
  "version": 1,
  "categories": [
    { "id": "songs",   "order": 1, "nameZh": "儿歌", "nameEn": "Songs",   "glyph": "儿", "mvp": true },
    { "id": "stories", "order": 2, "nameZh": "故事", "nameEn": "Stories", "glyph": "故", "mvp": true },
    { "id": "poems",   "order": 3, "nameZh": "古诗", "nameEn": "Poems",   "glyph": "诗", "mvp": true },
    { "id": "idioms",  "order": 4, "nameZh": "成语", "nameEn": "Idioms",  "glyph": "成", "mvp": false },
    { "id": "festivals","order": 5,"nameZh": "节日", "nameEn": "Festivals","glyph": "节", "mvp": false }
  ],
  "items": [
    {
      "id": "xiaoxingxing",
      "categoryId": "songs",
      "titleZh": "小星星",
      "subtitle": "儿歌",
      "cover": "ill_cover_star",
      "glyph": "星",
      "audio": "audio/culture/xiaoxingxing.ogg",
      "durationSec": 150,
      "bedtime": true,
      "featured": [],
      "lyrics": [
        { "startMs": 0,    "hanzi": "一闪一闪亮晶晶", "pinyin": "yì shǎn yì shǎn liàng jīng jīng", "english": "Twinkle, twinkle, little star" },
        { "startMs": 6200, "hanzi": "满天都是小星星", "pinyin": "mǎn tiān dōu shì xiǎo xīng xing", "english": "Stars all over the sky" }
      ]
    }
  ]
}
```

- `durationSec` 只用于列表显示；播放器以实际音频时长为准。
- `featured`：节日内容在指定日期区间置顶首页，例如 `[{"from":"2026-09-20","to":"2026-09-27"}]`。日期由内容维护者按当年农历填写，**代码不计算农历**。
- `lyrics` 可选；故事可以没有歌词，播放器隐藏歌词卡。

**MVP 内容（建议）：**

| 类 | 条目 | 版权说明 |
|---|---|---|
| 儿歌 | 小星星、两只老虎、拔萝卜、找朋友 | 曲调为公版，**必须自行录音/演奏**，不能用网上的现成录音 |
| 故事 | 嫦娥奔月、守株待兔、龟兔赛跑、画蛇添足 | 自行改写文本、自行录音 |
| 古诗 | 静夜思、咏鹅、春晓 | 原文为公版；朗读自录 |

### 6.3 Room 数据库

```java
@Entity(tableName = "word_progress")
public class WordProgressEntity {
    @PrimaryKey @NonNull public String wordId = "";
    @Nullable public Long learnedAt;      // 首次「学会」的时间（epoch ms；印章或测验答对）
    public int box;                       // Leitner 盒 0–5
    @Nullable public String dueDate;      // ISO LocalDate，下次复习日
    @Nullable public Long lastSeenAt;
    public int correctCount;
    public int wrongCount;
}

@Entity(tableName = "daily_plan")         // 每天固定的「今日新字」，保证当天多次打开结果一致
public class DailyPlanEntity {
    @PrimaryKey @NonNull public String date = "";
    public int goal;                      // 当天生效的 N
    @NonNull public String newWordIds = "";   // 逗号分隔，长度 = goal
}

@Entity(tableName = "session")
public class SessionEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public String date = "";
    @NonNull public String kind = "DAILY";    // DAILY / REVIEW / THEME
    @Nullable public String themeId;
    @NonNull public String newWordIds = "";
    @NonNull public String reviewWordIds = "";
    public long startedAt;
    @Nullable public Long completedAt;
    public int stars;
    public int correct;
    public int total;
}

@Entity(tableName = "quiz_answer",
        foreignKeys = @ForeignKey(entity = SessionEntity.class, parentColumns = "id",
                                  childColumns = "sessionId", onDelete = ForeignKey.CASCADE),
        indices = @Index("sessionId"))
public class QuizAnswerEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    public long sessionId;
    @NonNull public String wordId = "";
    @NonNull public String chosenHanzi = "";
    public boolean correct;
    public boolean firstTry;
    public long answeredAt;
}

@Entity(tableName = "daily_usage")
public class DailyUsageEntity {
    @PrimaryKey @NonNull public String date = "";
    public int childSeconds;
    public int extraMinutesGranted;       // 家长当天延长的分钟数
}

@Entity(tableName = "favourite")
public class FavouriteEntity {
    @PrimaryKey @NonNull public String itemId = "";
    public long addedAt;
}
```

**DAO 约定：**

```java
@Dao
public interface WordProgressDao {
    @Query("SELECT * FROM word_progress")                       LiveData<List<WordProgressEntity>> observeAll();
    @Query("SELECT * FROM word_progress WHERE wordId = :id")    @Nullable WordProgressEntity getSync(String id);
    @Query("SELECT * FROM word_progress WHERE learnedAt IS NOT NULL AND dueDate <= :today ORDER BY dueDate, wordId")
    List<WordProgressEntity> dueSync(String today);
    @Insert(onConflict = OnConflictStrategy.REPLACE)            void upsert(WordProgressEntity e);
}
```

- 观察用 `LiveData<…> observeXxx()`；一次性读写用 `xxxSync()` 后缀，**只能在 `diskIO()` 线程调用**。
- 多步写入（答题 = 写 `quiz_answer` + 更新 `word_progress` + 更新 `session`）用 `db.runInTransaction(Runnable)`。
- `LocalDate` 统一以 ISO 字符串（`2026-10-21`）存储，不写 `TypeConverter`，字符串比较即日期比较。

**派生数据（查询，不存表）：**

- 累计星星 = `SUM(session.stars)`
- 某天学会字数 = `COUNT(word_progress WHERE learnedAt 落在当天)`
- 某天测验 = 当天 `session` 的 `correct/total`
- 本周柱状图 = 近 7 天的 `daily_usage.childSeconds`

不要开 `fallbackToDestructiveMigration`；版本 1 起导出 schema（`room.schemaLocation`）。

### 6.4 SharedPreferences 键（`SettingsStore`）

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `daily_limit_min` | Int | 30 | 15/30/45/60 |
| `rest_reminder` | Bool | true | 连续 15 分钟提醒休息 |
| `daily_goal` | Int | 3 | 1–10 |
| `reminder_time` | String | "19:30" | HH:mm |
| `reminder_days` | String | "1,2,3,4,5" | ISO 星期 |
| `sleep_default_min` | Int | 20 | |
| `sleep_finish_track` | Bool | true | 播完当前这首再停 |
| `onboarded` | Bool | false | |
| `notif_rationale_shown` | Bool | false | |

`SettingsStore` 写法：每个键一个 getter / setter（`getDailyGoal()`、`setDailyGoal(int)`），写入用 `apply()`。需要观察变化的页面（首页进度段数、家长设置页）用 `SettingsStore.observeXxx()` 返回 `LiveData`，内部基于 `OnSharedPreferenceChangeListener` 实现（监听器要用强引用持有，否则会被 GC）。键名集中在 `SettingsStore.Keys` 常量类里。

`tools/validate_content.py` 必须检查：ID 唯一；每个 `audio` 文件存在；每个 `image` / `cover` 在 `res/drawable` 里有同名文件；`distractors` 里的字不等于正确答案；每主题恰好 6 字。在 `preBuild` 前手动跑，或者挂一个 Gradle `Exec` 任务。

---

## 7. 核心业务逻辑

全部放在 `domain/`，纯 Java（不 import 任何 `android.*`），**每个类都要有单测**（§12）。

### 7.1 首页模式 `HomeModeResolver`

```
06:00–16:59 → WORDS
17:00–05:59 → CULTURE（22:00 后主角卡只推 bedtime=true 的条目）
```

手动切换：点「字」或「文」模块后，模式存在 `HomeViewModel` 内存里（**不写 SharedPreferences**）。下次冷启动按时间规则重新算。进程被杀也算冷启动。

### 7.2 今日计划与会话 `SessionBuilder`

**今日新字**（每天第一次打开首页时生成并写 `daily_plan`，当天不变）：

1. `N = settings.daily_goal`
2. 按主题 `order`、字的数组顺序遍历，取前 N 个 `learnedAt == null` 的字。
3. 如果全部学完，`newWordIds` 为空，首页显示「全部学会啦」，START 变「复习一下」。

**一轮 DAILY 会话：**

```
k        = 今日新字里已 learned 的数量
newWords = 今日新字里还没 learned 的前 min(3, N − k) 个
reviews  = ReviewScheduler.pickReviews(limit = 5 − newWords.size, exclude = newWords)
quiz     = newWords + reviews，打乱；少于 5 题就有几题出几题，最少 1 题
```

- `newWords` 为空时 kind = REVIEW：跳过识字卡，直接进测验。
- `newWords` 和 `reviews` 都为空（第一天、一个字都没学）不可能发生，因为第一天一定有新字。
- 识字卡只展示 `newWords`。

**THEME 会话**（词表页 Quiz 入口）：从该主题 6 字里随机取 5 题，不发星星，不影响今日进度，**但会更新 `word_progress`**（答对算学会）。

### 7.3 间隔复习 `ReviewScheduler`（Leitner）

| box | 下次复习间隔 |
|---|---|
| 1 | 1 天 |
| 2 | 2 天 |
| 3 | 4 天 |
| 4 | 7 天 |
| 5 | 14 天 |

- 第一次学会（印章或答对）：`box = 1`，`dueDate = today + 1`，写 `learnedAt`。
- 测验答对（第一次就对）：`box = min(box + 1, 5)`，`dueDate = today + interval(box)`。
- 测验答错：`box = 1`，`dueDate = today + 1`。**`learnedAt` 不清空**，学会就是学会，只是要多复习。
- `pickReviews(limit)`：先取 `dueDate <= today`，按 `dueDate` 升序；不够再取已学会里 `lastSeenAt` 最早的；都按 `wordId` 稳定排序，保证可测。

### 7.4 出题 `QuizGenerator`

- 题型只有一种：**听音 + 看图 → 四选一选字**。
- 干扰项：优先用 `distractors`；不够 3 个时从同主题其他字补；同主题不够再从其他已学主题补。四个选项随机排，正确答案位置均匀分布（测试里跑 1000 次，每个位置占 20%–30%）。
- `Random` 通过构造函数注入，测试用固定种子。

### 7.5 星星 `StarCalculator`

```
DAILY 会话：stars = max(1, 本轮 newWords 中「第一次就答对」的个数)，上限 3
REVIEW 会话：stars = 1（完成星）
THEME 会话：stars = 0（奖励页隐藏星星行）
```

奖励页的星星槽位 = `newWords.size`（1–3），亮起的数量 = stars。
**星星只在 Reward 页发放并写库**，其他地方都不加星。

### 7.6 印章「学会」

识字卡点印章 → `markLearned(wordId)`（同 §7.3 首次学会）。再点一次**不撤销**，只播放一次「嗒」，防止孩子来回点。
测验答对一个未学会的字，同样算学会。

### 7.7 家长验证 `ParentChallenge`

- 题型：两位数加法（结果 20–99，且必须进位），如 `27 + 38`；或一位数乘法 `6 × 7`。各占一半。设计图里的 `8 + 3` 对 6 岁孩子太简单，不用。
- 答错：输入框抖动、清空、**换一道新题**。不锁定，不计次。
- 键盘：`inputType="number"`，`imeOptions="actionDone"`，回车即确认。

---

## 8. 设计系统落地

### 8.1 颜色（已按评审修正）

`values/colors.xml`：

```xml
<!-- 中性 -->
<color name="ls_paper">#F8EFDD</color>
<color name="ls_card">#FFFDF6</color>
<color name="ls_ink">#211D18</color>
<color name="ls_muted">#6E6252</color>
<color name="ls_stroke">#211D18</color>
<color name="ls_shadow">#211D18</color>
<color name="ls_track">#E2D3B8</color>        <!-- 未完成进度段、关闭的开关 -->
<!-- 职务色 -->
<color name="ls_red">#B8331C</color>          <!-- 修正：原 #C93A22 在纸白上 4.47 -->
<color name="ls_on_red">#FFFDF6</color>
<color name="ls_yellow">#F5B301</color>
<color name="ls_on_yellow">#211D18</color>
<color name="ls_green">#27A567</color>
<color name="ls_on_green">#211D18</color>     <!-- 青绿上只用墨字 -->
<color name="ls_blue">#1F6FD0</color>
<color name="ls_on_blue">#FFFDF6</color>
<!-- 反馈容器 -->
<color name="ls_clay">#F6D3C6</color>
<color name="ls_on_clay">#7A4A33</color>      <!-- 答错说明文字 -->
<color name="ls_mint">#CDEBD8</color>
<color name="ls_on_mint">#1B5E3B</color>
```

`values-night/colors.xml`：

```xml
<color name="ls_paper">#17130E</color>
<color name="ls_card">#241F19</color>
<color name="ls_ink">#F3E7D3</color>
<color name="ls_muted">#C9B79B</color>
<color name="ls_stroke">#B8AA93</color>
<color name="ls_shadow">#000000</color>       <!-- 硬影在深色下用纯黑，不跟随文字色 -->
<color name="ls_track">#3A3229</color>
<color name="ls_red">#FF8A6E</color>
<color name="ls_on_red">#17130E</color>
<color name="ls_yellow">#FFC94D</color>
<color name="ls_on_yellow">#17130E</color>
<color name="ls_green">#6FD3A2</color>
<color name="ls_on_green">#17130E</color>
<color name="ls_blue">#7FB4FF</color>
<color name="ls_on_blue">#17130E</color>
<color name="ls_clay">#4A2A24</color>
<color name="ls_on_clay">#F3C7B8</color>
<color name="ls_mint">#1E4A3A</color>
<color name="ls_on_mint">#BFEBD3</color>
```

**`tools/contrast_check.py`**：解析两份 colors.xml，对下列组合算 WCAG 对比度，任何一项 < 4.5 就以非零码退出：
`ink/paper, ink/card, muted/paper, muted/card, on_red/red, on_yellow/yellow, on_green/green, on_blue/blue, on_clay/clay, on_mint/mint, ink/clay, ink/mint`。
另外 `red/paper`（5.21，彩色描边文字，如「Quiz」入口）要求 ≥ 4.5。`blue/paper` 只有 4.33：**蓝色文字只能放在卡片色上**（`blue/card` 4.86），纸白底上的蓝色只用于图标和填充块。

M3 主题映射（`themes.xml`）：`colorPrimary=ls_red`、`colorOnPrimary=ls_on_red`、`colorSecondary=ls_blue`、`colorTertiary=ls_green`、`colorSurface=ls_card`、`android:colorBackground=ls_paper`、`colorOnSurface=ls_ink`、`colorOnSurfaceVariant=ls_muted`、`colorOutline=ls_stroke`、`colorError=ls_on_clay`、`colorErrorContainer=ls_clay`。
**注意：** M3 组件只在家长区用（TimePicker、Snackbar、MaterialSwitch）。孩子区的块全部用 `BlockLayout`，不用 MaterialButton。

### 8.2 字体

| 资源 | 内容 | 用法 |
|---|---|---|
| `font/poppins_medium/semibold/bold/extrabold.ttf` | Poppins 500/600/700/800 | 拼音、英文、按钮、数字 |
| `font/littlestar_latin.xml` | font-family 组合上面 4 个 | `fontFamily="@font/littlestar_latin"` |
| `font/noto_sans_sc_hanzi.otf` | Noto Sans SC Bold **子集** | 识字卡大字、词块、测验选项、首页今日字 |

- **教学汉字用打包的子集字体**。不同厂商（MIUI、One UI）的系统中文字体不一样，打包才能保证「月」的字形和课本一致。子集包含 `words.json` + `culture.json` 里所有汉字 + 常用 UI 字，用 `pyftsubset` 生成，目标 < 500KB。新增内容后要重跑子集脚本（写进 `tools/`）。
- UI 中文（标题、说明）用系统默认 `sans-serif`，不打包全量字体。
- **所有显示汉字的 TextView 设 `android:textLocale="zh-CN"`**（或在主题里设置）。澳洲设备系统语言多为英文，不设的话 CJK 回退可能选到日文字形。
- 拼音必须带声调符号（ā á ǎ à），Poppins 支持；在 Gallery 里检查 `ǚ`、`ǜ`。

`TextAppearance`（`styles_text.xml`）：

| 名称 | 字体 | 字号 | 用途 |
|---|---|---|---|
| `TextAppearance.LS.HanziHero` | hanzi 子集 | 100sp（首页 108sp） | 识字卡 / 首页今日字 |
| `TextAppearance.LS.HanziTile` | hanzi 子集 | 40sp | 词块 |
| `TextAppearance.LS.HanziOption` | hanzi 子集 | 34sp | 测验选项 |
| `TextAppearance.LS.Display` | sans-serif bold | 28sp | 页面大标题 |
| `TextAppearance.LS.Title` | sans-serif bold | 22sp | 卡片 / 模块名 |
| `TextAppearance.LS.Headline` | sans-serif bold | 24sp | 「选一个主题」 |
| `TextAppearance.LS.Body` | sans-serif medium | 16sp | 家长正文 |
| `TextAppearance.LS.Caption` | sans-serif medium | 12sp | 孩子区最小字号 |
| `TextAppearance.LS.Pinyin` | latin semibold | 24sp / 14sp | 识字卡 / 词块（评审建议最小 14） |
| `TextAppearance.LS.English` | latin medium | 16sp / 12sp | |
| `TextAppearance.LS.Button` | latin extrabold | 20sp | 主块文字 |

**大字裁切保护：** 汉字大字在 1.3 倍字体下会变成 130sp+。`HanziHero` 的 TextView 用 `autoSizeTextType="uniform"`，`autoSizeMaxTextSize=140sp`，`autoSizeMinTextSize=72sp`，并固定高度（如 156dp）。

### 8.3 尺寸（`dimens.xml`）

```xml
<dimen name="ls_margin">24dp</dimen>
<dimen name="ls_section">24dp</dimen>
<dimen name="ls_section_lg">32dp</dimen>
<dimen name="ls_card_pad">20dp</dimen>
<dimen name="ls_gap">12dp</dimen>
<dimen name="ls_gap_sm">8dp</dimen>

<dimen name="ls_btn_primary">64dp</dimen>
<dimen name="ls_btn_secondary">56dp</dimen>
<dimen name="ls_btn_icon">48dp</dimen>
<dimen name="ls_row_parent">56dp</dimen>

<dimen name="ls_radius_hero">28dp</dimen>
<dimen name="ls_radius_block">18dp</dimen>
<dimen name="ls_radius_tile">16dp</dimen>
<dimen name="ls_radius_chip">10dp</dimen>
<dimen name="ls_radius_input">14dp</dimen>

<dimen name="ls_stroke_hero">4dp</dimen>
<dimen name="ls_stroke_block">3dp</dimen>
<dimen name="ls_stroke_chip">2dp</dimen>
<dimen name="ls_shadow_hero">8dp</dimen>
<dimen name="ls_shadow_block">6dp</dimen>
<dimen name="ls_shadow_tile">4dp</dimen>
<dimen name="ls_shadow_parent">2dp</dimen>
<dimen name="ls_press_remain">2dp</dimen>   <!-- 按下后剩余的影子 -->

<dimen name="ls_content_max_width">480dp</dimen>
```

`values-sw600dp/dimens.xml`：`ls_margin=40dp`；内容容器宽度上限 480dp 居中。

### 8.4 硬影块 `BlockLayout`（核心组件）

**不要用 layer-list + elevation 拼。** 硬影要求按下时内容下沉、影子变短，layer-list 做不到「内容动、影子不动」。写一个自定义 `FrameLayout`：

```java
public class BlockLayout extends FrameLayout {

    private ColorStateList fill;          // attr: ls_fill（支持选中态）
    private int strokeColor;              // attr: ls_strokeColor，默认 ?attr/lsStroke
    private int shadowColor;              // attr: ls_shadowColor，默认 ?attr/lsShadow
    private float strokeWidth;            // attr: ls_strokeWidth
    private float shadowOffset;           // attr: ls_shadowOffset
    private float cornerRadius;           // attr: ls_cornerRadius
    private float pressRemain;            // = @dimen/ls_press_remain（2dp）
    private boolean pressable;            // attr: ls_pressable，默认 = isClickable()

    private final Paint fillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float pressFraction;          // 0 = 抬起，1 = 按下
    @Nullable private ValueAnimator pressAnim;

    public BlockLayout(@NonNull Context c) { this(c, null); }
    public BlockLayout(@NonNull Context c, @Nullable AttributeSet a) { this(c, a, R.attr.blockLayoutStyle); }
    public BlockLayout(@NonNull Context c, @Nullable AttributeSet a, int defStyleAttr) {
        super(c, a, defStyleAttr);
        // 1. obtainStyledAttributes 读取上面的属性（记得 recycle()）
        // 2. strokePaint.setStyle(Paint.Style.STROKE)；其余 FILL
        setWillNotDraw(false);
        setClipToPadding(false);
        // 3. 预留影子空间：内容区不包含右下角的影子
        setPadding(getPaddingLeft(), getPaddingTop(),
                   getPaddingRight() + (int) shadowOffset, getPaddingBottom() + (int) shadowOffset);
        setElevation(0f);
    }

    @Override protected void dispatchDraw(@NonNull Canvas canvas) {
        float travel = (shadowOffset - pressRemain) * pressFraction;
        float w = getWidth() - shadowOffset, h = getHeight() - shadowOffset;
        float half = strokeWidth / 2f, r = cornerRadius;
        fillPaint.setColor(fill.getColorForState(getDrawableState(), fill.getDefaultColor()));
        // 1. 影子：固定在 (offset, offset)，不随按下移动
        canvas.drawRoundRect(shadowOffset, shadowOffset, w + shadowOffset, h + shadowOffset, r, r, shadowPaint);
        // 2. 块本体 + 子 View 整体平移 travel
        int save = canvas.save();
        canvas.translate(travel, travel);
        canvas.drawRoundRect(half, half, w - half, h - half, r, r, fillPaint);
        canvas.drawRoundRect(half, half, w - half, h - half, r, r, strokePaint);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (pressable) animatePress(isPressed() ? 1f : 0f);   // 按下 100ms，松开 180ms，OvershootInterpolator(1.2f)
        invalidate();                                          // 选中态可能改变填充色
    }

    private void animatePress(float target) {
        if (pressFraction == target) return;
        if (pressAnim != null) pressAnim.cancel();
        if (!ValueAnimator.areAnimatorsEnabled()) { pressFraction = target; invalidate(); return; }
        pressAnim = ValueAnimator.ofFloat(pressFraction, target);
        pressAnim.setDuration(target == 1f ? 100 : 180);
        pressAnim.setInterpolator(target == 1f ? new DecelerateInterpolator() : new OvershootInterpolator(1.2f));
        pressAnim.addUpdateListener(a -> { pressFraction = (float) a.getAnimatedValue(); invalidate(); });
        pressAnim.start();
    }

    @Override public CharSequence getAccessibilityClassName() { return Button.class.getName(); }

    // setFill(ColorStateList) / setShadowOffset(float) 等 setter：改完调用 invalidate()（改 offset 还要 requestLayout()）
}
```

要点：

- 按下时位移 = `shadowOffset − 2dp`，这样 6dp 影子按下后剩 2dp，和设计书一致。
- 点按区域包含影子区域，整个 View 都可点。
- 按下时调用 `container.sfx.play(Sfx.TAP)` 和 `performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)`。`BlockLayout` 不直接依赖 `AppContainer`：通过静态 `BlockLayout.setPressSoundHook(Runnable)` 在 `LittleStarApp.onCreate` 里注入。
- 覆盖 `getAccessibilityClassName()` 返回 `Button.class.getName()`，TalkBack 读成「按钮」。
- **减少动态效果**：`ValueAnimator.areAnimatorsEnabled()` 为 `false` 时 `pressFraction` 直接跳变，不做动画。
- 在 `res/values/attrs.xml` 里声明 `<declare-styleable name="BlockLayout">` 和主题属性 `blockLayoutStyle`、`lsStroke`、`lsShadow`。
- `fillColor` 支持 `ColorStateList`，用于选中态（选中 = 黄底）。
- 硬影绝对不能用模糊、`elevation` 或 `outlineProvider`。所有 `BlockLayout` 设 `elevation=0`。

**样式（`styles_block.xml`）：**

| 样式 | 高 | 填充 | 线 | 影 | 圆角 |
|---|---|---|---|---|---|
| `Widget.LS.Block.Primary` | 64dp | `ls_red` | 4dp | 6dp | 18 |
| `Widget.LS.Block.Primary.Blue` | 64dp | `ls_blue` | 4dp | 6dp | 18 |
| `Widget.LS.Block.Secondary` | 56dp | `ls_card` | 3dp | 5dp | 18 |
| `Widget.LS.Block.Icon` | 48dp | `ls_card` | 3dp | 4dp | 14 |
| `Widget.LS.Block.Hero` | — | `ls_card` | 4dp | 8dp | 28 |
| `Widget.LS.Block.Module` | 104dp | `ls_card` | 4dp | 6dp | 18 |
| `Widget.LS.Block.Tile` | 160dp | `ls_card` | 3dp | 4dp | 16 |
| `Widget.LS.Block.Option` | 92dp | `ls_card` | 3dp | 4dp | 16 |
| `Widget.LS.Block.Parent` | 56dp | `ls_card` | 2dp | 2dp | 14 |

### 8.5 其他自定义 View

| View | 说明 |
|---|---|
| `SegmentProgressView` | 属性 `segments`、`filled`、`fillColor`；段宽 = (宽 − 间距 × (n−1)) / n，**撑满可用宽度**；n > 8 时退化为单条进度条。`contentDescription` 自动设为「已学 2 / 6」 |
| `StepperView` | − / 数字 / +，范围可配；到边界时按钮变灰并 `isEnabled=false` |
| `BarChartView` | 7 根柱，今天用 `ls_red`，其余 `ls_ink` 40%；顶部写分钟数；Canvas 绘制，不引图表库 |
| `HanziTextView` | 继承 `AppCompatTextView`，固定 `textLocale=zh-CN`、子集字体、`includeFontPadding=false` |
| `SoundWaveView` | 识字卡朗读中的 3 根声波条，`SpeechPlayer.isPlaying` 驱动 |
| `StampView` | 学会印章：未学 = 描边方印 + 「会」字；已学 = 朱红底 + 米白「会」字 + 盖章动效（缩放 1.3 → 1 + 旋转 −8° → 0，240ms） |

### 8.6 动效（`core/ui/Motion.java`）

| 名称 | 实现 |
|---|---|
| 翻卡 | `ViewPager2` + `PageTransformer`：translationX + rotation(±2°)，240ms |
| 答对 | 选项 `fillColor` → `ls_mint`，勾图标 scale 0 → 1，300ms |
| 答错 | translationX 关键帧 0, −4, 4, −4, 4, 0（dp），200ms；`fillColor` → `ls_clay` |
| 得星 | scale 0.6 → 1.15 → 1，400ms；每颗间隔 250ms；每颗播放一次 `sfx_star` |
| 页面切换 | 孩子区 `overrideActivityTransition`（API 34+）/ `overridePendingTransition`：右进左出 260ms；家长区淡入 180ms |

全部动效先检查 `ValueAnimator.areAnimatorsEnabled()`；关闭时改为淡入或直接切换。

### 8.7 Edge-to-edge 与安全区

- `targetSdk 35` 会强制 edge-to-edge。每个 Activity `enableEdgeToEdge()`。
- 根布局用 `ViewCompat.setOnApplyWindowInsetsListener` 把 `systemBars` 的 inset 加到 padding 上（`Insets.java` 提供 `View.applySystemBarPadding()`）。
- 底部主按钮距离手势条至少 16dp。
- 状态栏图标颜色：浅色主题深色图标，深色主题浅色图标（`WindowInsetsControllerCompat.isAppearanceLightStatusBars`）。
- **方向：** 手机 `screenOrientation="portrait"`；sw600dp 以上不锁方向，内容容器宽度上限 480dp 居中。

### 8.8 图标与插画

- 图标：24dp 网格、2.8dp 描边、圆头。对照设计书 §4.5 的 20 个名字做 `ic_back.xml` … `ic_scroll.xml`，`fillColor` 全部用 `?attr/colorOnSurface`，不写死。
- 插画：`assets/illustrations/*.svg` → Android Studio「Vector Asset」导入 → `ill_*.xml`。转换前要处理掉 VectorDrawable 不支持的东西：
  - `panda_sleep.svg` 里有 2 个 `<text>`（z z），要先在 Inkscape / Figma 里转成路径；
  - 导出的 SVG 带一个 480×480 的纸白背景矩形，要删掉；
  - `transform` 要展开（Studio 一般会处理，导入后肉眼对比）。
- 熊猫的黄色圆贴和墨圈**不放进 drawable**，由 `BlockLayout`（圆形）或 `ShapeableImageView` 提供，这样一份熊猫 drawable 在 40dp 和 100dp 都能用。
- 词卡配图（30–36 张）MVP 阶段可以用「彩色圆角块 + 字形」占位（`ill_placeholder`），不阻塞开发。

### 8.9 音频

| 类 | 实现 | 要点 |
|---|---|---|
| `SfxPlayer` | `SoundPool`（maxStreams 4），启动时预加载 5 个音效 | 家长区不播放音效；音量跟随媒体音量 |
| `SpeechPlayer` | 单独的 `ExoPlayer` 实例，播放 `asset:///audio/words/xx.ogg` | `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_SPEECH)`，`handleAudioFocus=true`；新请求打断旧的；暴露 `LiveData<Boolean> isPlaying()` |
| `PlaybackService` | `MediaSessionService` + ExoPlayer | 长音频；后台播放；系统媒体通知 |

**字音缺失降级：** 文件不存在时，debug 构建用 `TextToSpeech(Locale.SIMPLIFIED_CHINESE)` 读 `hanzi` 并打 `Log.w`；release 构建静默跳过，`validate_content.py` 会在构建前拦住这种情况。

**占位音频：** `tools/make_placeholder_audio.sh` 用 macOS `say -v Tingting -o x.aiff "月"` 再 `ffmpeg` 转 ogg，开发阶段用。**正式版必须换成真人录音**。

---

## 9. 逐屏实现要点

下面只写**工程上要注意的地方**，视觉结构看设计书对应章节，再加上评审修订。

### 9.1 Home

- 布局：`NestedScrollView` > 竖向 `LinearLayout`（小屏能滚动；360×800 刚好一屏）。
- **按评审 P0-4 修订**：主角卡里**不放**「字 / 文」切换。主角卡 = 「今天 · 识字 / 故事」标签 + 主体 + 蓝色「听」块。下方两个模块是**描边块**（`Widget.LS.Block.Module`，卡片底），点「字」或「文」模块 = 切换模式 **并且** 主角卡切换内容；**长按或第二次点击**已选中的模块进入 Themes / CultureCategories。
  > 如果设计方坚持原方案，只需要把模块改回实色、在主角卡加切换条，ViewModel 不用改。
- 字态：主体 = 今日下一个未学的字（`HanziHero` 108sp）+ 配图；主按钮 START（红）。
- 文态：主体 = 推荐条目封面 + 标题；主按钮 LISTEN（蓝）→ `PlayerActivity`。
- 今日进度：`SegmentProgressView(segments = N, filled = k)` + 「Today k/N words」。目标完成时文案变「今天学完啦」，START 文案变「复习一下」。
- 星星胶囊：显示累计星数，**不可点**（`isClickable=false`，`importantForAccessibility=yes`，描述「12 颗星星」）。
- 家长入口：低权文字行，点击 → `ParentGateActivity`。
- 进入时自动朗读今日字一次（只在当天第一次进入时）。

### 9.2 Themes

- `RecyclerView` + `GridLayoutManager(2)`，6 个 `Tile`。
- 每块：主题插画（无插画时用朱红字形块）+ 序号徽章（黄）+ 中文名 + 英文 + 「6 个字」+ 已学数（如「2/6」）；6/6 时显示绿勾徽章。
- 颜色统一：字形块全部 `ls_red`（评审 P1-3），不按主题换色。

### 9.3 WordList

- 顶栏右侧「Quiz」描边入口（`ls_red` 描边 + 红字；`red/paper` ≥ 4.5 已验证）。
- `SegmentProgressView(6, learned)`。
- 2×3 网格；点块 → `Flashcard(BROWSE, wordIds = 主题 6 字, startIndex = i)`，进入后自动朗读。
- 从 Flashcard 返回时刷新已学状态（DAO 返回 `LiveData`，Activity 观察即可自动刷新）。

### 9.4 Flashcard

- `ViewPager2`，每页一张主角卡；左右滑切换。翻页播放 `sfx_flip` 并自动朗读新字。
- 点卡片或「听」→ `SpeechPlayer.play(word)`；朗读中显示 `SoundWaveView`。
- 印章 `StampView`（评审 P0-5：印章里是「会」字，不是星星）。
- NEXT：最后一张时文案变「完成」。SESSION 模式「完成」→ `Quiz(SESSION, sessionId)` 并 `finish()`；BROWSE 模式「完成」→ `finish()`。
- 进度：顶栏「2/6」+ `SegmentProgressView`。

### 9.5 Quiz

- 一页一题，**不用 ViewPager**（不能滑过题目）。题目卡 = 配图 + 蓝色「听」；评审建议去掉 28sp 英文，放到 debug 开关 `showEnglishHint`，默认关。
- 进入每题自动读题（`SpeechPlayer.play(word)`）。
- 点选项后立即锁定全部选项（防连点），判定：
  - 对：该选项 → mint + 勾，播放 `sfx_correct`；
  - 错：该选项 → clay + ×（墨色，不用红），正确选项 → mint + 勾，播放 `sfx_wrong` 后再朗读正确字。
  - 底部出现反馈条（48dp）+ NEXT（64dp），从下方滑入。
- 每题写一条 `QuizAnswerEntity`，并调用 `ReviewScheduler` 更新 `word_progress`。
- 最后一题 NEXT → 「完成」→ `Reward(sessionId)`，`finish()`。
- 状态保存在 ViewModel（旋转不丢）；当前题号和已答结果同时写进 `SavedStateHandle`（ViewModel 构造函数接收它，工厂用 `CreationExtras` 里的 `SavedStateHandleSupport.createSavedStateHandle(extras)` 取得），进程被杀后回到同一题。题目顺序在会话创建时就固定写进 `session` 表，恢复时按 `sessionId` 重建，不重新随机。

### 9.6 Reward

- 读取 session，计算星星（`StarCalculator`），**写库只写一次**（session.completedAt 为空才写）。
- 星星逐颗弹出 + `sfx_star`；播放提示音「太棒了」。
- 学会的字块（本轮 newWords）。
- **按评审 P1-7**：主按钮「完成」（红，回首页）；次按钮「再来一次」（描边）。THEME 模式隐藏星星行。
- confetti 只在黄色舞台内，最多 12 个，播放一次。

### 9.7 CultureCategories

- 2×2 + 节日宽卡；MVP 只显示 `mvp=true` 的 3 类，布局变成 2 + 1 宽卡（古诗做宽卡）。
- 颜色统一青绿（评审 P1-2）。
- 收藏行 → Favourites。

### 9.8 CultureList

- `RecyclerView`，行高 80dp。点行 → Player（`queue` = 本类全部 id）；点行内播放按钮 → 直接让 `PlaybackService` 播放，并高亮该行（收集播放状态）。
- 底部 Sleep timer 行 → `SleepTimerSheet`。

### 9.9 Player

- 用 `MediaController`（`SessionToken` 连接 `PlaybackService`）控制播放，不在 Activity 里持有 ExoPlayer。
- 进度条：自定义 10dp 粗轨道 + 方块拖柄（可以用 `Slider` 改样式，或者自定义 View）；每 250ms 刷新一次位置。
- 歌词：按 `startMs` 找当前句，当前句黄底高亮，后两句 `ls_muted`。没有歌词就隐藏卡片。
- 收藏 → `FavouriteRepository.toggle(itemId)`。
- Sleep timer 行显示剩余时间（`SleepTimerController.remaining`）。

### 9.10 Favourites

- 有内容：列表 + 排序（最新 / 最早，内存切换）。点红心取消收藏，**行不立刻消失**，变成空心，离开页面后再移除（防止误点后找不回）。
- 空状态：黄色舞台 + 睡熊猫 + 「还没有收藏」+ 主块「去听一首」→ CultureCategories。

### 9.11 SleepTimerSheet

- 10 / 20 / 30 三个选项块（选中 = 黄底），开关「播完当前这首再停」，主块「开始计时」，文字按钮「取消」。
- 已在计时：显示剩余时间 + 「取消计时」。

### 9.12 ParentGate

- `ParentChallenge` 出题；输入框 + 确认（56dp）。
- 键盘弹出时：`windowSoftInputMode="adjustResize"`，确认按钮放在 `ScrollView` 里，保证不被遮挡。
- 答对 → 启动 `target`，`finish()`。

### 9.13 ParentDashboard

- 「今天学得不错」+ 日期；3 张统计块：今日时长 / 今日学会 / **测验正确率**（评审 P0-5，不显示星星）。
- `BarChartView` 显示近 7 天；点柱子在柱顶显示分钟数。
- 三行入口 → Time / Plan / Records。

### 9.14 ParentTime

- 4 个选项块 15 / 30 / 45 / 60（选中 = 黄底 + 勾，评审 P1-4）；开关「休息提醒」。
- **新增「今天再给 10 分钟」行**（评审 P0-3）：只有在今天已达上限时显示，点击 → `extraMinutesGranted += 10`。
- 保存 → 写 `SettingsStore` → Snackbar「已保存」→ `finish()`。取消直接 `finish()`。

### 9.15 ParentPlan

- `StepperView(1..10)`；提醒时间行 → `MaterialTimePicker`（24 小时制）；7 个星期块，每块 44×48dp（评审 P1-5）。
- 至少选一天，否则保存按钮禁用 + 提示「至少选一天」。
- 本周目标卡 = N × 选中天数。
- 保存 → `SettingsStore` → `ReminderScheduler.reschedule()`；**当天的 `daily_plan` 不重算**，从明天起生效（避免当天进度突变）。在保存提示里写明「明天起生效」。

### 9.16 ParentRecords

- 汇总卡：本周学会字数、测验次数、与上周对比。
- 按天分组的列表：日期 / 学会字数 / 测验得分 / 时长；排序切换。
- 空状态：睡熊猫 +「还没有记录」+ 主块「回首页」。
- 底部隐私说明：「所有数据只保存在这台设备上」。

### 9.17 TimeUp

- `kind = REST`：月亮 + 睡熊猫，「眼睛累了吧」，20 秒倒计时块；倒计时结束前「好的」禁用；结束后「好的」→ `finish()`。
- `kind = LIMIT`：「今天看完啦，明天见」+ 睡熊猫，**没有倒计时**；「好的」→ `finishAffinity()`。底部低权文字行「家长」→ `ParentGate(target = ParentTime)`。
- 都不计入孩子时长；不播放奖励音效；暂停 `PlaybackService`。

---

## 10. 系统能力

### 10.1 孩子时长 `ScreenTimeTracker`

**计什么：** 只计「孩子区 Activity 处于 RESUMED」的时间。后台播放（锁屏听故事）**不计**，因为限制的目的是保护眼睛。家长区和 TimeUp 不计。

```java
public final class ScreenTimeTracker {
    private static final long TICK_MS = 15_000;
    private final UsageRepository usage; private final SettingsStore settings;
    private final AppClock clock; private final Handler main;

    @Nullable private Instant resumedAt, continuousSince, lastPausedAt;
    @Nullable private WeakReference<ChildActivity> current;   // 不要强引用 Activity
    private final Runnable tick = new Runnable() {
        @Override public void run() { flushAndCheck(); main.postDelayed(this, TICK_MS); }
    };

    public void onChildResumed(@NonNull ChildActivity a) {
        current = new WeakReference<>(a);
        Instant now = clock.now();
        resumedAt = now;
        if (continuousSince == null || lastPausedAt == null
                || Duration.between(lastPausedAt, now).toMinutes() >= 5) continuousSince = now;
        flushAndCheck();                      // 一进来就检查：超限的直接去 LIMIT
        main.removeCallbacks(tick);
        main.postDelayed(tick, TICK_MS);
    }

    public void onChildPaused() {
        main.removeCallbacks(tick);
        flush();
        resumedAt = null; current = null; lastPausedAt = clock.now();
    }

    /** 主线程调用：算出增量后交给 usage 在 diskIO 线程累加；跨零点按零点拆成两段。 */
    private void flush() { /* usage.addSeconds(date, seconds)；resumedAt = now */ }

    private void flushAndCheck() {
        flush();
        usage.loadToday(today -> {           // diskIO 读 → 回调在主线程
            ChildActivity a = current == null ? null : current.get();
            if (a == null || a.isFinishing()) return;
            int limitSec = (settings.getDailyLimitMin() + today.extraMinutesGranted) * 60;
            if (today.childSeconds >= limitSec) {
                if (!a.deferTimeUp()) TimeUpActivity.start(a, TimeUpActivity.Kind.LIMIT);
            } else if (settings.isRestReminder() && continuousSince != null
                    && Duration.between(continuousSince, clock.now()).toMinutes() >= 15
                    && !a.deferTimeUp()) {
                continuousSince = null;
                TimeUpActivity.start(a, TimeUpActivity.Kind.REST);
            }
        });
    }
}
```

- 精度 15 秒就够了，不要每秒写库。
- 跨零点：flush 时如果 `resumedAt` 和 `now` 不在同一天，按零点拆开记到两天。
- 正在会话中（Flashcard / Quiz）达到上限时：**等这一轮奖励页结束后再弹 LIMIT**，不要在答题中间打断。`ChildActivity` 提供 `protected boolean deferTimeUp() { return false; }`，Flashcard / Quiz 覆盖返回 `true`；Reward「完成」时再检查一次。

### 10.2 播放与睡前定时

- `PlaybackService : MediaSessionService`，manifest：
  ```xml
  <service android:name=".playback.PlaybackService"
           android:foregroundServiceType="mediaPlayback" android:exported="true">
      <intent-filter><action android:name="androidx.media3.session.MediaSessionService"/></intent-filter>
  </service>
  ```
  权限：`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK`、`POST_NOTIFICATIONS`。
- `SleepTimerController`（应用级单例，放在 `AppContainer`）：持有 `MutableLiveData<Instant> deadline`（null = 未计时）和 `boolean finishTrack`，提供 `start(int minutes, boolean finishTrack)`、`cancel()`、`LiveData<Duration> remaining()`（每秒更新，供 Player / Sheet 显示）。`PlaybackService` 用 `observeForever` 订阅 deadline（`onDestroy` 里 `removeObserver`）：
  - 到点且 `finishTrack = true`：`player.setPauseAtEndOfMediaItems(true)`，当前音轨结束后进入停止流程（在 `Player.Listener.onPlayWhenReadyChanged` 里，reason 为 `PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM` 时触发）。
  - 到点且 `finishTrack = false`：用 `Handler` 每 100ms 把 `player.setVolume()` 降一档，3 秒淡出后 `pause()`，再把音量恢复为 1。
  - 停止后：发「故事讲完啦，晚安」通知（ID 见 §10.3），`stopSelf()`，清空 deadline。
- 定时用 Service 内的 `Handler.postDelayed(stopRunnable, millis)`；deadline 变化时先 `removeCallbacks` 再重新 post。Service 在前台运行，不会被杀。App 被划掉时 `onTaskRemoved` 里如果没在播放就 `stopSelf()`。
- Activity 侧连接：`MediaController.Builder(context, sessionToken).buildAsync()` 返回 `ListenableFuture`，用 `future.addListener(() -> { controller = future.get(); ... }, ContextCompat.getMainExecutor(this))`；`onStop` 里 `MediaController.releaseFuture(future)`。

### 10.3 通知

| 通知 | Channel | ID | 触发 | 点击 |
|---|---|---|---|---|
| 每日学习提醒 | `reminder`（默认重要性） | 1001 | `ReminderReceiver` | `HomeActivity` |
| 休息提醒 | `rest`（低） | 1002 | 仅当 app 在后台且孩子区连续使用到点（实际上大多在前台直接弹 TimeUp，通知是兜底） | `TimeUpActivity(REST)` |
| 睡前故事结束 | `bedtime`（低，无声） | 1003 | `PlaybackService` | `HomeActivity` |
| 媒体播放 | Media3 默认 | Media3 管理 | 播放中 | Player |

- 小图标 `ic_stat_panda.xml`：单色白色剪影。
- 文案用 `BigTextStyle`。每日提醒内容动态生成：「该学汉字啦：今天还有 2 个字」（剩余 = N − k；为 0 时当天**不发**）。
- **通知权限（Android 13+）：** 不在启动时申请。家长第一次在 Plan 页保存提醒时间时，先显示一句说明，再 `requestPermission(POST_NOTIFICATIONS)`；拒绝了就在 Plan 页显示「通知已关闭，去设置打开」行。

### 10.4 提醒调度 `ReminderScheduler`

- `AlarmManager.setWindow(RTC_WAKEUP, 下一个提醒时刻, 10 分钟窗口, PendingIntent→ReminderReceiver)`。不申请 `SCHEDULE_EXACT_ALARM`。
- 下一个提醒时刻：从今天开始找下一个在 `reminder_days` 里、并且时间还没过的日子。
- `ReminderReceiver`：`PendingResult pr = goAsync();` 然后 `container.executors.diskIO().execute(() -> { try { 查库算剩余字数 → 发通知 → 排下一次 } finally { pr.finish(); } })`。必须在 10 秒内 `finish()`。
- 重排时机：`BOOT_COMPLETED`、`TIMEZONE_CHANGED`、`TIME_SET`、Plan 保存、App 启动。
- 权限：`RECEIVE_BOOT_COMPLETED`。

---

## 11. 无障碍、深色、字体缩放

- 所有图标按钮有 `contentDescription`（strings.xml）；装饰图 `importantForAccessibility="no"`。
- 测验选项的描述 = 「选项：月，yuè」；答对 / 答错后用 `announceForAccessibility`。
- `BlockLayout` 被 TalkBack 读成「按钮」。
- 颜色不单独承担意义：答对有勾，答错有 ×，都有声音。
- 深色模式：所有颜色走 `ls_*`，不写死；`ComponentGalleryActivity` 提供深色 / 浅色 / 1.3 倍字体切换，逐个组件检查。
- 1.3 倍字体：标题 `maxLines=1` + `ellipsize=end`；按钮文字 `maxLines=1` + autoSize；卡片高度用 `wrap_content` 或 `minHeight`，不要写死 `layout_height` 后放文字。
- 最小点按：孩子区 ≥ 56dp（图标按钮 48dp 是例外，但要 `TouchDelegate` 扩到 56dp），家长区 ≥ 48dp。

---

## 12. 测试

### 12.1 JVM 单测（必须）

| 类 | 关键用例 |
|---|---|
| `HomeModeResolver` | 05:59 → CULTURE；06:00 → WORDS；16:59 → WORDS；17:00 → CULTURE；22:30 → CULTURE 且 bedtimeOnly |
| `ReviewScheduler` | 首学 box1 / due+1；答对升盒，box5 封顶；答错回 box1，learnedAt 不变；pickReviews 排序稳定、遵守 limit 和 exclude |
| `SessionBuilder` | N=3,k=0 → 3 新 + 2 复习；N=5,k=3 → 2 新 + 3 复习；k=N → REVIEW；全部学完 → 新字为空；daily_plan 当天不变；改 N 当天不生效 |
| `QuizGenerator` | 干扰项不含答案、不重复；同主题不够时跨主题补；正确位置分布均匀 |
| `StarCalculator` | 全错 → 1；3 新全对 → 3；REVIEW → 1；THEME → 0 |
| `ParentChallenge` | 加法结果 20–99 且进位；乘法因子 2–9；答案判定忽略首尾空格 |
| `ScreenTimePolicy` | FakeClock：增量计算正确；跨零点拆分；超限 → LIMIT；extra 分钟生效；连续 15 分钟 → REST；暂停 ≥ 5 分钟重置连续计时；`defer` 时返回 NONE |
| `ContentRepository` | 解析真实 `words.json` / `culture.json` 成功；ID 唯一 |

**JVM 测试写法约定：**

- `domain/` 的类不依赖 Android，直接 `new` 出来测。`ScreenTimeTracker` 依赖 `Handler`，所以把判断逻辑抽成纯 Java 的 `domain/ScreenTimePolicy`（输入：已用秒数、上限、extra、连续开始时间、当前时间、是否 defer；输出：`NONE / REST / LIMIT`），Tracker 只负责计时和跳页。
- `FakeClock implements AppClock`：可 `advance(Duration)`、`set(LocalDateTime)`。
- 测 ViewModel 时加 `@Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();`，并给 ViewModel 传同步的 `AppExecutors`（`Runnable::run`）。
- 读 `LiveData` 的值用测试工具 `LiveDataTestUtil.getOrAwaitValue(liveData)`（官方 architecture-samples 里的写法，自己放到 `src/test/.../util/`）。
- 不引入 Robolectric（除非某个用例确实绕不开，届时记入 `DECISIONS.md`）。

### 12.2 Instrumented

- Room DAO（in-memory）：派生查询（累计星星、本周学会数）。
- Espresso 冒烟：冷启动 → START → 3 张卡点 NEXT → 5 题依次点第一个选项 → Reward 出现 → 完成回 Home，进度显示 3/3。
- Gate：答错清空并换题；答对进入 Dashboard。

### 12.3 截图验收

每个里程碑结束时，在 Pixel 7 模拟器（1080×2400，和设计图同尺寸）下，每屏截 4 张：浅色、深色、浅色 + 1.3 倍字体、360×640 小屏。存到 `docs/screenshots/M<n>/<screen>_<variant>.png`，并和 `assets/screens/<screen>.png` 并排看。

---

## 13. 里程碑

每个里程碑：**目标 → 任务 → 完成标准**。没达到完成标准不要进下一个。

### M0 骨架（0.5 天）
- Gradle（Groovy DSL）、version catalog、包结构、`LittleStarApp`、`AppContainer`、`AppExecutors`、`AppClock`、`Event`、`ViewModelFactory`。
- `colors.xml` ×2、`dimens.xml`、`themes.xml`（Child / Parent）、字体资源、`TextAppearance`。
- `ChildActivity` / `ParentActivity` 基类、edge-to-edge。
- `tools/contrast_check.py`。
- ✅ `./gradlew assembleDebug` 通过；app 启动显示空白 Home（纸白背景）；`contrast_check.py` 退出码 0；`find . -name '*.kt' -o -name '*.kts'` 无输出，`app/build.gradle` 中没有 kotlin 插件。

### M1 组件库（1 天）
- `BlockLayout` 及其 9 个样式、`SegmentProgressView`、`StepperView`、`StampView`、`SoundWaveView`、`HanziTextView`、`BarChartView`、20 个图标、熊猫 drawable。
- `SfxPlayer` + 5 个音效（可用占位音）。
- `ComponentGalleryActivity`：所有组件在一页，带浅 / 深 / 1.3 倍切换。
- ✅ Gallery 截图和 `assets/system/system_overview.png` 对比，硬影按下效果正确（内容动、影子不动，剩 2dp）；深色下硬影是黑色。

### M2 数据层（1 天）
- `words.json`（5 主题 30 字）、`culture.json`（3 类 10 条）、占位音频、`validate_content.py`。
- Room 全部表 + DAO、`SettingsStore`、各 Repository。
- `domain/` 全部类 + §12.1 单测。
- ✅ `./gradlew test` 全绿；`validate_content.py` 退出码 0。

### M3 核心学习流（2 天）
- Home（字态）、Flashcard、Quiz、Reward；`SpeechPlayer`。
- ✅ Espresso 冒烟通过；4 屏 × 4 种截图；每屏实心主块只有一个。

### M4 字模块（0.5 天）
- Themes、WordList、Flashcard(BROWSE)、Quiz(THEME)。
- ✅ 从 WordList 学一个字后返回，绿勾和进度立刻更新；THEME 测验后 Reward 不显示星星。

### M5 文化模块（1.5 天）
- Home（文态 + 时间规则）、CultureCategories、CultureList、Player、Favourites、SleepTimerSheet、`PlaybackService`、`SleepTimerController`。
- ✅ 锁屏后继续播放并能在通知里暂停；设 1 分钟定时（debug 下允许 1 分钟选项）后播完当前音轨停止并发通知；收藏空状态有出口。

### M6 家长区（1.5 天）
- Gate、Dashboard、Time、Plan、Records。
- ✅ 改每日目标后，次日首页进度段数变化（用 FakeClock / 改系统时间验证）；Records 空态和有数据态都正确。

### M7 时长与通知（1 天）
- `ScreenTimeTracker`、TimeUp(REST/LIMIT)、`ReminderScheduler`、`ReminderReceiver`、`BootReceiver`、通知权限流程。
- ✅ 把上限设为 debug 专用的 1 分钟：1 分钟后（不在答题中）进入 LIMIT；退出再进仍是 LIMIT；家长延长后可以继续；重启手机后提醒仍然会触发。

### M8 打磨与验收（1 天）
- 深色 / 1.3 倍 / 小屏 / TalkBack 全屏走查；动效与「减少动态效果」；性能（冷启动 < 1.5s，列表无掉帧）。
- 跑 §15 验收清单，结果写进 `docs/ACCEPTANCE.md`。
- ✅ 清单全部打勾；release 构建（R8 开启）能正常运行，Room / serialization 的 keep 规则正确。

---

## 14. 设计冲突裁决表

开发先按「裁决」做。设计书更新后，再改成和设计一致。

| # | 冲突 | 裁决 | 来源 |
|---|---|---|---|
| 1 | 朱红 `#C93A22` 在纸白上 4.47 | 用 `#B8331C` | 评审 P0-1 |
| 2 | 深色模式没有 on-color；硬影跟随文字色 | 按 §8.1；硬影用纯黑 | P0-2 |
| 3 | 休息页点「好的」就能继续 | 拆 REST / LIMIT（§9.17、§10.1） | P0-3 |
| 4 | 首页 3 个实心块、8 个可点区 | 去掉主角卡内的切换，模块改描边（§9.1） | P0-4 |
| 5 | 印章里用星星；家长区显示星星 | 印章用「会」字；家长区第三项改测验正确率 | P0-5 |
| 6 | 每日 1–10 字和每轮 3 字冲突 | §7.2 / §7.5 | P0-6 |
| 7 | 答错用了深红 | × 用墨色，文字用 `ls_on_clay` | P1-1 |
| 8 | 文化 / 主题多色 | 文化统一青绿，主题统一朱红 | P1-2/3 |
| 9 | 家长选中态用陶土 | 黄底 + 勾 | P1-4 |
| 10 | 星期块 40dp | 44×48dp | P1-5 |
| 11 | 奖励页主按钮是 PLAY AGAIN | 主「完成」，次「再来一次」 | P1-7 |
| 12 | 22:00–05:59 首页模式没有定义 | CULTURE + 只推睡前内容 | 评审 4.3 |
| 13 | 家长验证 8+3 太简单 | 两位数进位加法 / 一位数乘法 | §7.7 |
| 14 | 「家长区无硬影」与图不符 | 家长区 2dp 线 + 2dp 影 | 评审 4.3 |
| 15 | 设计书说 16 个 Activity 但没列 | §5.1；睡前定时用 BottomSheet | 评审 4.3 |
| 16 | 按下：影子 6 → 2，下沉 3dp，数字对不上 | 下沉 = 影子 − 2dp，按下后剩 2dp | §8.4 |
| 17 | 测验题目卡里的英文 28sp | 默认隐藏 | 评审 4.1 |
| 18 | 孩子区最小 10–11sp | 最小 12sp，拼音最小 14sp | 评审 4.1 |
| 19 | 会话中途达到上限 | 本轮结束后再弹 LIMIT | §10.1 |
| 20 | 后台听故事算不算时长 | 不算 | §10.1 |
| 21 | 开发语言 | Java 17 + Groovy 构建脚本；LiveData + AppExecutors 代替 Flow / 协程；SharedPreferences 代替 DataStore；Gson 代替 kotlinx.serialization | 需求变更 2026-10-21 |

---

## 15. 最终验收清单

设计书 §9.3 的 15 条全部保留，另外加：

- [ ] `contrast_check.py` 浅色、深色都通过。
- [ ] 没有 `INTERNET` 权限（`aapt dump permissions` 检查）。
- [ ] 布局 / Java 代码里没有十六进制色值、硬编码中文（`grep` 检查，内容 JSON 除外）。
- [ ] 教学汉字用子集字体 + `textLocale=zh-CN`，在英文系统语言下截图「骨」「直」字形正确（中文字形）。
- [ ] 硬影按下：内容下沉、影子剩 2dp；深色下影子为黑色。
- [ ] 每日目标改为 5，第二天首页显示 5 段，第一轮 3 新、第二轮 2 新。
- [ ] LIMIT 后重开 app 仍停在休息页；家长延长后可用。
- [ ] 会话中途到时，不打断答题。
- [ ] 睡前定时「播完这首再停」生效，并发出晚安通知。
- [ ] 重启设备后每日提醒仍然触发；当天目标完成时不发提醒。
- [ ] 进程被杀后（开发者选项「不保留活动」）从 Quiz 恢复到同一题。
- [ ] 小屏 360×640 每屏可滚动、无裁切。
- [ ] `./gradlew test connectedAndroidTest` 全绿。
- [ ] 全工程无 `.kt` / `.kts` 文件、无 Kotlin 插件、无 `-ktx` 依赖（`./gradlew :app:dependencies` 中允许出现由 AndroidX 传递引入的 `kotlin-stdlib`，这属于正常现象）。
- [ ] 没有 `allowMainThreadQueries()`；StrictMode（debug 构建开启 `detectDiskReads/Writes`）跑主流程无违规。
- [ ] 公开方法的参数与返回值都有 `@NonNull` / `@Nullable` 标注；Lint 无 nullability 错误。
- [ ] release APK < 30MB（音频压成 ogg q4 左右）。

---

## 附录 A：给 Agent 的启动提示词

下面这段可以直接作为第一条任务发给 coding agent：

> 你要用 **Java 17** 实现 Android 应用 LittleStar（不允许使用 Kotlin，构建脚本用 Groovy DSL）。工作目录里有三份文档：
> `LittleStar_工程技术文档.md`（施工图，最高优先级）、`LittleStar_设计评审_v3.md`（修订意见）、`LittleStar_设计书_v3.md`（视觉与交互）。设计图在 `assets/screens/`。
>
> 1. 先完整阅读工程技术文档的 §0、§5、§13、§14。
> 2. 在 `LittleStar/` 目录下新建 Android 工程，只做 **M0**。
> 3. 完成后运行 M0 的完成标准里的每一项检查，把命令和输出贴出来。
> 4. 在 `docs/DECISIONS.md` 里记录你做的所有不在文档里的决定。
> 5. 提交 `M0: ...`，然后停下来等我确认，不要开始 M1。
>
> 之后每个里程碑我会发「继续 M<n>」。每个里程碑结束时，都要贴出完成标准的检查结果和截图路径。

## 附录 B：每个里程碑的复核提示词

> 复核 M<n>：
> 1. 对照工程技术文档 §13 M<n> 的完成标准，逐条给出证据（命令输出 / 截图路径）。
> 2. 对本里程碑涉及的每一屏，把截图和 `assets/screens/` 对应设计图并排比较，列出差异并说明是故意的（引用 §14 裁决编号）还是缺陷。
> 3. `grep` 检查硬编码色值和中文字面量。
> 4. 列出未解决问题和对下一个里程碑的风险。

## 附录 C：资源对照速查

| 设计图 | 布局文件 | ViewModel |
|---|---|---|
| home_word / home_culture | `activity_home.xml` | `HomeViewModel` |
| words_themes | `activity_themes.xml` + `item_theme.xml` | `ThemesViewModel` |
| words_list | `activity_word_list.xml` + `item_word_tile.xml` | `WordListViewModel` |
| flashcard / flashcard_learned | `activity_flashcard.xml` + `page_flashcard.xml` | `FlashcardViewModel` |
| quiz_question / correct / wrong | `activity_quiz.xml` | `QuizViewModel` |
| reward | `activity_reward.xml` | `RewardViewModel` |
| culture_categories | `activity_culture_categories.xml` | `CultureCategoriesViewModel` |
| culture_stories | `activity_culture_list.xml` + `item_culture_row.xml` | `CultureListViewModel` |
| player | `activity_player.xml` | `PlayerViewModel` |
| favourites / favourites_empty | `activity_favourites.xml` + `view_empty_state.xml` | `FavouritesViewModel` |
| sleep_timer | `sheet_sleep_timer.xml` | `SleepTimerViewModel` |
| parent_gate | `activity_parent_gate.xml` | `ParentGateViewModel` |
| parent_dashboard | `activity_parent_dashboard.xml` | `DashboardViewModel` |
| parent_time | `activity_parent_time.xml` | `ParentTimeViewModel` |
| parent_plan | `activity_parent_plan.xml` | `ParentPlanViewModel` |
| parent_records | `activity_parent_records.xml` + `item_record_row.xml` | `RecordsViewModel` |
| time_up | `activity_time_up.xml` | `TimeUpViewModel` |
