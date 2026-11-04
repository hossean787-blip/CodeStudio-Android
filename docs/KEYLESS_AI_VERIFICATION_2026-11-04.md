# Saba Studio — keyless AI live verification (2026-11-04)

## Verified against live endpoint

- GitHub Actions run: https://github.com/hossean787-blip/CodeStudio-Android/actions/runs/41896258944
- Audit source: `scripts/verify_keyless_ai.py`
- Endpoint: `https://api.kilo.ai/api/gateway`
- Anonymous `GET /models`: HTTP 200, 381 models reported, 11 explicitly marked `isFree` with tool support in the audit.
- Anonymous `POST /chat/completions` with model `kilo-auto/free`: HTTP 200; non-empty assistant message, `finish_reason=stop` on a bounded 512 output-token request.
- No API keys supplied, no authenticated/paid model requested, no response text or secrets saved in the report.

## Android status

The existing native `HarnessProvider` routes `kilo-auto/free` through Kilo without user-supplied credentials. Its Android unit tests and a prior debug APK build passed. The live verification above was run from a GitHub Actions Linux runner, **not a physical Android device**.

## Not completed

- Live verification for all 47+ catalog presets. Each external service may require its own account/key.
- Packaging or executing the upstream desktop-first Python 3.14.10 Free Claude Code runtime and every CLI launcher on Android.
- Security/runtime validation of optional connectors (Discord, third-party OAuth) on a physical device.
- A production-signed Android release. Existing artifacts are debug builds.

The external model service can change or revoke anonymous access. A successful catalog read alone is not proof of generation; the separate live generation check above succeeded.
