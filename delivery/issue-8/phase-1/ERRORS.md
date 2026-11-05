# Provider diagnostics increment (R-01 / R-07, partial)

The existing troubleshooting page now distinguishes HTTP 401 authentication,
403 denied access, 403 with an explicit recognized provider region code,
402/429 credit or quota depletion, and transient 429 request limiting.
A generic 403 never asserts a region restriction. Structured HTTP status wins
over legacy explicit-HTTP message parsing; random request IDs do not classify.
The model-catalog response retains only a known machine-code allowlist from at
most 16 KiB of an unsuccessful body, never provider reason phrases or raw body.

Fixed advice is available in Persian and English; all 11 locale resource sets
contain the same new keys, with English fallback text outside Persian. This is
not full localization of every existing app screen. The existing diagnostics
report remains a safe state whitelist; its brand is now Saba Studio.

Tests prepared: catalog-to-diagnostics integration via a deterministic HTTP
interceptor, malformed and secret-bearing error JSON, status precedence,
forbidden-vs-region-vs-quota distinctions, and unrelated numeric text. No live
provider, key, OAuth account, paid request or regional bypass was used. Actual
provider generation remains untested; catalog access is never called generation.

Security: no raw error body or echoed key enters this diagnostic result. The
existing copied diagnostics report excludes credentials/endpoints/chat/file
content. Other legacy error paths are outside this increment and still require
review. Rollback: revert the dedicated provider diagnostics commit.

Validation so far: all 11 new XML resource files parse; git diff --check passes.
JVM/Android checks are pending for this increment.
