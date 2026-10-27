# LittleStar 当前工程说明

2026-10-26。本文描述当前实现与模块接口。
旧的完整施工稿保存在 [调整前工程文档](archive/ENGINEERING_before_team_modules_20261026.md)，仅供历史参考。
当前界面与流程以 [设计文档](DESIGN.md) 为准，责任与验收分配见 [团队计划](TEAM_PLAN.md)。

## 工程与运行环境

- Java 17 源码与字节码；Groovy Gradle 脚本；XML View + ViewBinding。
- minSdk 30，compileSdk 37，targetSdk 37；Gradle 使用本地 JDK 21。
- MVVM、LiveData、Repository；使用现有非 ktx AndroidX 依赖。
- 正式页面 13 个，清单来自 app/src/main/AndroidManifest.xml。
- 学习记录存入本地 JSON，设置使用 SharedPreferences；无数据库及 INTERNET 权限。

## 子系统目录与责任

| 角色 | 主要目录与文件 | 边界 |
|---|---|---|
| A | feature/words、feature/flashcard、feature/quiz、domain/SessionBuilder、QuizGenerator、ReviewScheduler | 识字流程、会话创建与答题 |
| B | feature/culture、feature/player、playback、data/content、assets/audio 与文化内容 JSON | 文化发现、介绍、播放与素材一致性 |
| C | feature/parent、feature/settings、feature/reward、feature/review、notify、data/store 与 data/prefs | 计划、反馈、提醒和持久化接口 |
| 共同 | feature/home、core、AppContainer、AppExecutors、共享资源与构建 | 入口、组件、依赖和整合 |

目录不代表全部历史作者身份。共享模型由提供者与调用者共同检查，避免各模块复制存储状态。

## 运行架构

```text
Activity + ViewBinding
    → ViewModel + LiveData
    → Repository / domain
    → ProgressStore → AtomicFile progress.json
    → SettingsStore → SharedPreferences
    → ContentRepository → 内置 words.json / culture.json

AppContainer 提供共享依赖
AppExecutors 提供单一 diskIO 线程及主线程回调
PlayerActivity → MediaController → PlaybackService + Media3
ReminderScheduler → AlarmManager → ReminderReceiver → Notifier
```

- 所有 progress.json 的读写走 diskIO，UI 观察状态，主线程不做内容文件读取。
- 页面只传 ID、模式及播放队列；不通过 Intent 传整个可变模型。
- 业务时间优先通过 AppClock，便于测试。
- 字符串、色值和尺寸放资源文件；教学内容放 JSON。
- 纯 Java 业务逻辑使用 JVM 测试；真实跳转与媒体服务使用设备测试。
- 设备回归关闭系统动画，避免页面切换中点击尚未稳定的窗口。
- ChildActivity / ParentActivity 先调用 super 应用 AppCompat 主题，再启用 EdgeToEdge，并在窗口获得焦点时按本页配置同步系统栏图标外观。

## Activity 清单和数据契约

| 角色 | Activity | 主要参数或状态 |
|---|---|---|
| 共同 | HomeActivity | 首次引导、字/文模式、今日进度 |
| A | ThemesActivity | 无输入，读取内容主题 |
| A | WordListActivity | themeId |
| A | FlashcardActivity | mode、wordIds、startIndex、sessionId；每日会话可能在本页创建 |
| A | QuizActivity | mode、sessionId |
| B | CultureCategoriesActivity | 无输入，读取三个当前分类 |
| B | CultureListActivity | categoryId |
| B | CultureDetailActivity | itemId；savedInstanceState 保存 previewExpanded |
| B | PlayerActivity | itemId、queue |
| C | ParentGateActivity | 按 PIN 是否已设决定创建或验证 |
| C | SettingsActivity | goal、time、days 草稿及 saving 状态 |
| C | RewardActivity | sessionId |
| C | RoundReviewActivity | sessionId；只读回顾 |

独立 HelpActivity 已删除，设置菜单使用 menu_settings.xml。
ComponentGalleryActivity 仅存在于 debug source set，不计入作业正式页面数量。

