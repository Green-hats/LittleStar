<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/banner_dark.png?v=20261026">
  <img src="docs/assets/banner.png?v=20261026" width="880" alt="LittleStar — 汉字积木，给海外华裔孩子的识字 App" />
</picture>

**汉字积木 · 给海外华裔孩子的识字 App**

<sub>A Chinese-literacy app for heritage kids growing up in English-speaking homes.</sub>

<p>
  <a href="https://github.com/Green-hats/LittleStar/releases/latest"><img alt="Download" src="https://img.shields.io/badge/Download-APK-8957e5?style=flat-square" /></a>
  <img alt="CI" src="https://github.com/Green-hats/LittleStar/actions/workflows/ci.yml/badge.svg?style=flat-square" />
  <img alt="Java" src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white" />
  <img alt="Offline" src="https://img.shields.io/badge/Offline-no%20INTERNET-27A567?style=flat-square" />
  <img alt="License" src="https://img.shields.io/badge/License-MIT-1F6FD0?style=flat-square" />
</p>

<hr />

<a href="#架构"><img src="docs/screenshots/readme/hero.png?v=20261026" width="880" alt="首页 / 识字卡 / 完成" /></a>

<sub>历史界面截图（当前版本已将帮助移入家长设置）。每天 3 个新字 + 2 个复习字：学 3 分钟，测 2 分钟，回顾本轮汉字，然后结束。</sub>

</div>

## 它是什么

一个给三到六岁、在英语环境里长大的孩子学汉字的 Android App。

界面是英文的，学的内容是中文的——孩子还不识字，靠图标、位置和声音操作。除了认字，还能听儿歌、故事和古诗；家长通过 PIN 进入学习任务页，设置每天学几个字、几点和星期几提醒；设置菜单提供帮助、关于和测试通知。

## 两个内容模块

<table>
<tr>
<td align="center" width="50%"><img src="docs/screenshots/readme/feature_quiz.png?v=20261026" width="230" alt="测验：答错显示陶土色并直接给出答案" /><br/>
<b>字</b><br/><sub>识字卡 → 四选一测验 → 学习完成。答错变陶土色，直接给答案。</sub></td>
<td align="center" width="50%"><img src="docs/screenshots/readme/feature_player.png?v=20261026" width="230" alt="播放器：歌词逐句高亮" /><br/>
<b>文</b><br/><sub>儿歌、故事、古诗。先看简介与学习重点，再收听；支持后台播放、暂停、切歌和拖动进度。</sub></td>
</tr>
</table>

## 三人团队结构

识字学习与测评、文化内容学习、学习计划与反馈各包含 4 个 Activity，共同首页 1 个，总计 13 个正式页面。
文化流程为分类 → 列表 → Discover 详情 → 播放器；帮助、关于和测试通知位于家长设置菜单。
具体职责、五个用例与数据交接见 [团队分工](docs/TEAM_PLAN.md)。

## 家长设置

首页 **Learning plan** → PIN 验证 → 每日识字目标、学习提醒时间与星期。首次引导只保留目标和提醒；第一次打开任务页才创建 PIN。学习记录保存在本机。

## 架构

<p align="center"><img src="docs/screenshots/readme/architecture.png?v=20261026" width="880" alt="LittleStar 架构" /></p>

<p align="center">
  <sub><b>Java 17</b> · XML View + ViewBinding · MVVM + LiveData · Media3 ExoPlayer · Gson + AtomicFile<br/>
  零 Kotlin · 无数据库 · JVM 单测与设备回归</sub>
</p>

<details>
<summary><b>🧩 几个实现细节</b> &nbsp;<sub>数据库 · 磁盘 · 硬影 · 字体</sub></summary>

<br/>

- **不用数据库** — 一年数据只有几百 KB。`progress.json` 原子写 + 2 秒防抖 + 每日备份 + 损坏回退，查询就是普通 Java 循环。
- **磁盘单线程** — 所有读写投递到同一个线程，状态只在那里改，天然免锁；一次 `update()` 就是一次事务。
- **硬影自己画** — `BlockLayout` 用 Canvas 画 4dp 墨线 + 6dp 硬影，按下时内容下沉、影子不动、只剩 2dp。
- **汉字子集字体** — 从 Noto Sans SC 提取内置教学内容和预览文本所需字形，保证「月」「骨」的写法不随厂商变。

</details>

## 快速开始

**装到手机** —— 下载 [LittleStar-0.1.0-preview.apk](https://github.com/Green-hats/LittleStar/releases/latest)，或：

```bash
adb install -r LittleStar-0.1.0-preview.apk
```

**从源码构建** —— JDK 17+ / Android SDK 37

```bash
git clone https://github.com/Green-hats/LittleStar.git && cd LittleStar
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 文档

| 文档 | 内容 |
|---|---|
| [**团队分工**](docs/TEAM_PLAN.md) | 三个子系统、每人四个 Activity、五个用例及模块交接 |
| [**设计书**](docs/DESIGN.md) | 当前 13 个 Activity、用户流程、文化详情与家长菜单 |
| [**工程技术文档**](docs/ENGINEERING.md) | 数据模型、业务逻辑、组件规格、逐屏实现要点 |
| [**验收清单**](docs/ACCEPTANCE.md) | 20 项结果与证据：对比度、权限、进程恢复、APK 体积 |
| [**决策记录**](docs/DECISIONS.md) | 38 条「为什么不那样做」 |
| [**协作指南**](CONTRIBUTING.md) | 环境、提交前自检、代码约定 |
| [**截图归档**](docs/screenshots) | M1 → M8 每屏的浅色 / 深色真机截图 |

<sub>MIT © 2026 Green-hats · 字体 Noto Sans SC / Poppins（SIL OFL 1.1）</sub>
