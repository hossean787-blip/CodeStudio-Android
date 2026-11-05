# Saba Studio — Issue 8 and thirteen-source execution status

Updated 2026-11-05 12:02 UTC. Repository: `hossean787-blip/CodeStudio-Android`.
Only verified behavior is called complete. Issue #8 still has unfinished items.

## Main and rollback

`main` includes PR #24 at merge commit `20d5abac468c021ba05a1a1755a4fb12bdbeacd0`.
PRs #10, #13, #14 and the thirteen-source audit PR #23 were merged after their
respective checks. The 13 exact upstream gitlinks are documented in
`docs/THIRTEEN_AI_RUNTIME_AUDIT_FA.md`; none of those independent projects is
compiled into Saba Studio or device-tested as its own runtime. Gitlinks do not
prove functional integration or grant redistribution rights. Keep the
proprietary-derived OpenClaude code out of the APK, and resolve Syntac licensing
before any code transfer. `third_party/free-claude-code` and
`third_party/GPT_API_free` are separate archives. Kilo and other model APIs are
provider integrations, not members of the thirteen-source Gitlink list.

Rollback bundles: `/workspace/deliverables/issue8-baseline.bundle` and
`/workspace/deliverables/issue8-main-refresh.bundle`. History is preserved.

## In-review implementation

PR #24, merged from app-code commit `ce81fab` into `main`:
explicit native document studio with Android Files destination, verified
readback, PDF/PNG preview, native PDF/DOCX/XLSX/PPTX/ZIP/text/SVG/PNG encoders;
bounded Skills import and saved-key quota fallback policy. Automatic writing
of generated files into project workspaces is blocked after a concrete
cross-project directory-swap counterexample; its unsafe implementation was
reverted. See `delivery/issue-8/phase-2/PUBLICATION-BLOCKER.md`.

Local Gradle on merged main + phase-two code: 2013 JVM tests in 308 suites,
0 failures/errors/skips, and all Android instrumentation sources compiled.
Source pin audit: 13/13 exact Gitlinks, none executed. A capped, anonymous
Kilo `kilo-auto/free` request returned HTTP 200 and text on 2026-11-05 11:10 UTC;
this was outside the APK and does not prove other providers.

Run 42015801943 on the previous phase-two head `5b7ff04`: release build passed;
20/21 emulator tests passed. The sole failure was the new picker fixture's
missing ActivityResultRegistryOwner after substituting a Persian Context. The
test was fixed in `ce81fab` and compiled locally. Focused Debug emulator run
42019250039 then passed 18/18, including a real Android Files PDF save,
verified readback and PDF reopening. Its native document fixtures were opened
independently with python-docx, openpyxl, python-pptx, Pillow and pypdf.
Full standard run 42019488737 passed on exact `ce81fab`: 21/21 Android
emulator tests, Release install/startup, and independent readers. The CI reader
job opened every native fixture and LibreOffice converted DOCX, XLSX and
PPTX to readable PDF plus literal-preserving XLSX to CSV.

Native Release/JNI run 42015801898 passed on `5b7ff04`: CPU tiny GGUF inference
returned 21 events, emulator startup passed, GPU was unavailable. The only
change from `5b7ff04` to `ce81fab` is the picker instrumentation fixture.
Native run 42019488476 on exact `ce81fab` passed: test-signed native Release,
CPU GGUF JNI inference and emulator startup. Independent source-security review
found no actionable issue; `CreateDocumentTool` remains unregistered. PR #24
was merged only after all checks passed. No physical Android device was tested.

## APK identity and signing limit

The current standard CI alpha Release at `ce81fab` is non-debuggable, signed v2,
package `ir.sabagostar.sabastudio`, versionCode 2, versionName
`0.1.0-alpha.2`, minSdk 26. SHA-256 of its APK:
`26c7d2d553ea9621cfc1e6a2c24681655926a2508e24a779dc4d6a73d9266652`.
The native alpha APK at `ce81fab` has SHA-256
`c62f86fce05b7d03ecf7aefab4de9f5cda5d027c25cf7c725adabdab5cffaefe`.
These CI jobs used different per-run Android Debug test certificates. The
installed app's signing key/certificate is unknown; an upgrade retaining data
cannot be promised. A stable compatible key must be identified and securely
configured before testing a real-device upgrade. These APKs are not a public
production release.

## Boundaries and next gate

No paid API, account-wide OAuth, all-files/phone-control permission, cloud
hosting or production signing material is newly authorized. Such capabilities
remain not configured until explicit permission and a successful independent
runtime test. Open PRs #19, #21 and #22 are separate and unmerged as of this
update; preserve them when synchronizing `main`.

Next: continue individual source/runtime licensing and capability work,
project-scoped knowledge and the remaining Issue #8 phases. Establish an
original-key-compatible stable signing configuration and prove data-retaining
upgrade on a real device before promising upgrade compatibility. Do not label
all thirteen sources functional based on Gitlinks or this phase-two APK.
