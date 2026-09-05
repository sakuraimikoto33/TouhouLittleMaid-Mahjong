#!/usr/bin/env python3

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from release_version import (
    SemVer,
    decide_release,
    history_url,
    read_gradle_property,
    write_github_output,
)


FORGE_PROPERTIES = """mod_version=1.0.0
minecraft_version=1.20.1
forge_version=47.4.0
forge_version_range=[47.4.0,)
touhou_little_maid_version=1.5.3-forge+mc1.20.1
touhou_little_maid_version_range=[1.5.3,)
riichi_mahjong_version=0.2.0
riichi_mahjong_version_range=[0.2.0,)
"""
NEOFORGE_PROPERTIES = """mod_version=1.0.0
minecraft_version=1.21.1
neo_version=21.1.219
neo_version_range=[21.1.219,)
touhou_little_maid_version=1.5.3-neoforge+mc1.21.1
touhou_little_maid_version_range=[1.5.3,)
riichi_mahjong_version=0.4.1
riichi_mahjong_version_range=[0.2.0,)
architectury_version=13.0.8
"""


class SemVerTest(unittest.TestCase):
    def test_semver_precedence_examples(self) -> None:
        ordered = [
            "1.0.0-alpha",
            "1.0.0-alpha.1",
            "1.0.0-alpha.beta",
            "1.0.0-beta",
            "1.0.0-beta.2",
            "1.0.0-beta.11",
            "1.0.0-rc.1",
            "1.0.0",
            "1.0.1",
            "1.1.0",
            "2.0.0",
        ]
        parsed = [SemVer.parse(value) for value in ordered]
        for older, newer in zip(parsed, parsed[1:]):
            with self.subTest(older=older.source, newer=newer.source):
                self.assertLess(older, newer)

    def test_build_metadata_does_not_change_precedence(self) -> None:
        self.assertEqual(SemVer.parse("1.0.0+build.1"), SemVer.parse("1.0.0+build.2"))

    def test_prerelease_detection(self) -> None:
        self.assertTrue(SemVer.parse("0.2.0-beta.1").is_prerelease)
        self.assertFalse(SemVer.parse("0.2.0").is_prerelease)

    def test_invalid_versions_are_rejected(self) -> None:
        invalid = [
            "1", "1.0", "01.0.0", "1.01.0", "1.0.01", "1.0.0-01",
            "v1.0.0", "1.0.0-", "1.0.0+", "1.0.0\n",
        ]
        for value in invalid:
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    SemVer.parse(value)


class ReleaseDecisionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tags = [
            "0.1.0-mc1.20.1",
            "0.1.1-mc1.20.1",
            "0.9.0-mc1.21.1",
        ]

    def test_newer_version_is_released(self) -> None:
        decision = decide_release("0.1.2", "1.20.1", self.tags)
        self.assertTrue(decision.should_release)
        self.assertEqual("0.1.2-mc1.20.1", decision.tag)
        self.assertEqual("0.1.1-mc1.20.1", decision.previous_tag)

    def test_equal_and_older_versions_are_skipped(self) -> None:
        self.assertFalse(decide_release("0.1.1", "1.20.1", self.tags).should_release)
        self.assertFalse(decide_release("0.1.0", "1.20.1", self.tags).should_release)

    def test_other_minecraft_versions_do_not_interfere(self) -> None:
        decision = decide_release("0.2.0", "1.20.1", self.tags)
        self.assertTrue(decision.should_release)
        self.assertEqual("0.1.1-mc1.20.1", decision.previous_tag)

    def test_prerelease_and_stable_promotion(self) -> None:
        prerelease = decide_release("0.2.0-beta.1", "1.20.1", self.tags)
        self.assertTrue(prerelease.should_release)
        self.assertTrue(prerelease.prerelease)
        stable = decide_release(
            "0.2.0", "1.20.1", [*self.tags, "0.2.0-beta.1-mc1.20.1"]
        )
        self.assertTrue(stable.should_release)
        self.assertFalse(stable.prerelease)

    def test_build_metadata_with_equal_precedence_is_skipped(self) -> None:
        decision = decide_release("1.0.0+build.2", "1.20.1", ["1.0.0+build.1-mc1.20.1"])
        self.assertFalse(decision.should_release)

    def test_same_precedence_previous_tag_is_deterministic(self) -> None:
        tags = ["1.0.0+build.a-mc1.20.1", "1.0.0+build.b-mc1.20.1"]
        for ordered in (tags, list(reversed(tags))):
            self.assertEqual(
                tags[1], decide_release("1.0.1", "1.20.1", ordered).previous_tag
            )

    def test_first_version_is_released(self) -> None:
        decision = decide_release("0.1.0", "1.20.1", [])
        self.assertTrue(decision.should_release)
        self.assertIsNone(decision.previous_tag)

    def test_malformed_matching_tag_is_ignored(self) -> None:
        decision = decide_release("0.1.2", "1.20.1", [*self.tags, "latest-mc1.20.1"])
        self.assertEqual(("latest-mc1.20.1",), decision.ignored_tags)
        self.assertTrue(decision.should_release)

    def test_invalid_minecraft_version_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            decide_release("1.0.0", "1.20.1/invalid", [])


