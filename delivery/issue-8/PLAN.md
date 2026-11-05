# Issue 8 — execution and acceptance plan

Baseline: see BASELINE.json. Work is isolated; no unchecked merge to main.
The parent Issue is preserved verbatim in MASTER.md. All 26 requirements
remain individually tracked in requirements.json; presence in source is not
functional verification.

- Phase 0: semantically integrate existing PRs 5/6/7 in this validation branch;
  fix Android-test callback compilation; verify audio cancellation fixture;
  compile ALL instrumentation sources before lengthy APK builds; validate
  pinned-source licensing/import restrictions; run complete JVM + selected
  emulator persistence, navigation, import and startup tests.
- Phase 1: shared truthful provider error/recovery classification; internal
  resume events separated from user messages; light minimal Persian UI and
  notifications; project/session instruction precedence and isolation.
- Phase 2: real local document/file export and preview; reviewed instruction
  Skills imports; honest SAF/mail/MCP states; private project-scoped library
  and retrievable knowledge with source provenance. No cross-project reads.
- Phase 3: explicit API-management route, official provider configuration,
  constrained agents, cost/privacy-aware failover and curated free API catalog.
- Phase 4: modular real scene/layer/timeline/export tools. Cloud generation,
  media APIs and device-control permissions remain not configured until
  explicitly authorized and successfully tested. A video is not a 3D model.
- Phase 5: regressions, security and permission review, Persian screenshots,
  production signing configuration only with explicit approval, real upgrade
  verification with a compatible original signing key and retained data.

Each phase gets a reviewable commit/PR, test ledger, artifact manifest,
security/localization review and blockers. Shared application/manifest/
workflow integrations are coordinated in this worktree; other projects and
existing PR branches are not modified. No arbitrary vendored scripts execute.

## Phase 0 findings

- PR 5 emulator compile: missing required onSkills in two sidebar UI tests.
- PR 6 emulator compile: missing onIntegrations in the same tests.
- Semantic conflict: preserve BOTH callbacks/routes and source-audit entries.
- PR 7 historical import signature regression was corrected in head 96819c6;
  verify compilation of GitHubDialogs with the retained original API.
- PR 6 native JVM failure: GroqWhisperClientTest line 103 waits for server-side
  closure. Its fixture swallows TCP reset and never resolves that future.
  Fully consume the multipart POST before replying, recognize EOF/reset only
  after headers are sent, and propagate unrelated fixture errors. Keep the
  1-second cancellation and 3-second peer-closure deadlines unchanged.
- The persistent release signing key is not configured in main. Prior CI APKs
  used per-run Debug certificates. The user does not know the installed key.
  Do not claim upgrade compatibility or create/use a production key silently.

## Cost and access boundary

Existing public-repository hosted CI, local deterministic processing and
read-only public metadata are used for verification. No paid API, new hosting,
trial, account-wide OAuth scope, private repository clone, camera/microphone
or phone-control access is activated by this coding task. Such execution
requires explicit opt-in. CI test signing is not production signing or proof
of compatibility with the user's installed APK. Public store/GitHub Release
publication is not authorized; review branches/artifacts are task outputs.

## Source rights boundary

The preserved main baseline permits all 13 pinned source-only workspace imports.
Syntac (UNVERIFIED) and proprietary-derived OpenClaude require an explicit license
acknowledgment before cloning; they are not categorically disabled. Cloning or
acknowledging a warning grants no redistribution/relicensing rights. No source
implementation is linked into this APK. AGPL/GPL modules still require a rights
and compatibility review before linking/distribution. The optional runtime bridge
uses separately installed, explicitly enabled CLIs; no CLI/account is activated
by this verification. Git submodules are NOT initialized by CI.
