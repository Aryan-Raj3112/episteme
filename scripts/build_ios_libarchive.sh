#!/bin/sh
set -eu

if [ "$#" -ne 3 ]; then
  echo "usage: $0 <iphoneos|iphonesimulator> <arch> <output-directory>" >&2
  exit 2
fi

sdk_name="$1"
arch_name="$2"
output_directory="$3"
repository_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
source_directory="$repository_root/third_party/libarchive"
xz_source_directory="$repository_root/third_party/xz"
developer_directory="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
android_sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

if [ -z "$android_sdk" ] && [ -f "$repository_root/local.properties" ]; then
  android_sdk="$(sed -n 's/^sdk\.dir=//p' "$repository_root/local.properties" | tail -n 1 | sed 's/\\\\:/:/g; s/\\\\ / /g')"
fi
if [ -z "$android_sdk" ]; then
  echo "Android SDK path is required to locate the bundled CMake and Ninja tools" >&2
  exit 1
fi

cmake_directory="$(find "$android_sdk/cmake" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
cmake_binary="$cmake_directory/bin/cmake"
ninja_binary="$cmake_directory/bin/ninja"
if [ ! -x "$cmake_binary" ] || [ ! -x "$ninja_binary" ]; then
  echo "Could not locate executable CMake and Ninja tools under $android_sdk/cmake" >&2
  exit 1
fi

rm -rf "$output_directory"
mkdir -p "$output_directory"

DEVELOPER_DIR="$developer_directory" "$cmake_binary" \
  -S "$xz_source_directory" \
  -B "$output_directory/xz" \
  -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$ninja_binary" \
  -DCMAKE_SYSTEM_NAME=iOS \
  -DCMAKE_OSX_SYSROOT="$sdk_name" \
  -DCMAKE_OSX_ARCHITECTURES="$arch_name" \
  -DCMAKE_OSX_DEPLOYMENT_TARGET=16.0 \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=OFF \
  -DXZ_NLS=OFF \
  -DXZ_TOOL_XZ=OFF \
  -DXZ_TOOL_XZDEC=OFF \
  -DXZ_TOOL_LZMADEC=OFF \
  -DXZ_TOOL_LZMAINFO=OFF \
  -DXZ_TOOL_SCRIPTS=OFF \
  -DXZ_DOC=OFF

DEVELOPER_DIR="$developer_directory" "$cmake_binary" \
  --build "$output_directory/xz" \
  --target liblzma

DEVELOPER_DIR="$developer_directory" "$cmake_binary" \
  -S "$source_directory" \
  -B "$output_directory" \
  -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$ninja_binary" \
  -DCMAKE_SYSTEM_NAME=iOS \
  -DCMAKE_OSX_SYSROOT="$sdk_name" \
  -DCMAKE_OSX_ARCHITECTURES="$arch_name" \
  -DCMAKE_OSX_DEPLOYMENT_TARGET=16.0 \
  -DBUILD_SHARED_LIBS=OFF \
  -DENABLE_TAR=OFF \
  -DENABLE_CPIO=OFF \
  -DENABLE_CAT=OFF \
  -DENABLE_UNZIP=OFF \
  -DENABLE_TEST=OFF \
  -DENABLE_INSTALL=OFF \
  -DENABLE_OPENSSL=OFF \
  -DENABLE_LIBB2=OFF \
  -DENABLE_LZ4=OFF \
  -DENABLE_LZO=OFF \
  -DENABLE_LZMA=ON \
  -DLIBLZMA_INCLUDE_DIR="$xz_source_directory/src/liblzma/api" \
  -DLIBLZMA_LIBRARY="$output_directory/xz/liblzma.a" \
  -DENABLE_ZSTD=OFF \
  -DENABLE_BZip2=OFF \
  -DENABLE_LIBXML2=OFF \
  -DENABLE_EXPAT=OFF \
  -DENABLE_PCREPOSIX=OFF \
  -DENABLE_PCRE2POSIX=OFF \
  -DENABLE_ICONV=OFF \
  -DENABLE_ACL=OFF \
  -DENABLE_XATTR=OFF

DEVELOPER_DIR="$developer_directory" "$cmake_binary" \
  --build "$output_directory" \
  --target archive_static

/usr/bin/libtool -static \
  -o "$output_directory/libreaderarchive.a" \
  "$output_directory/libarchive/libarchive.a" \
  "$output_directory/xz/liblzma.a"
