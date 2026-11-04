import importlib.util
import json
import os
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "release_version", Path(__file__).with_name("verify-release-version.py")
)
release_version = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release_version)


class ReleaseVersionTest(unittest.TestCase):
    def test_parses_android_versions(self):
        self.assertEqual(release_version.read_version(
            '    versionCode = 8\n    versionName = "8"\n'
        ), ("8", 8))

    def test_invalid_versions_fail(self):
        with self.assertRaises(ValueError):
            release_version.read_version('versionName = "8"')

    def test_both_versions_must_increase(self):
        release_version.check_version(("10", 10), ("9", 9))
        release_version.check_version(("1.10", 10), ("1.9", 9))
        for current in [("9", 10), ("8", 10), ("10", 9), ("10", 8)]:
            with self.subTest(current=current), self.assertRaises(ValueError):
                release_version.check_version(current, ("9", 9))

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": "owner/repo"})
    @patch.object(Path, "read_text", return_value='versionCode = 8\nversionName = "8"')
    def test_first_release_and_draft_retry(self, read_text):
        with patch.object(subprocess, "check_output", return_value=json.dumps([
            [{"tag_name": "v8", "draft": True}], []
        ])) as command:
            release_version.main()
            self.assertEqual(command.call_count, 1)

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": "owner/repo"})
    @patch.object(Path, "read_text", return_value='versionCode = 8\nversionName = "8"')
    def test_checks_published_releases_across_pages(self, read_text):
        pages = [[{"tag_name": "v7", "draft": False}],
                 [{"tag_name": "v9", "draft": False}]]
        with patch.object(subprocess, "check_output", side_effect=[
            json.dumps(pages), 'versionCode = 7\nversionName = "7"',
            '7\n8\n', 'versionCode = 9\nversionName = "9"'
        ]), self.assertRaises(ValueError):
            release_version.main()

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": "owner/repo"})
    @patch.object(Path, "read_text", return_value='versionCode = 8\nversionName = "8"')
    def test_api_failure_does_not_skip_validation(self, read_text):
        with patch.object(subprocess, "check_output", side_effect=
                          subprocess.CalledProcessError(1, "gh")):
            with self.assertRaises(subprocess.CalledProcessError):
                release_version.main()


if __name__ == "__main__":
    unittest.main()
