# MyShelf branding and release readiness

Reviewed 2026-10-20 against Phase 1 commit b8e86c3e042cf179b3c0e4b655850d19624d4696.

## Decision

Branding-only compatibility update. Keep the Android namespace `com.aryan.reader` and OSS installation ID `com.aryan.reader.oss`. A private/premium experience does not require a package rename. A new ID such as `com.narinder.myshelf` would install alongside the old app with a separate sandbox; it does not inherit books, Room data, URI grants, bookmarks or annotations. Defer that until an explicitly tested export/import migration exists.

Changes: non-translatable launcher, OSS display and About names become MyShelf; copied APK filenames start MyShelf; versionCode rises from 64 to 65. Version name remains 1.0.58-oss. No database/schema, reader engine, data path, provider authority, backup policy, signing configuration or dependency changes.

## Inspection findings

- Manifest application label references `@string/app_name`; MainActivity has no label override. MainActivity class and launcher component remain unchanged.
- Default application ID is com.aryan.reader; OSS adds .oss. Debug has no additional applicationIdSuffix, so OSS debug and release address the same installation ID.
- FileProvider and startup authorities, and external-open task affinities, use `${applicationId}`. Preserved.
- Existing icon is the upstream blue fan/book mark. Adaptive resources reference a solid background, PNG foreground and monochrome vector, with legacy square/round PNGs across five densities. This is technically provisioned for themed launchers but is NOT original MyShelf branding. It is intentionally retained in this small change: generator, shared in-app artwork, splash/notification usages and existing icon contracts must move together in a dedicated design change. No new icon or rendered launcher QA is claimed.
- Residual Episteme text exists in localized About/support/account/Pro text and shared/desktop surfaces. No blanket replacement: upstream credits, license, service identities and legal notices must remain accurate. Full independent visual/text branding remains unfinished.
- Existing release signing reads MYAPP_RELEASE_* properties from local.properties; no keystore is supplied or changed. Never commit signing secrets. Default debug signing is machine-local and is not a durable cross-machine update identity.
- Backup rules include reader_database, preferences and annotation directories. Cloud backup does not include private book/audio originals; device-transfer rules do. Neither is a substitute for a tested export and backup before changing identity/signers.
- Phase 1 uses a 600dp rail breakpoint, saveable search/filter/section state and light/dark library surfaces. MainActivity handles orientation/screenSize/screenLayout configuration changes. These source observations do not establish correct reader viewport or anchor restoration at runtime.

## Update compatibility gate

Before installation, compare the actual installed APK and candidate APK, not only the source:

```sh
adb shell pm path com.aryan.reader.oss
# Pull the base.apk path printed above, preserving a copy of the installed build.
adb pull /path/from/pm/base.apk installed-MyShelf.apk
apksigner verify --print-certs installed-MyShelf.apk
apksigner verify --print-certs app/build/outputs/apk/oss/debug/app-oss-debug.apk
apkanalyzer manifest application-id installed-MyShelf.apk
apkanalyzer manifest application-id app/build/outputs/apk/oss/debug/app-oss-debug.apk
apkanalyzer manifest version-code installed-MyShelf.apk
apkanalyzer manifest version-code app/build/outputs/apk/oss/debug/app-oss-debug.apk
```

Require matching package and signer certificate SHA-256 and a candidate versionCode above the installed version. Code 65 is above the documented Phase 1 code 64, not necessarily above an unseen installed build. A changed signing certificate normally blocks an in-place update; do not uninstall to bypass it. Preserve the original debug keystore if that APK is already installed. Moving to a new release signer requires a separate migration/signing plan. Do not claim data preservation from source inspection alone.

After a tested backup, install the compatible candidate using `adb install -r <candidate.apk>` (no uninstall, clear-data or downgrade flags). Verify all library and annotation state after upgrade. For durable personal releases, retain an encrypted backup of one stable release keystore and record certificate fingerprint, package, versionCode, source SHA and APK SHA-256 for each release. Publish an APK as a release/CI artifact rather than committing binaries. This change does not produce a signed release.

## Validation performed in this environment

- Baseline `bash ./gradlew :app:assembleOssDebug --no-daemon --console=plain` failed before Gradle configuration while downloading gradle-9.3.1-bin.zip: `java.net.SocketException: Network is unreachable`.
- Only JDK 17 was found; project requests JDK 21. No adb executable was available. Android SDK/NDK readiness is not established.
- Python XML/source checks verified all three display names, unique non-translatable app_name, no localized app_name override, manifest label/icon references, adaptive icon layer references, five-density PNG presence, unchanged package/suffix and versionCode 65. `git diff --check` passed.
- No APK build, Kotlin compilation, Gradle unit tests, APK signer comparison, emulator run or physical-device run completed in this review. Previous 19-test and APK results belong to the Phase 1 report, not this patch.

On a configured JDK21/SDK/NDK machine, run assembleOssDebug and focused testOssDebugUnitTest filters for AndroidLauncherIconContractTest, AndroidBackupRulesContractTest, UnifiedLibraryScreenTest, MyShelfHomeTest and MyShelfBookPresentationTest. Inspect the merged manifest/APK label and signature. These commands are pending, not passed.

## Z Fold device acceptance matrix (all NOT RUN)

Use the actual device dimensions, Android/One UI version and font scale in the evidence. Start with one PDF and one EPUB, a collection, a bookmark and an annotation/note in each supported reader. Record exact location and take screenshots before transitions.

| Scenario | Required acceptance |
| --- | --- |
| In-place upgrade | Same package/signer; launcher shows MyShelf once; counts, collections, highlights, notes, bookmarks and folder grants survive; no re-import needed. |
| Folded portrait/light | Covers, long titles, search, filters and bottom navigation fit; no overlap with system bars or keyboard; open PDF and EPUB. |
| Unfolded portrait/landscape | Rail follows available window width; grid adapts without stretched covers; selection/search/filter/scroll survive; no clipping. |
| Dark mode | Toggle system/app theme on each display while in library and each reader; readable text/dialogs, correct cover treatment, no unintended light flashes. |
| Split-screen | Resize on both displays across 600dp where supported; no crash, lost selection or inaccessible controls; keyboard and insets respected. |
| PDF fold/rotation | At a known page and zoomed vertical offset, annotate then fold/unfold/rotate repeatedly. Same page and meaningful viewport anchor remain; highlight coordinates stay aligned. |
| EPUB fold/rotation | Record chapter and a sentence, then fold/unfold/rotate and resize. Same content anchor returns after repagination; page number may change. Bookmarks/highlights still point to the same text. |
| Resume/process recreation | For both formats, Home/back/reopen, force-stop/relaunch and process recreation preserve reading anchor and annotations. Do not use clear-data. |
| Accessibility | Large text and TalkBack on both displays: readable labels, focus order and usable touch targets; no truncated essential actions. |

Capture actual results with device/build identifiers, before/after screenshots and repro details for failures. Keep Phase 1 PR #2 draft and withhold release-ready status until these gates pass. Branding is improved; independent icon branding, reproducible signed builds and device acceptance remain outstanding.
