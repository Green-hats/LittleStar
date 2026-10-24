# DECISIONS

开发过程中的「问题 / 采用的做法 / 理由」记录。新问题追加在末尾。

## D1 · 2026-10-21 · 截图验收设备
- 问题：工程文档 §12.3 规定用 Pixel 7 模拟器截图验收；用户要求用 adb 真机测试。
- 做法：用已连接的一加 8T（KB2000，1080×2400，Android 15）通过 `adb exec-out screencap` 截图验收；360×640 小屏变体在真机上用 `adb shell wm size` 模拟（测完恢复）。
- 理由：用户明确指示；真机分辨率与设计图同尺寸。

## D2 · 2026-10-21 · 汉字子集字体暂用全量
- 问题：M0 时 words.json / culture.json 尚未生成，无法做子集。
- 做法：先把完整 NotoSansSC-Bold.otf（约 8.3MB）放入 res/font；M2 内容定稿后用 tools/subset_font.py 生成 < 500KB 子集替换。
- 理由：不阻塞 M0/M1；子集内容依赖 M2 的 JSON。

## D3 · 2026-10-21 · ScreenTimeTracker 占位
- 问题：M0 需要 ChildActivity 基类，但计时逻辑属于 M7。
- 做法：M0 创建只维护当前页弱引用的占位实现，M7 补全 §10.1 全部逻辑。
- 理由：保持基类 API 稳定，避免 M7 改动所有 Activity。

## D4 · 2026-10-21 · AGP / Gradle 版本
- 问题：文档未指定 AGP 与 Gradle 版本。
- 做法：Gradle 9.6.0 + AGP 9.4.1（本机缓存中已有、可离线复用）；依赖版本取最新稳定版（media3 1.11.1 等），见 gradle/libs.versions.toml。
- 理由：构建环境一致性与「取最新稳定版」的要求。

## D5 · 2026-10-21 · compileSdk 提到 36
- 问题：文档规定 compileSdk 35，但最新稳定版 androidx.core 1.17.0 等要求 compileSdk ≥ 36。
- 做法：compileSdk 36，targetSdk 仍为 35（edge-to-edge 行为不变），minSdk 26 不变。
- 理由：「取最新稳定版依赖」与「compileSdk 35」冲突，升级 compileSdk 只影响编译期 API 可见性，不改变运行时行为；是最保守的解法。

## D6 · 2026-10-21 · 子集字体已生成；占位图与占位音频
- 问题：M0 时临时用了全量 NotoSansSC-Bold.otf（8.3MB）。
- 做法：内容 JSON 定稿后用 tools/subset_font.py 生成 43KB 子集（195 字，含 words/culture 全部汉字 + distractors + 常用 UI 字），已替换 res/font/noto_sans_sc_hanzi.otf。系统 python3 缺 fontTools，用 `python3 -m venv /tmp/fontenv && /tmp/fontenv/bin/pip install fonttools brotli` 后运行。
- 词卡配图与封面全部先用 ill_placeholder（工程文档 §8.8 允许的 MVP 占位）；占位音频用 macOS say 婷婷 + ffmpeg vorbis（工具：tools/make_placeholder_audio.sh）。
- 理由：不阻塞开发，正式版前替换为真人录音与定稿插画。

## D7 · 2026-10-21 · 占位音效编码器（M1）
- 问题：任务示例用 `ffmpeg -c:a libvorbis`，但本机 ffmpeg 9.0.2 未编译 libvorbis。
- 做法：改用内置 vorbis 编码器（`-c:a vorbis -strict -2`），该编码器只支持双声道，滤镜链末尾加 `pan=stereo|c0=c0|c1=c0`；输出仍为 .ogg vorbis，SoundPool 可解。
- 理由：占位音效只为区分 5 个反馈音，正式版前替换为录制音效。

