# Saba Studio stabilization — 2026-11-04

Goal: preserve the existing Android application, verify native provider integration,
and deliver an honestly labelled, signed non-debuggable alpha APK.

Baseline / rollback: `1f762648f1fb026b5cf938165b396222a466dc18` on main; clean checkout.
Work branch: `codex/saba-stabilization-20261104`. No history rewriting.

Implementation plan (native execution, as requested):
- [x] Inspect architecture, providers, vendored sources, tests and workflows.
- [ ] Run all JVM tests and selected Android emulator persistence/integration tests.
- [ ] Fix failures at their source, preserving healthy functionality.
- [ ] Strengthen APK verification, provenance and evidence artifacts.
- [ ] Build and verify signed Release; record hash, package and signing limitations.
- [ ] Merge only after passing relevant checks; record final evidence.

| ID | Requirement | Initial status / evidence |
|---|---|---|
| R-01 | Baseline and source audit | verified: baseline-commit.txt; complete clean clone |
| R-02 | FCC provider/native integration | in progress: Kotlin presets + OpenAiCompatProvider; Python CLI is optional external runtime |
| R-03 | GPT API Free integration | in progress: ChatAnywhere presets + native OpenAI wire; requires personal API key |
| R-04 | RTL, dark mode, mobile UI | not tested: Compose UI, values-fa, supportsRtl present |
| R-05 | Unit/integration/persistence | in progress: full suite + selected emulator tests planned |
| R-06 | Actual free inference | in progress: one anonymous capped Kilo request, no paid requests |
| R-07 | Signed non-debuggable Release | in progress: CI Release exists; strengthen verification |
| R-08 | Upgrade compatibility | blocked: prior CI generated an ephemeral Debug certificate; private key unavailable; Secrets API returns HTTP 403 |
| R-09 | Physical-device verification | not tested: no attached physical Android device |
| R-10 | Main + downloadable artifact + CI URL | in progress |

Decisions: retain Kotlin/Compose/Room architecture; no Python runtime embedded; no
API credentials bundled; retain vendored trees exactly. Test signing is alpha only.
Do not label catalogs, presets, Python archival imports or mock-server tests as
verified external-provider inference. Full native inference needs pinned NDK and
model fixtures; a build without them must say so.

Artifact manifest and test results are completed in REPORT.md after execution.

CI diagnosis: first emulator setup failed before tests because
`android-actions/setup-android@v3` requested removed SDK package `tools`.
Use the runner's current sdkmanager with explicit supported packages instead.
Local checks: SQLite truncation 4/4; editor semantic/formatting tests 7/7;
anonymous Kilo inference HTTP 200 and non-empty text (keyless-report.json).

Full JVM run `41900304316`: 1943 tests, 2 failures in ProviderBrandsTest.
Root cause: older tests assumed OpenRouter was always second and every
OpenAI-compatible wire used a /v1 URL. FCC includes valid /v4, /v3/compat,
/v1/openai and gateway roots; ChatAnywhere is now second. Preserve correct
production presets, update exact-root regression expectations, add checks
against credentials/query parameters in compiled presets.

Expand emulator evidence: actual file-edit/undo tool flow with a deterministic
provider fixture, plus install/start the exact Release artifact in Persian dark
mode and capture screenshot/crash buffer. Fixture tests do not prove live AI
inference. Also attempt a separate full native Release with pinned NDK/CMake;
do not equate the existing no-NDK alpha with working local inference.

Neon red test: run 41900957846 executed 1943 JVM tests; only the new default
palette test failed at ThemePaletteTest.kt:47 (expected NEON, actual BLUE).
Implementation: a selectable generated cyan/violet/pink Saba Neon palette,
new-install default with wallpaper dynamic color opt-in; explicit stored
palettes and dynamic-color settings remain honored. Existing contrast tests
cover every palette in light/dark/AMOLED modes. Version advances to
versionCode=2, versionName=0.1.0-alpha.2; package stays unchanged.

Full JVM run 41901585622: 1943 tests, 18 localization coverage failures.
New Neon resource existed only in default/Persian groups; the existing suite
requires every UI resource group in all supported locales. Added translated
resource groups for all nine other locales; did not weaken or exclude tests.
Independent review found no important code bugs and one workflow path-filter
gap, now fixed for native smoke scripts.
