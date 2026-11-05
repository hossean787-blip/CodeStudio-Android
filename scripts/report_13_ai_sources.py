#!/usr/bin/env python3
"""Report the registered 13 source projects without claiming runtime support."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "third_party/ai-sources/ai-sources.lock.json"


def audit():
    data = json.loads(LOCK.read_text(encoding="utf-8"))
    assert data["integration_status"] == "source-mounted-not-runtime-integrated"
    sources = data["sources"]
    assert len(sources) == 13
    assert len({entry["id"] for entry in sources}) == 13
    result = []
    for entry in sources:
        path = entry["path"]
        tree = subprocess.check_output(
            ["git", "ls-tree", "HEAD", "--", path], cwd=ROOT, text=True
        ).strip()
        assert tree == f"160000 commit {entry['commit']}\t{path}", (path, tree)
        assert entry["compilation"] == "excluded"
        assert entry["functional_status"] == "pending-adapter-and-tests"
        result.append({
            "id": entry["id"],
            "repository": entry["repo"],
            "pinned_commit": entry["commit"],
            "declared_license": entry["license"],
            "source_gitlink_present": True,
            "compiled_into_app": False,
            "android_runtime_test": "not_run",
            "independent_live_test": "not_run",
            "integration_status": "source_only",
        })
    return {"schema_version": 1, "app_revision": subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(), "sources": result}


if __name__ == "__main__":
    print(json.dumps(audit(), ensure_ascii=False, indent=2))
