#!/usr/bin/env python3
"""Collect only the distribution JAR for the checked-out Minecraft branch."""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import shutil
import sys

from detect_mod_changes import OID_PATTERN, artifact_name_prefix
from release_version import (
    MINECRAFT_VERSION_PATTERN,
    SemVer,
    read_gradle_property,
    write_github_output,
)


def collect_artifact(
    root: Path, branch: str, sha: str, destination: Path
) -> tuple[str, Path]:
    properties = root / "gradle.properties"
    minecraft_version = read_gradle_property(properties, "minecraft_version")
    mod_version = read_gradle_property(properties, "mod_version")
    mod_id = read_gradle_property(properties, "mod_id")
    SemVer.parse(mod_version)
    if not MINECRAFT_VERSION_PATTERN.fullmatch(minecraft_version):
        raise ValueError("invalid minecraft_version")
    if not re.fullmatch(r"[a-z][a-z0-9_]*", mod_id):
        raise ValueError("invalid mod_id")
    if branch != f"mc/{minecraft_version}":
        raise ValueError("branch and minecraft_version do not match")
    if OID_PATTERN.fullmatch(sha) is None:
        raise ValueError("sha must be a full Git object ID")

    keys = {
        line.split("=", 1)[0].strip()
        for raw_line in properties.read_text(encoding="utf-8").splitlines()
        if (line := raw_line.strip()) and not line.startswith("#") and "=" in line
    }
    loaders = [
        (loader, key)
        for loader, key in (("forge", "forge_version"), ("neoforge", "neo_version"))
        if key in keys
    ]
    if len(loaders) != 1:
        raise ValueError("gradle.properties must define exactly one mod loader")
    loader, loader_key = loaders[0]
    read_gradle_property(properties, loader_key)

    jar = root / "build" / "libs" / f"{mod_id}-{minecraft_version}-{loader}-{mod_version}.jar"
    if not jar.is_file() or jar.stat().st_size == 0:
        raise ValueError(f"expected distribution JAR was not produced: {jar}")
    # A fresh directory prevents stale or non-distribution JARs from being uploaded.
    destination.mkdir(parents=True, exist_ok=False)
    copied_jar = destination / jar.name
    shutil.copyfile(jar, copied_jar)
    name = f"{artifact_name_prefix(branch)}{sha[:12].lower()}"
    return name, copied_jar


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--branch", required=True)
    parser.add_argument("--sha", required=True)
    parser.add_argument("--destination", type=Path, default=Path("release-artifacts"))
    parser.add_argument("--github-output", type=Path, required=True)
    args = parser.parse_args()
    try:
        name, jar = collect_artifact(args.root, args.branch, args.sha, args.destination)
        write_github_output(args.github_output, {"name": name})
        print(f"Collected {jar}")
        return 0
    except (OSError, ValueError) as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
