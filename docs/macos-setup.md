# macOS local development

The macOS app uses the existing Compose Desktop application and shared JVM
reader code. It is currently intended for local testing only; macOS release
publishing, signing, and notarization are intentionally not configured.

## PDFium

Place these V8-enabled archives in `~/Downloads`:

- `pdfium-v8-mac-arm64.tgz`
- `pdfium-v8-mac-x64.tgz`

Install and validate both local runtimes:

```sh
sh scripts/desktop/install-macos-pdfium.sh
```

The archives are installed under the ignored `third_party/pdfium` directory.
At runtime the app automatically chooses `mac-arm64-v8` or `mac-x64-v8` to
match the JVM architecture.

## Run on Apple Silicon

Use the JDK bundled with Android Studio:

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" \
./gradlew :desktopApp:run
```

The Gradle run task supplies `-XstartOnFirstThread`. SWT owns the macOS main
thread for its system WebKit browser, while Compose/AWT runs on a separate UI
thread.

## Intel testing

An Intel build must run with an x64 JDK and the x64 Compose/SWT dependencies.
The app then selects `third_party/pdfium/mac-x64-v8` automatically. The ARM64
and x64 PDFium libraries are not interchangeable.
