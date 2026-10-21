#!/usr/bin/env python3
"""把 assets/illustrations/panda_v3.svg / panda_sleep.svg 转成 VectorDrawable。

处理规则（工程技术文档 §8.8 + 用户任务）：
- 删掉纸白背景矩形与黄色圆贴（黄圆 + 墨圈由 BlockLayout / ShapeableImageView 提供）；
- SVG 的 <g transform="translate(90 109.6) scale(0.274223)"> 映射为 VectorDrawable 的
  <group translateX/translateY/scaleX/scaleY>，path 数据原样保留；
- panda_sleep.svg 的两个 <text>z</text> 用手写折线路径代替（VectorDrawable 不支持 text）；
- 颜色一律引用 @color/ls_panda_*（values 与 values-night 保持一致）。

用法：python3 tools/svg_to_vector.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT.parent / "assets" / "illustrations"
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable"

COLOR_MAP = {
    "#ffffff": "@color/ls_panda_white",
    "#211d18": "@color/ls_panda_ink",
    "#232323": "@color/ls_panda_patch",
    "#333333": "@color/ls_panda_nose",
    "#e6e6e6": "@color/ls_panda_shade",
    "#f28294": "@color/ls_panda_blush",
    "#6e6252": "@color/ls_panda_zzz",
}

HEADER = (
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    android:width="120dp"\n'
    '    android:height="120dp"\n'
    '    android:viewportWidth="480"\n'
    '    android:viewportHeight="480">\n'
)
GROUP_OPEN = (
    '    <group\n'
    '        android:translateX="90"\n'
    '        android:translateY="109.6"\n'
    '        android:scaleX="0.274223"\n'
    '        android:scaleY="0.274223">\n'
)


def color_of(value: str) -> str:
    mapped = COLOR_MAP.get(value.lower())
    if mapped is None:
        raise SystemExit(f"未映射的颜色 {value}，请先加进 colors.xml 与 COLOR_MAP")
    return mapped


def extract_group_paths(svg: str) -> str:
    """取 <g transform=...> 内的全部 <path>，生成 VectorDrawable path 元素。"""
    m = re.search(r'<g transform="[^"]*">(.*?)</g>', svg, re.S)
    if not m:
        raise SystemExit("找不到带 transform 的 <g>")
    body = m.group(1)
    lines = []
    for tag in re.finditer(r"<path\b[^>]*>", body):
        attrs = tag.group(0)
        d = re.search(r'd="([^"]*)"', attrs).group(1)
        fill = re.search(r'fill="([^"]*)"', attrs)
        stroke = re.search(r'stroke="([^"]*)"', attrs)
        stroke_w = re.search(r'stroke-width="([^"]*)"', attrs)
        lines.append("        <path")
        if fill and fill.group(1).lower() != "none":
            lines.append(f'            android:fillColor="{color_of(fill.group(1))}"')
        if stroke:
            lines.append(f'            android:strokeColor="{color_of(stroke.group(1))}"')
            lines.append(f'            android:strokeWidth="{stroke_w.group(1)}"')
            lines.append('            android:strokeLineCap="round"')
            lines.append('            android:strokeLineJoin="round"')
        lines.append(f'            android:pathData="{d}" />')
    return "\n".join(lines)


# panda_sleep 的两个 <text>z</text>（Poppins 800, 60sp @ (324,154) / 42sp @ (360,112)）
# 手写折线： lowercase z = 横-斜-横。
ZZ_PATHS = """    <!-- 睡着的 z z：SVG 里是 <text>，VectorDrawable 不支持，用折线路径代替 -->
    <path
        android:strokeColor="@color/ls_panda_zzz"
        android:strokeWidth="9"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M326,124 L358,124 L326,154 L358,154" />
    <path
        android:strokeColor="@color/ls_panda_zzz"
        android:strokeWidth="7"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M362,91 L384,91 L362,112 L384,112" />
"""


def convert(src_name: str, out_name: str, extra: str = "") -> None:
    svg = (ASSETS / src_name).read_text(encoding="utf-8")
    # 校验背景矩形和黄圆确实被跳过（防呆：如果源文件结构变了要人工复核）
    if '<rect x="0.0" y="0.0" width="480.0" height="480.0"' not in svg:
        raise SystemExit(f"{src_name}: 背景矩形不见了，请人工复核")
    if '<circle cx="240.0" cy="250.0" r="168.0"' not in svg:
        raise SystemExit(f"{src_name}: 黄色圆贴不见了，请人工复核")
    xml = HEADER + GROUP_OPEN + extract_group_paths(svg) + "\n    </group>\n" + extra + "</vector>\n"
    (OUT / out_name).write_text(xml, encoding="utf-8")
    print(f"写入 {OUT / out_name}（{len(xml)} 字节）")


if __name__ == "__main__":
    convert("panda_v3.svg", "ic_panda.xml")
    convert("panda_sleep.svg", "ic_panda_sleep.xml", extra=ZZ_PATHS)
    print("完成")
