#!/usr/bin/env python3
"""Read-only anonymous API catalog diagnostic for Saba Studio.

Never transmits secret values, never uses authenticated endpoints and never
makes chat-completion requests that may consume paid or limited credits.
"""
from __future__ import annotations
import argparse
import json
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

CATALOGS = {
    "kilo": "https://api.kilo.ai/api/gateway/models",
}

def check(name: str, url: str) -> dict:
    result = {"provider": name, "url": url, "status": "unavailable", "http_status": None,
              "model_count": 0, "free_tool_models": 0, "diagnostic": ""}
    req = urllib.request.Request(url, headers={"Accept": "application/json",
        "User-Agent": "SabaStudio-ReadOnly-Check/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            result["http_status"] = response.status
            # Prevent huge or unbounded remote responses.
            raw = response.read(2 * 1024 * 1024 + 1)
            if len(raw) > 2 * 1024 * 1024:
                raise ValueError("catalog response exceeds safety limit")
            payload = json.loads(raw)
            data = payload.get("data", [])
            if not isinstance(data, list):
                raise ValueError("response has no array of models")
            result["model_count"] = len(data)
            result["free_tool_models"] = sum(
                1 for item in data if isinstance(item, dict)
                and item.get("isFree") is True
                and isinstance(item.get("supported_parameters", []), list)
                and "tools" in item.get("supported_parameters", [])
            )
            result["status"] = "reachable"
            result["diagnostic"] = "Catalog reachable; generation/auth not tested"
    except urllib.error.HTTPError as exc:
        result["http_status"] = exc.code
        result["diagnostic"] = f"HTTP {exc.code}; anonymous catalog may require access"
    except (urllib.error.URLError, TimeoutError, ValueError, json.JSONDecodeError) as exc:
        result["diagnostic"] = type(exc).__name__
    return result

def check_free_inference() -> dict:
    """One anonymous, capped request against the explicitly free Kilo route."""
    url = "https://api.kilo.ai/api/gateway/chat/completions"
    result = {"provider": "kilo", "model": "kilo-auto/free",
              "status": "unavailable", "http_status": None,
              "returned_text": False, "diagnostic": ""}
    payload = json.dumps({"model": "kilo-auto/free",
                          "messages": [{"role": "user", "content": "Reply OK"}],
                          "max_tokens": 8, "stream": False}).encode("utf-8")
    req = urllib.request.Request(
        url, data=payload, method="POST",
        headers={"Accept": "application/json", "Content-Type": "application/json",
                 "User-Agent": "SabaStudio-Free-Model-Check/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            result["http_status"] = response.status
            raw = response.read(65537)
            if len(raw) > 65536:
                raise ValueError("response exceeds 64 KiB")
            body = json.loads(raw)
            choices = body.get("choices", [])
            result["returned_text"] = bool(choices and isinstance(choices[0], dict)
                                           and choices[0].get("message", {}).get("content"))
            result["status"] = "working" if result["returned_text"] else "unexpected_response"
            result["diagnostic"] = "Anonymous free-model response received" if result["returned_text"] else "No text returned"
    except urllib.error.HTTPError as exc:
        result["http_status"] = exc.code
        result["diagnostic"] = f"HTTP {exc.code}; this route may require authorization or quota"
    except (urllib.error.URLError, TimeoutError, ValueError, json.JSONDecodeError) as exc:
        result["diagnostic"] = type(exc).__name__
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path("keyless-report.json"))
    parser.add_argument("--free-inference", action="store_true", help="Test one capped anonymous free-model generation")
    args = parser.parse_args()
    report = {"checked_at_utc": datetime.now(timezone.utc).isoformat(),
              "checks": [check(name, url) for name, url in CATALOGS.items()],
              "inference": check_free_inference() if args.free_inference else None,
              "limitations": ["Model-list access is not proof of model inference.",
                              "No private API credentials were used.",
                              "No paid inference requests were sent."]}
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    catalog_ok = all(item["status"] == "reachable" for item in report["checks"])
    inference_ok = not args.free_inference or report["inference"]["status"] == "working"
    return 0 if catalog_ok and inference_ok else 1

if __name__ == "__main__":
    raise SystemExit(main())
