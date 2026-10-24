#!/usr/bin/env python3
"""生成 README 用图：三屏首图、模块单机图、架构图。

需要 Pillow；字体：tools/fonts/NotoSansSC-{Bold,Regular}.otf 与
app/src/main/res/font/poppins_semibold.ttf。

用法：python3 tools/make_readme_assets.py
素材：docs/screenshots/readme/src/*.png（真机截图，1080×2400）
输出：docs/screenshots/readme/{hero,feature_*,architecture}.png
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/screenshots/readme"
SRC = OUT / "src"

# ---- palette（与 App 的 colors.xml 同源）----
PAPER = (248, 239, 221)
BG = (255, 250, 240)
INK = (33, 29, 24)
MUTED = (122, 110, 94)
HAIR = (226, 214, 192)
CARD = (255, 255, 255)
CARD_LINE = (228, 216, 196)
ARROW = (140, 126, 104)
RED = (184, 51, 28)
BLUE = (31, 111, 208)
GREEN = (39, 165, 103)
AMBER = (196, 134, 15)
CLAY = (150, 92, 62)

BOLD = str(ROOT / "tools/fonts/NotoSansSC-Bold.otf")
REG = str(ROOT / "tools/fonts/NotoSansSC-Regular.otf")
POP_SB = str(ROOT / "app/src/main/res/font/poppins_semibold.ttf")


def nb(s):
    return ImageFont.truetype(BOLD, s)


def nr(s):
    return ImageFont.truetype(REG, s)


def pb(s):
    return ImageFont.truetype(POP_SB, s)


def tint(c, a=0.08):
    return tuple(int(255 - (255 - c[i]) * a) for i in range(3))


def line_of(c, a=0.28):
    return tuple(int(255 - (255 - c[i]) * a) for i in range(3))


# ---------------------------------------------------------------- 手机拼图
def phone(canvas, path, x, y, w, h, r=34):
    im = Image.open(path).convert("RGB").resize((w, h), Image.LANCZOS)
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=r, fill=255)
    sh = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([x + 10, y + 16, x + w + 10, y + h + 16],
                                         radius=r, fill=(33, 29, 24, 52))
    canvas = Image.alpha_composite(canvas.convert("RGBA"),
                                   sh.filter(ImageFilter.GaussianBlur(18))).convert("RGB")
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    layer.paste(im, (0, 0), mask)
    canvas.paste(layer, (x, y), layer)
    return canvas


def hero():
    c = Image.new("RGB", (1600, 1000), PAPER)
    pw, ph, gap = 404, 898, 46
    x0 = (1600 - (3 * pw + 2 * gap)) // 2
    y0 = (1000 - ph) // 2 - 6
    for i, f in enumerate(["01_home.png", "02_flashcard.png", "04_reward.png"]):
        c = phone(c, SRC / f, x0 + i * (pw + gap), y0, pw, ph)
    c.save(OUT / "hero.png", optimize=True)


def features():
    for src, dst in [("03_quiz.png", "feature_quiz.png"),
                     ("09_player.png", "feature_player.png"),
                     ("07_parent.png", "feature_parent.png")]:
        c = Image.new("RGB", (540, 1160), PAPER)
        c = phone(c, SRC / src, 40, 62, 460, 1022)
        c.save(OUT / dst, optimize=True)


# ---------------------------------------------------------------- 架构图
def architecture():
    W, H = 1600, 1150
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    def txt(x, y, t, f, fill=INK, anchor="la"):
        d.text((x, y), t, font=f, fill=fill, anchor=anchor)

    def panel(x, y, w, h, num, title, meta, accent):
        d.rounded_rectangle([x, y, x + w, y + h], radius=18,
                            fill=tint(accent, 0.07), outline=line_of(accent, 0.26), width=2)
        txt(x + 24, y + 18, f"{num}  {title}", nb(22), INK)
        if meta:
            txt(x + w - 24, y + 24, meta, nr(14), MUTED, anchor="ra")

    def card(x, y, w, h, title, sub, meta=None, ts=18, ss=13):
        d.rounded_rectangle([x, y, x + w, y + h], radius=12,
                            fill=CARD, outline=CARD_LINE, width=2)
        txt(x + 18, y + 13, title, nb(ts), INK)
        if meta:
            txt(x + w - 18, y + 16, meta, nr(12), MUTED, anchor="ra")
        txt(x + 18, y + h - 29, sub, nr(ss), MUTED)

    def arrow(x, y1, y2, label):
        d.line([(x, y1), (x, y2 - 13)], fill=ARROW, width=2)
        d.polygon([(x - 7, y2 - 13), (x + 7, y2 - 13), (x, y2)], fill=ARROW)
        tw = d.textlength(label, font=nr(14))
        pad = 16
        d.rounded_rectangle([x - tw / 2 - pad, y1 + 6, x + tw / 2 + pad, y1 + 38],
                            radius=16, fill=CARD, outline=HAIR, width=2)
        txt(x, y1 + 14, label, nr(14), MUTED, anchor="ma")

    def dashed_rect(x1, y1, x2, y2, r=12, dash=10, gap=7, color=(202, 182, 154), w=2):
        def seg(a, b):
            length = math.hypot(b[0] - a[0], b[1] - a[1])
            n = max(1, int(length // (dash + gap)) + 1)
            for i in range(n):
                s = i * (dash + gap) / length
                e = min(1, (i * (dash + gap) + dash) / length)
                if s < 1:
                    d.line([(a[0] + (b[0] - a[0]) * s, a[1] + (b[1] - a[1]) * s),
                            (a[0] + (b[0] - a[0]) * e, a[1] + (b[1] - a[1]) * e)],
                           fill=color, width=w)
        seg((x1 + r, y1), (x2 - r, y1))
        seg((x1 + r, y2), (x2 - r, y2))
        seg((x1, y1 + r), (x1, y2 - r))
        seg((x2, y1 + r), (x2, y2 - r))
        for box, a0, a1 in [([x1, y1, x1 + 2 * r, y1 + 2 * r], 180, 270),
                            ([x2 - 2 * r, y1, x2, y1 + 2 * r], 270, 360),
                            ([x1, y2 - 2 * r, x1 + 2 * r, y2], 90, 180),
                            ([x2 - 2 * r, y2 - 2 * r, x2, y2], 0, 90)]:
            d.arc(box, a0, a1, fill=color, width=w)

    # ---- 页眉 ----
    d.rounded_rectangle([56, 42, 64, 118], radius=4, fill=RED)
    txt(84, 44, "LittleStar architecture", pb(42), INK)
    txt(84, 100, "从一次点击到本地文件：单向数据流，全程离线", nr(19), MUTED)

    pill = "Android 8.0+ · minSdk 26"
    tw = d.textlength(pill, font=nr(14))
    d.rounded_rectangle([W - 56 - tw - 56, 54, W - 56, 92], radius=19,
                        fill=tint(GREEN, 0.10), outline=line_of(GREEN, 0.30), width=2)
    d.ellipse([W - 56 - tw - 42, 69, W - 56 - tw - 34, 77], fill=GREEN)
    txt(W - 56 - tw - 26, 64, pill, nr(14), INK)

    X0, LW = 56, 1088
    RX = X0 + LW + 24
    RW = W - 56 - RX
    INNER, IW = X0 + 24, LW - 48

    # ---- 01 界面 ----
    y, h = 160, 172
    panel(X0, y, LW, h, "01", "界面", "12 Activity · ViewBinding", RED)
    cw = (IW - 3 * 16) // 4
    for i, (t, s) in enumerate([("孩子区", "首页 · 识字卡 · 测验 · 完成"),
                                ("学习任务", "PIN · 每日字数 · 学习提醒"),
                                ("内容区", "主题 · 字表 · 儿歌 · 故事"),
                                ("组件库", "BlockLayout · 6 个自绘 View")]):
        card(INNER + i * (cw + 16), y + 56, cw, 98, t, s)
    arrow(X0 + LW // 2, y + h, 386, "点击 → 事件")

    # ---- 02 状态 ----
    y, h = 386, 172
    panel(X0, y, LW, h, "02", "状态", "ViewModel · LiveData · 单向渲染", BLUE)
    for i, (t, s) in enumerate([("UiState", "不可变快照 · render 唯一入口"),
                                ("Event", "一次性事件 · 旋转不重放"),
                                ("SavedStateHandle", "当前题号 · 进程恢复"),
                                ("AppExecutors", "diskIO 单线程 + mainThread")]):
        card(INNER + i * (cw + 16), y + 56, cw, 98, t, s)
    arrow(X0 + LW // 2, y + h, 612, "调用 Repository")

    # ---- 03 业务 ----
    y, h = 612, 250
    panel(X0, y, LW, h, "03", "业务", "纯 Java · 不 import android.* · 100% 可单测", GREEN)
    repo_w = 560
    dw_all = IW - repo_w - 24
    txt(INNER, y + 56, "Repository", nb(16), MUTED)
    rw = (repo_w - 2 * 14) // 3
    for i, t in enumerate(["Progress", "Content", "Favourite"]):
        card(INNER + i * (rw + 14), y + 86, rw, 68, t + "Repository", "LiveData 派生数据",
             ts=16, ss=13)

    dx = INNER + repo_w + 24
    txt(dx, y + 56, "domain", nb(16), MUTED)
    dw = (dw_all - 2 * 12) // 3
    for i, (t, s) in enumerate([("SessionBuilder", "组一轮会话"),
                                ("ReviewScheduler", "Leitner 复习"),
                                ("QuizGenerator", "四选一干扰项"),
                                ("HomeModeResolver", "字 / 文规则")]):
        r, c = divmod(i, 3)
        card(dx + c * (dw + 12), y + 86 + r * 74, dw, 66, t, s, ts=12, ss=12)
    txt(INNER, y + h - 44, "页面不直接碰 progress.json：所有读写都经过 Repository", nr(13), MUTED)
    arrow(X0 + LW // 2, y + h, 936, "读 / 写")

    # ---- 04 存储 ----
    y, h = 936, 152
    panel(X0, y, LW, h, "04", "存储", "无数据库 · 全部本地", AMBER)
    sw = (IW - 2 * 16) // 3
    for i, (t, s, m) in enumerate([("progress.json", "原子写 · 防抖 · 备份", "AtomicFile"),
                                   ("assets/content", "36 字 · 11 条文化", "只读"),
                                   ("SharedPreferences", "PIN · 每日目标 · 提醒", "设置")]):
        card(INNER + i * (sw + 16), y + 50, sw, 86, t, s, meta=m, ts=17, ss=12)

    # ---- 05 运行时（右栏）----
    y = 160
    panel(RX, y, RW, 1088 - y, "05", "运行时", "后台服务 · 不做网络", CLAY)
    yy = y + 58
    iw = RW - 48
    for t, s in [("锁屏播放", "Media3 ExoPlayer + MediaSessionService"),
                 ("睡前定时", "播完当前音轨 → 晚安通知"),
                 ("每日提醒", "AlarmManager · 19:30 · 可设星期"),
                 ("通知渠道", "reminder / bedtime")]:
        d.rounded_rectangle([RX + 24, yy, RX + 24 + iw, yy + 76], radius=12,
                            fill=CARD, outline=CARD_LINE, width=2)
        txt(RX + 40, yy + 12, t, nb(17), INK)
        txt(RX + 40, yy + 42, s, nr(12), MUTED)
        yy += 90

    dashed_rect(RX + 24, yy + 2, RX + 24 + iw, yy + 150)
    txt(RX + 44, yy + 20, "尚未实现", nb(16), MUTED)
    txt(RX + 44, yy + 50, "云同步 · 账号 · 内购", nr(13), MUTED)
    txt(RX + 44, yy + 74, "明确不做，见 DECISIONS", nr(12), MUTED)

    by = yy + 214
    txt(RX + 24, by, "约束", nb(15), MUTED)
    for i, (t, s) in enumerate([("无网络请求", "没有 INTERNET 权限"),
                                ("数据只在本机", "progress.json 不出设备"),
                                ("无第三方 SDK", "无统计 / 广告 / 推送")]):
        ry = by + 36 + i * 62
        d.ellipse([RX + 24, ry + 7, RX + 32, ry + 15], fill=CLAY)
        txt(RX + 42, ry, t, nb(14), INK)
        txt(RX + 42, ry + 22, s, nr(12), MUTED)

    # ---- 图例 ----
    ly = H - 40
    x = X0
    for c, t in [(RED, "界面"), (BLUE, "状态"), (GREEN, "业务"), (AMBER, "存储"), (CLAY, "运行时")]:
        d.ellipse([x, ly, x + 13, ly + 13], fill=c)
        txt(x + 21, ly - 1, t, nr(15), MUTED)
        x += 21 + d.textlength(t, font=nr(15)) + 32
    txt(W - 56, ly - 1, "全部离线 · 无 INTERNET 权限", nr(15), MUTED, anchor="ra")

    img.save(OUT / "architecture.png", optimize=True)


if __name__ == "__main__":
    hero()
    features()
    architecture()
    print("README assets regenerated →", OUT)
