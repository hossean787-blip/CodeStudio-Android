# Issue 8 R-02 isolated implementation

Status: implemented and committed; Android/Kotlin execution BLOCKED locally. Not merged or pushed. Root remains clean at 13d1b9010a68ce81d0cd8ed48849121002713266.

Worktree: `/workspace/CodeStudio-Android-issue8-resume`
Branch: `issue8-r02-internal-resume`
Commit range: `13d1b90..1d27b80` (one commit, `1d27b80 fix(issue8): keep resume controls out of visible conversations`).

## Behavior and design

Root cause: RunManager only assigned the persisted internal continuation ID to Chat resumes; Code resumes got random ordinary IDs. Existing search SQL already excluded the reserved prefix. The UI filtered it, but recall snippets and answer previews/prompt selection could still expose or select controls.

Both modes now use RunRecovery.continuationMessage(), with the existing reserved `internal-chat-continuation-` ID prefix. MessageVisibility centralizes that metadata contract. This is durable app-generated origin metadata: Room IDs, version archives, and imports preserve it verbatim. No text matching, Room schema change, destructive migration, or general user text rewriting is used. The role remains USER for provider context. Complete repository messages/history/audit remain intact. UI presentation and recall snippets exclude controls, answer-version previews hide them, and retry/archive/fork/title repair select ordinary user messages. Existing reserved-ID legacy Chat data stays recognized after reload.

Backup format v6 is necessary to satisfy visible exports while preserving recovery audit. `BackupSession.messages` excludes reserved-ID controls; indexed `recoveryAudit` records retain their complete payload, original IDs, and order (including identical timestamps). Import reconstructs the full sequence. Version 1–5 files still decode; reserved legacy IDs are normalized into audit without changing ordinary user text. Encoder always writes v6, so older apps reject the newer format instead of silently dropping audit fields. Full backup JSON intentionally includes controls in the clearly separate audit section, never in the visible message list. Invalid audit records that attempt to reclassify a non-reserved ID are rejected. Import result counts include audit rows.

## Coverage added/prepared

- TaskControlTest: same literal user text remains visible; app-generated controls have unique durable IDs; legacy marker recognized.
- ChatBackupTest: audit partition/reconstruction with controls before and between equal-timestamp messages; v5 legacy import and v6 re-export; preservation of exact user text; malformed audit rejection. Existing v1 compatibility/newer-version rejection tests remain.
- InternalResumeDeviceTest: isolated disk-backed Room database, separate projects/sessions for CHAT and CODE, close/reopen, full history/audit retained, FTS and substring search retain only ordinary user same-text messages, answer archive/preview, first-user selection, and an actual AgentEngine with local fake provider verifies recovery controls still reach provider context. No external API calls.
- ChatExperienceDeviceTest existing UI fixture now inserts controls in both modes and asserts neither appears on the rendered mode's screen.

## Tests actually run

PASS: `git diff --check` before commit and `git diff HEAD^ HEAD --check` after commit, exit 0.

PASS: Python sqlite3 in-memory FTS4 and substring regression exercise: reserved-ID control plus ordinary same-text user in separate chat-project-a/code-project-b fixtures; both queries return only the ordinary user for the selected session. This checks SQLite query behavior, not Kotlin/Room compilation or Android UI.

BLOCKED: `./gradlew :app:testDebugUnitTest --tests 'com.androidharness.app.agent.TaskControlTest' --tests 'com.androidharness.app.data.ChatBackupTest'`: wrapper attempted to download Gradle 9.7.0 and failed with java.net.ConnectException: Connection refused before task configuration/tests.

BLOCKED: second Gradle attempt with `:app:testBenchmarkUnitTest` had the identical wrapper-download failure. That variant requires `-PcardBenchmark`; use the debug unit selector below for normal CI.

Observed runtime: `/usr/bin/java`, OpenJDK 21.0.12.1. The brief's missing-JDK assumption does not match this isolated environment. Android SDK availability was not established because the wrapper could not start. No actual Android, emulator, build, Kotlin compilation, CI execution, or red/green cycle claimed.

## CI/integration instructions (NOT RUN)

After root phase0 review, cherry-pick `1d27b80` and update root-owned imported-source audit allowlist for the original source paths listed below. Then run:

```
./gradlew :app:testDebugUnitTest --tests 'com.androidharness.app.agent.TaskControlTest' --tests 'com.androidharness.app.data.ChatBackupTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.androidharness.app.data.InternalResumeDeviceTest,com.androidharness.app.ui.chat.ChatExperienceDeviceTest
```

For the benchmark test variant, add `-PcardBenchmark` and use benchmark task names. No workflow or audit allowlist was modified here.

Original main-source paths requiring allowlist review/update:

- `app/src/main/java/com/androidharness/app/agent/RunManager.kt`
- `app/src/main/java/com/androidharness/app/agent/TaskControl.kt`
- `app/src/main/java/com/androidharness/app/data/ChatBackup.kt`
- `app/src/main/java/com/androidharness/app/data/SessionRepository.kt`
- `app/src/main/java/com/androidharness/app/data/db/Database.kt`
- `app/src/main/java/com/androidharness/app/tools/ChatRecallTools.kt`
- `app/src/main/java/com/androidharness/app/ui/chat/ChatViewModel.kt`

New main source: `app/src/main/java/com/androidharness/app/core/MessageVisibility.kt`.
Other modified original files: `app/src/test/java/com/androidharness/app/agent/TaskControlTest.kt`, `app/src/test/java/com/androidharness/app/data/ChatBackupTest.kt`, `app/src/androidTest/java/com/androidharness/app/ui/chat/ChatExperienceDeviceTest.kt`.
New device test: `app/src/androidTest/java/com/androidharness/app/data/InternalResumeDeviceTest.kt`.

## Security/isolation and remaining limitations

No paid APIs, credentials, sensitive files, external writes/publication, agents, main edits, push, or merge. Own linked worktree only; root phase0 remained clean on final check. The device fixture uses a unique temporary DB and directory, fake provider, and deletes both; project/session identity remains explicit on reload.

Historical Code controls already persisted without any origin marker are indistinguishable from a user typing the same words. They remain visible rather than risk hiding ordinary user text; safe automatic retroactive classification is impossible from those rows alone. Future Code controls and all legacy reserved-ID Chat controls are covered. A manually authored/imported row with a reserved app ID is treated as internal by the established contract; ordinary UI input never supplies such IDs.

The format increment intentionally prevents older app versions importing new v6 backups; migration is forward-compatible with existing v1–5 backups, not downgrade-compatible. Required Kotlin and device checks remain unrun; do not merge until CI validates them. Full SAF manager export/import roundtrip is not exercised by the new device fixture; pure backup codec roundtrip/order is covered by prepared JVM tests, and manager reconstruction is wired to allMessages().

## Scoped review correction

Commit `f8e31fc test(issue8): expect backup format version six` changes only the stale `ChatBackupTest` assertion from 5 to 6. The encoded-version assertion remains. Inspected every other version literal in that test: v5 input/re-export fixture intentionally proves legacy normalization and forced v6 encoding; v1 fixtures retain legacy decode/invalid-format coverage; v999 retains newer-version rejection. No other changes needed.

Actual checks: `git diff --check` PASS; reviewed one-line diff and all version literals with `rg`; working tree clean after commit. Gradle was not rerun as instructed; Kotlin/Android execution remains blocked/unverified. Updated commit range: `13d1b90..f8e31fc` (implementation `1d27b80`, scoped correction `f8e31fc`).
