# 一起做 LittleStar

欢迎。这份文档只写「怎么把这件事做对」，不写背景——背景在 [设计书](docs/DESIGN.md) 和 [工程文档](docs/ENGINEERING.md)。

## 环境

| 需要 | 版本 |
|---|---|
| JDK | 21（源码与字节码目标 17） |
| Android Studio | 任意近期版本（Ladybug 以上） |
| Android SDK | compileSdk 37，minSdk 30 |
| 真机 | Android 11.0+，开启 USB 调试（模拟器也可，但本项目按真机验收） |

第一次跑起来：

```bash
git clone https://github.com/Green-hats/LittleStar.git
cd LittleStar
# 用 Android Studio 打开会自动生成 local.properties；命令行则手动写：
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 提交前必须跑的四件事

```bash
./gradlew testDebugUnitTest && ./gradlew assembleDebug
python3 tools/contrast_check.py
python3 tools/validate_content.py
```

四条都绿再提 PR。CI 会跑同样的东西，红着不会被合。

## 代码约定（不是建议，是验收项）

1. **只用 Java 17 + XML View**。工程里不允许出现 `.kt` / `.kts`，不允许加 Kotlin 插件，依赖不允许用 `-ktx`。
2. **颜色只在 `colors.xml`**（深浅两份），**尺寸只在 `dimens.xml`**，**文案只在 `strings.xml`**。布局和 Java 里出现 `#RRGGBB` 或中文字面量都算缺陷（内容 JSON 除外）。
3. **孩子区一屏只有一个实心主按钮**；孩子区不用系统弹窗（AlertDialog / Toast），反馈靠页面内状态、动效和声音。
4. **主线程不做磁盘 IO**。读改 `progress.json` 一律走 `AppExecutors.diskIO()`；UI 只观察 `LiveData`。
5. **不确定的设计取舍先记一条** `docs/DECISIONS.md`（问题 / 采用的做法 / 理由），再按最保守的做法写。
6. 时间相关的逻辑一律走 `AppClock`，不要直接 `System.currentTimeMillis()` / `LocalDate.now()`——否则没法单测。

## 分支与提交

- `main` 是受保护分支，**只能通过 PR 合入**。
- 分支名：`feat/xxx`、`fix/xxx`、`docs/xxx`、`chore/xxx`。
- 提交信息用一句话说清「改了什么」；大改动在 PR 描述里补「为什么」。
- 一个 PR 只做一件事。重构和新功能分开提。

## 改内容（不用改代码）

字和故事都在 `app/src/main/assets/content/`：

1. 改 `words.json` 或 `culture.json`（每个主题 6 个字；`id` 全局唯一，用无声调拼音）。
2. 音频放到 `assets/audio/`，文件名与 JSON 里 `audio` 字段一致。
3. 跑 `python3 tools/validate_content.py`。
4. 加了新汉字 → 跑 `python3 tools/subset_font.py` 重新生成子集字体（需要 `fonttools`）。

开发期占位音频可以用 `bash tools/make_placeholder_audio.sh`（macOS `say` 生成，**正式版必须真人录音**）。

## 目录导览

```
core/       组件与基础设施（BlockLayout、AppClock、音频）
data/       内容、progress.json、SharedPreferences、Repository
domain/     纯 Java 业务逻辑（100% 可单测，不 import android.*）
feature/    各 Activity 对应的界面层；settings/ 仅配置每日任务和学习提醒，parent/ 保留 PIN 验证
playback/   Media3 播放服务与睡前定时
notify/     通知与每日提醒
```

## 需要人配合的验证

有些事 CI 做不了，PR 里请附结果：

- 真机截图（改动涉及界面时）：浅色 + 深色各一张。
- 涉及学习提醒 / 睡前定时的改动：说明触发时间、系统通知权限及复现步骤。
- 进程恢复类改动：`adb shell am kill` 后重进验证。

## 发布

当前的 Release 用 **debug 签名**（[D27](docs/DECISIONS.md)），只够内部安装。正式上架前要：

1. 生成正式 keystore，**不要提交进仓库**（走 CI secret 或本机 `~/.gradle/gradle.properties`）。
2. 在 `app/build.gradle` 配 `signingConfig`。
3. 把 `applicationId` 的 `.debug` 后缀确认只作用于 debug 构建。
