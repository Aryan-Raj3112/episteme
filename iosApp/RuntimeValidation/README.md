# iOS runtime validation

This directory is the reproducible runtime checklist for the iOS parity work. It is deliberately
separate from production code and does not add simulator-only branches, coordinate-based UI tests,
or copies of large document assets.

## Why this is a checklist rather than an XCTest target

`iosApp/Reader.xcodeproj` currently contains one target (`Reader`). The app UI is a Kotlin
Multiplatform Compose surface hosted by SwiftUI, and the project has no native XCTest/XCUITest
target, stable native accessibility identifiers for every shared surface, or iOS fixture bundle.
The remaining flows also cross native file importers, security-scoped URLs, StoreKit, Firebase,
PDFium, WKWebView, and AVAudioSession. Adding a UI target now would either require brittle screen
coordinates or production-only fixture/test hooks. Those tests would be weaker than the existing
Android Compose tests and could diverge from the shared Android benchmark.

The checked-in manifest and runner therefore provide the stable boundary: shared policy and iOS
adapter tests compile in Gradle, the app is built with the same Xcode scheme used by developers,
and the authorized runtime pass records screenshots and logs for the flows that cannot be proven
statically. If stable semantic IDs and an iOS fixture bundle are added later, this checklist can be
promoted to XCUITest cases without changing the flow contract.

The Android benchmark remains authoritative. A runtime observation is only marked **pass** when its
state transition, failure behavior, persistence, and native feedback match Android or the result is
recorded as an intentional iOS platform difference.

## Compile-only gate

These commands do not install or launch an app:

```sh
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64 --console=plain
xcodebuild \
  -project iosApp/Reader.xcodeproj \
  -scheme Reader \
  -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  build-for-testing
```

The first command compiles shared iOS production code and the existing iOS test source set without
executing the native test binary. The second command compiles the Swift host, embedded shared
framework, native PDFium/libmobi adapters, and the app product. `build-for-testing` is intentional:
it prepares a testable product but does not install or launch a simulator.

## Authorized runtime runner

The runner refuses to mutate simulator state unless the caller supplies an explicit one-shot
authorization environment variable. A booted simulator is expected; the script does not boot or
reset one.

```sh
IOS_RUNTIME_AUTHORIZED=1 \
  ./iosApp/RuntimeValidation/validate_ios_runtime.sh <booted-simulator-udid>
```

It performs the compile-only gate, installs the resulting app, launches bundle
`com.aryan.episteme`, captures a launch screenshot, and collects the recent Reader log stream.
Evidence is written outside the repository under `/tmp` by default. Override the locations when a
run needs to be retained:

```sh
IOS_RUNTIME_AUTHORIZED=1 \
IOS_RUNTIME_EVIDENCE_DIR="$PWD/runtime-evidence/iphone" \
IOS_RUNTIME_DERIVED_DATA="/tmp/reader-ios-derived" \
  ./iosApp/RuntimeValidation/validate_ios_runtime.sh <booted-simulator-udid>
```

The runner is intentionally not a UI automation substitute. After launch, follow the manifest in
`fixtures.json` on both an iPhone-class and iPad-class simulator. Keep the evidence directory and
record the device model, iOS version, build commit, and whether the run used the Local StoreKit
scheme.

## Runtime flow order

Run these flows in order so a failure leaves a useful boundary:

1. Launch and library restoration.
2. Settings navigation and safe-area/keyboard behavior.
3. Account, Apple/Google sign-in state, Pro/credits gating, and local StoreKit restore/purchase
   behavior using the Local StoreKit scheme only.
4. Diagnostic log export and the resulting share/save surface.
5. Import/open one PDF, one EPUB, and one PPTX; verify close/reopen and saved position.
6. PDF split reader with two documents: focus each pane, scroll/zoom, move the divider, swap, close
   one pane, rotate, background/foreground, and restore.
7. EPUB navigation/search/TTS interruption and resume.
8. Performance sampling and memory/thermal observation while repeating the heaviest flow.

Use the small repository-owned PDF and EPUB sources named in `fixtures.json`. Do not add a binary
PPTX solely for testing; use a user-owned or separately licensed deck and record its source and
slide count in the evidence notes.

## Performance evidence

Capture both the user-visible result and the corresponding app evidence:

- launch-to-first-library-frame and document-open latency;
- p50/p95/max frame time during scroll, zoom, divider drag, and toolbar toggles;
- PDF tile render duration, peak render bytes, OCR duration/cache hits, and search latency;
- EPUB/WKWebView chapter load and layout-switch latency;
- TTS start, interruption, resume, and stop latency;
- memory peak, resident growth after close, and whether memory returns after repeated open/close;
- sync/import duration and payload size where an account is available.

Export the in-app diagnostic log after the run. It includes the bounded iOS reader/EPUB/PDF/OCR/TTS
events and performance snapshots. For frame and memory evidence, also use Instruments (Time
Profiler + Allocations) on the same fixture and viewport. Compare the observations with the Android
benchmark run; do not invent a pass threshold when Android has no corresponding measurement.

## Current limitation

Until the authorized runtime pass is performed, this directory is preparation only. Compile output,
shared tests, and source inspection must not be reported as proof of native gesture, rendering,
StoreKit, WebView, audio-session, file-import, split-pane, or accessibility runtime parity.
