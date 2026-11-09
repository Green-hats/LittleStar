#!/usr/bin/env python3
"""内容校验：words.json / culture.json 与资源文件一一对应。
检查：ID、资源引用、词义与干扰项、歌词顺序和文化详情；
已替换素材的来源、授权、校验和、PNG 尺寸及字音对应关系。
用法：python3 tools/validate_content.py；任何一项失败以非零码退出。
CI 使用 --skip-rights-checks，只跳过版权来源/署名审核，保留技术完整性检查。
"""
import argparse
import hashlib
import html
import json
import re
import struct
import sys
import unicodedata
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
RES = ROOT / "app/src/main/res"

errors = []


def fail(msg: str) -> None:
    errors.append(msg)
    print(f"FAIL {msg}")


def drawable_exists(name: str) -> bool:
    return any(path.suffix in (".xml", ".png") for path in RES.glob(f"drawable*/{name}.*"))


def check_english(owner: str, field: str, value) -> None:
    if not isinstance(value, str) or not re.search(r"[A-Za-z]{2,}", value):
        fail(f"{owner}: 缺英文支持 {field}")


def check_words() -> None:
    data = json.loads((ASSETS / "content/words.json").read_text(encoding="utf-8"))
    seen = set()
    for theme in data["themes"]:
        check_english(theme["id"], "nameEn", theme.get("nameEn"))
        if not drawable_exists(theme.get("illustration", "")):
            fail(f"缺主题图: {theme['id']}")
        words = theme["words"]
        if len(words) != 6:
            fail(f"主题 {theme['id']} 有 {len(words)} 字，应为 6")
        for w in words:
            check_english(w["id"], "english", w.get("english"))
            if w["id"] in seen:
                fail(f"word id 重复: {w['id']}")
            seen.add(w["id"])
            if not (ASSETS / w["audio"]).exists():
                fail(f"缺音频: {w['audio']}")
            if not drawable_exists(w["image"]):
                fail(f"缺图: {w['image']}")
            if w["hanzi"] in w.get("distractors", []):
                fail(f"{w['id']}: distractors 含正确答案")
            if len(set(w.get("distractors", []))) != len(w.get("distractors", [])):
                fail(f"{w['id']}: distractors 有重复")


def check_culture() -> None:
    data = json.loads((ASSETS / "content/culture.json").read_text(encoding="utf-8"))
    cat_ids = {c["id"] for c in data["categories"]}
    seen = set()
    for category in data["categories"]:
        check_english(category["id"], "nameEn", category.get("nameEn"))
    for it in data["items"]:
        for field in ("titleEn", "subtitleEn", "summary", "learningFocus"):
            check_english(it["id"], field, it.get(field))
        if not it.get("lyrics"):
            check_english(it["id"], "transcriptEn", it.get("transcriptEn"))
        for index, line in enumerate(it.get("lyrics", [])):
            check_english(f"{it['id']}: line {index + 1}", "english", line.get("english"))
        for field in ("summary", "learningFocus", "transcript"):
            if not isinstance(it.get(field), str) or not it[field].strip():
                fail(f"{it['id']}: 缺详情字段 {field}")
        if it["id"] in seen:
            fail(f"culture id 重复: {it['id']}")
        seen.add(it["id"])
        if it["categoryId"] not in cat_ids:
            fail(f"{it['id']}: 未知 categoryId {it['categoryId']}")
        if not (ASSETS / it["audio"]).exists():
            fail(f"缺音频: {it['audio']}")
        if not drawable_exists(it["cover"]):
            fail(f"缺封面: {it['cover']}")
        starts = [l["startMs"] for l in it.get("lyrics", [])]
        if starts != sorted(starts):
            fail(f"{it['id']}: lyrics startMs 未递增")
        if it.get("durationSec", 0) <= 0:
            fail(f"{it['id']}: durationSec 非法")


