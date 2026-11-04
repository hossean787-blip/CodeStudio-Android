#!/bin/sh
# Free Claude Code runtime preflight for Saba Studio's optional on-device Linux environment.
# No automatic installs, privileged actions, network downloads, or remote binds.
set -eu

usage() {
  printf '%s\n' 'Usage: sh fcc-local.sh check [repo-root]' '       sh fcc-local.sh start [repo-root]'
}
if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then usage; exit 2; fi
action=$1
case "$action" in check|start) ;; *) usage; exit 2;; esac
root=${2:-.}
source_path="$root/third_party/free-claude-code/src"
if [ ! -f "$source_path/free_claude_code/cli/entrypoints.py" ]; then
  echo "FCC source not found: open a checkout of CodeStudio-Android or pass its root." >&2
  exit 3
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "Python 3.14.10 not available in this Android Linux environment." >&2
  exit 4
fi
version=$(python3 -c 'import sys; print(".".join(map(str,sys.version_info[:3])))')
if [ "$version" != "3.14.10" ]; then
  echo "Unsupported Python $version: upstream Free Claude Code requires exactly 3.14.10." >&2
  exit 4
fi
if ! python3 -c 'import fastapi, uvicorn, httpx, pydantic, loguru, sqlalchemy, discord, telegram' >/dev/null 2>&1; then
  echo "One or more required dependencies are missing. Install only after checking Android wheel compatibility." >&2
  exit 5
fi
echo "FCC Python and basic dependencies present. Complete upstream runtime compatibility is not verified."
if [ "$action" = check ]; then exit 0; fi
# Bind to loopback through the upstream configuration if supported by this release.
# Never expose the admin interface on Wi-Fi or public addresses.
export HOST=127.0.0.1
export PYTHONPATH="$source_path${PYTHONPATH:+:$PYTHONPATH}"
exec python3 -c 'from free_claude_code.cli.entrypoints import serve; serve()'
