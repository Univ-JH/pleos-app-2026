"""Protect the required CI check from incorrectly skipping Android changes."""

from pathlib import Path
import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from ci_changes import is_documentation, should_build


class DocumentationPolicyTests(unittest.TestCase):
    def test_only_known_documentation_paths_can_skip(self):
        for path in ["README.md", "docs/SETUP.md", "docs/images/screen.png", "docs/images/nested/screen.webp"]:
            with self.subTest(path=path):
                self.assertTrue(is_documentation(path))
        for path in [
            "app/src/main/MainActivity.kt", "app/src/test/ExampleTest.kt", "gradle.properties",
            "gradle/libs.versions.toml", "gradlew", ".github/workflows/android.yml",
            ".github/scripts/ci_changes.py", "settings.gradle.kts", "future-module/build.gradle.kts",
            "docs/build.gradle.kts", "docs/images/script.py", "app/README.md", ".gitattributes",
        ]:
            with self.subTest(path=path):
                self.assertFalse(is_documentation(path))


class GitChangesTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name) / "repo"
        self.repo.mkdir()
        self.git("init", "-b", "main")
        self.git("config", "user.name", "CI test")
        self.git("config", "user.email", "ci@example.invalid")
        self.write("README.md", "Initial docs\n")
        self.write("app/code.kt", "Initial code\n")
        self.base = self.commit()

    def git(self, *args):
        return subprocess.run(
            ["git", *args], cwd=self.repo, check=True, capture_output=True, text=True,
        ).stdout.strip()

    def write(self, path, content):
        file = self.repo / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content)

    def commit(self):
        self.git("add", "-A")
        self.git("commit", "-m", "Fixture change")
        return self.git("rev-parse", "HEAD")

    def decision(self, head, base=None):
        return should_build("push", {"before": base or self.base}, head, self.repo)[0]

    def test_docs_only_skips(self):
        self.write("docs/file with space.md", "Docs\n")
        self.assertFalse(self.decision(self.commit()))

    @unittest.skipIf(os.name == "nt", "Windows filenames cannot contain newlines")
    def test_filename_with_newline_skips(self):
        self.write("docs/file with\nnewline.md", "Docs\n")
        self.assertFalse(self.decision(self.commit()))

    def test_mixed_change_builds(self):
        self.write("README.md", "Updated docs\n")
        self.write("app/code.kt", "Changed code\n")
        self.assertTrue(self.decision(self.commit()))

    def test_deleted_code_builds(self):
        (self.repo / "app/code.kt").unlink()
        self.assertTrue(self.decision(self.commit()))

    def test_code_renamed_to_docs_builds(self):
        (self.repo / "docs").mkdir()
        self.git("mv", "app/code.kt", "docs/example.md")
        self.assertTrue(self.decision(self.commit()))

    def test_push_compares_all_commits(self):
        self.write("app/code.kt", "Changed code\n")
        self.commit()
        self.write("README.md", "Only docs in the latest commit\n")
        self.assertTrue(self.decision(self.commit()))

    def test_pr_compares_base_to_tested_merge(self):
        self.git("checkout", "-b", "docs-change")
        self.write("README.md", "PR docs\n")
        self.commit()
        self.git("checkout", "main")
        self.write("app/code.kt", "Unrelated change already on main\n")
        updated_base = self.commit()
        self.git("merge", "--no-ff", "docs-change", "-m", "Test merge")
        head = self.git("rev-parse", "HEAD")
        self.assertFalse(should_build(
            "pull_request", {"pull_request": {"base": {"sha": updated_base}}}, head, self.repo,
        )[0])

    def test_shallow_checkout_fetches_missing_base(self):
        self.write("README.md", "Updated docs\n")
        head = self.commit()
        clone = Path(self.temp.name) / "clone"
        self.git("clone", "--depth=1", self.repo.as_uri(), str(clone))
        self.assertFalse(should_build("push", {"before": self.base}, head, clone)[0])

    def test_manual_missing_base_empty_diff_and_wrong_checkout_build(self):
        self.write("README.md", "Updated docs\n")
        head = self.commit()
        for event_name, event, sha in [
            ("workflow_dispatch", {}, head),
            ("unknown", {}, head),
            ("push", {"before": "0" * 40}, head),
            ("push", {}, head),
            ("push", {"before": head}, head),
            ("push", {"before": self.base}, self.base),
        ]:
            with self.subTest(event=event_name, data=event, sha=sha):
                self.assertTrue(should_build(event_name, event, sha, self.repo)[0])

    def test_failed_git_comparison_builds(self):
        with patch("ci_changes.git", side_effect=subprocess.CalledProcessError(128, "git")):
            self.assertTrue(self.decision(self.base))


if __name__ == "__main__":
    unittest.main()
