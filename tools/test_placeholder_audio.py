from pathlib import Path
import re
import subprocess
import tempfile
import unittest


class PlaceholderAudioTest(unittest.TestCase):
    def test_generator_does_not_overwrite_existing_recordings(self):
        source = Path(__file__).with_name("make_placeholder_audio.sh").read_text()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "tools").mkdir()
            script = root / "tools/make_placeholder_audio.sh"
            script.write_text(source)
            paths = re.findall(r'gen "([^"\n]+\.ogg)"', source)
            self.assertGreater(len(paths), 0)
            for relative in paths:
                path = root / "app/src/main/assets/audio" / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b"real recording")
            # Existing files must be left alone without invoking the speech/encoding tools.
            prelude = 'say() { echo invoked-say; return 1; }; ffmpeg() { echo invoked-ffmpeg; return 1; }; export -f say ffmpeg; bash "$1"'
            result = subprocess.run(["bash", "-c", prelude, "test", str(script)],
                                    capture_output=True, text=True, timeout=10)
            self.assertNotIn("invoked-", result.stdout + result.stderr)
            self.assertEqual(0, result.returncode, result.stderr)
            for relative in paths:
                self.assertEqual(b"real recording", (root / "app/src/main/assets/audio" / relative).read_bytes())


if __name__ == "__main__":
    unittest.main()
