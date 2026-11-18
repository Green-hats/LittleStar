<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/banner_dark.png?v=20261026">
  <img src="docs/assets/banner.png?v=20261026" width="880" alt="LittleStar — 汉字积木，给海外华裔孩子的识字 App" />
</picture>

**星芽小径 · 给英语环境中的儿童学汉字的 App**

<sub>A Chinese-literacy app for heritage kids growing up in English-speaking homes.</sub>

<p>
  <a href="https://github.com/Green-hats/LittleStar/releases/latest"><img alt="Download" src="https://img.shields.io/badge/Download-APK-8957e5?style=flat-square" /></a>
  <img alt="CI" src="https://github.com/Green-hats/LittleStar/actions/workflows/ci.yml/badge.svg?style=flat-square" />
  <img alt="Java" src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white" />
  <img alt="Offline" src="https://img.shields.io/badge/Offline-no%20INTERNET-27A567?style=flat-square" />
  <img alt="License" src="https://img.shields.io/badge/License-MIT-1F6FD0?style=flat-square" />
</p>

<hr />

<sub>星芽小径 · Material Design 3 · 首次打开就可以开始学习。</sub>

</div>

## 它是什么

一个给三到六岁、在英语环境里长大的孩子学汉字的 Android App。

界面是英文的，学的内容是中文的——孩子还不识字，靠图标、位置和声音操作。除了认字，还能听儿歌、故事和古诗；家长通过 PIN 进入学习任务页，设置每天学几个字、几点和星期几提醒；设置菜单提供帮助、关于和测试通知。

## 学习入口

| 入口 | 用法 |
|---|---|
| **Learn** | 可一键开始或回到当天未完成的短课。看图听音后选字，答错可看提示并重试；错字安排复习，首答成绩不被重试改写。 |
| **Words** | 在主题词库按兴趣浏览，点字可听发音，也可练习该主题。 |
| **Culture** | 离线听儿歌、故事和古诗。可先读双语简介和原文，也可直接播放；支持同类队列、后台播放和歌词跟读。 |

## 三人团队结构

识字学习与测评、文化内容学习、学习计划与反馈各包含 4 个 Activity，共同首页 1 个，总计 13 个正式页面。
文化流程为分类 → 列表 → Discover 详情 → 播放器；帮助、关于和测试通知位于家长设置菜单。
具体职责、五个用例与数据交接见 [团队分工](docs/TEAM_PLAN.md)。

## 家长设置

首页 **Parents** → PIN 验证 → **Learning plan**，设置每日识字目标、提醒时间与星期。首次进入家长区时才创建 PIN；原有 PIN、进度和设置随原位升级保留。

## 架构

<p align="center"><img src="docs/screenshots/readme/architecture.png?v=20261026" width="880" alt="LittleStar 架构" /></p>

<p align="center">
  <sub><b>Java 17</b> · XML View + ViewBinding · Material Design 3 · MVVM + LiveData · Media3 ExoPlayer · Gson + AtomicFile<br/>
  零 Kotlin · 无数据库 · JVM 单测与设备回归</sub>
</p>

<details>
<summary><b>几个实现细节</b> &nbsp;<sub>数据库 · 磁盘 · 字体</sub></summary>

<br/>

- **不用数据库** — 一年数据只有几百 KB。`progress.json` 原子写 + 2 秒防抖 + 每日备份 + 损坏回退，查询就是普通 Java 循环。
- **磁盘单线程** — 所有读写投递到同一个线程，状态只在那里改，天然免锁；一次 `update()` 就是一次事务。
- **汉字子集字体** — 从 Noto Sans SC 提取内置教学内容和预览文本所需字形，保证「月」「骨」的写法不随厂商变。

</details>

## 快速开始

**从源码构建** —— Gradle daemon 用 JDK 25，应用使用 Java 17，Android SDK 37。已有 [GitHub Release](https://github.com/Green-hats/LittleStar/releases/latest) 为此前发布的版本。

```bash
git clone --branch refactor/java-md3 https://github.com/Green-hats/LittleStar.git && cd LittleStar
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 文档

| 文档 | 内容 |
|---|---|
| [**项目设计书**](docs/refactor/项目设计书.md) | 星芽小径的首课、关键页面和导航 |
| [**架构书**](docs/refactor/架构书.md) | 模块、存储、迁移和测试 |
| [**验收记录**](docs/refactor/验收记录.md) | 回归范围、升级复现与 Debug APK |
| [**施工计划书**](docs/refactor/施工计划书.md) | 分步实施和验收 |
| [**团队分工**](docs/TEAM_PLAN.md) | 三个子系统、每人四个 Activity、五个用例及模块交接 |
| [**历史设计书**](docs/DESIGN.md) | 重构前的页面及用户流程 |
| [**历史工程文档**](docs/ENGINEERING.md) | 重构前的数据模型、组件规格和逐屏实现 |
| [**历史验收清单**](docs/ACCEPTANCE.md) | 重构前的验收结果和证据 |
| [**决策记录**](docs/DECISIONS.md) | 38 条「为什么不那样做」 |
| [**协作指南**](CONTRIBUTING.md) | 环境、提交前自检、代码约定 |
| [**截图归档**](docs/screenshots) | M1 → M8 每屏的浅色 / 深色真机截图 |

<sub>MIT © 2026 Green-hats · 字体 Noto Sans SC / Poppins（SIL OFL 1.1）</sub>
