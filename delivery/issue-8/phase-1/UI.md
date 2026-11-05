# Approved light UI increment (R-06 / R-10, partial)

Issue 8 explicitly supersedes the prior Neon-default request with a light,
minimal Persian interface. This increment uses the existing neutral SLATE
Material palette and LIGHT mode for missing settings and backup fields.
Explicit SYSTEM/DARK/AMOLED, Neon/custom palettes and dynamic-color choices
remain unchanged on upgrade. No data migration or preference reset occurs.

Checks prepared: existing every-palette light/dark/AMOLED contrast assertions,
backup/default/explicit-selection round trips, all instrumentation compile,
and actual Release Persian welcome screenshot on an emulator whose system is
set to dark (the app's new default remains light). The screenshot is named
release-fa-light.png. Manual screenshot inspection is required before calling
UI acceptance complete. This is not completion of all Persian localization,
all sidebar flows, or all 26 requirements.

Security: no credentials, account access, permissions, network service, package
ID or Room schema changes. Rollback: revert the isolated UI increment commit;
explicit stored user preferences are preserved by both versions.

Test status: prepared, not yet executed for this increment.
