# LittleStar 设计书 v2：墨与朱（Ink & Vermilion）

> 面向 3–6 岁、在澳洲学中文的孩子，以及他们的家长。
> 遵循 Material Design 3（m3.material.io）：颜色角色、字体层级、圆角档位、组件规范。
> 本文所有色值都由 Google 官方 material-color-utilities 计算得出（脚本：[make_scheme.mjs](<assets/make_scheme.mjs>)，结果：[scheme.json](<assets/scheme.json>)）。

![首页与识字卡，浅色 / 深色](<assets/overview.png>)

---

## 0. 上一版为什么不好看

| 问题 | 表现 | v2 的做法 |
|---|---|---|
| 颜色太多 | 首页同时出现红、蓝、绿三套主色，还都是高饱和的 Vibrant 方案，互相抢 | **全 app 只用一套配色**：一个强调色（朱红），一个辅助色（青绿），其余是暖白 / 墨色中性色 |
| 每个模块一种颜色 | 进入 Culture 变蓝、进入家长中心变绿，像三个 app 拼在一起 | 取消分模块配色。模块只靠**图标块**区分（字 = 朱红块，文 = 青绿块） |
| emoji 当界面图标 | 🀄 🏮 🛡 ⏱ 📅 风格、大小、颜色都不统一，深色模式下尤其乱 | 界面图标统一用 **Material Symbols Rounded**；模块入口用**汉字图标块**；emoji 只当学习内容的配图 |
| 熊猫插画粗糙 | 渐变 + 描边，眼睛方向不对，缩小后糊成一团 | **重绘**：扁平、几何、只用 3 种颜色，40dp 和 160dp 都清楚 |
| 没有层次 | 首页就是三张一样大的卡片，看不出重点 | 首页有**一个主角**（今日一词）和一个主按钮，其余都退后 |
| 文字太挤 | 系统字体放大后标题换行、错位 | 标题单行 + 省略；文案缩短；在 1.3 倍字体下验收 |

---

## 1. 设计原则

1. **一屏一个主角。** 每个页面只有一个最重要的东西（一个词、一首歌、一道题），只有一个实心主按钮。
2. **颜色有意义才用。** 朱红 = "去做这件事"；青绿 = 文化 / 答对；金色只给星星。其他地方一律中性色。
3. **大而少。** 给孩子的点按区域最小 56dp，每屏可点的东西尽量不超过 4 个。
4. **安静的背景，热闹的内容。** 界面用纸色和墨色，彩色留给汉字配图和熊猫。

---

## 2. 配色

![色板](<assets/palette.png>)

### 2.1 色源

| 调色板 | 种子色 | 色度 | 含义 |
|---|---|---|---|
| Primary 朱红 | #C8402F | 56 | 主操作、当前状态、品牌 |
| Secondary 暖沙 | #9C7A4A | 16 | 次要操作（"听一听"）、家长区图标 |
| Tertiary 青绿 | #2F7D6D | 30 | 文化模块、答对反馈 |
| Neutral 宣纸 | #8A7B6A | 4 | 背景、卡片、文字 |
| Neutral Variant | #8A7B6A | 9 | 次要文字、描边 |
| 自定义：金 | #E8A317 | 60 | **只用于星星**（奖励、星星数） |

方案按 M3 的 TonalSpot 规则（`DynamicScheme`，variant = TONAL_SPOT）生成，但五个调色板是手动指定的：朱红保持足够鲜艳，中性色偏暖、不发灰。

### 2.2 主要颜色角色

| 角色 | 浅色 | 深色 | 用在哪 |
|---|---|---|---|
| primary | #A33C2E | #FFB4A8 | 实心主按钮、进度条、英文释义 |
| onPrimary | #FFFFFF | #640D06 | 主按钮文字 |
| primaryContainer | #FFDAD4 | #832519 | 熊猫底色圆、"字"图标块、"今日一词"标签 |
| onPrimaryContainer | #832519 | #FFDAD4 | 上述元素上的文字 |
| secondaryContainer | #FADEBC | #56442A | 色调按钮（听一听）、家长区图标底 |
| onSecondaryContainer | #56442A | #FADEBC | 上述元素上的文字和图标 |
| tertiaryContainer | #B0EFDE | #075044 | "文"图标块、答对的选项 |
| onTertiaryContainer | #075044 | #B0EFDE | 上述元素上的文字 |
| surface | #FFF8F4 | #17130E | 页面背景 |
| surfaceContainerLow | #FCF2EA | #1F1B16 | 卡片（默认） |
| surfaceContainerHighest | #EAE1D9 | #39342F | 进度条轨道、输入框底 |
| onSurface | #1F1B16 | #EAE1D9 | 正文、汉字 |
| onSurfaceVariant | #504537 | #D5C4B2 | 拼音、说明文字 |
| outlineVariant | #D5C4B2 | #504537 | 描边卡片、分隔线 |
| errorContainer | #FFDAD6 | #93000A | 答错的选项 |
| star / onStar | #E39E00 / #281900 | #FFBA3E / #281900 | 星星图标、星星数胶囊 |