## 文化详情实现

CultureItem 新增三个内容字段：

| 字段 | 类型 | 用途 |
|---|---|---|
| summary | String | 简短内容简介 |
| learningFocus | String | 相关词语、动作或亲子讨论提示 |
| transcript | String | 可展开的中文文本预览 |

现有 11 条内容均提供上述字段。歌曲与古诗预览来自现有歌词，故事预览沿用现有音频生成脚本中的文本。
这些是当前内置演示内容，不声称已经由教师或目标用户验证。

CultureDetailViewModel 在 ContentRepository.load 完成后根据 itemId 查询内容，并按 categoryId 生成播放队列。
UiState 包含可空 item 和不可变 queue。无效 ID 返回空状态，页面显示说明并禁用播放。
Activity 用 Intent 保留选中条目，Bundle 保存文本展开状态。异步回调检查 Activity 是否已结束。
播放器跳转前禁用主按钮，返回详情后重新启用，避免重复打开多个播放器页面。
列表行使用 BindingAdapterPosition 检查边界，避免列表刷新时以失效位置打开错误内容。

主页文化卡和 EXPLORE 主操作均进入详情；文化模式隐藏只对汉字有效的听音小按钮。

## 设置菜单和通知

SettingsActivity 保留原有表单与自定义返回控件，使用 MaterialToolbar 作为标准 options menu 宿主。
菜单支持帮助、测试通知和关于，帮助与关于不修改草稿。

- 保存使用原 notificationPermission 回调，权限流程结束后关闭已保存页面。
- 测试使用独立 testNotificationPermission 回调，授权后发送测试通知并留在设置页；拒绝时提示。
- 测试前检查系统通知是否开启；Notifier.ensureChannels 保证 reminder 渠道存在。
- 日常提醒 ID 为 1001，测试通知 ID 为 1099；点击都返回 Home。
- 菜单测试不调用 save，不修改目标、提醒时间或星期。
- 提醒继续使用 AlarmManager 的非精确窗口，不申请精确闹钟权限。

## 学习与反馈数据

ProgressStore 保存字进度、每日计划、会话与答案，使用既有 schemaVersion 3 迁移逻辑。
本次新增详情不修改 progress.json 格式，也不清空原有记录。
A 创建会话和保存作答；C 的完成与回顾根据同一 sessionId 读取数据。
手动“已学”与测验正确是两类信息，报告不应把手动标记描述成已经测得掌握。
修改设置只影响未来计划；已生成的当天计划保持固定。

## 组件和内容资源

BlockLayout 统一硬描边、阴影、按压动画；沿用已有深色和 sw600dp 尺寸覆盖。
颜色在 values/colors.xml 与 values-night/colors.xml，尺寸在 dimens.xml，文案在 strings*.xml。
新详情的中文预览使用汉字子集字体；subset_font.py 同时收集 transcript 和 learningFocus。
更新文化内容后需运行内容检查，增加汉字时还需重建子集字体。

当前词卡和文化封面仍含占位资源，原有七条带歌词音频存在时间戳越界问题。
validate_content.py 目前验证资源存在、ID、分类、详情字段和歌词顺序，并不证明音画同步。

## 验证命令

```bash
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
python3 tools/validate_content.py
python3 tools/contrast_check.py
python3 tools/subset_font.py
```

字体脚本需要 fonttools；首次构建需要现有 Gradle 与 SDK 依赖。
设备测试需要 Android 11 或更高版本设备。结果与设备信息记入 ACCEPTANCE.md，不能用编译通过代替设备通过。

## 发布与提交

源码包保留 Gradle wrapper、app 源码及文档；不要包含 local.properties、Gradle 缓存或本地构建缓存。
课程项目命名仍为 com.littlestar.app，是否按附件更名需单独确认。
当前 release 仍使用 debug 签名，适用于课堂安装；正式发布签名不在本轮修改范围。
AI 对话、同伴评审、成员姓名与实际贡献必须使用真实材料，项目规格不能替代这些证据。
