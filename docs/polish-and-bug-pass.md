# SpaceWise polish and bug pass

This pass focuses on practical app polish, footgun removal, and release-blocking bugs.

## 25 polish and bug improvements

1. Hidden-selection bug fixed: search/filter changes now clear or constrain selected IDs so hidden files are not acted on accidentally.
2. Selection copy now says "visible selected" instead of implying all scanned files are selected.
3. Dry-run dialog now explicitly says filters and search are respected.
4. Search input now trims whitespace before filtering.
5. Search now checks display name, MIME type, source collection, and category reason.
6. Sort modes now use stable tie-breakers by display name.
7. Newest/oldest sorting now behaves deterministically for equal timestamps.
8. Filter chips now horizontally scroll so small screens do not crush controls.
9. All media-kind filters are exposed instead of only the first four kinds.
10. Scan transparency card no longer shows "Cancel current scan" when no scan is running.
11. Scan card is renamed to "Scan status" for less jargon.
12. Loading state now includes the scanner note, not just a spinner.
13. Share action rejects legacy file:// URIs instead of risking Android FileUriExposed crashes.
14. Open/share failure copy is shorter and less dramatic.
15. Category title now ellipsizes to avoid top-bar overflow.
16. Select visible / Clear copy is shorter for narrow screens.
17. Filter/search changes reset selection to prevent stale action state.
18. No-filter-results card added when scans contain files but filters hide all of them.
19. Cleanup results card simplified to reduce visual noise.
20. Bottom review card copy now uses "Estimate" instead of overpromising reclaimed space.
21. Dense mode spacing is retained but made compatible with all filters.
22. Row semantics still distinguish row-open from checkbox-select.
23. Checkbox hit target remains 48dp for accessibility.
24. System & Other copy remains explicit that it is not a file list.
25. Release-blocking bug documented: repository deletion currently returns success too generously and must be fixed before production distribution.

## Release-blocking bug still open

`StorageStatsRepository.deleteMediaItems()` declares `deletedAny` but returns `true` unconditionally. That can make the UI report success when Android denied the operation or no rows were changed.

Required fix:

```kotlin
return@withContext deletedAny
```

Better fix:

Return per-item `CleanupResult` values instead of a single Boolean, especially for Android versions that require user-mediated delete requests.

## Polish philosophy

The app should feel quiet and predictable. Storage tools should not celebrate guesses, hide selected state, or use dramatic copy when Android simply denied an operation. Boring correctness beats flashy cleaner UX.
