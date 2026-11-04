# Free Claude Code × Saba Studio — Android integration

## Source provenance
- Vendored source: `third_party/free-claude-code/`
- Upstream: https://github.com/Alishahryar1/free-claude-code
- Pinned upstream commit: `1c72f5fef14249936902aa7927348caec29939d6`
- License: upstream AGPL-3.0-only, retained in vendored directory.

## Implemented on Android
- The existing AndroidHarness-derived native chat, agent, code editor and model-management runtime remains authoritative.
- 47 API-key/OpenAI-compatible and local provider connection presets based on Free Claude Code's `config/provider_catalog.py` were integrated into `ProviderSheet.kt`.
- Each preset is handled by Android's existing `ProviderRepository`, `ModelCatalog` and `OpenAiCompatProvider` implementation. Keys remain in the application's existing encrypted key store; no upstream credentials are included.
- Model availability, access tiers and costs come from the chosen provider; presets do not enable free paid access.
- JVM tests check provider presence, HTTPS cloud endpoints, explicitly local HTTP endpoints, duplicate endpoints, and absence of embedded credentials.

## Not implemented / architectural limits
- Python Free Claude Code's runtime, admin dashboard, CLI harnesses, messaging bots, subscription OAuth sessions and local background server are NOT running inside the Android APK.
- Native provider adapters with non-OpenAI protocols are not automatically ported merely by vendoring Python files.
- Provider APIs may require provider-specific headers, HTTP dialects or account settings: successful completion of actual network calls is not guaranteed by a preset.
- Without a server or supported Python-on-Android runtime, full parity with desktop Free Claude Code is not available.

## Verification
The GitHub workflow `.github/workflows/saba-studio-build.yml` audits the untouched upstream AndroidHarness source (except explicit allowlist), executes `FreeClaudeCodeProviderPresetsTest`, builds the Android debug APK and publishes the debug artifact only after success.

This is an incremental native/mobile integration, not a claim that every Free Claude Code feature operates on-device.
