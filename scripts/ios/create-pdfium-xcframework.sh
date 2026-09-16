#!/usr/bin/env sh
# Builds a framework-based PDFium.xcframework for iOS.
#
# App Store Connect rejects apps that embed loose .dylib files inside
# Payload/*.app/Frameworks (ITMS-90426, reported as a missing SwiftSupport
# folder), so pdfium is shipped as proper .framework bundles inside an
# XCFramework instead of bare dylibs. The Xcode target links and embeds
# Pdfium.framework; Xcode then handles slicing and code signing.
#
# The prebuilt dylibs under third_party/pdfium/{ios-device-arm64,
# ios-simulator-arm64} are only copied, never modified in place. Re-run this
# script after updating those pdfium binaries (currently 152.0.7934.0).
set -eu

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
PDFIUM_ROOT="$ROOT_DIR/third_party/pdfium"
DEVICE_DIR="$PDFIUM_ROOT/ios-device-arm64"
SIMULATOR_DIR="$PDFIUM_ROOT/ios-simulator-arm64"
OUTPUT_DIR="$PDFIUM_ROOT/ios"
OUTPUT="$OUTPUT_DIR/PDFium.xcframework"
FRAMEWORK_NAME="Pdfium"
BUNDLE_ID="com.aryan.reader.pdfium"
# Upstream pdfium release. App Store Connect requires CFBundleShortVersionString
# to be at most three dot-separated integers (server error 90060 rejects e.g.
# "152.0.7934.0"), so the framework plist uses the truncated form below.
PDFIUM_VERSION="152.0.7934.0"
PDFIUM_SHORT_VERSION="152.0.7934"
# Must equal the prebuilt binaries' LC_BUILD_VERSION minos (check with
# `otool -l ... | grep -A3 LC_BUILD_VERSION`) and be <= the app's
# IPHONEOS_DEPLOYMENT_TARGET. A lower value triggers ITMS-90208 because the
# bundle would advertise support for OS versions its binary cannot run on.
MINIMUM_OS="26.0"

if [ ! -f "$DEVICE_DIR/lib/libpdfium.dylib" ]; then
    echo "Missing device PDFium dylib: $DEVICE_DIR/lib/libpdfium.dylib" >&2
    exit 1
fi

if [ ! -f "$SIMULATOR_DIR/lib/libpdfium.dylib" ]; then
    echo "Missing simulator PDFium dylib: $SIMULATOR_DIR/lib/libpdfium.dylib" >&2
    exit 1
fi

STAGING_DIR="$(mktemp -d)"
trap 'rm -rf "$STAGING_DIR"' EXIT INT TERM

make_framework() {
    variant="$1"       # ios-arm64 | ios-arm64-simulator
    source_dir="$2"
    platform="$3"      # iPhoneOS | iPhoneSimulator
    framework_dir="$STAGING_DIR/$variant/$FRAMEWORK_NAME.framework"

    mkdir -p "$framework_dir/Headers"
    cp "$source_dir/lib/libpdfium.dylib" "$framework_dir/$FRAMEWORK_NAME"
    chmod 755 "$framework_dir/$FRAMEWORK_NAME"
    install_name_tool -id "@rpath/$FRAMEWORK_NAME.framework/$FRAMEWORK_NAME" \
        "$framework_dir/$FRAMEWORK_NAME"
    cp -R "$source_dir/include/." "$framework_dir/Headers/"
    chmod -R a+r "$framework_dir/Headers"

    cat > "$framework_dir/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CFBundleDevelopmentRegion</key>
	<string>en</string>
	<key>CFBundleExecutable</key>
	<string>$FRAMEWORK_NAME</string>
	<key>CFBundleIdentifier</key>
	<string>$BUNDLE_ID</string>
	<key>CFBundleInfoDictionaryVersion</key>
	<string>6.0</string>
	<key>CFBundleName</key>
	<string>$FRAMEWORK_NAME</string>
	<key>CFBundlePackageType</key>
	<string>FMWK</string>
	<key>CFBundleShortVersionString</key>
	<string>$PDFIUM_SHORT_VERSION</string>
	<key>CFBundleSupportedPlatforms</key>
	<array>
		<string>$platform</string>
	</array>
	<key>CFBundleVersion</key>
	<string>$PDFIUM_SHORT_VERSION</string>
	<key>MinimumOSVersion</key>
	<string>$MINIMUM_OS</string>
</dict>
</plist>
EOF
}

make_framework "ios-arm64" "$DEVICE_DIR" "iPhoneOS"
make_framework "ios-arm64-simulator" "$SIMULATOR_DIR" "iPhoneSimulator"

mkdir -p "$OUTPUT_DIR"
rm -rf "$OUTPUT"

xcodebuild -create-xcframework \
    -framework "$STAGING_DIR/ios-arm64/$FRAMEWORK_NAME.framework" \
    -framework "$STAGING_DIR/ios-arm64-simulator/$FRAMEWORK_NAME.framework" \
    -output "$OUTPUT"

echo "Created $OUTPUT"
