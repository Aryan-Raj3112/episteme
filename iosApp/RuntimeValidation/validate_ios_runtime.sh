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
xcrun simctl launch --console "$DEVICE_ID" "$BUNDLE_ID" \
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
