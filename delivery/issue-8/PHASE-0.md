# Phase 0 execution evidence (acceptance pending)

Main baseline was 5dd7802. During execution another actor merged updated PR7;
origin/main83d9f4f was fetched and merged into this worktree without rewriting
history. This merge added source-provenance documentation and verification
checks. Original rollback bundle plus a refreshed origin/main bundle remain
available; no unrelated PR11/12 work was modified or merged.

## Actual results
Head13d1b9010a68ce81d0cd8ed48849121002713266:
- Full JVM suite: 1,953 tests / 298 suites, zero failures/errors/skips, parsed
  from exact run41934975086 artifact13373348885 JUnit XML.
- All Android instrumentation sources compile; Debug and non-debuggable alpha
  Release build/signature/identity verification succeeded in that run.
- Four persistence/migration/isolation emulator cases passed. Added combined
  sidebar-navigation case FAILED: expanded Connections footer plus fixed header
  consumed the whole chat viewport on Pixel2. No test was removed or weakened.
- A prior run41933961871 on373ae77 (before adding sidebar case) passed its four
  persistence tests and exact Release Persian startup smoke.
- Native run41933961945 on373ae77 passed Release native build, exact APK identity,
  JNI CPU tiny-model probe and Persian startup smoke on emulator. Not hardware.
- Local source-pin verifier passed all13 gitlinks; no engine code was executed.

## Current correction
Sidebar now uses actual measured header/footer heights to scroll its footer
with the chat list when fewer than144dp would remain for chats; it retains
docked controls where content fits. Header density still depends only on the
viewport, avoiding selection-mode measurement oscillation. This fixes a product
layout defect rather than removing the failing assertion. Same five-case
emulator selection and all previous checks must pass on the amended tree.

## Functional and security limits
Skills/Connections routes are preserved; the navigation test does not prove
Gmail/Drive OAuth or external service operation. Drive uses explicit SAF file
selection; Gmail uses an Android composer. Thirteen AI repositories remain
pinned source references, not engines linked into the APK. Restricted sources
remain disabled. Production signature compatibility remains unknown because
the installed version's private signing key is unavailable/unknown. CI alpha
signing is test signing; no retained-data real-device upgrade was tested.

No Phase0 acceptance or main merge is claimed until amended checks pass.
