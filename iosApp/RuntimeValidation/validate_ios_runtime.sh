#!/usr/bin/env bash
set -euo pipefail

if [[ "${IOS_RUNTIME_AUTHORIZED:-}" != "1" ]]; then
  echo "Refusing simulator install/launch: set IOS_RUNTIME_AUTHORIZED=1 for an explicitly authorized run." >&2
  exit 64
fi

if [[ $# -ne 1 ]]; then
  echo "Usage: IOS_RUNTIME_AUTHORIZED=1 $0 <booted-simulator-udid>" >&2
  exit 64
fi

DEVICE_ID="$1"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PROJECT_PATH="$PROJECT_ROOT/iosApp/Reader.xcodeproj"
SCHEME_NAME="${IOS_RUNTIME_SCHEME:-Reader}"
BUNDLE_ID="com.aryan.episteme"
DERIVED_DATA_PATH="${IOS_RUNTIME_DERIVED_DATA:-$(mktemp -d /tmp/reader-ios-derived.XXXXXX)}"
EVIDENCE_DIR="${IOS_RUNTIME_EVIDENCE_DIR:-$(mktemp -d /tmp/reader-ios-evidence.XXXXXX)}"
COPY_FIXTURES="${IOS_RUNTIME_COPY_FIXTURES:-1}"
SECOND_PDF_PATH="${IOS_RUNTIME_SECOND_PDF:-}"

mkdir -p "$EVIDENCE_DIR"

echo "Building $SCHEME_NAME for simulator device $DEVICE_ID"
xcodebuild \
  -project "$PROJECT_PATH" \
  -scheme "$SCHEME_NAME" \
  -configuration Debug \
  -destination "platform=iOS Simulator,id=$DEVICE_ID" \
  -derivedDataPath "$DERIVED_DATA_PATH" \
  build-for-testing \
  | tee "$EVIDENCE_DIR/xcodebuild.log"

APP_PATH="$(find "$DERIVED_DATA_PATH/Build/Products" -type d -name 'Reader.app' -print -quit)"
if [[ -z "$APP_PATH" || ! -d "$APP_PATH" ]]; then
  echo "Could not locate Reader.app under $DERIVED_DATA_PATH/Build/Products" >&2
  exit 1
fi

xcrun simctl bootstatus "$DEVICE_ID" -b
xcrun simctl install "$DEVICE_ID" "$APP_PATH"

if [[ "$COPY_FIXTURES" == "1" ]]; then
  APP_DATA_CONTAINER="$(xcrun simctl get_app_container "$DEVICE_ID" "$BUNDLE_ID" data)"
  FIXTURE_DIR="$APP_DATA_CONTAINER/Documents/RuntimeFixtures"
  mkdir -p "$FIXTURE_DIR"
  cp "$PROJECT_ROOT/app/src/main/assets/sample.pdf" "$FIXTURE_DIR/sample.pdf"
  cp "$PROJECT_ROOT/app/src/androidTest/assets/epub/reader_test_book.epub" "$FIXTURE_DIR/reader_test_book.epub"
  printf '%s\n' \
    "sample.pdf=$FIXTURE_DIR/sample.pdf" \
    "reader_test_book.epub=$FIXTURE_DIR/reader_test_book.epub" \
    >"$EVIDENCE_DIR/fixture-paths.txt"

  if [[ -n "$SECOND_PDF_PATH" ]]; then
    if [[ ! -f "$SECOND_PDF_PATH" ]]; then
      echo "IOS_RUNTIME_SECOND_PDF does not exist: $SECOND_PDF_PATH" >&2
      exit 1
    fi
    if cmp -s "$PROJECT_ROOT/app/src/main/assets/sample.pdf" "$SECOND_PDF_PATH"; then
      echo "IOS_RUNTIME_SECOND_PDF must be byte-distinct from sample.pdf for split coverage." >&2
      exit 1
    fi
    cp "$SECOND_PDF_PATH" "$FIXTURE_DIR/split_secondary.pdf"
    printf '%s\n' "split_secondary.pdf=$FIXTURE_DIR/split_secondary.pdf" >>"$EVIDENCE_DIR/fixture-paths.txt"
  else
    echo "Warning: no IOS_RUNTIME_SECOND_PDF supplied; do not mark the PDF split flow as passed." >&2
    printf '%s\n' "split_secondary.pdf=not-supplied;split-flow-unverified" >>"$EVIDENCE_DIR/fixture-paths.txt"
  fi
elif [[ -n "$SECOND_PDF_PATH" ]]; then
  echo "IOS_RUNTIME_SECOND_PDF was provided but IOS_RUNTIME_COPY_FIXTURES=0; it was not copied." >&2
else
  echo "Warning: no IOS_RUNTIME_SECOND_PDF supplied; do not mark the PDF split flow as passed." >&2
fi

if [[ "$SCHEME_NAME" == "Reader Local StoreKit" ]]; then
  echo "Warning: direct simctl launch does not attach the Xcode StoreKit configuration; use Xcode Run for purchase assertions." >&2
fi

xcrun simctl launch --console "$DEVICE_ID" "$BUNDLE_ID" \
  -episteme.desktop.diagnostics YES \
  -AppleLanguages '(en)' \
  -AppleLocale en_US \
  >"$EVIDENCE_DIR/launch.log" 2>&1
xcrun simctl io "$DEVICE_ID" screenshot "$EVIDENCE_DIR/launch.png"

# The log command can return no rows on a fresh simulator. Keep the launch evidence in that case.
xcrun simctl spawn "$DEVICE_ID" log show \
  --last 2m \
  --style compact \
  --predicate 'process == "Reader"' \
  >"$EVIDENCE_DIR/reader-log.txt" 2>&1 || true

cat <<EOF
Runtime launch completed.
Evidence: $EVIDENCE_DIR
Next: follow iosApp/RuntimeValidation/fixtures.json on iPhone and iPad simulator profiles,
then export in-app diagnostics and capture Instruments Time Profiler + Allocations traces.
EOF