class InputOutputTest(unittest.TestCase):
    def test_gradle_property_must_be_unique_and_nonempty(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "gradle.properties"
            path.write_text("# comment\nmod_version = 0.1.0\n", encoding="utf-8")
            self.assertEqual("0.1.0", read_gradle_property(path, "mod_version"))
            for content in (
                "mod_version=0.1.0\nmod_version=0.1.1\n",
                "mod_version=0.1.0\nmod_version=\n",
                "mod_version=\n",
                "# mod_version=0.1.0\n",
            ):
                with self.subTest(content=content):
                    path.write_text(content, encoding="utf-8")
                    with self.assertRaises(ValueError):
                        read_gradle_property(path, "mod_version")

    def test_history_links(self) -> None:
        self.assertEqual(
            "https://github.com/owner/repo/compare/0.1.1-mc1.20.1...abc123",
            history_url("owner/repo", "abc123", "0.1.1-mc1.20.1"),
        )
        self.assertEqual(
            "https://github.com/owner/repo/commits/abc123",
            history_url("owner/repo", "abc123", None),
        )
        self.assertEqual(
            "https://github.com/owner/repo/compare/1.0.0%2Bbuild.1-mc1.20.1...mc%2F1.20.1",
            history_url("owner/repo", "mc/1.20.1", "1.0.0+build.1-mc1.20.1"),
        )

    def test_github_output_appends_and_rejects_multiline_values(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "output"
            write_github_output(path, {"existing": "value"})
            write_github_output(path, {"should_release": "true"})
            self.assertEqual("existing=value\nshould_release=true\n", path.read_text())
            with self.assertRaises(ValueError):
                write_github_output(path, {"tag": "first\nsecond"})


class CommandLineTest(unittest.TestCase):
    def test_real_git_repository_selects_releases_for_both_loaders(self) -> None:
        script = Path(__file__).with_name("release_version.py").resolve()
        minimal_properties = "".join(
            line for line in NEOFORGE_PROPERTIES.splitlines(keepends=True)
            if line.partition("=")[0] in {"mod_version", "minecraft_version"}
        )
        cases = (
            (FORGE_PROPERTIES, "1.20.1", "0.9.0-mc1.20.1", True),
            (NEOFORGE_PROPERTIES, "1.21.1", None, True),
            (FORGE_PROPERTIES.replace("mod_version=1.0.0", "mod_version=0.9.0"),
             "1.20.1", "0.9.0-mc1.20.1", False),
            # Changelog generation must not require loader or dependency metadata.
            (minimal_properties, "1.21.1", None, True),
        )
        for properties, minecraft, previous, should_release in cases:
            with self.subTest(minecraft=minecraft, should_release=should_release):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)

                    def git(*args: str) -> None:
                        subprocess.run(
                            ["git", *args], cwd=root, check=True,
                            capture_output=True, text=True,
                        )

                    git("init", "--quiet")
                    git("-c", "user.name=Release Test", "-c", "user.email=test@example.invalid",
                        "-c", "commit.gpgsign=false", "-c", "core.hooksPath=",
                        "commit", "--quiet", "--allow-empty", "-m", "Fixture")
                    git("tag", "0.9.0-mc1.20.1")
                    git("tag", "9.0.0-mc1.19.2")
                    (root / "gradle.properties").write_text(properties, encoding="utf-8")
                    result = subprocess.run(
                        [sys.executable, str(script), "--github-output", "output",
                         "--repository", "owner/repo", "--target", "abc123",
                         "--release-notes", "release-notes.md"],
                        cwd=root, capture_output=True, text=True,
                    )
                    self.assertEqual(0, result.returncode, result.stderr)
                    outputs = dict(
                        line.split("=", 1)
                        for line in (root / "output").read_text(encoding="utf-8").splitlines()
                    )
                    self.assertEqual({
                        "mod_version", "minecraft_version", "tag", "previous_tag",
                        "should_release", "prerelease", "history_url",
                    }, set(outputs))
                    self.assertEqual(str(should_release).lower(), outputs["should_release"])
                    self.assertEqual(minecraft, outputs["minecraft_version"])
                    self.assertEqual(previous or "", outputs["previous_tag"])
                    self.assertEqual(f"{outputs['mod_version']}-mc{minecraft}", outputs["tag"])
                    self.assertEqual("false", outputs["prerelease"])
                    self.assertEqual(history_url("owner/repo", "abc123", previous), outputs["history_url"])
                    notes = (root / "release-notes.md").read_text(encoding="utf-8")
                    expected_url = (
                        f"https://github.com/owner/repo/compare/{previous}...abc123"
                        if previous else "https://github.com/owner/repo/commits/abc123"
                    )
                    self.assertEqual(f"[Full Changelog]({expected_url})\n", notes)


if __name__ == "__main__":
    unittest.main()
