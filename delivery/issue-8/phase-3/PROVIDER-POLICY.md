# R-26 provider-limit correction (partial)

Existing saved-key recovery retried HTTP402/403/429 and quota error codes on
a second key. This contradicted Issue8's explicit no-limit-bypass constraint.
The corrected policy stops saved-key fallback on billing/credit, quota/rate,
forbidden and explicit region restrictions, including nested stream batches.
A separately saved valid credential may still recover a rejected401 or explicit
authentication error before any output. Normal opted-in per-request key order,
output/tool/usage replay barriers, disabled-key behavior and cancellation remain.
No new key is created or acquired. Cross-provider routing remains the existing
separate explicit privacy/cost-consent policy; it is not implicitly enabled.

Ruling: prioritize this narrow R26 compliance fix before final APK delivery,
even though most provider work belongs to phase3. Leaving it active would
contradict an explicit parent requirement. No working authenticated fallback
or user-saved provider is removed.

Prepared tests exercise both raw and nested batched failures for status402,
403,429, quota/credit/region codes and conflicting401/quota signals. They assert
one credential attempted, no retry event, original status/code retained and
all saved keys scrubbed. Legitimate auth recovery, partial-output barriers,
cancellation and configured request ordering remain covered. No live account,
paid request or quota exhaustion test is performed.

Validation so far: git diff --check passed; Kotlin/Android CI pending. This does
not complete provider key creation, all provider live tests, full API catalog,
or all26 requirements. No automatic uninterrupted failover is promised.