### 2.3 对比度（WCAG，实测）

| 组合 | 浅色 | 深色 | 要求 |
|---|---|---|---|
| onSurface / surface | 16.3 : 1 | 14.3 : 1 | ≥ 4.5 |
| onSurfaceVariant / surface | 8.9 : 1 | 10.9 : 1 | ≥ 4.5 |
| onPrimary / primary | 6.5 : 1 | 7.7 : 1 | ≥ 4.5 |
| onPrimaryContainer / primaryContainer | 7.2 : 1 | 7.2 : 1 | ≥ 4.5 |
| onTertiaryContainer / tertiaryContainer | 7.3 : 1 | 7.3 : 1 | ≥ 4.5 |

### 2.4 用色规则

- **60 / 30 / 10。** 约 60% 是 surface，30% 是 surfaceContainer 卡片，强调色不超过 10%。
- **一屏只有一个实心朱红按钮。** 其他按钮用色调（secondaryContainer）、描边或文字按钮。
- **金色只给星星。** 不要拿它做按钮或标题。
- **答题反馈：** 答对用 tertiaryContainer（青绿），答错用 errorContainer。不再用纯绿和纯红。
- **不写死颜色。** 布局里只能用 `?attr/color*` 角色。深色模式靠 `values-night` 自动切换。

---

## 3. 字体

Android 上英文用 Roboto，中文用 Noto Sans CJK（系统默认）。视觉稿是在 Mac 上用苹方渲染的，观感接近。

| 用途 | M3 样式 | 大小 / 行高 | 字重 |
|---|---|---|---|
| 识字卡大字 | Display Large（定制） | 80 / 96sp | Medium 500 |
| 今日一词、网格里的字 | Display Medium | 45–64sp | Medium 500 |
| 首页问候、页面大标题 | Headline Medium | 28 / 36sp | Regular 400 |
| 顶部栏标题、卡片标题 | Title Large | 22 / 28sp | Regular 400 |
| 列表标题、模块名 | Title Medium | 16 / 24sp | Medium 500 |
| 正文、歌词 | Body Large | 16 / 24sp | Regular 400 |
| 说明文字 | Body Medium | 14 / 20sp | Regular 400 |
| 按钮文字 | Label Large | 14–16sp | Medium 500 |
| 标签、胶囊 | Label Medium | 12 / 16sp | Medium 500 |

**规则：**
- 拼音用 onSurfaceVariant，英文释义用 primary，汉字用 onSurface。三者的层级靠颜色和字号拉开，不靠加粗。
- 标题一律单行，放不下就省略。所有页面都要在系统字体 1.3 倍下验收（你的手机字体偏大，正好用来测）。
- 不在布局里写 `textSize`。emoji 配图除外。

---

## 4. 形状、间距、层级

| 元素 | M3 圆角档 | 数值 |
|---|---|---|
| 主角卡片（今日一词、识字卡、播放器封面卡） | Extra Large | 28dp |
| 模块方块、列表卡片 | Large+ | 20dp |
| 图标块（字 / 文） | Medium+ | 14dp |
| 标签（今日一词） | Small | 8dp |
| 按钮、胶囊、进度条 | Full | 全圆角 |
| 测验选项方块 | Large | 16dp |

**间距（8dp 网格）：** 页面左右 16dp；区块之间 24dp；卡片之间 8dp；卡片内边距 16dp，主角卡片 24dp。

**层级：** 不用阴影，**只用色调区分层级**。surface 是背景，surfaceContainerLow 是卡片，surfaceContainerHighest 是轨道和输入框。描边卡片（outlineVariant 1dp）只给"低调入口"和可选择的行。

---

## 5. 图标与插画

### 5.1 熊猫"盼盼" v2

![熊猫 v2](<assets/panda.png>)

- **颜色只有三种：** 墨 #2B2622、纸白 #FFFBF7、腮红 #F28B7B（55% 透明）。
- **全部由圆和椭圆组成：** 黑眼圈是两个向外倾斜 28° 的椭圆，眼睛带一个小高光，嘴是 W 形。
- **始终放在底色圆上**（primaryContainer），这样白脸在浅色和深色背景下都看得清。
- 要转成 Android VectorDrawable（`ic_panda.xml`）。只用 path、circle、ellipse，不用渐变和滤镜，缩到 40dp 还清楚。
- 源文件：[panda.svg](<assets/panda.svg>)

