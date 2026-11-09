import hashlib
import json
import struct
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import validate_content as validator


class MediaValidationTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.main = Path(self.tmp.name)
        self.assets = self.main / "assets"
        self.res = self.main / "res"
        (self.assets / "content").mkdir(parents=True)
        (self.assets / "audio/words").mkdir(parents=True)
        (self.res / "drawable-nodpi").mkdir(parents=True)
        words = []
        for n in range(6):
            words.append({"id": str(n), "hanzi": "猫", "pinyin": "māo",
                          "audio": f"audio/words/{n}.ogg", "image": f"ill_word_{n}", "english": "cat"})
            (self.assets / f"audio/words/{n}.ogg").write_bytes(b"OggS fixture")
            (self.res / f"drawable-nodpi/ill_word_{n}.png").touch()
        (self.assets / "content/words.json").write_text(json.dumps({
            "themes": [{"id": "animals", "nameEn": "Animals", "illustration": "ill_theme_animals",
                        "words": words}],
        }))
        (self.res / "drawable-nodpi/ill_theme_animals.png").touch()
        (self.assets / "content/culture.json").write_text(json.dumps({"items": [
            {"id": "poem", "cover": "ill_culture_poem", "audio": "audio/culture/poem.ogg",
             "transcript": "鹅鹅鹅"},
        ]}))
        self.entry = {
            "wordId": "0", "kind": "human-pronunciation", "path": "assets/audio/words/0.ogg",
            "sourceTitle": "Recording of 猫", "sourcePage": "https://example.org/recording",
            "sourceUrl": "https://example.org/recording.ogg", "creator": "Speaker",
            "license": "CC0-1.0", "licenseUrl": "https://creativecommons.org/publicdomain/zero/1.0/",
            "sourceSha256": hashlib.sha256(b"OggS fixture").hexdigest(),
            "sha256": hashlib.sha256(b"OggS fixture").hexdigest(), "changes": "Unmodified",
            "language": "cmn", "transcript": "猫", "pinyin": "māo",
            "sourceTranscript": "猫", "sourcePinyin": "māo", "reuse": "exact",
        }
        self.patches = [patch.object(validator, "ASSETS", self.assets),
                        patch.object(validator, "RES", self.res)]
        for p in self.patches:
            p.start()
        validator.errors.clear()

    def tearDown(self):
        for p in reversed(self.patches):
            p.stop()
        validator.errors.clear()
        self.tmp.cleanup()

    def save_manifest(self, entries=None):
        (self.assets / "content/media_sources.json").write_text(json.dumps({
            "version": 1, "assets": [self.entry] if entries is None else entries,
        }))

    def test_word_images_can_resolve_from_nodpi(self):
        validator.check_words()
        self.assertEqual([], validator.errors)

    def test_missing_theme_illustration_is_rejected(self):
        (self.res / "drawable-nodpi/ill_theme_animals.png").unlink()
        with patch("builtins.print"):
            validator.check_words()
        self.assertTrue(any("缺主题图" in error for error in validator.errors))

    def test_changed_audio_requires_updated_checksum(self):
        self.save_manifest()
        (self.assets / "audio/words/0.ogg").write_bytes(b"OggS changed")
        with patch("builtins.print"):
            validator.check_media()
        self.assertTrue(any("SHA-256" in error for error in validator.errors))

    def test_recording_transcript_must_match_word(self):
        self.entry["transcript"] = "狗"
        self.save_manifest()
        with patch("builtins.print"):
            validator.check_media()
        self.assertTrue(any("发音信息" in error for error in validator.errors))

    def test_licensed_unchanged_recording_is_accepted(self):
        self.save_manifest()
        validator.check_media()
        self.assertEqual([], validator.errors)

    def test_skip_rights_keeps_technical_media_checks(self):
        entry = dict(self.entry, sourceTitle="", sourcePage="", sourceUrl="",
                     creator="", license="NOASSERTION", licenseUrl=None,
                     sourceSha256="", changes="", reviewPending=True)
        self.save_manifest([entry])
        with patch("builtins.print"):
            validator.check_media(check_rights=False)
        self.assertEqual([], validator.errors)
        (self.assets / "audio/words/0.ogg").write_bytes(b"OggS changed")
        with patch("builtins.print"):
            validator.check_media(check_rights=False)
        self.assertTrue(any("SHA-256" in error for error in validator.errors))

    def test_attribution_license_requires_packaged_credit(self):
        self.entry["license"] = "CC-BY-4.0"
        self.entry["licenseUrl"] = "https://creativecommons.org/licenses/by/4.0/"
        self.save_manifest()
        with patch("builtins.print"):
            validator.check_media()
        self.assertTrue(any("署名" in error for error in validator.errors))

    def test_strict_mode_reports_words_and_culture_not_in_manifest(self):
        self.save_manifest()
        with patch("builtins.print"):
            validator.check_media(require_complete=True)
        self.assertTrue(any("未登记非占位素材" in error and "poem" in error for error in validator.errors))
        self.assertTrue(any("未登记非占位素材" in error and "字词配图" in error for error in validator.errors))

    def test_source_checksum_must_be_sha256(self):
        self.entry["sourceSha256"] = "not-a-checksum"
        self.save_manifest()
        with patch("builtins.print"):
            validator.check_media()
        self.assertTrue(any("SHA-256 格式" in error for error in validator.errors))

    def test_different_source_character_requires_disclosure(self):
        self.entry["sourceTranscript"] = "锚"
        self.save_manifest()
        with patch("builtins.print"):
            validator.check_media()
        self.assertTrue(any("同音或异体复用说明" in error for error in validator.errors))

    def test_disclosed_same_tone_homophone_is_accepted(self):
        self.entry.update(sourceTranscript="锚", reuse="homophone", reuseNote="Source 锚 and target 猫 are māo.")
        self.save_manifest()
        validator.check_media()
        self.assertEqual([], validator.errors)

    def test_different_tone_cannot_be_used_as_homophone(self):
        self.entry.update(sourceTranscript="毛", sourcePinyin="máo", reuse="homophone", reuseNote="Different source")
        self.save_manifest()
        with patch("builtins.print"):
            validator.check_media()
        self.assertTrue(any("来源拼音与目标不匹配" in error for error in validator.errors))

    def test_source_character_is_required_even_if_target_is_correct(self):
        del self.entry["sourceTranscript"]
        self.save_manifest()
        with patch("builtins.print"):
            validator.check_media()
        self.assertTrue(any("缺录音原字" in error for error in validator.errors))

    def test_culture_audio_transcript_must_match_existing_text(self):
        (self.assets / "audio/culture").mkdir()
        (self.assets / "audio/culture/poem.ogg").write_bytes(b"OggS fixture")
        entry = dict(self.entry, cultureId="poem", kind="human-culture-audio",
                     path="assets/audio/culture/poem.ogg", transcript="其他文字")
        del entry["wordId"]
        self.save_manifest([entry])
        with patch("builtins.print"):
            validator.check_media()
        self.assertTrue(any("文化音频与正文不匹配" in error for error in validator.errors))

    def test_pending_review_cannot_count_as_finished_media(self):
        self.entry["reviewPending"] = True
        self.save_manifest()
        with patch("builtins.print"):
            validator.check_media()
        self.assertTrue(any("待审核" in error for error in validator.errors))

    def test_clip_requires_valid_bounds_and_matching_source_syllable(self):
        clip = {"startMs": 100, "endMs": 500, "sourceDurationMs": 900,
                "syllableIndex": 0, "originalTranscript": "猫狗",
                "originalSyllables": ["māo", "gǒu"]}
        for change in ({"endMs": 1000}, {"startMs": -1}, {"syllableIndex": 1},
                       {"originalSyllables": ["máo", "gǒu"]}, {"endMs": True}):
            with self.subTest(change=change):
                validator.errors.clear()
                self.entry["clip"] = dict(clip, **change)
                self.save_manifest()
                with patch("builtins.print"):
                    validator.check_media()
                self.assertTrue(any("剪辑" in error for error in validator.errors))

    def test_disclosed_matching_clip_is_accepted(self):
        self.entry["clip"] = {"startMs": 100, "endMs": 500, "sourceDurationMs": 900,
                              "syllableIndex": 0, "originalTranscript": "猫猫",
                              "originalSyllables": ["māo", "mao"]}
        self.save_manifest()
        validator.check_media()
        self.assertEqual([], validator.errors)

    def photo_entry(self):
        data = b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR" + struct.pack(">II", 512, 512)
        (self.res / "drawable-nodpi/ill_word_0.png").write_bytes(data)
        return dict(self.entry, kind="photo", path="res/drawable-nodpi/ill_word_0.png",
                    sha256=hashlib.sha256(data).hexdigest())

    def test_pexels_requires_canonical_source_and_packaged_credit(self):
        self.entry = dict(self.photo_entry(), license="LicenseRef-Pexels",
                          sourcePage="https://www.pexels.com/photo/example-123/",
                          sourceUrl="https://images.pexels.com/photos/123/example.jpeg",
                          licenseUrl="https://www.pexels.com/license/")
        (self.res / "values").mkdir()
        (self.res / "values/strings.xml").write_text(
            "Speaker https://www.pexels.com/photo/example-123/ https://www.pexels.com/license/")
        self.save_manifest()
        validator.check_media()
        self.assertEqual([], validator.errors)
        self.entry["sourceUrl"] = "https://images.pexels.com.example.org/photo.jpeg"
        self.save_manifest()
        with patch("builtins.print"):
            validator.check_media()
        self.assertTrue(any("Pexels" in error for error in validator.errors))

    def test_word_illustration_obeys_image_integrity_checks(self):
        self.entry = dict(self.photo_entry(), kind="word-illustration")
        self.save_manifest()
        validator.check_media()
        self.assertEqual([], validator.errors)
        (self.res / "drawable-nodpi/ill_word_0.png").write_bytes(b"broken PNG")
        with patch("builtins.print"):
            validator.check_media()
        self.assertTrue(any("SHA-256" in error for error in validator.errors))
        self.assertTrue(any("不是 PNG" in error for error in validator.errors))

    def test_theme_illustration_must_match_theme_and_image_reference(self):
        photo = self.photo_entry()
        data = (self.res / "drawable-nodpi/ill_word_0.png").read_bytes()
        (self.res / "drawable-nodpi/ill_theme_animals.png").write_bytes(data)
        self.entry = dict(photo, kind="theme-illustration", themeId="animals",
                          path="res/drawable-nodpi/ill_theme_animals.png")
        del self.entry["wordId"]
        self.save_manifest()
        validator.check_media()
        self.assertEqual([], validator.errors)
        for change in ({"themeId": "unknown"}, {"path": "res/drawable-nodpi/ill_word_0.png"}):
            with self.subTest(change=change):
                validator.errors.clear()
                self.save_manifest([dict(self.entry, **change)])
                with patch("builtins.print"):
                    validator.check_media()
                self.assertTrue(validator.errors)

    def test_placeholder_cannot_be_registered_as_a_theme_illustration(self):
        photo = self.photo_entry()
        words_path = self.assets / "content/words.json"
        words = json.loads(words_path.read_text())
        words["themes"][0]["illustration"] = "ill_placeholder"
        words_path.write_text(json.dumps(words))
        path = self.res / "drawable-nodpi/ill_placeholder.png"
        path.write_bytes((self.res / "drawable-nodpi/ill_word_0.png").read_bytes())
        theme = dict(photo, kind="theme-illustration", themeId="animals",
                     path="res/drawable-nodpi/ill_placeholder.png")
        del theme["wordId"]
        self.save_manifest([theme])
        with patch("builtins.print"):
            validator.check_media(check_rights=False)
        self.assertTrue(any("仍为占位" in error for error in validator.errors))

    def test_photo_and_illustration_cannot_claim_the_same_path_twice(self):
        photo = self.photo_entry()
        self.save_manifest([photo, dict(photo, kind="word-illustration")])
        with patch("builtins.print"):
            validator.check_media()
        self.assertTrue(any("重复" in error for error in validator.errors))

    def test_number_photo_requires_matching_count_and_non_overlapping_tiles(self):
        path = self.assets / "content/words.json"
        words = json.loads(path.read_text())
        words["themes"][0]["words"][0]["hanzi"] = "二"
        path.write_text(json.dumps(words))
        self.entry = dict(self.photo_entry(), objectCount=2, composition=[
            {"x": 0, "y": 128, "width": 256, "height": 256},
            {"x": 256, "y": 128, "width": 256, "height": 256}])
        self.save_manifest()
        validator.check_media()
        self.assertEqual([], validator.errors)
        for change in ({"objectCount": 1}, {"objectCount": True}, {"composition": []},
                       {"composition": [self.entry["composition"][0]] * 2}):
            with self.subTest(change=change):
                validator.errors.clear()
                self.save_manifest([dict(self.entry, **change)])
                with patch("builtins.print"):
                    validator.check_media()
                self.assertTrue(any("计数" in error for error in validator.errors))

    def test_strict_mode_accepts_complete_themes_words_and_culture(self):
        png_header = b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR" + struct.pack(">II", 512, 512)
        entries = []
        for n in range(6):
            entries.append(dict(self.entry, wordId=str(n), path=f"assets/audio/words/{n}.ogg"))
            (self.res / f"drawable-nodpi/ill_word_{n}.png").write_bytes(png_header)
            entries.append(dict(self.entry, wordId=str(n), kind="photo",
                                path=f"res/drawable-nodpi/ill_word_{n}.png",
                                sha256=hashlib.sha256(png_header).hexdigest()))
        (self.res / "drawable-nodpi/ill_culture_poem.png").write_bytes(png_header)
        cover = dict(self.entry, cultureId="poem", kind="culture-cover",
                     path="res/drawable-nodpi/ill_culture_poem.png",
                     sha256=hashlib.sha256(png_header).hexdigest())
        del cover["wordId"]
        (self.assets / "audio/culture").mkdir()
        (self.assets / "audio/culture/poem.ogg").write_bytes(b"OggS fixture")
        audio = dict(self.entry, cultureId="poem", kind="human-culture-audio",
                     path="assets/audio/culture/poem.ogg", transcript="鹅鹅鹅")
        del audio["wordId"]
        (self.res / "drawable-nodpi/ill_theme_animals.png").write_bytes(png_header)
        theme = dict(cover, kind="theme-illustration", themeId="animals",
                     path="res/drawable-nodpi/ill_theme_animals.png")
        del theme["cultureId"]
        entries[1]["kind"] = "word-illustration"
        self.save_manifest(entries + [cover, audio, theme])
        with patch("builtins.print"):
            validator.check_media(require_complete=True)
        self.assertEqual([], validator.errors)

    def test_unverified_culture_license_is_not_treated_as_licensed(self):
        (self.assets / "audio/culture").mkdir()
        (self.assets / "audio/culture/poem.ogg").write_bytes(b"OggS fixture")
        audio = dict(self.entry, cultureId="poem", kind="human-culture-audio",
                     path="assets/audio/culture/poem.ogg", transcript="鹅鹅鹅",
                     license="NOASSERTION", licenseUrl=None, reviewPending=True)
        del audio["wordId"]
        self.save_manifest([audio])
        with patch("builtins.print"):
            validator.check_media()
        self.assertTrue(validator.errors)

    def test_story_summary_culture_audio_uses_display_text(self):
        (self.assets / "audio/culture").mkdir()
        (self.assets / "audio/culture/poem.ogg").write_bytes(b"OggS fixture")
        culture = json.loads((self.assets / "content/culture.json").read_text())
        culture["items"][0]["transcript"] = "故事梗概（非逐字字幕）\n\n一只鹅。"
        (self.assets / "content/culture.json").write_text(json.dumps(culture))
        audio = dict(self.entry, cultureId="poem", kind="human-culture-audio",
                     path="assets/audio/culture/poem.ogg",
                     displayText=culture["items"][0]["transcript"],
                     transcriptRole="story-summary")
        del audio["wordId"]
        del audio["transcript"]
        self.save_manifest([audio])
        validator.check_media(check_rights=False)
        self.assertEqual([], validator.errors)

    def test_culture_recording_must_match_transcript_and_asset_path(self):
        (self.assets / "audio/culture").mkdir()
        (self.assets / "audio/culture/poem.ogg").write_bytes(b"OggS fixture")
        audio = dict(self.entry, cultureId="poem", kind="human-culture-audio",
                     path="assets/audio/culture/poem.ogg", transcript="鹅鹅鹅")
        del audio["wordId"]
        for change in ({"transcript": "鹅鹅"}, {"path": "assets/audio/words/0.ogg"}):
            with self.subTest(change=change):
                validator.errors.clear()
                self.save_manifest([dict(audio, **change)])
                with patch("builtins.print"):
                    validator.check_media()
                self.assertTrue(any("文化音频与正文不匹配" in error
                                    for error in validator.errors))


if __name__ == "__main__":
    unittest.main()
