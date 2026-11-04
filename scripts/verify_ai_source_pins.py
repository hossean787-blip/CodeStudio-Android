#!/usr/bin/env python3
"""Check the exact, complete upstream source gitlinks without running source code."""
import configparser
import json
import pathlib
import re
import subprocess

root = pathlib.Path(__file__).resolve().parents[1]
manifest = json.loads((root / "third_party/ai-sources/ai-sources.lock.json").read_text(encoding="utf-8"))
sources = manifest["sources"]
assert manifest["integration_status"] == "source-mounted-not-runtime-integrated"
assert len(sources) == 13, f"Expected 13 projects, got {len(sources)}"
assert len({s["id"] for s in sources}) == 13
assert len({s["repo"] for s in sources}) == 13
assert len({s["path"] for s in sources}) == 13

modules = configparser.ConfigParser(interpolation=None)
modules.read(root / ".gitmodules", encoding="utf-8")
catalogue = (root / "app/src/main/java/com/androidharness/app/plugins/AiSourceCatalog.kt").read_text(encoding="utf-8")

for item in sources:
    path, sha, repo = item["path"], item["commit"], item["repo"]
    assert re.fullmatch(r"[0-9a-f]{40}", sha), f"Invalid SHA for {repo}"
    assert path == "third_party/ai-sources/" + item["id"], f"Incorrect source path {path}"
    assert item["compilation"] == "excluded", f"Unreviewed source was included in build: {repo}"
    assert item["functional_status"] == "pending-adapter-and-tests", f"Incorrect runtime claim: {repo}"
    section = 'submodule "' + path + '"'
    assert section in modules, f"Missing submodule declaration {path}"
    assert modules[section]["path"] == path
    assert modules[section]["url"] == "https://github.com/" + repo + ".git"
    entry = subprocess.check_output(["git", "ls-tree", "HEAD", path], cwd=root, text=True).strip()
    assert entry, f"Missing exact pinned source gitlink: {path}"
    meta, actual_path = entry.split("\t", 1)
    mode, kind, actual_sha = meta.split()
    assert (mode, kind, actual_sha, actual_path) == ("160000", "commit", sha, path), (
        f"Incorrect source revision for {repo}: {entry}"
    )
    assert all(v in catalogue for v in (item["id"], sha, repo)), f"App catalog misses {repo}"

# In-app source references must remain external to Gradle builds until each
# license and Android runtime integration is separately approved.
for gradle in ("settings.gradle.kts", "app/build.gradle.kts"):
    assert "third_party/ai-sources/" not in (root / gradle).read_text(encoding="utf-8"), (
        f"Unreviewed upstream source added to Android compilation: {gradle}"
    )

assert 'license != "UNVERIFIED" && !license.startsWith("RESTRICTED")' in catalogue, (
    "Unlicensed or legally restricted sources must not have a workspace import action"
)

print(f"PASS: all {len(sources)} complete source gitlinks exactly pinned; no unreviewed code executed.")
