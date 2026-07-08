# iOS Setup

The shared module now declares iOS targets:

- `iosArm64`
- `iosSimulatorArm64`

The generated Kotlin framework is named `ReaderShared`.

## PDFium

PDFium iOS binaries are kept local under `third_party/pdfium`, which is ignored by git.

Expected local layout:

```text
third_party/pdfium/ios-device-arm64/
third_party/pdfium/ios-simulator-arm64/
third_party/pdfium/ios/PDFium.xcframework/
```

The current local binaries came from:

- `/Users/aryan/Downloads/pdfium-ios-device-arm64.tar`
- `/Users/aryan/Downloads/pdfium-ios-simulator-arm64.tar`

Both archives report PDFium `152.0.7934.0`.

To recreate the XCFramework after extracting those archives:

```sh
sh scripts/ios/create-pdfium-xcframework.sh
```

## Next Build Step

Install or configure a JDK before running Gradle on this Mac. Once Java is available, verify the shared iOS framework with:

```sh
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

The repository's current `gradlew` file has CRLF line endings and is not executable in this checkout. If that is still true locally, normalize it or run a temporary normalized copy before building.
