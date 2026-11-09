#!/usr/bin/env python3
"""Compare timed lyrics with real Ogg Vorbis audio length, without extra dependencies.
Default is a strict quality gate; --report-only lists existing problems without failing.
This checks metadata, not the audible alignment of words.
"""
import argparse
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"

def ogg_duration(path):
    data = path.read_bytes()
    pos, rate, granule = 0, None, 0
    while pos < len(data):
        if data[pos:pos+4] != b"OggS" or len(data)-pos < 27:
            raise ValueError("invalid Ogg page")
        count = data[pos+26]
        table = data[pos+27:pos+27+count]
        if len(table) != count:
            raise ValueError("truncated segment table")
        end = pos + 27 + count + sum(table)
        if end > len(data):
            raise ValueError("truncated Ogg payload")
        payload = data[pos+27+count:end]
        if payload.startswith(b"\x01vorbis"):
            rate = struct.unpack_from("<I", payload, 12)[0]
        page_granule = struct.unpack_from("<Q", data, pos+6)[0]
        if page_granule != 2**64-1:
            granule = max(granule, page_granule)
        pos = end
    if not rate or granule <= 0:
        raise ValueError("missing Vorbis sample rate or duration")
    return granule / rate

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report-only", action="store_true")
    args = parser.parse_args()
    data = json.loads((ASSETS / "content/culture.json").read_text(encoding="utf-8"))
    problems = []
    affected = set()
    for item in data["items"]:
        try:
            duration = ogg_duration(ASSETS / item["audio"])
        except (OSError, ValueError, struct.error) as error:
            problems.append(f"{item['id']}: {error}"); affected.add(item["id"]); continue
        previous = -1
        for line, lyric in enumerate(item.get("lyrics", []), 1):
            start = lyric.get("startMs")
            valid = isinstance(start, (int, float)) and not isinstance(start, bool)
            if not valid or not 0 <= start < duration * 1000 or start <= previous:
                problems.append(f"{item['id']} line {line}: startMs={start}; audio={duration:.3f}s")
                affected.add(item["id"])
            if valid: previous = start
    for problem in problems: print("FAIL " + problem)
    print(f"Timing check: {len(problems)} problem(s) in {len(affected)} item(s)")
    return 1 if problems and not args.report_only else 0

if __name__ == "__main__":
    raise SystemExit(main())
