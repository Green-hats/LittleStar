import hashlib
import json
from pathlib import Path
import unittest

from check_culture_timing import ogg_duration


ASSETS = Path(__file__).resolve().parent.parent / "app/src/main/assets"


class CultureContentTest(unittest.TestCase):
    def setUp(self):
        self.items = json.loads((ASSETS / "content/culture.json").read_text())["items"]
        self.recordings = [entry for entry in json.loads(
            (ASSETS / "content/media_sources.json").read_text())["assets"]
            if entry["kind"] == "human-culture-audio"]

    def test_all_culture_audio_is_registered_with_matching_asset_hashes(self):
        self.assertCountEqual([item["id"] for item in self.items],
                              [entry["cultureId"] for entry in self.recordings])
        items = {item["id"]: item for item in self.items}
        for entry in self.recordings:
            with self.subTest(culture=entry["cultureId"]):
                item = items[entry["cultureId"]]
                self.assertEqual("assets/" + item["audio"], entry["path"])
                display = entry.get("displayText") if entry.get("transcriptRole") == "story-summary" else entry.get("transcript")
                self.assertEqual(item["transcript"], display)
                self.assertEqual(entry["sha256"], hashlib.sha256(
                    (ASSETS / item["audio"]).read_bytes()).hexdigest())

    def test_audio_duration_and_every_lyric_start_are_in_bounds(self):
        for item in self.items:
            with self.subTest(culture=item["id"]):
                duration = ogg_duration(ASSETS / item["audio"])
                self.assertLessEqual(abs(item["durationSec"] - duration), 0.5)
                starts = [line["startMs"] for line in item["lyrics"]]
                self.assertEqual(sorted(set(starts)), starts)
                self.assertTrue(all(type(start) is int and 0 <= start < duration * 1000
                                    for start in starts))

    def test_lyrics_have_matching_display_text_and_nonempty_reading_fields(self):
        for item in self.items:
            if not item["lyrics"]:
                continue
            with self.subTest(culture=item["id"]):
                self.assertEqual(item["transcript"], "\n".join(
                    line["hanzi"] for line in item["lyrics"]))
                self.assertTrue(all(isinstance(line.get(field), str) and line[field].strip()
                                    for line in item["lyrics"]
                                    for field in ("hanzi", "pinyin", "english")))

    def test_story_text_role_is_explicit_and_displayed_honestly(self):
        entries = {entry["cultureId"]: entry for entry in self.recordings}
        for item in self.items:
            if item["categoryId"] != "stories":
                continue
            with self.subTest(culture=item["id"]):
                role = entries.get(item["id"], {}).get("transcriptRole")
                self.assertIn(role, ("story-summary", "verbatim-transcript"))
                self.assertEqual(role == "story-summary", item["transcript"].startswith(
                    "故事梗概（非逐字字幕）\n"))
                if role == "story-summary":
                    self.assertNotIn("transcript", entries[item["id"]])


if __name__ == "__main__":
    unittest.main()
