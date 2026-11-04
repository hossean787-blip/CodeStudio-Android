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

Baseline-status clarification: `baseline-status.txt` contains `?? delivery/`
because redirecting the baseline output created that directory before Git
inspected untracked files. The checkout itself was clean before ledger creation
(as observed immediately after clone); no pre-existing user edits were changed.

Native probe audit found an existing stale reflection signature: LocalNative's
current generateChat adds promptThreads and cacheKey, while ReleaseProbe still
looked up the older method. Updated the probe to the actual JNI API before
claiming a native runtime result. Host Java compilation was unavailable because
`javac` is absent (only a Java runtime is installed); compilation is pending CI.
This changes only
test tooling, not the shipped application's inference API.

Native-enabled run 41902362561 failed the existing strict SSE cancellation test
with SocketException: Socket closed (SseStreamTest, not a native compiler error).
The 1943-test no-NDK run had passed; heavy native compilation exposed the race.
Root cause: cleanup closed the HTTP call before marking its blocking reader
cancelled, and the generic error path could close the flow with that IOException.
Fix: mark reader cancelled before closing the socket and ensureActive before
propagating generic failures. Keep genuine active HTTP failures intact. Also
sequence full JVM tests before native compilation instead of co-scheduling them.
Existing failing cancellation regression remains enabled and unmodified.

Emulator run 41902362533: three selected tests passed; the v15 migration fixture
failed because its manually supplied migration list stopped at v22 while the
real app is v25. Production registers all v15->v25 migrations. Reconstructed
the v15 fixture completely (remove post-v15 tables/columns), registered the
full path in the test, and added a persisted message assertion after migration.
No destructive migration fallback or weakened assertion was added. Also added
the intentionally modified upstream JNI probe to source-audit change tracking.

Visual audit of the successful native startup screenshot found English
AndroidHarness onboarding, covered by a notification dialog. Startup liveness
was valid, but this was NOT Persian/RTL evidence. Rebrand the two onboarding
strings in all locales; grant notifications only in the emulator probe, prime
AppCompat's first launch before setting fa, and require Persian welcome text
in a UIAutomator dump before accepting a Persian screenshot. Rebuild the
changed deliverable and repeat native/persistence/startup verification.
Legacy Android 9/10 storage test default package corrected to Saba debug ID;
only shell syntax checked, those older OS tests remain unexecuted.

Complete display-brand audit: lock screen, Share target, notification setup,
router lifecycle, TTS sample and app-facing tips now name Saba Studio. Resource
keys, namespaces, protocol IDs, upstream attribution/fundraising/contribution
links and the technical AndroidHarness bundle format are preserved.

Strict two-launch Persian startup probe found a real background crash in the
no-NDK variant: UnsatisfiedLinkError for libharness_local.so from memory-trim
cache release, even though no local model had ever loaded. Native release
contained the library and had passed earlier. Fix LocalModelManager to avoid
initializing JNI when its tracked cache is empty; keep actual loaded-model
release behavior. Do not clear/ignore the crash evidence. Mark the auxiliary
no-NDK release artifact explicitly no-local-runtime; the primary deliverable
is the separate native release. Repeat tests on the changed app.

## Final pre-merge evidence snapshot

Source b84b476: both runs 41913010323 and 41913010385 completed successfully.
JVM: 1943/1943 in each configuration, no skips; emulator persistence: 4/4;
Release JNI CPU, install, Persian UI assertion and dark RTL screenshot passed.
The no-NDK first-launch crash regression passed after the cache guard.
Exact delivered APK and ZIP hash/signature/source identity reverified on host.
See test-summary.json and REPORT.md for artifact IDs, hashes and scopes.

R-01 verified. R-02 native-equivalent transports/presets verified by unit tests;
external FCC account/runtime parity remains blocked/not tested. R-03 blocked
for live keyed ChatAnywhere inference (native preset/wire tests passed).
R-04 verified for Persian/dark/RTL startup only; other physical UI paths not tested.
R-05 verified for recorded JVM/integration scopes. R-06 verified for anonymous
Kilo host requests, including fresh text inference at 21:24 UTC. R-07 verified
for the exact native Release test-signed APK. R-08 blocked for durable signing
and previous-alpha upgrade compatibility. R-09 not tested: physical hardware.
R-10 verified: all artifacts verified and PR #3 merged to main at
1d2f328683a6571cb57cdf0b645e79dc6b9ff730 on 2026-11-04 21:35 UTC.
The app tree on main is byte-identical to tested source b84b476. This final
record update changes documentation/JSON only and is delivered on its own branch.

Artifact manifest: delivery/REPORT.md (human report), delivery/CAPABILITIES.md
(feature/source mapping), delivery/test-summary.json (test/artifact facts),
delivery/source-inventory.json (module/dependency/manifest inventory),
delivery/keyless-report.json and keyless-tool-report.json (actual API evidence),
baseline-commit.txt and baseline-status.txt (rollback records). Native APK,
checksum, raw test reports and screenshots are retained in the linked Actions
artifacts and /workspace/deliverables; no signing private key is included.
