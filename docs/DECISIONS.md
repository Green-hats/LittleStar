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
