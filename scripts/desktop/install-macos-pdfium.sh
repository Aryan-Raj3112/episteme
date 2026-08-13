#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
DOWNLOAD_DIR=${1:-"$HOME/Downloads"}
PDFIUM_ROOT="$ROOT_DIR/third_party/pdfium"

install_archive() {
    archive_name=$1
    expected_sha256=$2
    expected_arch=$3
    destination_name=$4
    archive="$DOWNLOAD_DIR/$archive_name"
    destination="$PDFIUM_ROOT/$destination_name"

    if [ ! -f "$archive" ]; then
        echo "Missing PDFium archive: $archive" >&2
        exit 1
    fi

    actual_sha256=$(shasum -a 256 "$archive" | awk '{print $1}')
    if [ "$actual_sha256" != "$expected_sha256" ]; then
        echo "Unexpected SHA-256 for $archive_name" >&2
        echo "Expected: $expected_sha256" >&2
        echo "Actual:   $actual_sha256" >&2
        exit 1
    fi

    staging=$(mktemp -d)
    trap 'rm -rf "$staging"' EXIT HUP INT TERM
    tar -xzf "$archive" -C "$staging"

    library="$staging/lib/libpdfium.dylib"
    if [ ! -f "$library" ]; then
        echo "$archive_name does not contain lib/libpdfium.dylib" >&2
        exit 1
    fi
    actual_arch=$(lipo -archs "$library")
    if [ "$actual_arch" != "$expected_arch" ]; then
        echo "$archive_name contains '$actual_arch'; expected '$expected_arch'." >&2
        exit 1
    fi

    mkdir -p "$destination"
    cp -R "$staging/." "$destination/"
    rm -rf "$staging"
    trap - EXIT HUP INT TERM
    echo "Installed $archive_name into $destination_name"
}

install_archive \
    "pdfium-v8-mac-arm64.tgz" \
    "3ab2b7e07ef07960d724b45ede9e737ec0d975655af4f477bed64c7d17c7505c" \
    "arm64" \
    "mac-arm64-v8"

install_archive \
    "pdfium-v8-mac-x64.tgz" \
    "96a712f28dfb8afb4c8aeeb0a9f5415e16db84aae56a85b357106b44a5574d7f" \
    "x86_64" \
    "mac-x64-v8"