### 5.2 界面图标

统一用 **Material Symbols Rounded**（24dp，跟随颜色角色着色），不再用 emoji：

| 位置 | 图标 |
|---|---|
| 返回 | arrow_back |
| 进入 | chevron_right |
| 听一听 | volume_up |
| 播放 / 暂停 / 上一首 / 下一首 | play_arrow / pause / skip_previous / skip_next |
| 收藏 | favorite / favorite_border |
| 家长中心 | lock |
| 星星 | star（金色） |
| 时长 / 计划 / 记录 | timer / event / history |
| 睡眠定时 | bedtime |

### 5.3 模块图标块

两个模块入口用一个汉字当图标，既是图标，又是学习内容：

- **字**：Chinese Words，primaryContainer 底，onPrimaryContainer 字。
- **文**：Culture Corner，tertiaryContainer 底，onTertiaryContainer 字。
- 家长中心不用汉字块，用 lock 图标（secondaryContainer 底）。这样它在视觉上是"给大人的"。

### 5.4 内容配图

学习内容（熊猫、灯笼、饺子……）继续用现在的彩色图片，但必须放在 primaryContainer 的圆里，并且大小统一。它们是全屏唯一"热闹"的元素。

---

## 6. 组件规范

| 场景 | 组件 | 说明 |
|---|---|---|
| 每屏一个主操作 | Filled Button（primary） | 56dp 高，全圆角 |
| 次要操作 | Filled Tonal Button（secondaryContainer） | 听一听、再玩一次 |
| 低调操作 | Outlined / Text Button | 取消、回首页 |
| 图标操作 | Icon Button（Standard / Tonal） | 48dp 点按区 |
| 顶部栏 | Small Top App Bar | surface 底，滚动时变 surfaceContainer，不用彩色顶栏 |
| 模块 / 主题入口 | Filled Card（surfaceContainerLow） | 整卡可点，有水波纹 |
| 低调入口（家长中心） | Outlined Card | 1dp outlineVariant |
| 标签 / 计数 | Assist Chip 或 8dp 小标签 | 不可点的信息不要做成按钮的样子 |
| 进度 | Linear Progress Indicator | 6dp 粗，全圆角，轨道 surfaceContainerHighest |
| 识字卡翻页 | 页码点（当前点拉长为 20dp） | 代替原来的 "Card 1 of 6" 胶囊 |
| 分类切换 | Primary Tabs | Culture Corner 的 Songs / Stories / Poems |
| 设置 | Material Switch、Radio、Filter Chip、Slider、Time Picker | 跟上一版相同 |
| 提示 | Snackbar | 不用 Toast |

---

## 7. 页面设计

### 7.1 首页（重点）

![首页 浅色](<assets/home_light.png>)

**从上到下：**

1. **顶部栏**（64dp）：40dp 熊猫头像（primaryContainer 圆），"LittleStar"（Title Large），右边一个星星数胶囊（starContainer 底，金色星 + 数字）。
2. **问候**：「你好！Nǐ hǎo」（Headline Medium），下面一行「Ready for today's word?」（Body Large，onSurfaceVariant）。
3. **今日一词卡**（主角，28dp 圆角，surfaceContainerLow）：
   - 左上角小标签「Word of the day」（primaryContainer，8dp 圆角）；
   - 大字「熊猫」（Display，onSurface），下面是「xióng māo · panda」；
   - 右边是配图（放在 primaryContainer 圆里）；
   - 底部一个实心主按钮「Start learning」，旁边一个色调圆形图标按钮（volume_up，点了直接朗读）。
   - **数据：** 按日期从 CardData 里固定选一个词，同一天总是同一个。点按钮进入这个词所在主题的识字卡页。
4. **今日进度**：「Today」+ 右侧「2 of 10 words」，下面是线性进度条。目标来自家长中心的学习计划。（需要给 Prefs 增加"今日已学词数"，按日期清零。）
5. **Explore**：两个并排的方块卡（160 × 148dp）：
   - 「字」Chinese Words · Cards · Quiz
   - 「文」Culture Corner · Songs · Stories · Poems
6. **家长中心**：最下面一行描边卡片（lock 图标 + 「Parent Centre」+ 一行说明 + chevron）。低调，但随时能找到。

