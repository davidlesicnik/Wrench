"""Validate Android versions against published GitHub releases (including prereleases)."""

import json
import os
from pathlib import Path
import re
import subprocess


def read_version(source):
    name = re.search(r'^\s*versionName\s*=\s*"([^"\n]+)"', source, re.MULTILINE)
    code = re.search(r"^\s*versionCode\s*=\s*(\d+)\s*$", source, re.MULTILINE)
    if not name or not code:
        raise ValueError("Could not parse versionName and versionCode")
    return name.group(1), int(code.group(1))


def check_version(current, previous):
    name, code = current
    previous_name, previous_code = previous
    if code <= previous_code:
        raise ValueError(f"versionCode must increase: {code} <= {previous_code}")
    ordered = subprocess.check_output(
        ["sort", "-V"], input=f"{previous_name}\n{name}\n", text=True
    ).splitlines()
    if name == previous_name or ordered[-1] != name:
        raise ValueError(f"versionName must increase: {name} <= {previous_name}")


def main():
    source_path = "app/build.gradle.kts"
    current = read_version(Path(source_path).read_text())
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+-]*", current[0]):
        raise ValueError("versionName must be safe for use as a release tag and APK filename")
    # Fail on API errors; an unavailable API must not bypass version validation.
    pages = json.loads(subprocess.check_output(
        ["gh", "api", "--paginate", "--slurp",
         f"repos/{os.environ['GITHUB_REPOSITORY']}/releases"], text=True
    ))
    published = [release for page in pages for release in page if not release["draft"]]
    for release in published:
        tag = release["tag_name"]
        if not tag.startswith("v"):
            continue
        previous_source = subprocess.check_output(
            ["git", "show", f"refs/tags/{tag}:{source_path}"], text=True
        )
        check_version(current, read_version(previous_source))
        print(f"Version is newer than published release {tag}")
    print(f"Release version validated: {current[0]} (code {current[1]})")


if __name__ == "__main__":
    main()
