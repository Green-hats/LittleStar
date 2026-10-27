# LittleStar 当前界面与用户流程设计

2026-10-26。本文描述当前三人团队版本，与正式 AndroidManifest 页面清单对应。
历史设计和旧功能另存于 [调整前设计书](archive/DESIGN_before_team_modules_20261026.md)。
分工与模块接口见 [团队计划](TEAM_PLAN.md)，实现约定见 [工程文档](ENGINEERING.md)。

## 用户与应用目标

目标用户是生活在英语环境中的 3–6 岁华裔儿童及其家长。
儿童通过字形、拼音、英文提示和音频接触中文；家长设置识字目标与学习提醒。
词卡插画仍是占位内容，尚不能将现有界面视为已经验证的低龄儿童可用性结果。

## 设计原则

- 孩子区一屏一个实心主操作，其余使用描边块或图标。
- 汉字使用标准字形，颜色和动效用于操作反馈，不改变教学字形。
- 暖白背景、白卡片、墨色描边与硬影构成浅色视觉；深色资源单独提供。
- 红色用于学习与保存，蓝色用于听音播放，绿色用于文化与正确反馈，黄色用于局部强调。
- 控件使用共享尺寸、文字和颜色资源；通过 contentDescription 提供操作说明。
- 内容页支持滚动，主要播放按钮固定在详情底部，避免长文本把播放操作挤出屏幕。
- 长文本预览默认收起，由孩子与家长选择 Read together 展开，不要求孩子阅读全部介绍。
- 不通过增加空页面满足数量；详情页负责了解内容，播放器负责实际收听。

## 子系统与页面清单

| 角色 | Activity | 页面目标 | 下一步 |
|---|---|---|---|
| 共同 | HomeActivity | 选择今日任务、字或文模块、家长计划 | A、B、C 的入口 |
| A | ThemesActivity | 查看六个识字主题 | WordList |
| A | WordListActivity | 浏览所选主题的六个汉字 | Flashcard |
| A | FlashcardActivity | 浏览字形、听发音、标记已学 | Quiz 或返回浏览 |
| A | QuizActivity | 四选一作答并展示正确或错误反馈 | Reward |
| B | CultureCategoriesActivity | 选择儿歌、故事、古诗 | CultureList |
| B | CultureListActivity | 查看标题、类型和时长，选择条目 | CultureDetail |
| B | CultureDetailActivity | 看简介和学习重点，选择展开文本 | Player |
| B | PlayerActivity | 播放、暂停、上一首、下一首及拖动进度 | 返回详情或后台收听 |
| C | ParentGateActivity | 首次创建或验证四位数字 PIN | Settings |
| C | SettingsActivity | 配置目标、提醒，使用帮助菜单 | 返回 Home |
| C | RewardActivity | 本轮完成反馈、完成、再来一次 | RoundReview 或 Home |
| C | RoundReviewActivity | 查看本轮所选和正确答案，重听字音 | 返回 Reward |

共 13 个正式 Activity；开发用 ComponentGalleryActivity 不计入。
独立 HelpActivity 已移除，帮助、关于和测试通知合并到家长设置。

## 用户流程

```text
Home
├─ START → Flashcard（每日会话）→ Quiz → Reward → RoundReview
│                                           └─ DONE → Home
├─ Words → Themes → WordList → Flashcard（浏览）
├─ Culture → CultureCategories → CultureList → CultureDetail → Player
├─ 文模式推荐 → CultureDetail → Player
└─ Learning plan → ParentGate → Settings
                                  └─ 菜单：Help / Send test notification / About
```

家长设置可先于儿童学习，也可在以后调整；文化学习属于自主扩展。
字/文切换只改变首页推荐卡，模块入口进入浏览流程。
文化模式主按钮是 EXPLORE，进入内容详情；不再用 LISTEN 标签进入介绍页。
Player 返回所选内容详情，详情返回来源列表或首页。

## 首页

目的：呈现一个明确的今日学习入口，同时提供字、文及家长计划入口。

