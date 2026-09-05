import tempfile
from pathlib import Path
import unittest

from collect_artifact import collect_artifact


class CollectArtifactTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "build" / "libs").mkdir(parents=True)
        self.destination = self.root / "release-artifacts"

    def prepare(self, minecraft: str, loader_key: str, loader: str) -> Path:
        (self.root / "gradle.properties").write_text(
            "mod_id=touhou_little_maid_mahjong\n"
            "mod_version=1.0.0\n"
            f"minecraft_version={minecraft}\n"
            f"{loader_key}=47.4.0\n",
            encoding="utf-8",
        )
        jar = self.root / "build" / "libs" / (
            f"touhou_little_maid_mahjong-{minecraft}-{loader}-1.0.0.jar"
        )
        jar.write_bytes(b"distribution jar")
        return jar

    def test_collects_forge_jar_and_excludes_sources_and_other_loader(self) -> None:
        jar = self.prepare("1.20.1", "forge_version", "forge")
        (jar.parent / "example-sources.jar").write_bytes(b"sources")
        (jar.parent / "touhou_little_maid_mahjong-1.21.1-neoforge-1.0.0.jar").write_bytes(b"other")
        name, copied = collect_artifact(self.root, "mc/1.20.1", "a" * 40, self.destination)
        self.assertEqual(name, "touhou-little-maid-mahjong-mc-1.20.1-aaaaaaaaaaaa")
        self.assertEqual(list(self.destination.iterdir()), [copied])
        self.assertEqual(copied.read_bytes(), jar.read_bytes())

    def test_collects_neoforge_jar(self) -> None:
        jar = self.prepare("1.21.1", "neo_version", "neoforge")
        name, copied = collect_artifact(self.root, "mc/1.21.1", "b" * 40, self.destination)
        self.assertEqual(copied.name, jar.name)
        self.assertEqual(name, "touhou-little-maid-mahjong-mc-1.21.1-bbbbbbbbbbbb")

    def test_rejects_branch_mismatch(self) -> None:
        self.prepare("1.20.1", "forge_version", "forge")
        with self.assertRaisesRegex(ValueError, "do not match"):
            collect_artifact(self.root, "mc/1.21.1", "c" * 40, self.destination)
        self.assertFalse(self.destination.exists())

    def test_rejects_missing_distribution_even_if_other_jars_exist(self) -> None:
        jar = self.prepare("1.20.1", "forge_version", "forge")
        jar.rename(jar.with_name("example-dev.jar"))
        with self.assertRaisesRegex(ValueError, "was not produced"):
            collect_artifact(self.root, "mc/1.20.1", "d" * 40, self.destination)
        self.assertFalse(self.destination.exists())

    def test_rejects_ambiguous_loader(self) -> None:
        self.prepare("1.20.1", "forge_version", "forge")
        with (self.root / "gradle.properties").open("a", encoding="utf-8") as properties:
            properties.write("neo_version=21.1.219\n")
        with self.assertRaisesRegex(ValueError, "exactly one mod loader"):
            collect_artifact(self.root, "mc/1.20.1", "e" * 40, self.destination)

    def test_rejects_stale_output_directory_without_overwriting_it(self) -> None:
        self.prepare("1.20.1", "forge_version", "forge")
        self.destination.mkdir()
        stale = self.destination / "previous.jar"
        stale.write_bytes(b"previous")
        with self.assertRaises(FileExistsError):
            collect_artifact(self.root, "mc/1.20.1", "f" * 40, self.destination)
        self.assertEqual(stale.read_bytes(), b"previous")


if __name__ == "__main__":
    unittest.main()
