#!/usr/bin/env python3
"""汉字子集字体：从 words.json / culture.json 提取所有汉字 + 常用 UI 字，
用 pyftsubset 生成 < 500KB 的 res/font/noto_sans_sc_hanzi.otf。
依赖：pip3 install fonttools brotli。新增内容后重跑本脚本。
用法：python3 tools/subset_font.py
"""
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
SRC_FONT = ROOT / "tools/fonts/NotoSansSC-Bold.otf"
OUT_FONT = ROOT / "app/src/main/res/font/noto_sans_sc_hanzi.otf"

# 教学区常用 UI 字（印章、单位、提示等）
EXTRA_CHARS = "会已学啦个主题个字今天天学完复习一下星儿歌故事古诗成语节日分钟秒听说读写看拼"


def collect_chars() -> str:
    chars = set(EXTRA_CHARS)
    words = json.loads((ASSETS / "content/words.json").read_text(encoding="utf-8"))
    for theme in words["themes"]:
        chars.update(theme["nameZh"])
        chars.update(theme["glyph"])
        for w in theme["words"]:
            chars.update(w["hanzi"])
            chars.update(w.get("distractors", []))
    culture = json.loads((ASSETS / "content/culture.json").read_text(encoding="utf-8"))
    for c in culture["categories"]:
        chars.update(c["nameZh"])
        chars.update(c["glyph"])
    for it in culture["items"]:
        chars.update(it["titleZh"])
        chars.update(it.get("subtitle", ""))
        chars.update(it["glyph"])
        chars.update(it.get("transcript", ""))
        chars.update(it.get("learningFocus", ""))
        for line in it.get("lyrics", []):
            chars.update(line["hanzi"])
    return "".join(sorted(chars))


def main() -> int:
    if not SRC_FONT.exists():
        print(f"缺源字体：{SRC_FONT}（从 noto-cjk 下载 NotoSansSC-Bold.otf 放到 tools/fonts/）")
        return 1
    chars = collect_chars()
    print(f"子集字符数：{len(chars)}")
    OUT_FONT.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([
        sys.executable, "-m", "fontTools.subset", str(SRC_FONT),
        f"--text={chars}",
        "--output-file=" + str(OUT_FONT),
        "--layout-features=*",
        "--notdef-outline",
        "--drop-tables+=DSIG",
    ], check=True)
    size_kb = OUT_FONT.stat().st_size // 1024
    print(f"输出：{OUT_FONT.name} {size_kb}KB")
    if size_kb > 500:
        print("警告：超过 500KB 目标")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