**删掉的东西：** 三张一样大的彩色卡片、emoji 图标、大熊猫头像 + 大标题的组合（熊猫缩到顶栏，并出现在今日一词卡里）。

![首页 深色](<assets/home_dark.png>)

### 7.2 Chinese Words

- **主题选择**：两列方块卡。每个主题卡上是一张大配图（primaryContainer 圆）、英文名 + 中文名（例如 "Animals" 下面一行 "动物"，不再挤在一行），以及一个小字「6 words」。去掉「Step 1」胶囊，推荐顺序直接体现为排列顺序。
- **词列表**：两列卡片，内容依次是配图、汉字、拼音、英文。已学的卡片右上角有一个小金星。顶部栏右侧放一个色调按钮「Quiz」，不再用整行大按钮。
- **识字卡**：见上方视觉稿。28dp 主角卡；页码点代替计数胶囊；下方是色调「Listen」和实心「I learned it」。已学之后，主按钮变成「Learned ✓」，并变为不可点状态。
- **测验**：顶部栏标题「Quiz」，下方进度条，不再要计数胶囊，进度条右侧用小字写「1/5」。题目是一张配图 + 英文提示；四个选项是 16dp 圆角的描边方块，内容为汉字加拼音。答对变青绿，答错变浅红，再显示正确答案。
- **奖励**：居中摆放熊猫（大）、三颗星（金色，未得到的星用 outlineVariant），标题用 Headline，下面是实心「Play again」和文字按钮「Back home」。去掉 🎉 emoji。

### 7.3 Culture Corner

- **分类页**：顶部栏 + Primary Tabs（Songs / Stories / Poems）。列表项用 72dp 的行（不是卡片）：左边 56dp 圆形封面（tertiaryContainer 底 + 配图），中间是标题和时长，右边是 play_arrow 图标按钮。行与行之间不加分隔线，靠间距区分。
- **播放页**：大封面（28dp 圆角方形，tertiaryContainer），标题 + 中文名，Slider 进度条，控制区是「上一首 · 大的实心播放键 72dp · 下一首」。收藏是顶部栏的图标按钮。歌词放在 surfaceContainerLow 卡片里，每段三行：汉字（Body Large）、拼音（Body Medium，onSurfaceVariant）、英文（Body Medium，斜体）。
- **收藏 / 睡眠定时**：沿用上一版结构，换成本套配色和图标。

### 7.4 Parent Centre

- 家长验证、概览、时长、计划、记录：结构沿用上一版，改动有三点：
  - **配色**：从绿色换成本套中性色加朱红强调；
  - **图标**：emoji 全部换成 Material Symbols（timer / event / history），放在 secondaryContainer 的圆里；
  - **统计卡**：数字用 Headline Large，标签用 Body Medium，三张卡等宽。
- 家长区信息密度可以比孩子区高一点：按钮 48dp 即可，文字以 Body 为主。

---

## 8. 深色模式、无障碍

- 所有颜色都走角色，`values-night` 中是对应的深色值（见色板）。熊猫底色圆在深色下是深朱红，白脸依然清楚。
- 点按区域：孩子区 ≥ 56dp，家长区 ≥ 48dp。
- 所有图标按钮都有 contentDescription；装饰性图片设置 `importantForAccessibility="no"`。
- 在系统字体 1.3 倍、深浅两种模式下逐页截图验收。

---

## 9. 和上一版相比，要改什么（实施清单）

1. **配色**：用 [make_scheme.mjs](<assets/make_scheme.mjs>) 生成 `colors_m3.xml`（浅色）和 `values-night/colors_m3.xml`（深色）。删掉 Media / Parent 两套分模块配色。
2. **熊猫**：把 [panda.svg](<assets/panda.svg>) 转成新的 `ic_panda.xml`。
3. **图标**：补齐 Material Symbols Rounded 矢量图标（lock、timer、event、history、bedtime、play_arrow、pause 等），替换界面上所有 emoji 图标。
4. **首页**：按 7.1 重做；Prefs 增加"今日已学词数"；CardData 增加"今日一词"。
5. **其他页面**：按 7.2–7.4 调整。
6. **验收**：深浅两种模式 × 1.3 倍字体，逐页截图，对照本设计书检查。

## 附：说明

- m3.material.io 在我这边的网络环境里打不开，本设计书依据的是 M3 规范在 Material Components for Android 1.12 和 material-color-utilities 里的官方实现（颜色角色、字体层级、圆角档位、组件样式），没有逐页对照官网原文。
- 视觉稿是 SVG 手绘（[assets](<assets>) 文件夹），尺寸按 360 × 800dp 手机画布。实际 app 的字体渲染会略有不同。
