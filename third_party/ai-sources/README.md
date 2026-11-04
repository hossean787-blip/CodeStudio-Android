# Saba Studio — AI source preservation (phase 1)

This folder stores **13 original, full upstream source repositories** as **Git submodules pinned to exact 40-character commits**. Their contents remain completely unmodified and independently licensed. The original commit references are recorded in `ai-sources.lock.json`.

**This is source provenance, not feature integration:** These repositories are separate Android apps / agents; they are NOT AndroidHarness plugins and are NOT packaged into Saba Studio's APK. The existing plugin manager requires a validated `plugin.json` and cannot install these app repositories as plugins.

## Reproduce the full source trees

```bash
git clone https://github.com/hossean787-blip/CodeStudio-Android.git
cd CodeStudio-Android
git checkout integration/ai-harness-suite-20261105
git submodule update --init --recursive -- third_party/ai-sources
```

To prove an exact source pin, run `git -C third_party/ai-sources/lyra-code rev-parse HEAD` and compare with `ai-sources.lock.json`. Repeat for each entry. Do **not** automatically run scripts or binaries from external repositories.

## Status and constraints

- Full original source references mounted: **13/13** (Gitlinks; recursive checkout is required for physical files).
- Built into Saba Studio APK: **0/13**.
- Functionality migrated and device-tested: **0/13**.
- Source redistribution or linking to the Saba Studio APK is **not approved** for all repositories yet: AGPL/GPL are reciprocal; several repos have unverified/NOASSERTION licenses.
- Do not enable GitHub workflows to execute arbitrary upstream source code without a supply-chain security review. No credentials should be transferred.
- The base Saba Studio branch `main` was not modified. Snapshot commit: `5dd78026d594f0cc476dab1f1e12ef4e9963ab9b`.

## Next implementation gates

1. License compatibility and copyright notices for each project.
2. Capability-by-capability inventory and selection of standalone adapters (MCP, shell, provider connectors, phone tools, editor, runtime). Avoid embedding duplicate Android Applications.
3. Port isolated code for permissively licensed modules, under an explicit boundary; for copyleft/unknown licenses, design independently unless distribution obligations are accepted.
4. Unit, integration and Android device tests; permission and secret isolation; build signed APK; compare feature behavior to upstream.

**Never claim a project has been functionally integrated based only on its Git submodule.**
