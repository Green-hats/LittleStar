#!/usr/bin/env python3
"""生成 README 用的图片（首页拼图、模块单机图、架构图）。
需要 Pillow；字体在 tools/fonts 与 app/src/main/res/font。
用法：python3 tools/make_readme_assets.py
"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/screenshots/readme"
SRC = OUT / "src"

PAPER = (248, 239, 221)
CARD = (255, 253, 246)
INK = (33, 29, 24)
MUTED = (110, 98, 82)
RED = (184, 51, 28)
YELLOW = (245, 179, 1)
GREEN = (39, 165, 103)
BLUE = (31, 111, 208)
SHADOW = (215, 202, 175)

BOLD = str(ROOT / "tools/fonts/NotoSansSC-Bold.otf")
REG = str(ROOT / "tools/fonts/NotoSansSC-Regular.otf")
POP_SB = str(ROOT / "app/src/main/res/font/poppins_semibold.ttf")


def nb(s):
    return ImageFont.truetype(BOLD, s)


def nr(s):
    return ImageFont.truetype(REG, s)


def ps(s):
    return ImageFont.truetype(POP_SB, s)


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


def tracked_right(d, x_right, y, txt, font, fill, track=2):
    total = sum(d.textlength(ch, font=font) for ch in txt) + track * (len(txt) - 1)
    x = x_right - total
    for ch in txt:
        d.text((x, y), ch, font=font, fill=fill)
        x += d.textlength(ch, font=font) + track


def architecture():
    W, H = 1600, 900
    img = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(img)

    def chip(x, y, w, h, title, sub=None, ts=20, ss=14):
        d.rounded_rectangle([x, y + 3, x + w, y + h + 3], radius=14, fill=SHADOW)
        d.rounded_rectangle([x, y, x + w, y + h], radius=14, fill=CARD, outline=INK, width=2)
        if sub:
            d.text((x + 20, y + 13), title, font=nb(ts), fill=INK)
            d.text((x + 20, y + 45), sub, font=nr(ss), fill=MUTED)
        else:
            bb = d.textbbox((0, 0), title, font=nb(ts))
            d.text((x + (w - (bb[2] - bb[0])) / 2 - bb[0],
                    y + (h - (bb[3] - bb[1])) / 2 - bb[1]), title, font=nb(ts), fill=INK)

    def rail(y, cn, en, color):
        d.text((228 - d.textlength(cn, font=nb(26)), y - 4), cn, font=nb(26), fill=INK)
        tracked_right(d, 228, y + 38, en, ps(12), MUTED)
        d.rounded_rectangle([246, y + 2, 252, y + 50], radius=3, fill=color)

    def arrow(x, y1, y2):
        d.line([(x, y1), (x, y2 - 12)], fill=INK, width=2)
        d.polygon([(x - 7, y2 - 12), (x + 7, y2 - 12), (x, y2)], fill=INK)

    X0, X1, GAP = 310, 1530, 24
    w1 = (X1 - X0 - 3 * GAP) // 4

    y1 = 104
    rail(y1, "界面", "UI LAYER", RED)
    for i, (t, s) in enumerate([("孩子区", "首页 · 识字卡 · 测验"), ("家长区", "PIN · 概览 · 计划"),
                                ("系统页", "休息页 · 上限页"), ("组件", "BlockLayout 硬影块")]):
        chip(X0 + i * (w1 + GAP), y1, w1, 92, t, s)
    arrow(920, y1 + 98, 276)

    y2 = 280
    rail(y2, "状态", "STATE LAYER", BLUE)
    for i, (t, s) in enumerate([("UiState", "不可变快照"), ("Event", "一次性事件"),
                                ("SavedStateHandle", "进程恢复"), ("AppExecutors", "diskIO 单线程")]):
        chip(X0 + i * (w1 + GAP), y2, w1, 92, t, s)
    arrow(920, y2 + 98, 452)

    y3 = 456
    rail(y3, "业务", "DOMAIN LAYER", GREEN)
    d.text((X0, y3 + 2), "Repository", font=nb(19), fill=MUTED)
    rw = 190
    for i, t in enumerate(["Progress", "Usage", "Favourite"]):
        chip(X0 + i * (rw + 14), y3 + 38, rw, 56, t + "Repository", None, ts=16)
    d0 = X0 + 3 * (rw + 14) + 44
    dw_all = X1 - d0
    d.text((d0, y3 + 2), "domain · 不 import android.*", font=nb(19), fill=MUTED)
    dw = (dw_all - 2 * 12) // 3
    for i, t in enumerate(["SessionBuilder", "ReviewScheduler", "QuizGenerator",
                           "StarCalculator", "ScreenTimePolicy", "HomeModeResolver"]):
        r, c = divmod(i, 3)
        chip(d0 + c * (dw + 12), y3 + 38 + r * 62, dw, 52, t, None, ts=14)
    arrow(920, y3 + 158, 692)

    y4 = 704
    rail(y4, "存储", "STORAGE", YELLOW)
    w4 = (X1 - X0 - 2 * GAP) // 3
    for i, (t, s) in enumerate([("progress.json", "AtomicFile · 防抖 · 备份"),
                                ("assets/content", "words · culture · 只读"),
                                ("SharedPreferences", "PIN · 时长 · 计划")]):
        chip(X0 + i * (w4 + GAP), y4, w4, 92, t, s)

    ly = 838
    d.line([(X0, ly), (X1, ly)], fill=(222, 208, 182), width=1)
    d.text((X0, ly + 18), "↓ 点击 / 请求", font=nr(14), fill=MUTED)
    d.text((X0 + 210, ly + 18), "↑ 状态回流（LiveData）", font=nr(14), fill=MUTED)
    tracked_right(d, X1, ly + 18, "NO DATABASE · NO NETWORK", ps(12), MUTED)

    img.save(OUT / "architecture.png", optimize=True)


if __name__ == "__main__":
    hero()
    features()
    architecture()
    print("README assets regenerated →", OUT)
