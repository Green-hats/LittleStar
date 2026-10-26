# LittleStar 验收清单（§15）

## 2026-10-24 · 删除收藏与睡前定时（D36）

- `:app:testDebugUnitTest`：37/37 通过；覆盖 v1/v2 升级到 v3、收藏字段移除及学习记录保留。
- `assembleDebug`：通过。
- `:app:connectedDebugAndroidTest`：12/12 通过。新增分类 → 列表 → 播放器、暂停/恢复、上一首/下一首、退到后台继续播放，以及旧晚安渠道清理和学习提醒渠道保留测试。
- 内容校验、浅深色对比度检查及 XML 解析通过。
- 主程序 Java 文件由 68 个减少为 63 个；移除收藏页、收藏状态、睡前定时面板及控制器。
- 页面截图：[分类浅色](screenshots/simple-playback/categories_light.png)、[分类深色](screenshots/simple-playback/categories_dark.png)、[内容列表](screenshots/simple-playback/list_light.png)、[播放器浅色](screenshots/simple-playback/player_light.png)、[深色](screenshots/simple-playback/player_dark.png)、[360×640 / 1.3 倍字体](screenshots/simple-playback/player_small_font13.png)、[小屏底部](screenshots/simple-playback/player_small_font13_bottom.png)。

## 2026-10-24 · 暖白背景（D35）

- 浅色背景 `#FAF9F6`、卡片 `#FFFFFF`；通过统一颜色资源应用到全局页面与启动背景。
- `:app:testDebugUnitTest`、`assembleDebug`、内容校验、对比度检查均通过。
- 实际页面：[首页](screenshots/warm-white/home_light.png)、[文化分类](screenshots/warm-white/culture_light.png)、[文化分类深色](screenshots/warm-white/culture_dark.png)。

## 2026-10-24 · 文化分类页提示行调整

- 将贴底的大标题、时长副标题和黄色熊猫圆章，改为收藏入口下方的小熊猫与单行提示。
- 页面支持垂直滚动，小屏与放大字体时仍可访问收藏和提示行。
- `:app:testDebugUnitTest`、`assembleDebug`、内容校验、对比度检查均通过。
- 页面截图：[浅色](screenshots/culture-footer/light.png)、[深色](screenshots/culture-footer/dark.png)、[360×640 / 1.3 倍字体](screenshots/culture-footer/small_font13.png)。

## 2026-10-24 · 移除星星奖励（D34）

设备：Pixel 10 Pro 临时只读模拟器（Android 17）。

- `:app:testDebugUnitTest`：35/35 通过，覆盖旧星星存档迁移、学习历史保留和会话完成幂等保存。
- `assembleDebug`：通过。
- `:app:connectedDebugAndroidTest`：10/10 通过，覆盖完整学习流程、完成页重建、学习任务设置及原子文件存储。
- `validate_content.py` 与 `contrast_check.py`：通过。
- 首页、识字主题和文化分类均移除累计星星；完成页保留本轮汉字、鼓励反馈、完成和再来一次。
- 存档升级至 v2，只移除旧会话的 `stars` 字段，保留识字进度、每日计划、答题记录和完成时间。
- 页面目视检查：[首页](screenshots/no-stars/home_light.png)、[主题](screenshots/no-stars/themes_light.png)、[文化分类](screenshots/no-stars/culture_light.png)、[完成页浅色](screenshots/no-stars/completion_light.png)、[深色](screenshots/no-stars/completion_dark.png)、[360×640 / 1.3 倍字体](screenshots/no-stars/completion_small_font13.png)、[小屏底部](screenshots/no-stars/completion_small_font13_bottom.png)。小屏可滚动访问两个按钮。

## 2026-10-24 · 家长页精简为学习任务（D33）

设备：Pixel 10 Pro 模拟器（Android 17）。

- `:app:testDebugUnitTest`：36/36 通过；已删除功能对应的时长策略测试随实现移除。
- `assembleDebug`：通过。
- `:app:connectedDebugAndroidTest`：9/9 通过，含 PIN 创建/验证、编辑取消、保存后今日计划和历史不变、页面重建保留草稿、关闭学习提醒、旧限时配置不再阻止学习、完整识字到奖励流程、原子文件存储。
- `validate_content.py` 与 `contrast_check.py`：通过。
- 页面目视检查：[浅色](screenshots/learning-plan/learning_plan_light.png)、[深色](screenshots/learning-plan/learning_plan_dark.png)、[360×640 / 1.3 倍字体](screenshots/learning-plan/learning_plan_small_font13.png)、[小屏滚动到底部](screenshots/learning-plan/learning_plan_small_font13_bottom.png)。文字与星期选项可读，保存和取消可访问。
- 家长页仅提供每日识字目标、学习提醒时间及星期；PIN 保留。应用不再采集孩子使用时长，不再触发休息、限时或加时页面；旧学习历史保留。

## 历史验收 · 2026-10-21

以下为功能精简前的验收记录，其中限时、休息及家长统计相关条目不再适用于当前版本。

日期：2026-10-21 ｜ 设备：一加 8T（KB2000，1080×2400，ColorOS Android 15）
构建：debug `com.littlestar.app.debug` + release `com.littlestar.app`（R8 开启，验收用 debug 签名 D27）

