# SpaceWise

**SpaceWise** is a lightweight, privacy-first storage management application for Android devices. Built using Kotlin, Jetpack Compose, and Material 3, the application provides an elegant, visual, and highly accurate analysis of local storage space.

The core promise of SpaceWise is absolute privacy: **all storage calculation, media analysis, and statistics calculations remain strictly on the user's local device.**

---

## 🔒 Privacy & Safety Promise

- **No Internet Access**: The application does not declare `android.permission.INTERNET` in its manifest. Your usage logs, files, and statistics can never be sent to a remote server.
- **Offline Analytics**: Every byte count is calculated in real-time using public Android framework APIs.
- **No Cloud Backups**: Device data extraction and cloud backups are disabled (`android:allowBackup="false"`) to protect internal cache statistics.

---

## 📱 Features

1. **Teal Space Dashboard**: A beautiful, modern home view featuring a customized circular Canvas-drawn donut chart representing categorized storage ratios (Apps, Photos, Videos, Audio, Downloads, and System residual storage).
2. **Interactive Breakdown**: A searchable category detail list with high-contrast Material 3 tags, precise size formatting, and visual progress indicators.
3. **Application Storage Explorer**: Scans package sizes (app binary size, user data, cache space) and matches them with live relative last-used timeframes to identify neglected apps.
4. **Interactive File Viewer**: Drills down into individual categories (Photos, Videos, Audio, and Downloads) to list representative large files.
5. **Privacy Explanation**: A transparent dashboard notice explaining that system partitions cannot be scanned due to Android security sandboxes.

---

## 🛠️ Native Storage APIs Used

- **`StatFs`**: Reads raw filesystem sector ratios for total, used, and free block counts.
- **`StorageStatsManager`**: Queries actual package binaries, caches, and user data blocks directly from the Android storage compiler.
- **`UsageStatsManager`**: Queries relative package visibility metrics to calculate the `Last used: today/yesterday/weeks/months ago` timeline.
- **`MediaStore`**: Queries the media database for exact image, video, audio, and documents sizes safely without requesting all-file disk modification access.

---

## 🛡️ Android Version Permission Behavior

SpaceWise implements granular adaptation across API boundaries:

- **Android 14+ (API 34/35)**: Handles partial visual media authorization (`READ_MEDIA_VISUAL_USER_SELECTED`) safely alongside full images, videos, and audio checks.
- **Android 13 (API 33)**: Requests modern granular permissions (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`).
- **Android 12 & Below (API 26-32)**: Automatically falls back to standard `READ_EXTERNAL_STORAGE` permission checks.
- **App Statistics**: Safe check flows verify if the user has granted settings access for `Usage Stats`. If missing, the app triggers a deep link directly to the system `Settings.ACTION_USAGE_ACCESS_SETTINGS` page.

---

## ⚠️ Known Limitations & Play Store Caution

- **Query All Packages**: SpaceWise utilizes `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>` in this internal developer/MVP build to calculate package-level statistics. If deploying to the Google Play Store, appropriate policy declarations must be submitted, or the visibility rules must be narrowed to standard target dependencies.
- **System & Other**: Modern Android security guidelines sandbox apps from viewing file paths inside core system spaces. SpaceWise calculates the "System & Other" sector mathematically (`usedBytes - sumOfKnownCategories`) and displays a clear explanation to the user instead of presenting mock lists.

---

## 🚀 Building & Running Tests

### Gradle Build
Compile the app into a debug APK:
```bash
gradle :app:assembleDebug
```

### Running Tests
Execute the comprehensive local unit test suite (testing storage category calculations, negative byte clamping, and boundary byte formatting):
```bash
gradle :app:testDebugUnitTest
```
