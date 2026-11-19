#!/usr/bin/env python3
"""对比度验收：解析固定浅色主题的颜色资源，复算 WCAG 对比度。
文字需达到 4.5:1，非文字边界需达到 3:1。用法：python3 tools/contrast_check.py
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"

PAIRS = [
    ("ls_ink", "ls_paper"), ("ls_ink", "ls_card"),
    ("ls_muted", "ls_paper"), ("ls_muted", "ls_card"),
    ("ls_on_red", "ls_red"), ("ls_on_yellow", "ls_yellow"),
    ("ls_on_blue", "ls_blue"), ("ls_on_mint", "ls_mint"),
    ("ls_ink", "ls_mint"),
    ("ls_red", "ls_paper"),      # 彩色描边文字（如 Quiz 入口）也要 ≥ 4.5
    ("sprout_on_plum", "sprout_plum"), ("sprout_on_lilac", "sprout_lilac"),
    ("sprout_on_lilac", "sprout_cloud"), ("sprout_on_lilac", "ls_card"),
    ("sprout_on_lilac", "sprout_dew"), ("sprout_on_lilac", "sprout_honey"),
    ("sprout_plum", "sprout_cloud"), ("sprout_plum", "sprout_lilac"),
    ("sprout_on_plum", "sprout_fern"), ("sprout_fern", "sprout_dew"),
    ("sprout_on_plum", "sprout_amber"), ("sprout_on_honey", "sprout_honey"),
    ("sprout_on_plum", "sprout_on_rose"), ("sprout_on_rose", "sprout_rose"),
    ("sprout_on_rose", "ls_card"), ("sprout_on_lilac", "sprout_rose"),
    ("sprout_muted", "sprout_cloud"), ("sprout_muted", "ls_card"),
    ("sprout_muted", "sprout_lilac"), ("sprout_muted", "sprout_dew"),
    ("sprout_muted", "sprout_honey"), ("sprout_muted", "sprout_rose"),
    ("sprout_fern", "sprout_lilac"), ("sprout_on_rose", "sprout_lilac"),
]

NON_TEXT_PAIRS = [
    ("sprout_outline", "ls_card"), ("sprout_outline", "sprout_cloud"),
    ("sprout_fern", "sprout_cloud"), ("sprout_on_rose", "sprout_cloud"),
]


def load_colors(path: Path) -> dict:
    tree = ET.parse(path)
    colors = {}
    for el in tree.getroot().findall("color"):
        value = (el.text or "").strip()
        if re.fullmatch(r"#[0-9A-Fa-f]{6}", value):
            colors[el.get("name")] = value
    return colors


def srgb_to_linear(c: float) -> float:
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def luminance(hex_color: str) -> float:
    r = srgb_to_linear(int(hex_color[1:3], 16) / 255)
    g = srgb_to_linear(int(hex_color[3:5], 16) / 255)
    b = srgb_to_linear(int(hex_color[5:7], 16) / 255)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(fg: str, bg: str) -> float:
    l1, l2 = luminance(fg), luminance(bg)
    hi, lo = max(l1, l2), min(l1, l2)
    return (hi + 0.05) / (lo + 0.05)


def check(name: str, colors: dict) -> bool:
    ok = True
    thresholds = [(pair, 4.5) for pair in PAIRS] + [(pair, 3.0) for pair in NON_TEXT_PAIRS]
    for (fg_name, bg_name), threshold in thresholds:
        fg, bg = colors.get(fg_name), colors.get(bg_name)
        if fg is None or bg is None:
            print(f"[{name}] 缺色值 {fg_name} 或 {bg_name}")
            ok = False
            continue
        ratio = contrast(fg, bg)
        status = "OK  " if ratio >= threshold else "FAIL"
        if ratio < threshold:
            ok = False
        print(f"[{name}] {status} {fg_name}/{bg_name}: {ratio:.2f}")
    # 提示：蓝色文字只能放卡片色
    if "ls_blue" in colors and "ls_card" in colors:
        print(f"[{name}] 参考 blue/card: {contrast(colors['ls_blue'], colors['ls_card']):.2f}")
    return ok


def main() -> int:
    ok = True
    colors = load_colors(RES / "values/colors.xml") | load_colors(RES / "values/sprout.xml")
    ok &= check("light", colors)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
