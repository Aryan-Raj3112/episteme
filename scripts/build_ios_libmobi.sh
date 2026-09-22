#!/bin/sh
set -eu

if [ "$#" -ne 3 ]; then
  echo "usage: $0 <iphoneos|iphonesimulator> <arch> <output-directory>" >&2
  exit 2
fi

sdk_name="$1"
arch_name="$2"
output_directory="$3"
source_directory="$(CDPATH= cd -- "$(dirname -- "$0")/../app/src/main/cpp/libmobi/src" && pwd)"
bridge_directory="$(CDPATH= cd -- "$(dirname -- "$0")/../shared/src/nativeInterop/cinterop" && pwd)"
developer_directory="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
sdk_path="$(DEVELOPER_DIR="$developer_directory" xcrun --sdk "$sdk_name" --show-sdk-path)"

mkdir -p "$output_directory/objects"
rm -f "$output_directory/objects/"*.o "$output_directory/libmobi.dylib" "$output_directory/libmobi.a"

deployment_flag="-miphoneos-version-min=16.0"
if [ "$sdk_name" = "iphonesimulator" ]; then
  deployment_flag="-mios-simulator-version-min=16.0"
fi

for source_name in buffer compression debug index memory meta parse_rawml read structure util write; do
  DEVELOPER_DIR="$developer_directory" xcrun --sdk "$sdk_name" clang \
    -arch "$arch_name" \
    -isysroot "$sdk_path" \
    "$deployment_flag" \
    -std=c99 \
    -O2 \
    -fPIC \
    -DMOBI_INLINE=inline \
    -DHAVE_STRDUP \
    -I "$source_directory" \
    -c "$source_directory/$source_name.c" \
    -o "$output_directory/objects/$source_name.o"
done

DEVELOPER_DIR="$developer_directory" xcrun --sdk "$sdk_name" clang \
  -arch "$arch_name" \
  -isysroot "$sdk_path" \
  "$deployment_flag" \
  -std=c99 \
  -O2 \
  -fPIC \
  -DMOBI_INLINE=inline \
  -DHAVE_STRDUP \
  -I "$source_directory" \
  -I "$bridge_directory" \
  -c "$bridge_directory/mobi_reader_bridge.c" \
  -o "$output_directory/objects/mobi_reader_bridge.o"

# Static archive: libmobi is linked into the static ReaderShared framework,
# so the final app needs nothing embedded. A loose .dylib in
# Payload/*.app/Frameworks is rejected by App Store Connect (ITMS-90426),
# so this script must not produce one.
DEVELOPER_DIR="$developer_directory" xcrun --sdk "$sdk_name" libtool \
  -static \
  -o "$output_directory/libmobi.a" \
  "$output_directory/objects/"*.o
