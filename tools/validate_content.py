#!/usr/bin/env python3
"""内容校验：words.json / culture.json 与资源文件一一对应。
检查：ID 唯一；每个 audio 文件存在；每个 image/cover 在 res/drawable 里有同名文件；
distractors 里的字不等于正确答案；每主题恰好 6 字；歌词 startMs 递增。
用法：python3 tools/validate_content.py；任何一项失败以非零码退出。
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
DRAWABLE = ROOT / "app/src/main/res/drawable"

errors = []


def fail(msg: str) -> None:
    errors.append(msg)
    print(f"FAIL {msg}")


def check_words() -> None:
    data = json.loads((ASSETS / "content/words.json").read_text(encoding="utf-8"))
    seen = set()
    for theme in data["themes"]:
        words = theme["words"]
        if len(words) != 6:
            fail(f"主题 {theme['id']} 有 {len(words)} 字，应为 6")
        for w in words:
            if w["id"] in seen:
                fail(f"word id 重复: {w['id']}")
            seen.add(w["id"])
            if not (ASSETS / w["audio"]).exists():
                fail(f"缺音频: {w['audio']}")
            if not (DRAWABLE / f"{w['image']}.xml").exists() and not (DRAWABLE / f"{w['image']}.png").exists():
                fail(f"缺图: {w['image']}")
            if w["hanzi"] in w.get("distractors", []):
                fail(f"{w['id']}: distractors 含正确答案")
            if len(set(w.get("distractors", []))) != len(w.get("distractors", [])):
                fail(f"{w['id']}: distractors 有重复")


def check_culture() -> None:
    data = json.loads((ASSETS / "content/culture.json").read_text(encoding="utf-8"))
    cat_ids = {c["id"] for c in data["categories"]}
    seen = set()
    for it in data["items"]:
        if it["id"] in seen:
            fail(f"culture id 重复: {it['id']}")
        seen.add(it["id"])
        if it["categoryId"] not in cat_ids:
            fail(f"{it['id']}: 未知 categoryId {it['categoryId']}")
        if not (ASSETS / it["audio"]).exists():
            fail(f"缺音频: {it['audio']}")
        if not (DRAWABLE / f"{it['cover']}.xml").exists() and not (DRAWABLE / f"{it['cover']}.png").exists():
            fail(f"缺封面: {it['cover']}")
        starts = [l["startMs"] for l in it.get("lyrics", [])]
        if starts != sorted(starts):
            fail(f"{it['id']}: lyrics startMs 未递增")
        if it.get("durationSec", 0) <= 0:
            fail(f"{it['id']}: durationSec 非法")


def main() -> int:
    check_words()
    check_culture()
    if errors:
        print(f"\n{len(errors)} 个问题")
        return 1
    print("内容校验通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
