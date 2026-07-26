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
developer_directory="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
sdk_path="$(DEVELOPER_DIR="$developer_directory" xcrun --sdk "$sdk_name" --show-sdk-path)"

mkdir -p "$output_directory/objects"
rm -f "$output_directory/objects/"*.o "$output_directory/libmobi.dylib"

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
  -dynamiclib \
  -Wl,-install_name,@rpath/libmobi.dylib \
  -Wl,-current_version,0.12 \
  -Wl,-compatibility_version,0.12 \
  -o "$output_directory/libmobi.dylib" \
  "$output_directory/objects/"*.o \
  -lz
