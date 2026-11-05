# Saba Studio — Issue 8 execution status

Repository identity: hossean787-blip/CodeStudio-Android. Owner-defined scope:
all26 approved corrections in Issue8, phased real implementation and evidence;
no placeholder completion, no untested main merges. Production publication,
paid services, new sensitive account/device access and new production signing
materials require explicit approval. No such approval has been given.

## Confirmed architecture and decisions (2026-11-05 UTC)
Kotlin/Compose native Android, package ir.sabagostar.sabastudio, min26. Room25,
existing project-scoped workspaces and encrypted provider credentials retained.
Issue8 overrides earlier Neon default: fresh installs use LIGHT/SLATE; explicit
saved themes remain. Internal resume uses persisted origin IDs, not text
matching; backupv6 separates audit while importing v1–5. Private notification
defaults protect lockscreen details. Key retries must not bypass provider limits.
Thirteen gitlinked AI sources are provenance/workspace imports, not APK engines.
No arbitrary vendored scripts or restricted modules are executed/copied.

## Verified evidence
- Phase0 head13d1b90:1953 JVM tests/298suites,0failed/errors/skipped; all Android
  instrumentation compile and Debug/non-debuggable Release build/signature pass.
- Same head emulator:4persist/migration/isolation pass, new sidebar case FAIL
  (expanded pinned footer starved chat viewport). Regression is retained.
- Prior373ae77 runs41933961871 and41933961945 succeeded ordinary+native Release,
  four persistence cases, CPU tiny-model JNI and Persian startup on emulator.
  These are not tests of later source or physical hardware.
- Live anonymous network probe: Kilo catalogHTTP200/381models/11free tool models,
  one cappedkilo-auto/free inferenceHTTP200 returned text. OutsideAPK only.
- Source-pin verifier13gitlinks pass. XML new11locale resource sets parse.
- Independent scoped source reviews completed; findings corrected for backup
  version assertion, debug evidence directory and footer/header feedback loop.

## Current source and pending checks
Main independently advanced from5dd7802 to83d9f4f via updatedPR7; fetched and
merged into phase0 without overwriting newer work. Our changes remain separate:
PR10 phase0 head2ef06a5; PR13 phase1 light/resume/diagnostics headae4e39a;
PR14 notification increment headbd745ee; provider-policy branch3241c56.
All amended heads require current CI/device acceptance before main merge.
Older successful checks are not reused as proof for changed app source.
Document export implementer works separately; see controller ledger and brief
under /workspace/scratch/issue8-briefs. Root owns tool approvals/checkpoints,
shared registration/workflows and final integration. OtherPR11/12 are untouched.

## Blockers and limitations
Original installed APK signing key is unknown (user does not know). Existing
CI alpha certificates are test signatures. No matching-signature or retained-
data physical-device upgrade proof exists; do not promise upgrade compatibility.
Authenticated providers/OAuth, paid/cloud media models, camera/microphone/phone
control and proprietary runtime adapters are not activated/tested. Historical
Code resume rows without origin metadata cannot safely be hidden from text alone.
Local Gradle download was refused; JVM/Android work runs in existing public CI.

## Next actions and rollback
1. Finish current amended phase0+phase1 CI, inspect emulator failures/screenshots
   and artifacts; fix actual causes, preserve all meaningful regressions.
2. Review/integrate native document exports with scoped permissions and real
   independent PDF/Office/image reader validation; continue remaining phases.
3. Merge only verified increments; produce exact signed Release manifest/hash,
   explicit capability status and blockers. No public app release without approval.
Rollback bundles: /workspace/deliverables/issue8-baseline.bundle and
issue8-main-refresh.bundle. Detailed requirements/evidence: delivery/issue-8/.
Controller recovery ledger: /workspace/scratch/issue8-ledger.md. No secrets here.

## Security ruling and native document increment (2026-11-05)
Automatic FileFs publication was rejected after a concrete directory-swap
counterexample. Its registration/capability/engine hooks were reverted before
main. Four static host filesystem passes were insufficient and are superseded.
Do not reactivate automatic create_document without a proven isolation design.
Nine native encoder JVM tests pass; python-docx/openpyxl/python-pptx independently
open the generated Office fixtures. PDF close/cancellation compile fixb01d526 is
prepared; full Android compilation/renderer tests still required. A user-directed
Android CreateDocument studio is being implemented separately; no claim allR09.
Phase0 a2c4 ordinary run41975534416 passed allfive emulator cases+Release startup;
newmaine7d7a79 runtimebridge was preserved and requires fresh mergedheadchecks.
Phase1 all8 emulator tests passed on552176b, including nativefreeinference,
but evidencepull failed after Gradle uninstalled the debug app. Confirmed AGP
leaveApksInstalledAfterRun option added so artifacts can be copied; CIpending.