| # | 检查项 | 结果 | 证据 |
|---|---|---|---|
| 1 | contrast_check.py 浅色、深色都通过 | ✅ | `python3 tools/contrast_check.py` 退出码 0（全部 OK，含 night 组） |
| 2 | 没有 INTERNET 权限 | ✅ | `aapt dump permissions app-release.apk`：只有 POST_NOTIFICATIONS / RECEIVE_BOOT_COMPLETED / FOREGROUND_SERVICE(_MEDIA_PLAYBACK) / ACCESS_NETWORK_STATE / WAKE_LOCK（后两个来自 media3） |
| 3 | 布局 / Java 无十六进制色值、硬编码中文 | ✅ | `grep -rE "#[0-9a-fA-F]{6}" app/src/main/res/layout app/src/main/java` 无输出；中文字面量脚本扫描无输出（内容 JSON 除外） |
| 4 | 教学汉字子集字体 + zh-CN，「骨」「直」字形正确 | ✅ | M1 已验（docs/screenshots/M1）；子集字体 43KB（D6） |
| 5 | 硬影按下：内容下沉、影子剩 2dp；深色下影子黑色 | ✅ | M1 Gallery 截图；M8 深色走查 docs/screenshots/M8/*_dark.png 目视确认 |
| 6 | 每日目标改为 5，第二天首页 5 段 | ✅ | 真机无法改系统时间（shell 无 SET_TIME）；用「Plan 保存 5 → 删 progress.json 重建当天 plan → 首页 Today 0 / 5 words、5 段」等价验证（docs/screenshots/M6/home_goal5_next.png，方法 D26） |
| 7 | LIMIT 后重开 app 仍停在休息页；家长延长后可继续 | ✅ | M7 真机全流程验收（commit 268cde2） |
| 8 | 会话中途到时，不打断答题 | ✅ | Flashcard/Quiz deferTimeUp()=true（M3 起）；M7 TimeUp 验收通过 |
| 9 | 睡前定时「播完这首再停」生效并发晚安通知 | ✅ | M5 真机：1 分钟定时到点 → 播完当前音轨 → PAUSED + 通知 id=1003「故事讲完啦，晚安」 |
| 10 | 重启设备后每日提醒仍触发；目标完成当天不发 | ⚠️ 代码审查通过，真机重启未测 | BootReceiver 注册 BOOT_COMPLETED/TIME_SET/TIMEZONE_CHANGED → reminders.reschedule()（manifest 已注册）；不 reboot 测试机（D24 教训）；「当天完成不发」由 ReminderReceiver 读今日计划判定 |
| 11 | 进程被杀后从 Quiz 恢复到同一题 | ✅ | M8 真机：答到第 2 题 → `am kill` → 重开回到 Quiz 2/3（docs/screenshots/M8/quiz_recovery_before/after.png） |
| 12 | 小屏 360×640 每屏可滚动、无裁切 | ⚠️ 真机 ROM 限制未测 | ColorOS shell 无 WRITE_SECURE_SETTINGS，`wm size` 被拒；布局全部 wrap_content/minHeight + NestedScrollView 按 §11 实现 |
| 13 | `./gradlew test connectedAndroidTest` 全绿 | ✅ | JVM 单测 46/46；instrumented 4/4（SmokeTest 全流程、TimeUpActivityTest ×2、AtomicStateFile） |
| 14 | 无 .kt/.kts、无 Kotlin 插件、无 -ktx | ✅ | `find . -name "*.kt*"` 无输出；releaseRuntimeClasspath 只有传递的 kotlin-stdlib（§15 允许） |
| 15 | 依赖无 Room/SQLite、无 annotationProcessor | ✅ | build.gradle 仅 androidx/gson/media3；无 annotationProcessor 行 |
| 16 | StrictMode 主流程无违规 | ✅ | debug 开启 detectDiskReads/Writes；应用代码主线程无 progress.json/content 读写（D29：prefs 预热 + 朗读日期检查改异步）；残留违规均为框架内部（布局膨胀 mmap / startActivity / 输入子系统） |
| 17 | 学一个字按 Home 划掉 app，重开进度还在 | ✅ | release 包真机：学会「猫」→ `am kill` → 重开 Today 1/3 保留（docs/screenshots/M8/release_home_before/after.png） |
| 18 | progress.json 改非法 JSON 重开不崩溃、从备份恢复 | ✅ | ProgressStoreTest.corruptMain_fallsBackToBackup / corruptBoth_startsFresh 单测覆盖（真机 run-as 写文件被 SELinux 拒，代码路径单测覆盖） |
| 19 | 公开方法有 @NonNull/@Nullable；Lint 无 nullability 错误 | ✅ | 全部公开方法已标注；`assembleDebug` Lint 无 nullability 报错 |
| 20 | release APK < 30MB | ✅ | app-release.apk = 5.8MB（R8 + shrinkResources） |

## 性能

- 冷启动：`am start -W` TotalTime 726ms（< 1.5s 目标）。

## M8 走查结论（8 屏深色变体，docs/screenshots/M8/*_dark.png）

home_word / words_list / flashcard / quiz（答对反馈态）/ reward / culture_stories / player / parent_dashboard：
深色下硬影为纯黑、无文字裁切、无溢出；星星、印章、反馈色（mint/clay）正常。1.3 倍字体与小屏变体受设备 ROM 限制未做真机走查（见 #12 与 DECISIONS D24；组件级 1.3× 已由 M1 Gallery 覆盖）。