## D8 · 2026-10-21 · 熊猫 SVG 转换取舍（M1）
- 问题：panda_v3.svg / panda_sleep.svg 含纸白背景矩形、480 黄圆、`<text>z z`。
- 做法：按 §8.8 删掉背景矩形与黄色圆贴（黄圆 + 墨圈由 BlockLayout / ShapeableImageView 提供，gallery 里用 debug 专用的 bg_panda_disc 演示）；`translate+scale` 映射为 VectorDrawable `<group>`，path 原样保留；两个 `<text>z</text>` 用描边折线路径代替；颜色进 values(+night) 的 ls_panda_*（内容色，深浅色一致）。转换脚本固化在 tools/svg_to_vector.py。
- 理由：§8.8 明确要求；脚本可重复执行。

## D9 · 2026-10-21 · M1 / M2 并行开发的提交边界（M1）
- 问题：M1 进行中，父代理在同一工作区并行做 M2，AppContainer 等文件被并发修改。
- 做法：M1 只 add 显式路径（core/ui、core/audio、drawable、raw、src/debug、相关 values、tools/svg_to_vector.py、截图、DECISIONS），不用 `git add -A`；AppContainer 的 SfxPlayer 接线在 M2 提交前已增量合并，随 M2 提交入库；M1 提交不含 M2 未完成的 feature/*、androidTest 路径。
- 理由：两个里程碑各自提交，历史清晰，互不污染。

## D10 · 2026-10-21 · 音频缺失用页面内对话框而非 MaterialAlertDialog（M3）
- 问题：工程文档 §9.4 写「盖 38% 墨色遮罩 + MaterialAlertDialogBuilder」，但 §0.2 硬性规则禁止孩子区出现系统对话框（AlertDialog/Toast），两条冲突。
- 做法：Flashcard 布局里做 in-page 遮罩 + BlockLayout 卡片（继续 / 重试），不用 AlertDialog；视觉与 §7.26 设计图一致。
- 理由：§0.2 优先级最高；in-page 实现与 AlertDialog 视觉等价且更可控。

## D11 · 2026-10-21 · M3 不导航的入口（M3）
- 问题：ThemesActivity / CultureCategoriesActivity / ParentGateActivity / PlayerActivity 属 M4/M5/M6，M3 时类不存在。
- 做法：首页两个模块块、家长入口、文态 LISTEN 的点击暂不导航（保留按压反馈）；会话链 Home→Flashcard→Quiz→Reward 完整可用。
- 理由：一次只做一个里程碑；M4/M5/M6 接入时只需替换空 lambda。

## D12 · 2026-10-21 · 「当天第一次自动朗读」的记录位置（M3）
- 问题：§9.1 要求进入时当天第一次自动朗读今日字，需要跨冷启动记住「今天读过没有」。
- 做法：用 HomeActivity 的 getPreferences（Activity 级 SharedPreferences）存 last_speak_date，不动 SettingsStore（其键都是家长设置）。
- 理由：这是 UI 态而非家长设置；加 SettingsStore 键会让 M2 的类膨胀。

## D13 · 2026-10-21 · Quiz 的 SavedStateHandle 工厂（M3）
- 问题：QuizViewModel 需要 SavedStateHandle，通用 ViewModelFactory 不支持 CreationExtras。
- 做法：QuizActivity 内联一个 ViewModelProvider.Factory，在 create(Class, CreationExtras) 里用 SavedStateHandleSupport.createSavedStateHandle(extras) 构造。
- 理由：§9.5 许可的做法；不改通用工厂。

## D14 · 2026-10-21 · 引导「允许提醒」只写设置（M3）
- 问题：§7.20 要求点「允许提醒」才弹 Android 13+ 通知权限；通知调度属 M7。
- 做法：M3 只写 settings.setNotifRationaleShown(true) + completeOnboarding，不申请权限；M7 接 ReminderScheduler 时再弹权限。
- 理由：任务明确 M3 范围；不阻塞引导流程。

## D15 · 2026-10-21 · RewardViewModel 增加 Effect.Batch（M3）
- 问题：RewardViewModel.init 连续 setValue 多个 Effect（StarSfx×N + PraiseSfx），但 Activity 在 onCreate 注册的观察者到 STARTED 才激活，MutableLiveData 只保留最后一个值 → StarSfx 全丢，星星不亮。
- 做法：Effect 增加 record Batch(List<Effect>)，init 把一批事件打包成单个事件；Activity 端逐个处理。
- 理由：最小改动，不改变 Event 语义；QuizViewModel.choose 的连续两个事件发生在用户点击后（观察者已激活），不受影响。

## D16 · 2026-10-21 · REVIEW 会话奖励副标题（M3）
- 问题：REVIEW 会话无新字，「你学会了 0 个字」语义奇怪，设计书未定义该文案。
- 做法：learnedWords 为空时显示「复习完成！」（reward_subtitle_review）。
- 理由：保守的最小文案，不改变其他逻辑。

## D17 · 2026-10-21 · ContentRepository 增量查询方法（M3）
- 问题：Flashcard 顶栏要主题名（只有 themeIdOf），Quiz 选项要拼音（选项只有汉字串）。
- 做法：ContentRepository 增加 themeById(id) 与 wordByHanzi(hanzi) 两个只读查询。
- 理由：内容库是唯一的字/主题索引，比在各页面自建映射保守。

## D18 · 2026-10-21 · 一加 8T 禁止 pm clear（M3）
- 问题：`adb shell pm clear com.littlestar.app.debug` 抛 SecurityException（CLEAR_APP_USER_DATA 不授予 shell）。
- 做法：清数据用 `am force-stop` + `run-as <pkg> rm files/progress.json shared_prefs/*.xml`；Espresso 测试内通过容器重置（settings + store.update 清空）。
- 理由：真机限制；测试内重置同时覆盖内存态，比删文件更彻底。

## D19 · 2026-10-21 · 主题/词表网格横向间距（M4）
- 问题：GridLayoutManager(2) 没有内置间距。
- 做法：ThemesActivity.GapDecoration（左块右半个 gap、右块左半个 gap），纵向间距由 item 的 marginBottom 提供；WordList 复用同一装饰。
- 理由：不引入第三方库，块与块间距 = ls_gap。

## D20 · 2026-10-21 · THEME 会话奖励页仍播「太棒了」（M4）
- 问题：M3 的 RewardViewModel 只在 showStars 时发 PraiseSfx；THEME 不发星星但奖励页仍应有完成仪式。
- 做法：PraiseSfx 移出 showStars 条件，始终发；星星事件仍只限非 THEME。
- 理由：§7.5 只说 THEME 不发星星，提示音不是星星。

## D21 · 2026-10-21 · 播放进度条用自定义 View（M5）
- 问题：§9.9 允许「Slider 改样式或自定义 View」；Slider 是 Material 组件，主题与孩子区硬影语言不一致。
- 做法：core/ui/SongProgressView（10dp 圆头轨道 track 色 + 蓝色已播段 + 黄色方块拖柄带墨线，拖动回调 positionMs）。
- 理由：视觉与设计图一致，无 Material 依赖。

## D22 · 2026-10-21 · 睡前定时开关用自绘 toggle（M5）
- 问题：孩子区关联页面不使用 Material 组件；Switch 样式不搭。
- 做法：FrameLayout + 轨道 drawable（bg_switch_on/off）+ 圆点平移；状态写 SettingsStore.sleepFinishTrack。
- 理由：与块视觉一致。

## D23 · 2026-10-21 · Player 回前台不打断播放（M5）
- 问题：PlayerActivity.onStart 每次重连 MediaController，若直接 setMediaItems 会重置正在播的内容。
- 做法：当前 mediaId 与目标条目相同且队列长度一致时跳过 setMediaItems；否则从 queueIndex 重排。
- 理由：§7.24 暂停语义——后台回来进度不重置。

## D21 · 2026-10-21 · 睡前定时边界：队列提前播完
- 问题：若队列在 deadline 前播完（player 已停），onSleepDeadlineReached 只设 setPauseAtEndOfMediaItems，不会再有 END_OF_MEDIA_ITEM 事件，晚安通知/清理永远不触发（M5 子代理真机发现）。
- 做法：onSleepDeadlineReached 开头加 `if (!player.isPlaying()) { finishSleepStop(); return; }`。
- 理由：修复死等；行为与「到点即停」一致。

## D24 · 2026-10-21 · 真机验收环境限制（M5）
- 问题：一加 8T（ColorOS）上 shell 无法授予 POST_NOTIFICATIONS（pm grant / appops 都被拒绝）。
- 做法：通过 `am start APPLICATION_DETAILS_SETTINGS` 打开系统设置页，用坐标点按打开「允许通知」；M7 的权限弹窗流程不受影响（正常运行时弹窗）。
- 另：设备锁屏无法远程解开时会导致 PackageManager install 挂起；恢复手段是先叫人手动解锁，不要盲目 reboot（教训：reboot 后 USB 需人工恢复在线）。
- 理由：记录环境坑，后续里程碑验收复用。

## D25 · 2026-10-21 · 重置进度的实现（M6）
- 问题：SettingsSheet 重置需清 ProgressStore 全部数据；没有一个官方的「清空」API。
- 做法：MaterialAlertDialogBuilder 二次确认（家长区允许系统对话框）→ store.update 里清空 words/sessions/dailyPlans/usage/favourites 并重置 nextSessionId → flushNow → Snackbar「已重置」。
- 理由：不新增数据层 API；update+flushNow 是既有写路径。

## D26 · 2026-10-21 · 「改每日目标次日生效」的真机验证方法（M6）
- 问题：验收要求「目标改 5，第二天首页 5 段」；真机 shell 没有 SET_TIME 权限，不能直接改日期。
- 做法：Plan 保存 goal=5 后（settings.xml 确认 daily_goal=5），`run-as rm files/progress.json` + force-stop 重进 → 当天 plan 按新 goal 重建 → 首页显示 5 段（见 docs/screenshots/M6/home_goal5_next.png「Today 0 / 5 words」）。当天已有 plan 时不重算（SessionBuilder.todayPlan 有 plan 直接返回）。
- 理由：改不了系统时间时，这是等价于「次日」的最保守验证。

## D27 · 2026-10-21 · release 验收用 debug 签名（M8）
- 问题：release 没有签名配置，assembleRelease 出的包无法安装。
- 做法：buildTypes.release 加 `signingConfig signingConfigs.debug`，仅用于 M8 验收；正式发布前替换为正式 keystore 并从构建脚本移除。
- 理由：§15 要求验证 release（R8）行为；最小侵入。

## D28 · 2026-10-21 · R8 keep 规则补 ContentRepository 包装类（M8）
- 问题：release（R8 开启）冷启动崩溃：`内容解析失败: words.json` → Gson 反射实例化 ContentRepository 的私有包装类 WordsFile/CultureFile 失败。
- 做法：proguard-rules.pro 加 `-keep class com.littlestar.app.data.content.ContentRepository$* { *; }`；真机验证 release 学习进度写读正常。
- 理由：模型类已有 keep；漏了 repository 内部的 Gson 目标类。

## D29 · 2026-10-21 · StrictMode 范围与残留说明（M8）
- 问题：§15 要求 debug 开启 StrictMode 检测主线程磁盘 IO。开启后发现 SharedPreferences 首次读取（主线程）触发 DiskReadViolation。
- 做法：AppContainer 构造后在 diskIO 线程预热 settings（首次文件加载在后台完成）；HomeActivity「当天首次朗读」的日期检查改到 diskIO→main 回调。残留违规全部是框架内部路径（布局膨胀 mmap、startActivity、输入子系统 KeyCharacterMap、ColorOS Hans），与应用数据文件无关。
- 理由：§15 的核心是 progress.json 不上主线程；框架内部 mmap 读取不属于缺陷。

## D30 · 2026-10-22 · 中英文占比策略（用户反馈：中文含量过多）
- 问题：目标用户是澳洲学中文的 3–6 岁孩子（尚不识汉字、日常环境英文），但 UI 外壳中文过多。
- 做法（三层策略）：
  1. **UI 外壳（按钮、导航、提示、反馈、家长区、通知、无障碍描述）= 英文。** 孩子不识字，靠图标+位置+声音；家长是英文环境。
  2. **教学内容 = 中文为主。** 汉字大字、拼音、主题中文名、文化条目标题、歌词保持中文（这是被学的内容），英文以 gloss/副标题辅助。
  3. **标识性字形保留中文**：「字/文」切换、印章「会」、分类字形块（儿/故/诗）——它们是「汉字积木」的视觉语言，不承担阅读任务。
- 配套：TextAppearance 的 Display/Title/Headline/Body/Caption 改用 Poppins（latin 字族），汉字外观不变。

## D31 · 2026-10-22 · 家长验证改为 PIN 密码（用户需求，取代工程文档 §7.7 算术题）
- 问题：用户要求家长区改为密码登录，密码在首次引导时设置。
- 做法：引导从 2 步变 3 步（年龄目标 → 设 4 位 PIN → 提醒）；PIN 存 SettingsStore（parent_pin）；ParentGate 改为 numberPassword 输入，错误抖动清空不锁定；旧安装（未设 PIN）首次进 Gate 时引导创建 PIN。ParentChallenge 类与其单测删除。
- 理由：用户明确需求；算术题对会算数的二孩无阻隔作用，PIN 更直观。

## D32 · 2026-10-22 · 全量 bug 审查与修复（静态审查 + 自测）
静态审查（79 个文件）+ 自测发现并修复：
- [crash] FlashcardViewModel 过滤无效 id 后仍按 wordIds 下标取词 → 越界；改为先过滤再夹 index。
- [数据] FlashcardActivity 进程重建重复 buildSession（孤儿 session）；改为 savedInstanceState 保存 sessionId/initialized 复用；极端情况 session 丢失则重建。
- [数据] PlaybackService 重建时陈旧 deadline 立即触发「开播即停 + 误发晚安通知」；过期 deadline 直接 cancel 不调度。
- [数据] QuizViewModel 进程恢复不恢复 chosenIndex（错选项不标 clay）；恢复时从 answers 反查。
- [体验] PlayerViewModel observeForever 累积泄漏；改为换歌时解绑旧 observer + onCleared 移除。
- [体验] PlayerActivity 自动播放竞态（controller 早于 vm.init）→ 首次 render 补一次 start。
- [体验] Favourites「取消收藏不消失」失效：pendingUnfav 合并回列表。
- [体验] Player subtitle 可空导致显示 "null"。
- [体验] TimeUpActivity 补 EdgeToEdge。
- [体验] 首页 START / 词表 Quiz 双击防抖（800ms）。
- [隐患] ParentRecords 主线程 LocalDate.parse 坏数据崩溃 → 跳过坏行。
- [隐患] QuizGenerator 干扰项 < 3 时 subList 越界 → 按实际数量。
- [隐患] ProgressRepository 主线程 ensureLoaded → 未加载返回 0（ContentRepository.isLoaded）。
- 未修（记录）：diskIO 单线程拥堵（量级可接受）、PIN 明文 SharedPreferences（儿童锁定位，可接受）、ParentActivity 用 wall clock 判 60s。

## D33 · 2026-10-24 · 家长页仅配置学习任务
- 问题：当前 36 字学习产品的家长概览、周图表、按天记录和多层设置增加维护成本；用户明确要求删除休息提醒和每日限时，只保留任务配置与学习提醒。
- 做法：家长入口为 Learning plan，经 PIN 验证后仅配置每日识字数（1–10）、学习提醒时间和星期。删除统计、记录、通用设置入口及限时/休息/加时页面与逻辑，移除孩子页面的时长采集和拦截。
- PIN 保护保留：已有 PIN 继续有效；首次引导为目标 → 提醒两步，首次打开任务页时才创建 PIN。
- 目标与提醒统一点 Save 生效，返回或 Cancel 放弃编辑；daily_plan 当天固定，目标次日生效。选空提醒日表示关闭每日学习提醒。
- 兼容：学习历史和旧 usage 字段继续保存在原文件中；旧时长设置不再读取，旧休息通知及渠道在升级启动时清理。儿歌/故事的睡前定时仍属于播放功能。
- 理由：优先维护识字闭环，让家长只需配置学习任务。
- 验证配套：模拟器 Android 17 暴露 Espresso 3.6.1 的 InputManager.getInstance 反射异常；仅升级测试依赖为 Espresso 3.7.0 / ext.junit 1.3.0 / runner 1.7.0，不修改运行时依赖。修复依据：[AndroidX Test 发布说明](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0-alpha04)。
