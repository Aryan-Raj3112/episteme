#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER="$SCRIPT_DIR/validate_ios_runtime.sh"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/reader-ios-runtime-shell.XXXXXX")"
STUB_BIN="$TEST_ROOT/bin"
CALLS_FILE="$TEST_ROOT/xcrun-calls.txt"
CONTAINER_PATH="$TEST_ROOT/app-data"
mkdir -p "$STUB_BIN" "$CONTAINER_PATH"

cleanup() {
  rm -rf "$TEST_ROOT"
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_file() {
  [[ -f "$1" ]] || fail "expected file: $1"
}

assert_contains() {
  local needle="$1"
  local file="$2"
  rg -F -- "$needle" "$file" >/dev/null || {
    echo "--- $file ---" >&2
    sed -n '1,160p' "$file" >&2 || true
    fail "expected '$needle' in $file"
  }
}

cat >"$STUB_BIN/xcodebuild" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail

derived_data=""
while (($# > 0)); do
  if [[ "$1" == "-derivedDataPath" ]]; then
    derived_data="$2"
    shift 2
  else
    shift
  fi
done

[[ -n "$derived_data" ]] || exit 2
mkdir -p "$derived_data/Build/Products/Debug-iphonesimulator/Reader.app"
printf '%s\n' 'stub xcodebuild completed'
STUB

cat >"$STUB_BIN/xcrun" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail

: "${IOS_RUNTIME_STUB_CALLS:?IOS_RUNTIME_STUB_CALLS is required}"
: "${IOS_RUNTIME_STUB_CONTAINER:?IOS_RUNTIME_STUB_CONTAINER is required}"
{
  printf 'CALL'
  for argument in "$@"; do
    printf ' <%s>' "$argument"
  done
  printf '\n'
} >>"$IOS_RUNTIME_STUB_CALLS"

[[ "${1:-}" == "simctl" ]] || exit 2
shift
operation="${1:-}"
shift
case "$operation" in
  bootstatus|install)
    exit 0
    ;;
  get_app_container)
    mkdir -p "$IOS_RUNTIME_STUB_CONTAINER"
    printf '%s\n' "$IOS_RUNTIME_STUB_CONTAINER"
    ;;
  launch)
    bundle_id="$2"
    shift 2
    for argument in "$@"; do
      [[ "$argument" != "--console" ]] || {
        echo 'console-attached launch is forbidden by the harness contract' >&2
        exit 88
      }
    done
    if [[ "${IOS_RUNTIME_STUB_LAUNCH_SLEEP:-0}" != "0" ]]; then
      sleep "$IOS_RUNTIME_STUB_LAUNCH_SLEEP"
    fi
    printf '%s: 2468\n' "$bundle_id"
    ;;
  io)
    screenshot_path=""
    for argument in "$@"; do
      screenshot_path="$argument"
    done
    mkdir -p "$(dirname "$screenshot_path")"
    : >"$screenshot_path"
    ;;
  spawn)
    if [[ "${IOS_RUNTIME_STUB_LOG_SLEEP:-0}" != "0" ]]; then
      sleep "$IOS_RUNTIME_STUB_LOG_SLEEP"
    fi
    printf '%s\n' 'stub Reader log row'
    ;;
  *)
    echo "unexpected simctl operation: $operation" >&2
    exit 2
    ;;
esac
STUB

chmod +x "$STUB_BIN/xcodebuild" "$STUB_BIN/xcrun"

if rg -n -- '--console' "$RUNNER" >/dev/null; then
  fail "$RUNNER still contains a console-attached launch"
fi

if env -u IOS_RUNTIME_AUTHORIZED "$RUNNER" fake-device >"$TEST_ROOT/unauthorized.log" 2>&1; then
  fail "authorization guard accepted an unauthorized invocation"
else
  unauthorized_status=$?
  [[ "$unauthorized_status" -eq 64 ]] || fail "authorization guard returned $unauthorized_status"
fi
assert_contains 'Refusing simulator install/launch' "$TEST_ROOT/unauthorized.log"

run_runner_with_deadline() {
  local output_path="$1"
  local deadline_seconds="$2"
  shift 2

  local timeout_marker="${output_path}.test-timeout"
  rm -f "$timeout_marker"
  "$@" >"$output_path" 2>&1 &
  local runner_pid=$!
  (
    sleep "$deadline_seconds"
    if kill -0 "$runner_pid" 2>/dev/null; then
      : >"$timeout_marker"
      kill "$runner_pid" 2>/dev/null || true
    fi
  ) &
  local watchdog_pid=$!

  local runner_status=0
  if wait "$runner_pid"; then
    runner_status=0
  else
    runner_status=$?
  fi
  kill "$watchdog_pid" 2>/dev/null || true
  wait "$watchdog_pid" 2>/dev/null || true

  if [[ -e "$timeout_marker" ]]; then
    echo "runner exceeded ${deadline_seconds}s: $output_path" >&2
    sed -n '1,200p' "$output_path" >&2 || true
    return 125
  fi
  return "$runner_status"
}

