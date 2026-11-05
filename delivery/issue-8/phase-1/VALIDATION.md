# Phase 1 increment validation ledger

Prepared changes: R-02 future chat/code resume visibility and durable audit;
R-01/R-07 precise provider catalog diagnostics; R-06/R-10 light neutral defaults.
These are scoped increments, not completion of the whole Phase 1 or Issue 8.

## Executed
- Isolated source diff checks: pass.
- Eleven new diagnostic resource XML sets: parsed successfully.
- Resume search SQLite FTS/substr fixture: pass (not Android/Room execution).
- Anonymous live network check: `keyless-network-check.json`, Kilo catalog 200
  and one capped `kilo-auto/free` inference 200 with returned text. Outside APK;
  no credentials, paid API or private prompt. Other providers untested.
- Independent source reviews: light-default and diagnostics increments pass
  scoped review; resume backup expected-version finding fixed and re-reviewed.

## Required CI, pending for this phase
Full JVM suite; compile all instrumentation; eight emulator cases covering
four persisted-workspace regressions, combined Skills/Connections callbacks,
resume repository/engine reload, visible Chat/Code resume behavior, and one
explicitly opted-in anonymous free inference through the native streaming
adapter. The free test refuses auth headers, redirects and other API hosts;
no retry, no account credentials, only synthetic text and 512 output tokens.
Exact non-debuggable Release installation, Persian welcome screenshot and
startup crash check; independent native Release/JNI smoke.

## Limits and data
No physical device test, authenticated OAuth/provider request, production
signature compatibility or retained-data upgrade proof is claimed. Existing
explicit theme choices and Room schema remain intact. Backup v6 imports v1–5;
older app versions intentionally reject v6 so recovery audit cannot silently
be lost on downgrade. Historical Code controls without origin metadata stay
visible rather than hide indistinguishable real user text.

All 26 remain individually tracked; each needs its own acceptance evidence.
