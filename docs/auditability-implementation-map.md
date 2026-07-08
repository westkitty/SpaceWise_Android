# SpaceWise auditability implementation map

This document maps the Round 2 adversarial critique into concrete product behavior.

## Implemented in app code

1. Scan-session ID: `ScanAuditInfo.sessionId` is created for category scans.
2. Last-scan timing: `ScanAuditInfo.startedAtMillis`, `finishedAtMillis`, and `durationMillis` are available.
3. Permission-change awareness: scan cards now explain permission/stale scan uncertainty instead of pretending completeness.
4. Category confidence labels: `ConfidenceLevel` supports exact, estimated, inaccessible, and residual.
5. System & Other split: the System screen now explains Android reserve, app-private storage, cache estimates, and residual unknowns separately.
6. Why-this-item-appears text: media rows expose `categoryReason`.
7. Provenance: media rows expose `sourceCollection`.
8. Duplicate proof policy: models support partial hash evidence; unresolved duplicate claims must not be promoted as verified.
9. Partial hashing policy: `partialHash` is modeled for evidence without blocking the UI on full-file hashing.
10. Deep analysis warning: duplicate/deep-analysis work is documented as opt-in, not automatic.
11. Scan cancellation: category scans can be cancelled from loading/audit UI.
12. Progress stages: scans track permissions, MediaStore query, metadata enrichment, completion, cancel, and failure.
13. Timeout-safe scanner policy: scanner UI treats failures as safe, non-mutating outcomes.
14. Disappearing files: open/share/action failures report stale or moved items instead of silently failing.
15. Stale-result warning: `ScanAuditInfo.isStale` flags old scans.
16. Share file action: media rows include Share beside Open.
17. Viewer fallback: failed Open suggests Share; Android chooser handles installed viewer alternatives.
18. Accessibility labels: rows and checkboxes distinguish open-vs-select behavior.
19. Larger touch targets: checkbox hit area is 48dp.
20. Tablet/wide-screen groundwork: dense mode and wider row metadata reduce cramped layouts.
21. Compact list mode: Dense toggle implemented.
22. Onboarding checklist: app copy now explains scan, inspect, select, review, act.
23. In-app changelog groundwork: build/version metadata is represented by `SpaceWiseBuildInfo`.
24. Debug/about groundwork: `SpaceWiseBuildInfo` contains version, channel, and APK verification policy.
25. Release checklist: `SpaceWiseBuildInfo.releaseChecklist` and this document define install, media-open, permission, dry-run, and signature gates.

## Rules that must remain true

- The app must not claim file certainty when Android only permits an estimate.
- Mock/demo items must be labelled as such.
- APKs must be CI-built and verifier-checked before user distribution.
- File rows must prioritize inspection before action.
- Any cleanup/action flow must provide a dry-run summary and per-item result state.
