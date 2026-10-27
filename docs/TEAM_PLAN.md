# LittleStar 三人团队分工与交付计划

2026-10-26。团队已确认三人、重新分工。A、B、C 是待填姓名的角色，不代表历史作者身份。
本文件确定当前子系统边界、页面责任和模块交接。个人报告应按真实贡献填写。

## 共同应用概念

LittleStar 帮助生活在英语环境中的 3–6 岁华裔儿童接触中文。
孩子通过识字卡、听音和测验学习常用汉字，通过儿歌、故事和古诗探索中文；
家长设置每日识字目标和提醒。所有学习内容和记录保存在本机。

三个子系统分别承担学习与测评、文化内容学习、学习计划与反馈。
完整团队旅程：家长设定任务 → 孩子识字与测验 → 查看结果和回顾 → 自主探索文化内容。
文化内容是自主扩展，不作为每次学习的强制步骤。目前文化播放不计入识字目标，也不根据错题推荐。

## 页面责任

| 角色 | 子系统 | 四个 Activity | 负责的结果 |
|---|---|---|---|
| A | 识字学习与测评 | ThemesActivity、WordListActivity、FlashcardActivity、QuizActivity | 选择学习内容、听音识字、记录作答并交给反馈模块 |
| B | 文化内容学习 | CultureCategoriesActivity、CultureListActivity、CultureDetailActivity、PlayerActivity | 内容发现、简介与文本预览、播放及内容与音频的对应 |
| C | 学习计划与反馈 | ParentGateActivity、SettingsActivity、RewardActivity、RoundReviewActivity | 家长计划与提醒、本轮完成反馈和答题回顾 |
| 共同 | 首页与整合 | HomeActivity | 今日任务、进度、三个模块入口、首次引导和返回行为 |

正式清单为 13 个 Activity。debug 的 ComponentGalleryActivity 是开发工具，不计入正式清单。
帮助、关于、测试通知是 SettingsActivity 的菜单操作，不再占独立 Activity。

## 每人五个功能用例

| 编号 | 用例 | 页面或交接 |
|---|---|---|
| A01 | 选择汉字主题 | ThemesActivity |
| A02 | 浏览词表并选择汉字 | WordListActivity |
| A03 | 查看字形、拼音与英文并播放发音 | FlashcardActivity |
| A04 | 手动标记已学并继续学习 | FlashcardActivity |
| A05 | 完成四选一测验并记录答案 | QuizActivity → C 的 RewardActivity |
| B01 | 选择儿歌、故事或古诗分类 | CultureCategoriesActivity |
| B02 | 浏览分类内容及其时长 | CultureListActivity |
| B03 | 查看简介与学习重点 | CultureDetailActivity |
| B04 | 展开和收起文本预览 | CultureDetailActivity |
| B05 | 播放、暂停、切换内容及后台收听 | PlayerActivity |
| C01 | 创建或验证家长 PIN | ParentGateActivity |
| C02 | 编辑并保存每日识字目标 | SettingsActivity |
| C03 | 设置提醒时间与星期，确认通知行为 | SettingsActivity 及提醒服务 |
| C04 | 查看本轮完成反馈并回到首页 | RewardActivity |
| C05 | 回顾本轮逐题作答并重听字音 | RoundReviewActivity |

这五个用例是现有产品的最终功能清单，不是 AI 原始回答或已经发生的提问记录。

## 模块交接约定

| 交接 | 数据与约束 | 牵头角色 |
|---|---|---|
| Home → A | SESSION 模式创建每日会话；主题浏览使用主题 ID、汉字 ID 和起始位置 | A |
| C → A | dailyGoal 为 1–10；已生成的今日计划不随当天设置修改而重建 | C 与 A |
| A → C | 通过 sessionId 交接；从共享存储读取会话和答案，不传整个对象 | A 与 C |
| C → Home | 读取同一份学习状态，返回时刷新进度 | C |
| Home / B 列表 → B 详情 | itemId；无效 ID 显示不可播放的空状态 | B |
| B 详情 → B 播放器 | itemId 和该内容所属分类的 queue；上/下一首保持同分类顺序 | B |
| C 通知 → Home | 点击提醒回首页；测试通知与每日提醒使用不同 ID | C |

共同资源：颜色、尺寸、字符串、字体、BlockLayout、AppContainer、存储模型。
调整共享接口前，在决策记录中说明影响，并由调用方与提供方一起检查。
无需为了责任分配移动现有 Java 包或重复创建 Repository。

## 团队协作与验收责任

- A 牵头学习数据交接：验证从识字到测验，再到完成与回顾的数据一致。
- B 牵头视觉和内容一致性：统一组件使用，核对图像、文本、音频和歌词时间。
- C 牵头整合验收和提交清单：检查入口、权限、提醒、返回路径及提交文件。
- 每位负责人承担本模块的需求、设计、实现、验证、报告和演示；牵头人不代替模块负责人修复问题。
- 交叉评审建议采用 A → B、B → C、C → A。记录具体问题、影响、建议、回应及修改证据。

## 团队演示路径

1. 首页 → Learning plan → PIN → 每日目标、星期与时间 → 保存 → 首页。
2. 首页 → Words → 主题 → 词表 → 识字卡；另由 START 开始每日会话 → 测验 → 完成 → 回顾。
3. 首页 → Culture → 分类 → 列表 → 详情 → 展开文本 → 播放 → 返回详情 → 返回列表。
4. 家长设置菜单 → 帮助、关于、测试通知；查看通知并确认点击返回首页。
5. 检查深色、小屏和放大字体；在详情和设置页重建后，内容与草稿应保留。

## 提交前仍需真实补齐的材料

- 成员姓名、学号和实际贡献。
- 使用课程指定场景 agent 的真实记录、批判过程，以及各子系统场景说明。
- 每人的五个明确提问、AI 回答附件、批判和最终用例。
- 当前版本截图、用户流程图、真实交叉评审与回应。
- 每人不超过五分钟的视频，以及个人和小组提交包。

## 已知后续工作

本轮只调整子系统与页面结构。原有词卡配图仍是占位资源，文化音频仍需逐项试听。
七条带歌词内容存在超出实际音频时长的时间戳，需按音频重新对齐，不能把当前高亮当成完整同步证明。
当前 applicationId 为 com.littlestar.app；是否需按课程命名格式修改仍待确认。