NORMAL_DERIVED_DATA="$TEST_ROOT/normal-derived"
NORMAL_EVIDENCE="$TEST_ROOT/normal-evidence"
NORMAL_OUTPUT="$TEST_ROOT/normal-output.log"
if ! run_runner_with_deadline "$NORMAL_OUTPUT" 5 env \
  "PATH=$STUB_BIN:$PATH" \
  IOS_RUNTIME_AUTHORIZED=1 \
  IOS_RUNTIME_COPY_FIXTURES=0 \
  IOS_RUNTIME_DERIVED_DATA="$NORMAL_DERIVED_DATA" \
  IOS_RUNTIME_EVIDENCE_DIR="$NORMAL_EVIDENCE" \
  IOS_RUNTIME_LAUNCH_TIMEOUT_SECONDS=2 \
  IOS_RUNTIME_LOG_TIMEOUT_SECONDS=1 \
  IOS_RUNTIME_STUB_CALLS="$CALLS_FILE" \
  IOS_RUNTIME_STUB_CONTAINER="$CONTAINER_PATH" \
  IOS_RUNTIME_STUB_LAUNCH_SLEEP=0 \
  IOS_RUNTIME_STUB_LOG_SLEEP=2 \
  "$RUNNER" fake-device; then
  normal_status=$?
  fail "normal stubbed run failed with status $normal_status"
fi

assert_file "$NORMAL_EVIDENCE/launch.log"
assert_file "$NORMAL_EVIDENCE/launch.png"
assert_file "$NORMAL_EVIDENCE/reader-log.txt"
assert_contains 'com.aryan.episteme: 2468' "$NORMAL_EVIDENCE/launch.log"
assert_contains 'simctl log collection timed out after 1s' "$NORMAL_EVIDENCE/reader-log.txt"
assert_contains 'Runtime launch completed.' "$NORMAL_OUTPUT"
assert_contains 'Warning: Reader log collection timed out after 1s' "$NORMAL_OUTPUT"
assert_contains 'CALL <simctl> <launch> <fake-device> <com.aryan.episteme> <-episteme.desktop.diagnostics> <YES> <-AppleLanguages> <(en)> <-AppleLocale> <en_US>' "$CALLS_FILE"

TIMED_DERIVED_DATA="$TEST_ROOT/timed-derived"
TIMED_EVIDENCE="$TEST_ROOT/timed-evidence"
TIMED_OUTPUT="$TEST_ROOT/timed-output.log"
if run_runner_with_deadline "$TIMED_OUTPUT" 5 env \
  "PATH=$STUB_BIN:$PATH" \
  IOS_RUNTIME_AUTHORIZED=1 \
  IOS_RUNTIME_COPY_FIXTURES=0 \
  IOS_RUNTIME_DERIVED_DATA="$TIMED_DERIVED_DATA" \
  IOS_RUNTIME_EVIDENCE_DIR="$TIMED_EVIDENCE" \
  IOS_RUNTIME_LAUNCH_TIMEOUT_SECONDS=1 \
  IOS_RUNTIME_LOG_TIMEOUT_SECONDS=1 \
  IOS_RUNTIME_STUB_CALLS="$CALLS_FILE" \
  IOS_RUNTIME_STUB_CONTAINER="$CONTAINER_PATH" \
  IOS_RUNTIME_STUB_LAUNCH_SLEEP=3 \
  IOS_RUNTIME_STUB_LOG_SLEEP=0 \
  "$RUNNER" fake-device; then
  fail "a launch that exceeds its deadline unexpectedly succeeded"
else
  timed_status=$?
  [[ "$timed_status" -ne 125 ]] || fail 'launch timeout test itself exceeded its deadline'
  [[ "$timed_status" -ne 0 ]] || fail 'launch timeout was not reported as a failure'
fi
assert_file "$TIMED_EVIDENCE/launch.log"
assert_contains 'simctl launch timed out after 1s' "$TIMED_EVIDENCE/launch.log"
assert_contains 'simctl launch timed out after 1s' "$TIMED_OUTPUT"

echo 'iOS runtime harness shell validation passed (no simulator used).'
