#!/usr/bin/env bash

# Checks the generated PdfViewerScreen DEX for the failure mode where a
# Compose/R8 lambda captures enough state to exceed the verifier's safe range
# invocation limits. Run this after assembling an APK:
#
#   scripts/check_pdf_viewer_dex_limits.sh app/build/outputs/apk/oss/debug/app-oss-debug.apk

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
python3 "$script_dir/check_pdf_viewer_surface_state.py"
python3 "$script_dir/check_pdf_viewer_jvm_method_limits.py"

apk_path="${1:-app/build/outputs/apk/oss/debug/app-oss-debug.apk}"
if [[ ! -f "$apk_path" ]]; then
    echo "APK not found: $apk_path" >&2
    exit 2
fi

if command -v dexdump >/dev/null 2>&1; then
    dexdump_bin="$(command -v dexdump)"
elif [[ -n "${ANDROID_HOME:-}" ]]; then
    dexdump_bin="$(find "$ANDROID_HOME/build-tools" -type f -name dexdump -print | sort -V | tail -n 1)"
else
    dexdump_bin="/Users/aryan/Library/Android/sdk/build-tools/37.0.0/dexdump"
fi

if [[ ! -x "$dexdump_bin" ]]; then
    echo "Android dexdump not found; set ANDROID_HOME or install build-tools." >&2
    exit 2
fi

temp_dir="$(mktemp -d "${TMPDIR:-/private/tmp}/pdf-viewer-dex.XXXXXX")"
trap 'rm -rf "$temp_dir"' EXIT

unzip -q "$apk_path" 'classes*.dex' -d "$temp_dir"
for dex_path in "$temp_dir"/classes*.dex; do
    "$dexdump_bin" -d "$dex_path"
done > "$temp_dir/dexdump.txt"

python3 - "$temp_dir/dexdump.txt" <<'PY'
from __future__ import annotations

import sys
from pathlib import Path


DEXDUMP = Path(sys.argv[1])
TARGET_PREFIX = "Lcom/aryan/reader/pdf/PdfViewerScreenKt"
TARGET_CLASS = "Lcom/aryan/reader/pdf/PdfViewerScreenKt;"
MAX_CAPTURE_FIELDS = 200
MAX_LAMBDA_REGISTERS = 240


def descriptor_parameter_slots(descriptor: str) -> int:
    """Count DEX register slots in a method descriptor's parameter list."""
    if not descriptor.startswith("'("):
        return 0
    body = descriptor[2 : descriptor.find(")")]
    slots = 0
    index = 0
    while index < len(body):
        token = body[index]
        if token == "[":
            while index < len(body) and body[index] == "[":
                index += 1
            if index < len(body) and body[index] == "L":
                index = body.find(";", index) + 1
            else:
                index += 1
            slots += 1
        elif token == "L":
            index = body.find(";", index) + 1
            slots += 1
        elif token in "DJ":
            slots += 2
            index += 1
        else:
            slots += 1
            index += 1
    return slots


class_fields: dict[str, int] = {}
methods: list[tuple[str, str, str, int]] = []
current_class: str | None = None
section: str | None = None
method_name: str | None = None
method_type: str | None = None

for raw_line in DEXDUMP.read_text(errors="replace").splitlines():
    line = raw_line.strip()
    if line.startswith("Class descriptor  :"):
        current_class = line.split("'", 2)[1]
        class_fields.setdefault(current_class, 0)
        section = None
        method_name = None
        method_type = None
    elif line.startswith("Static fields") or line.startswith("Instance fields"):
        section = "fields"
    elif line.startswith("Direct methods") or line.startswith("Virtual methods"):
        section = "methods"
    elif section == "fields" and line.startswith("name          : '"):
        field_name = line.split("'", 2)[1]
        if field_name.startswith("f$"):
            class_fields[current_class] += 1
    elif section == "methods" and line.startswith("name          : '"):
        method_name = line.split("'", 2)[1]
        method_type = None
    elif section == "methods" and method_name and line.startswith("type          :"):
        method_type = line.split(":", 1)[1].strip()
    elif section == "methods" and method_name and line.startswith("registers     :"):
        methods.append(
            (
                current_class or "",
                method_name,
                method_type or "",
                int(line.split(":", 1)[1].strip()),
            )
        )
        method_name = None
        method_type = None

synthetic_fields = [
    (count, class_name)
    for class_name, count in class_fields.items()
    if class_name.startswith(TARGET_PREFIX + "$$ExternalSyntheticLambda")
]
target_lambdas = [
    row
    for row in methods
    if row[0] == TARGET_CLASS and "$lambda" in row[1]
]
synthetic_ctors = [
    row
    for row in methods
    if row[0].startswith(TARGET_PREFIX + "$$ExternalSyntheticLambda")
    and row[1] == "<init>"
]
surface_methods = [
    row
    for row in methods
    if row[0] == TARGET_CLASS
    and row[1]
    in {"PdfViewerSurfaceContent", "PdfViewerPaginationPage", "PdfViewerReaderSurface"}
]

failures: list[str] = []
if synthetic_fields:
    max_fields, max_class = max(synthetic_fields)
    if max_fields > MAX_CAPTURE_FIELDS:
        failures.append(
            f"{max_class} captures {max_fields} fields (limit {MAX_CAPTURE_FIELDS})"
        )
else:
    failures.append("no PdfViewerScreenKt R8 lambda classes found in the APK")

if target_lambdas:
    max_lambda = max(target_lambdas, key=lambda row: row[3])
    if max_lambda[3] > MAX_LAMBDA_REGISTERS:
        failures.append(
            f"{max_lambda[1]} uses {max_lambda[3]} registers (limit {MAX_LAMBDA_REGISTERS})"
        )
else:
    failures.append("no PdfViewerScreenKt lambda methods found in the APK")

for class_name, method_name, descriptor, _ in synthetic_ctors:
    slots = descriptor_parameter_slots(descriptor)
    if slots > MAX_LAMBDA_REGISTERS:
        failures.append(
            f"{class_name}.<init> has {slots} parameter slots (limit {MAX_LAMBDA_REGISTERS})"
        )

if not synthetic_fields or not target_lambdas:
    for failure in failures:
        print(f"FAIL: {failure}", file=sys.stderr)
    sys.exit(1)

max_fields, max_class = max(synthetic_fields)
max_lambda = max(target_lambdas, key=lambda row: row[3])
print(f"PdfViewerScreenKt R8 lambda classes: {len(synthetic_fields)}")
print(f"max captured fields: {max_fields} ({max_class})")
print(f"max PdfViewerScreenKt lambda registers: {max_lambda[3]} ({max_lambda[1]})")
for _, method_name, _, registers in surface_methods:
    print(f"{method_name} registers: {registers}")

if failures:
    for failure in failures:
        print(f"FAIL: {failure}", file=sys.stderr)
    sys.exit(1)

print("PASS: PdfViewerScreen generated lambda descriptors stay below verifier-safe limits")
PY
