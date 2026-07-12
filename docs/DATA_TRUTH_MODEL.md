# SpaceWise Data Truth Model

SpaceWise must never present generated, inferred, stale, or inaccessible information as a verified device finding.

## Confidence classes

### SYSTEM_MEASURED
Read directly from an Android system API representing current device state.

Examples:
- `StatFs.totalBytes`
- `StatFs.availableBytes`

### MEDIASTORE_MEASURED
Read from rows currently visible to the application through `MediaStore`.

Limitations:
- visibility depends on Android version and granted permission scope;
- Android 14 selected-media access is partial;
- inaccessible or sandboxed files are not represented.

### PACKAGE_STATS_MEASURED
Read from `StorageStatsManager` or `UsageStatsManager`.

Limitations:
- requires Usage Access;
- package visibility may be restricted;
- OEM behavior can differ;
- a failed query is not equivalent to zero bytes.

### ESTIMATED
Derived from measured values through a documented formula.

Rules:
- label the value as an estimate in the UI;
- document the formula;
- never use an estimate to claim that a specific file exists;
- never present estimated bytes as already reclaimed.

### PARTIAL
The app has access to only part of a category.

Example:
- Android 14 selected visual media permission.

The UI must say that results are partial and must not describe them as the full device library.

### UNAVAILABLE
The value cannot be read because permission is missing, an API is unavailable, or a query failed.

The UI must distinguish:
- permission not granted;
- access is partial;
- no matching items exist;
- query failed;
- feature is unsupported.

### SIMULATED
Generated demonstration data.

Simulated data is prohibited in production storage findings. It may exist only in previews, tests, screenshots, or explicitly labeled demo builds.

## Deletion contract

A deletion request is not a successful deletion.

For every cleanup operation, report:

- number requested;
- number verified deleted;
- number still present;
- number skipped;
- verified reclaimed bytes;
- whether Android confirmation is still required;
- recoverable or permission-related failures.

Only entries absent from a fresh source query may contribute to reclaimed-byte totals.

## Historical data contract

Storage history must come from persisted, timestamped snapshots.

Do not generate retrospective curves from the current value. Until at least two real snapshots exist, show an empty state explaining that history will appear after future scans.

## Recommendation contract

Recommendations must be based on visible measured items.

Allowed:
- a specific large visible video;
- a specific large visible download;
- an app with a measured size and an actual old last-used timestamp.

Not allowed:
- fixed generic savings values;
- an assumed percentage of total app size presented as cache size;
- invented duplicate files;
- invented system logs;
- invented cloud-sync status;
- fabricated device paths.

## Permission minimization

Each feature should request only the access it needs.

- Photo analysis: image access.
- Video analysis: video access.
- Audio analysis: audio access.
- App usage: Usage Access.
- Package inventory: narrowly scoped package visibility where possible.

A refusal should degrade one feature, not disable unrelated measurements.

## Release gate

Before release, verify:

1. No production screen contains simulated storage findings.
2. The manifest does not request internet access unless the product direction explicitly changes.
3. The dependency graph contains no unexplained network or cloud SDKs.
4. Every displayed value has a documented source and failure state.
5. Deletion results are verified through a fresh query.
6. Android 14 partial access is visibly labeled partial.
7. Missing permissions never produce misleading zero-byte claims.
8. `QUERY_ALL_PACKAGES` has been removed or has an approved distribution justification.
9. Debug signing material is not used for release distribution.
10. Unit, UI, and physical-device tests cover permission denial, partial access, query failure, and failed deletion.