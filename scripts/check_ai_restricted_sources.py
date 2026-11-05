#!/usr/bin/env python3
"""Issue #9: prevent accidental redistribution of restricted AI source trees.

This gate checks tracked Git objects, not the working tree. Gitlinks (mode
160000) are references, not bundled source. This is NOT a license audit of
dependencies, generated APK contents, or code copied elsewhere.
"""
import subprocess
import sys

RESTRICTED = ("Syntac", "openclaude-android")
ROOT = "third_party/ai-sources"


def tracked_objects():
    result = subprocess.run(
        ["git", "ls-tree", "-r", "-z", "HEAD", "--", ROOT],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True,
    )
    for entry in result.stdout.split(b"\0"):
        if not entry:
            continue
        metadata, path = entry.split(b"\t", 1)
        mode, kind, sha = metadata.decode("ascii").split()
        yield mode, kind, path.decode("utf-8", "surrogateescape")


def audit(entries):
    violations = []
    for mode, kind, path in entries:
        relative = path.removeprefix(ROOT + "/")
        segments = relative.split("/")
        if not any(segment.casefold() == name.casefold()
                   for segment in segments for name in RESTRICTED):
            continue
        if mode != "160000" or kind != "commit":
            violations.append(path)
    return violations


def main():
    try:
        violations = audit(tracked_objects())
    except (OSError, subprocess.CalledProcessError, ValueError) as exc:
        print(f"Cannot inspect Git tree: {exc}", file=sys.stderr)
        return 2
    if violations:
        print("Restricted source was committed instead of a Gitlink:", file=sys.stderr)
        for path in violations:
            print(f"  {path}", file=sys.stderr)
        return 1
    print("PASS: no tracked restricted source files under AI source directory.")
    print("NOTE: this is a narrow source-tree gate, not full legal clearance.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