def check_media(require_complete: bool = False, check_rights: bool = True) -> None:
    manifest = ASSETS / "content/media_sources.json"
    if not manifest.exists():
        fail("缺素材来源清单 media_sources.json")
        return
    themes = {theme["id"]: theme for theme in json.loads(
        (ASSETS / "content/words.json").read_text(encoding="utf-8"))["themes"]}
    words = {word["id"]: word for theme in themes.values() for word in theme["words"]}
    culture = {item["id"]: item for item in json.loads(
        (ASSETS / "content/culture.json").read_text(encoding="utf-8"))["items"]}
    seen = set()
    seen_paths = set()
    for entry in json.loads(manifest.read_text(encoding="utf-8"))["assets"]:
        is_culture = entry.get("kind") in ("culture-cover", "human-culture-audio")
        is_theme = entry.get("kind") == "theme-illustration"
        id_key = "themeId" if is_theme else "cultureId" if is_culture else "wordId"
        required = (id_key, "kind", "path", "sha256")
        if check_rights:
            required += ("sourceTitle", "sourcePage", "sourceUrl", "creator",
                         "license", "licenseUrl", "sourceSha256", "changes")
        if any(not isinstance(entry.get(key), str) or not entry[key].strip() for key in required):
            label = "素材来源" if check_rights else "素材技术信息"
            fail(f"不完整的{label}: {entry.get('path')}")
            continue
        key = (entry[id_key], entry["kind"])
        word = (themes if is_theme else culture if is_culture else words).get(entry[id_key])
        if key in seen or entry["path"] in seen_paths or word is None:
            fail(f"重复或未知内容素材: {key}")
            continue
        seen.add(key)
        seen_paths.add(entry["path"])
        if check_rights:
            if entry.get("reviewPending", False) is not False:
                fail(f"素材仍待审核: {entry['path']}")
            if entry["license"] not in ("CC0-1.0", "LicenseRef-Public-domain", "CC-BY-SA-3.0-US",
                                        "CC-BY-2.0-FR", "CC-BY-4.0", "CC-BY-SA-4.0", "LicenseRef-Pexels"):
                fail(f"未核验的素材许可: {entry['path']}")
            if entry["license"] == "LicenseRef-Pexels":
                source_page = urlsplit(entry["sourcePage"])
                if (entry["kind"] not in ("photo", "culture-cover")
                        or source_page.hostname != "www.pexels.com"
                        or not source_page.path.startswith("/photo/")
                        or urlsplit(entry["sourceUrl"]).hostname != "images.pexels.com"
                        or entry["licenseUrl"] != "https://www.pexels.com/license/"):
                    fail(f"Pexels 来源或许可信息不匹配: {entry['path']}")
            if entry["license"].startswith("CC-BY") or entry["license"] == "LicenseRef-Pexels":
                notice = RES / "values/strings.xml"
                credits = notice.read_text(encoding="utf-8") if notice.is_file() else ""
                if any(html.escape(entry[key], quote=False) not in credits
                       for key in ("creator", "sourcePage", "licenseUrl")):
                    fail(f"缺应用内素材署名: {entry['path']}")
            if any(not entry[key].startswith("https://") for key in ("sourcePage", "sourceUrl", "licenseUrl")):
                fail(f"素材来源不是 HTTPS: {entry['path']}")
        path = (ASSETS.parent / entry["path"]).resolve()
        if not path.is_relative_to(ASSETS.parent.resolve()) or not path.is_file():
            fail(f"缺素材或路径越界: {entry['path']}")
            continue
        data = path.read_bytes()
        checksum_fields = ("sourceSha256", "sha256") if check_rights else ("sha256",)
        if any(re.fullmatch(r"[0-9a-f]{64}", entry[key]) is None
               for key in checksum_fields):
            fail(f"素材 SHA-256 格式非法: {entry['path']}")
        if hashlib.sha256(data).hexdigest() != entry["sha256"]:
            fail(f"素材 SHA-256 不匹配: {entry['path']}")
        if entry["kind"] == "human-pronunciation":
            if (entry["path"] != "assets/" + word["audio"] or entry.get("language") != "cmn"
                    or entry.get("transcript") != word["hanzi"] or entry.get("pinyin") != word["pinyin"]):
                fail(f"发音信息与词条不匹配: {entry['wordId']}")
            if not data.startswith(b"OggS"):
                fail(f"字音不是 Ogg: {entry['path']}")
            source_text = entry.get("sourceTranscript")
            if not isinstance(source_text, str) or not source_text.strip():
                fail(f"缺录音原字: {entry['wordId']}")
            if entry.get("sourcePinyin") != word["pinyin"]:
                fail(f"来源拼音与目标不匹配: {entry['wordId']}")
            if "clip" in entry:
                clip = entry["clip"]
                valid = isinstance(clip, dict) and all(type(clip.get(field)) is int for field in (
                    "startMs", "endMs", "sourceDurationMs", "syllableIndex"))
                if valid:
                    text = clip.get("originalTranscript")
                    syllables = clip.get("originalSyllables")
                    index = clip["syllableIndex"]
                    valid = (0 <= clip["startMs"] < clip["endMs"] <= clip["sourceDurationMs"]
                             and isinstance(text, str) and isinstance(syllables, list)
                             and len(text) == len(syllables) and 0 <= index < len(text)
                             and all(isinstance(s, str) and s.strip() for s in syllables)
                             and text[index] == source_text
                             and syllables[index] == entry.get("sourcePinyin"))
                if not valid:
                    fail(f"录音剪辑范围或原始音节不匹配: {entry['wordId']}")
            if source_text != word["hanzi"]:
                note = entry.get("reuseNote")
                if (entry.get("reuse") not in ("homophone", "script-variant")
                        or not isinstance(note, str) or not note.strip()):
                    fail(f"缺同音或异体复用说明: {entry['wordId']}")
        elif entry["kind"] == "human-culture-audio":
            display_text = (entry.get("displayText")
                            if entry.get("transcriptRole") == "story-summary"
                            else entry.get("transcript"))
            if (entry["path"] != "assets/" + word["audio"] or entry.get("language") != "cmn"
                    or display_text != word["transcript"]):
                fail(f"文化音频与正文不匹配: {entry['cultureId']}")
            if not data.startswith(b"OggS"):
                fail(f"文化音频不是 Ogg: {entry['path']}")
        elif entry["kind"] in ("photo", "word-illustration", "theme-illustration", "culture-cover"):
            image = word["illustration"] if is_theme else word["cover"] if is_culture else word["image"]
            if image == "ill_placeholder":
                fail(f"配图仍为占位资源: {entry[id_key]}")
            if entry["path"] != f"res/drawable-nodpi/{image}.png":
                fail(f"配图资源引用不匹配: {entry[id_key]}")
            if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
                fail(f"配图不是 PNG: {entry['path']}")
                continue
            width, height = struct.unpack(">II", data[16:24])
            if min(width, height) < 512 or max(width, height) > 1024 or len(data) > 4 * 1024 * 1024:
                fail(f"配图尺寸或大小超出预算: {entry['path']}")
            number = (unicodedata.numeric(word["hanzi"], None)
                      if not is_culture and not is_theme and len(word["hanzi"]) == 1 else None)
            if number is not None:
                tiles = entry.get("composition")
                valid = (type(entry.get("objectCount")) is int and entry["objectCount"] == number
                         and isinstance(tiles, list) and len(tiles) == number)
                if valid:
                    valid = all(isinstance(t, dict) and all(type(t.get(k)) is int for k in (
                        "x", "y", "width", "height")) and t["x"] >= 0 and t["y"] >= 0
                        and t["width"] > 0 and t["height"] > 0
                        and t["x"] + t["width"] <= width and t["y"] + t["height"] <= height
                        for t in tiles)
                if valid:
                    valid = all(a["x"] + a["width"] <= b["x"] or b["x"] + b["width"] <= a["x"]
                                or a["y"] + a["height"] <= b["y"] or b["y"] + b["height"] <= a["y"]
                                for i, a in enumerate(tiles) for b in tiles[i+1:])
                if not valid:
                    fail(f"照片计数或拼图区域与数字不匹配: {entry[id_key]}")
            if len(list(RES.glob(f"drawable*/{image}.*"))) != 1:
                fail(f"配图仍有重复资源变体: {entry[id_key]}")
        else:
            fail(f"未知素材类型: {entry['kind']}")

    groups = (("字词配图（照片或插画）", ("photo", "word-illustration"), list(words)),
              ("主题图", ("theme-illustration",), list(themes)),
              ("真人字音", ("human-pronunciation",), list(words)),
              ("文化封面", ("culture-cover",), list(culture)),
              ("文化真人音频", ("human-culture-audio",), list(culture)))
    for label, kinds, ids in groups:
        missing = [item_id for item_id in ids if not any((item_id, kind) in seen for kind in kinds)]
        print(f"素材登记覆盖（不代表版权、真人或设备验收）: {label} {len(ids) - len(missing)}/{len(ids)}")
        if require_complete and missing:
            fail(f"未登记非占位素材 {label}: {', '.join(missing)}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-real-media", action="store_true",
                        help="要求全部主题、字词和文化内容有已登记的非占位配图及音频；插画不冒充照片")
    parser.add_argument("--skip-rights-checks", action="store_true",
                        help="跳过版权来源、许可及署名审核，仍检查资源、校验和与内容一致性")
    args = parser.parse_args()
    if args.skip_rights_checks:
        print("版权来源审核已跳过；技术检查通过不代表素材已获授权。")
    check_words()
    check_culture()
    check_media(require_complete=args.require_real_media, check_rights=not args.skip_rights_checks)
    if errors:
        print(f"\n{len(errors)} 个问题")
        return 1
    print("内容校验通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
