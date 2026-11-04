# Saba Studio — execution evidence (in progress)

Baseline: `1f762648f1fb026b5cf938165b396222a466dc18`.
Changes are isolated on `codex/saba-stabilization-20261104` until validation.
See CAPABILITIES.md for native / external / archived feature mapping.

## Observed checks

- `python3 scripts/test_message_truncation.py`: 4 passed. Executes the actual Room truncation SQL on host SQLite; not Android runtime coverage.
- `npm ci --ignore-scripts --prefix scripts/editor-tools && npm test --prefix scripts/editor-tools`: 7 passed, semantic references, outline, completion, rename and formatting.
- `python3 scripts/verify_keyless_ai.py --free-inference`: HTTP 200, catalog 381 models / 11 free tool models; one capped anonymous `kilo-auto/free` request returned text. Evidence: keyless-report.json. This is external API inference from the build environment, not on-device UI inference.
- Full JVM suite first run: 1943 executed, 1941 passed, 2 stale provider-directory expectation failures. Corrected exact provider roots and ordering expectations without altering valid endpoints.
- Source scan for known API/private-key literal shapes in app/src/main: no matches. Not a comprehensive security audit.

## Explicit limitations

No physical Android device is attached. ChatAnywhere and other keyed providers
cannot be verified without authorized personal credentials. FCC Python, desktop
CLIs, Discord and voice extras are not embedded Android capabilities.

The original alpha Release uses the build runner's Debug certificate while the
application is non-debuggable. The previous private signing key is unavailable.
GitHub Secrets API returned `403 Resource not accessible by integration`, so a
persistent confidential signing key cannot be installed with current access.
No private key is committed or uploaded as a public build artifact. Installing
over a previous alpha may fail with signature mismatch; do not uninstall an
existing app without backing up its data.

Final artifact identifiers, checksums, CI links and remaining failures will be
recorded after the in-flight verification jobs finish.
