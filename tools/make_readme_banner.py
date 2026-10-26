#!/usr/bin/env python3
"""生成 README 头图：LittleStar 汉字积木横幅（浅色 + 深色）。

用法：python3 tools/make_readme_banner.py
输出：docs/assets/banner.png、docs/assets/banner_dark.png
依赖：Pillow；字体用 tools/fonts 与 app/src/main/res/font。
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/assets"
PANDA = ROOT / "docs/assets/illustrations/panda_v3.png"

W, H = 1600, 560
NOTO_BOLD = str(ROOT / "tools/fonts/NotoSansSC-Bold.otf")
NOTO_REG = str(ROOT / "tools/fonts/NotoSansSC-Regular.otf")
POP_XB = str(ROOT / "app/src/main/res/font/poppins_extrabold.ttf")
POP_SB = str(ROOT / "app/src/main/res/font/poppins_semibold.ttf")
POP_MD = str(ROOT / "app/src/main/res/font/poppins_medium.ttf")

LIGHT = {
    "paper": (250, 249, 246),
    "ink": (33, 29, 24),
    "muted": (110, 98, 82),
    "red": (184, 51, 28),
    "yellow": (245, 179, 1),
    "green": (39, 165, 103),
    "blue": (31, 111, 208),
    "on_yellow": (33, 29, 24),
    "on_green": (33, 29, 24),
    "on_blue": (255, 253, 246),
    "on_red": (255, 253, 246),
    "card": (255, 255, 255),
    "line": (33, 29, 24),
    "dot": (235, 227, 214),
    "circle": (250, 235, 199),
    "shadow": (33, 29, 24),
}

DARK = {
    "paper": (23, 19, 14),
    "ink": (243, 231, 211),
    "muted": (201, 183, 155),
    "red": (255, 138, 110),
    "yellow": (255, 201, 77),
    "green": (111, 211, 162),
    "blue": (127, 180, 255),
    "on_yellow": (23, 19, 14),
    "on_green": (23, 19, 14),
    "on_blue": (23, 19, 14),
    "on_red": (23, 19, 14),
    "card": (36, 31, 25),
    "line": (184, 170, 147),
    "dot": (43, 36, 29),
    "circle": (48, 38, 24),
    "shadow": (0, 0, 0),
}


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size)


def text_width(draw: ImageDraw.ImageDraw, text: str,
               f: ImageFont.FreeTypeFont, tracking: float = 0) -> float:
    if not text:
        return 0
    return sum(draw.textlength(ch, font=f) for ch in text) + tracking * (len(text) - 1)


def tracked_text(draw: ImageDraw.ImageDraw, xy: tuple[float, float], text: str,
                 f: ImageFont.FreeTypeFont, fill: tuple[int, int, int],
                 tracking: float = 0) -> None:
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=f, fill=fill, anchor="lt")
        x += draw.textlength(ch, font=f) + tracking


def pill(draw: ImageDraw.ImageDraw, x: int, y: int, text: str,
         f: ImageFont.FreeTypeFont, fill: tuple[int, int, int],
         fg: tuple[int, int, int], outline: tuple[int, int, int],
         shadow: tuple[int, int, int]) -> int:
    pad_x, h, off = 18, 46, 5
    w = int(text_width(draw, text, f)) + pad_x * 2
    draw.rounded_rectangle((x + off, y + off, x + w + off, y + h + off),
                           radius=h // 2, fill=shadow)
    draw.rounded_rectangle((x, y, x + w, y + h), radius=h // 2, fill=fill,
                           outline=outline, width=3)
    draw.text((x + w // 2, y + h // 2 + 1), text, font=f, fill=fg, anchor="mm")
    return w


def tile(text: str, size: int, fill: tuple[int, int, int],
         fg: tuple[int, int, int], angle: float, shadow: tuple[int, int, int]) -> Image.Image:
    pad, off = 10, 8
    side = size + pad * 2 + off
    img = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((pad + off, pad + off, pad + size + off, pad + size + off),
                        radius=24, fill=shadow)
    d.rounded_rectangle((pad, pad, pad + size, pad + size),
                        radius=24, fill=fill, outline=shadow, width=4)
    f = font(NOTO_BOLD, int(size * 0.52))
    d.text((pad + size // 2, pad + size // 2 + 2), text, font=f, fill=fg, anchor="mm")
    return img.rotate(angle, expand=True, resample=Image.Resampling.BICUBIC)


def panda_image(size: int) -> Image.Image:
    src = Image.open(PANDA).convert("RGBA").crop((132, 152, 828, 848))
    mask = Image.new("L", (src.width * 4, src.height * 4), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, mask.width - 1, mask.height - 1), fill=255)
    mask = mask.resize(src.size, Image.Resampling.LANCZOS)
    src.putalpha(mask)
    return src.resize((size, size), Image.Resampling.LANCZOS)


def render(dark: bool) -> Image.Image:
    c = DARK if dark else LIGHT
    img = Image.new("RGBA", (W, H), c["paper"])
    d = ImageDraw.Draw(img)

    # 淡淡的点阵 / 背景圆
    for y in range(52, H - 30, 48):
        for x in range(64, W - 40, 48):
            d.ellipse((x, y, x + 2, y + 2), fill=c["dot"])
    d.ellipse((985, -120, 1705, 600), fill=c["circle"])

    # 左侧红色竖条
    d.rounded_rectangle((64, 78, 78, 446), radius=7, fill=c["red"])

    # 顶部小字 + 主标题
    over_f = font(POP_SB, 21)
    tracked_text(d, (120, 74), "LITTLESTAR", over_f, c["red"], 4)
    ox = 120 + text_width(d, "LITTLESTAR", over_f, 4) + 16
    cn_over_f = font(NOTO_BOLD, 21)
    d.text((ox, 75), "·  汉字积木", font=cn_over_f, fill=c["red"], anchor="lt")

    d.text((112, 104), "LittleStar", font=font(POP_XB, 104), fill=c["ink"], anchor="lt")
    d.text((120, 248), "给海外华裔孩子的识字 App",
           font=font(NOTO_BOLD, 36), fill=c["ink"], anchor="lt")

    en_f = font(POP_MD, 21)
    d.text((122, 306), "A Chinese-literacy app for heritage kids",
           font=en_f, fill=c["muted"], anchor="lt")
    d.text((122, 336), "growing up in English-speaking homes.",
           font=en_f, fill=c["muted"], anchor="lt")

    # 标签
    badge_f = font(NOTO_BOLD, 20)
    x = 120
    x += pill(d, x, 394, "3–6 岁", badge_f, c["yellow"], c["on_yellow"], c["line"], c["shadow"]) + 12
    x += pill(d, x, 394, "完全离线", badge_f, c["card"], c["ink"], c["green"], c["shadow"]) + 12
    pill(d, x, 394, "汉字 + 文化", badge_f, c["card"], c["ink"], c["blue"], c["shadow"])

    # 底部小字
    foot_f = font(POP_SB, 16)
    tracked_text(d, (122, 504), "ANDROID  ·  OFFLINE  ·  NO ADS", foot_f, c["muted"], 3)

    # 右侧卡片 + 熊猫
    card_x, card_y, card_s = 1100, 70, 430
    d.rounded_rectangle((card_x + 12, card_y + 12, card_x + card_s + 12, card_y + card_s + 12),
                        radius=48, fill=c["shadow"])
    d.rounded_rectangle((card_x, card_y, card_x + card_s, card_y + card_s),
                        radius=48, fill=c["card"], outline=c["line"], width=6)
    panda = panda_image(360)
    img.paste(panda, (card_x + 35, card_y + 35), panda)

    # 四块汉字贴纸
    img.alpha_composite(tile("猫", 92, c["yellow"], c["on_yellow"], -6, c["shadow"]), (978, 84))
    img.alpha_composite(tile("狗", 88, c["green"], c["on_green"], 7, c["shadow"]), (1462, 76))
    img.alpha_composite(tile("鸟", 84, c["blue"], c["on_blue"], 5, c["shadow"]), (978, 382))
    img.alpha_composite(tile("鱼", 88, c["red"], c["on_red"], -5, c["shadow"]), (1468, 382))

    return img.convert("RGB")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    render(False).save(OUT / "banner.png", optimize=True)
    render(True).save(OUT / "banner_dark.png", optimize=True)
    print("wrote", OUT / "banner.png")
    print("wrote", OUT / "banner_dark.png")


if __name__ == "__main__":
    main()
