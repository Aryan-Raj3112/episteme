#!/usr/bin/env sh
set -eu

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
PDFIUM_ROOT="$ROOT_DIR/third_party/pdfium"
DEVICE_DIR="$PDFIUM_ROOT/ios-device-arm64"
SIMULATOR_DIR="$PDFIUM_ROOT/ios-simulator-arm64"
OUTPUT_DIR="$PDFIUM_ROOT/ios"
OUTPUT="$OUTPUT_DIR/PDFium.xcframework"

if [ ! -f "$DEVICE_DIR/lib/libpdfium.dylib" ]; then
    echo "Missing device PDFium dylib: $DEVICE_DIR/lib/libpdfium.dylib" >&2
    exit 1
fi

if [ ! -f "$SIMULATOR_DIR/lib/libpdfium.dylib" ]; then
    echo "Missing simulator PDFium dylib: $SIMULATOR_DIR/lib/libpdfium.dylib" >&2
    exit 1
fi

install_name_tool -id "@rpath/libpdfium.dylib" "$DEVICE_DIR/lib/libpdfium.dylib"
install_name_tool -id "@rpath/libpdfium.dylib" "$SIMULATOR_DIR/lib/libpdfium.dylib"

mkdir -p "$OUTPUT_DIR"
rm -rf "$OUTPUT"

xcodebuild -create-xcframework \
    -library "$DEVICE_DIR/lib/libpdfium.dylib" \
    -headers "$DEVICE_DIR/include" \
    -library "$SIMULATOR_DIR/lib/libpdfium.dylib" \
    -headers "$SIMULATOR_DIR/include" \
    -output "$OUTPUT"

install_name_tool -id "@rpath/libpdfium.dylib" "$OUTPUT/ios-arm64/libpdfium.dylib"
install_name_tool -id "@rpath/libpdfium.dylib" "$OUTPUT/ios-arm64-simulator/libpdfium.dylib"

echo "Created $OUTPUT"
