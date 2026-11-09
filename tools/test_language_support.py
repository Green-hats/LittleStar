import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import validate_content as validator


class EnglishScaffoldingTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.assets = Path(self.tmp.name)
        (self.assets / "content").mkdir()
        (self.assets / "sound.ogg").touch()
        self.item = {
            "id": "song", "categoryId": "songs", "titleEn": "Two Tigers",
            "subtitleEn": "Song", "summary": "Meet two tigers.",
            "learningFocus": "Listen for 虎 (tiger).", "transcript": "两只老虎",
            "audio": "sound.ogg", "cover": "cover", "durationSec": 10,
            "lyrics": [{"startMs": 0, "hanzi": "两只老虎", "pinyin": "liǎng zhī lǎo hǔ",
                        "english": "Two tigers"}],
        }
        self.category = {"id": "songs", "nameEn": "Songs"}
        self.theme = {"id": "animals", "nameEn": "Animals", "illustration": "ill_theme_animals", "words": [
            {"id": str(i), "hanzi": "猫", "english": "cat", "audio": "sound.ogg",
             "image": "cover", "distractors": []} for i in range(6)
        ]}
        self.patches = [patch.object(validator, "ASSETS", self.assets),
                        patch.object(validator, "drawable_exists", return_value=True),
                        patch("builtins.print")]
        for p in self.patches:
            p.start()
        validator.errors.clear()

    def tearDown(self):
        for p in reversed(self.patches):
            p.stop()
        validator.errors.clear()
        self.tmp.cleanup()

    def check_culture(self):
        (self.assets / "content/culture.json").write_text(json.dumps({
            "categories": [self.category], "items": [self.item],
        }), encoding="utf-8")
        validator.check_culture()
        return validator.errors

    def check_words(self):
        (self.assets / "content/words.json").write_text(json.dumps({
            "themes": [self.theme],
        }), encoding="utf-8")
        validator.check_words()
        return validator.errors

    def test_valid_trilingual_song_passes(self):
        self.assertEqual([], self.check_culture())

    def test_title_requires_english_not_just_chinese(self):
        for value in (None, "", "  ", "两只老虎"):
            with self.subTest(value=value):
                validator.errors.clear()
                self.item["titleEn"] = value
                self.assertTrue(any("titleEn" in e for e in self.check_culture()))

    def test_subtitle_requires_english_type_or_author(self):
        self.item["subtitleEn"] = "古诗 · 李白"
        self.assertTrue(any("subtitleEn" in e for e in self.check_culture()))

    def test_summary_cannot_be_chinese_only(self):
        self.item["summary"] = "两只老虎。"
        self.assertTrue(any("summary" in e for e in self.check_culture()))

    def test_learning_focus_requires_english_explanation(self):
        self.item["learningFocus"] = "听一听。"
        self.assertTrue(any("learningFocus" in e for e in self.check_culture()))

    def test_story_requires_english_reading_support(self):
        self.item["lyrics"] = []
        self.assertTrue(any("transcriptEn" in e for e in self.check_culture()))

    def test_story_with_both_summaries_passes_without_lyrics(self):
        self.item["lyrics"] = []
        self.item["transcriptEn"] = "Story summary, not word-for-word subtitles."
        self.assertEqual([], self.check_culture())

    def test_each_lyric_needs_english_meaning(self):
        del self.item["lyrics"][0]["english"]
        self.assertTrue(any("line 1" in e for e in self.check_culture()))

    def test_category_requires_english_navigation(self):
        self.category["nameEn"] = "儿歌"
        self.assertTrue(any("nameEn" in e for e in self.check_culture()))

    def test_valid_words_keep_english_meanings(self):
        self.assertEqual([], self.check_words())

    def test_theme_requires_english_navigation(self):
        del self.theme["nameEn"]
        self.assertTrue(any("nameEn" in e for e in self.check_words()))

    def test_word_requires_english_meaning(self):
        self.theme["words"][0]["english"] = "猫"
        self.assertTrue(any("english" in e for e in self.check_words()))


if __name__ == "__main__":
    unittest.main()
