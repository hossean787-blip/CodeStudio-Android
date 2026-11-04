# Notification increment (R-04 / R-05, partial)

The ongoing agent notification now uses the app_name resource (صبا استودیو),
not the upstream hardcoded title. Eight known run-control states resolve to
existing localized resources, including Persian. User/model/tool-generated
free-form text is not machine-translated or falsely labeled. Existing localized
channels, real runtime activity state, Stop/approval actions and optional
notification permission behavior are retained.

Foreground, result and action-needed notifications request PRIVATE lockscreen
visibility; new action channels use PRIVATE instead of PUBLIC. Android and user
channel settings remain authoritative. No private chat/tool detail is added to
a public notification version.

Prepared Android integration test posts the real foreground service notification,
checks app brand/status/privacy, and verifies six distinct control-state
translations with a Persian resource context. This is not a physical-device,
OEM promotion, or all-free-form-text localization test. Existing Live Update
promotion tests stay intact (their API36 requirement exceeds this API35 CI).

Validation: git diff --check passed. Kotlin/Android execution pending. No account,
paid API, new device control or microphone/camera access was used.