- 顶栏为熊猫和应用名称；独立问号入口已移除。
- 字模式展示今日汉字、拼音、英文、听音及今日进度；主操作为 START 或 REVIEW。
- 文模式展示推荐文化内容；主操作为 EXPLORE，卡片本身也可进入同一详情。
- 文模式不显示只对汉字发音有效的听音按钮。
- 初次引导仍在 Home 内完成，不新增 Activity。

## A 子系统 识字学习与测评

### 主题与词表

主题页采用两列网格，每个主题显示名称、汉字数量和已学进度。
词表提供进入指定汉字的浏览入口。主题顺序和内容来自本地 JSON。

### 识字卡

展示大汉字、拼音、英文和听音操作；支持左右浏览、标记已学和继续。
每日会话与主题浏览使用不同模式，避免浏览产生重复每日会话。
当前小图块仍重复展示汉字，后续应替换为表达词义的插图。
“已学”目前表示用户手动标记，不等同于通过测验或已经掌握。

### 测验

一页一题，显示四个汉字选项。答错通过陶土背景和正确答案反馈，答对使用正确反馈。
每题结果保存到对应会话，进入 C 的完成与回顾页时只传 sessionId。
英文提示是否显示取决于已有设置；不允许滑动跳过题目。

## B 子系统 文化内容学习

### 分类与列表

提供儿歌、故事、古诗三个当前分类。列表展示内容标题、类型和时长。
行和右侧箭头均进入详情；箭头不再暗示点击后立即播放。

### 文化详情

目的：在播放前让用户知道这是什么、可以注意什么，并提供亲子共读入口。

- 顶栏：返回和 Discover。
- 内容卡：代表字形、中文标题、类型和时长。
- 简介：一至两句内容介绍。
- Listen for：与本条内容有关的词语、动作或讨论提示。
- Read together：展开中文文本预览，再点 Hide text 收起。
- 底部主操作 LISTEN：进入播放器，并携带所选条目和同分类播放队列。
- 页面重建保留所选条目和文本展开状态；无效条目显示说明并禁用播放。
- 详情不自动启动音频，也不新增收藏、下载或网络依赖。

### 播放器

保留播放、暂停、上一首、下一首、进度拖动、歌词区及后台媒体控制。
返回详情后可再次播放，沿用播放器既有的同内容播放保持逻辑。
歌词按时间戳驱动；当前已知内容时间戳与音频尚未全部对齐，需独立内容验收。

## C 子系统 学习计划与反馈

### 家长验证与设置

未设 PIN 时首次进入创建四位数字 PIN，已设 PIN 时验证。
设置页提供每日目标 1–10、提醒时间和星期，以及保存与取消。
未保存草稿在重建后保留；取消不写入；当天已生成的学习计划不会被新目标重建。

设置页右上标准菜单提供：

- Help：说明学习提醒、权限与离线行为。
- Send test notification：确认权限后发送独立测试通知，不保存或丢弃当前草稿。
- About：展示版本和离线应用说明。

菜单权限请求与保存计划的权限请求分开处理。测试通知完成后继续留在设置页。
提示在家长区使用 Snackbar，说明和关于使用 Material 对话框。

### 完成与本轮回顾

完成页展示鼓励反馈和本轮汉字，提供 DONE、Play again 和 Review this round。
本轮回顾读取指定会话的答案，展示所选和正确答案，可再次听字音。
回顾是只读操作，不重复计算学习进度或修改答题结果。
没有记录时提供空状态；不展示已经删除的星星奖励。

## 当前范围与验证

已删除：收藏、睡前定时、休息或限时页、家长统计与历史记录页、星星奖励。
应用无账户、云同步、广告和网络内容；学习计划只统计识字，文化播放不计入目标。
深色、小屏、放大字体和无效内容入口需要在设备上检查，自动配色检查不能代替可用性观察。
旧截图保留在 screenshots 的历史目录；应以当前构建和本文件说明为准。

## 本轮设备截图

- [文化详情浅色](screenshots/team-modules/culture_detail_light.png)
- [文本展开](screenshots/team-modules/culture_detail_preview.png)
- [文化详情深色](screenshots/team-modules/culture_detail_dark.png)
- [家长设置菜单](screenshots/team-modules/settings_menu.png)

截图来自 Pixel 10 Pro 模拟器的设备回归，覆盖本轮新增界面；不作为全部屏幕尺寸的验收证明。
