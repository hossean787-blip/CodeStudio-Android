---
name: free-claude-code
description: Diagnose optional local Free Claude Code runtime compatibility inside Saba Studio without a remote server.
category: software-development
---

# Free Claude Code on Android — local runtime diagnostics

The Python source is vendored at `third_party/free-claude-code/` in the Saba Studio GitHub repository. This is a Python 3.14.10 FastAPI desktop-first project, **not** an automatically installed Android engine.

## Supported workflows
- Native Android chat, provider management, fallback, editing, terminal, browser and Telegram are handled by Saba Studio's existing Android code. Prefer those native implementations.
- For explicit requests to run **the upstream Python runtime itself on the phone**, open an actual checkout of the CodeStudio-Android repository in the app's Linux workspace and locate `third_party/free-claude-code/src`.
- Use the script supplied by this skill (`scripts/fcc-local.sh`) through the app's built-in terminal/shell, after obtaining its contents with `skill_view`. Do not use `curl | sh` or run copied remote scripts.

## Safe procedure
1. Run `env_status` to verify the on-device Linux environment is ready.
2. Copy `scripts/fcc-local.sh` into the workspace from `skill_view`, only after user approves writing the workspace.
3. Run `sh fcc-local.sh check <repository-root>`. It performs read-only checks: source, **exact Python 3.14.10**, and a minimum set of Python dependencies.
4. If these checks fail, report the exact first failure. Android architecture-dependent wheels may be unavailable; do not silently install packages or modify the Python version requirement.
5. Only after the user explicitly requests starting the runtime, run `sh fcc-local.sh start <repository-root>`. Stop it with the terminal's interrupt/stop control. Keep admin access loopback-only. Check listening address before accepting any remote traffic.
6. Test actual health/API responses. A successful preflight alone is NOT proof the Python runtime works. Voice extras, Discord, CLI launchers and native desktop components require separate verification.

## Constraints
- The script does not embed source or install packages into the APK.
- A regular Android APK does not provide Python 3.14.10 or the upstream dependency wheels by default.
- No public network bind, no account tokens in logs, no credential copying from GitHub and no claim of 100% feature parity.
- This integration is an optional compatibility route alongside the native mobile implementation, not a guarantee that FCC can run on every device.
