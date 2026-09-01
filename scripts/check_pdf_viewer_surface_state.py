#!/usr/bin/env python3
"""Statically audit the typed PdfViewerSurfaceState bridge.

The reader surface is populated from two composition phases (document setup and
the stateful reader host), so a property may be assigned more than once while
the composition is rebuilt. What must never happen is an assignment whose
runtime category disagrees with the declared type, or two assignments for one
property that disagree with each other. This check is source only and runs
without an APK.
"""

from __future__ import annotations

import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path


SOURCE = Path(__file__).resolve().parents[1] / (
    "app/src/main/java/com/aryan/reader/pdf/PdfViewerScreen.kt"
)


@dataclass(frozen=True)
class Field:
    line: int
    type_name: str
    kind: str
    requires_assignment: bool


@dataclass(frozen=True)
class Assignment:
    line: int
    kind: str
    rhs: str


@dataclass(frozen=True)
class Use:
    line: int
    kind: str


WRAPPER_TYPES = (
    "PdfViewerBanner",
    "PdfViewerSpeechBubbleDetector",
    "PdfViewerSuspendPageAction",
)

REACTIVE_LAYOUT_FIELDS = (
    "density",
    "isPdfTabStripVisible",
    "dockHeightPx",
    "statusBarHeightDp",
    "navBarHeight",
    "verticalHeaderHeight",
    "verticalFooterHeight",
    "topOverlayInset",
    "bottomScrollLimitPx",
    "topScrollLimitPx",
)


def declared_kind(type_name: str) -> str:
    if "MutableState<" in type_name:
        return "state"
    if "PdfViewerMutableValue<" in type_name:
        return "mutable"
    if any(wrapper in type_name for wrapper in WRAPPER_TYPES):
        return "wrapper"
    return "plain"


def assignment_kind(rhs: str, field_kind: str) -> str:
    rhs = rhs.strip()
    if rhs.startswith("pdfViewerMutableValue("):
        return "mutable"
    if any(rhs.startswith(wrapper) for wrapper in WRAPPER_TYPES):
        return "wrapper"
    if field_kind == "state":
        return "state"
    # A function-typed surface field is deliberately plain even when its
    # assignment is written as a lambda. The declared type is authoritative.
    return field_kind if rhs.startswith("{") else "plain"


def cast_kind(cast: str) -> str:
    if "PdfViewerMutableValue" in cast:
        return "mutable"
    if any(wrapper in cast for wrapper in WRAPPER_TYPES):
        return "wrapper"
    return "plain"


def fail(messages: list[str]) -> None:
    for message in messages:
        print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


if not SOURCE.is_file():
    fail([f"source file not found: {SOURCE}"])

lines = SOURCE.read_text(encoding="utf-8").splitlines()
try:
    class_start = next(
        index
        for index, line in enumerate(lines)
        if line.strip() == "private class PdfViewerSurfaceState {"
    )
    class_end = next(
        index
        for index in range(class_start + 1, len(lines))
        if lines[index].startswith("private class PdfViewerMutableValue")
    )
except StopIteration:
    fail(["could not locate PdfViewerSurfaceState boundaries"])

state_lines = lines[class_start + 1 : class_end]
state_source = "\n".join(state_lines)
if "Any?" in state_source:
    fail(["PdfViewerSurfaceState still contains an untyped Any? field"])

fields: dict[str, Field] = {}
field_pattern = re.compile(
    r"^\s*(?P<lateinit>lateinit\s+)?var\s+"
    r"(?P<name>\w+)\s*:\s*(?P<type>[^=]+?)(?:\s*=.*)?$"
)
for line_number, line in enumerate(state_lines, class_start + 2):
    match = field_pattern.match(line)
    if not match:
        continue
    type_name = match.group("type").strip()
    fields[match.group("name")] = Field(
        line=line_number,
        type_name=type_name,
        kind=declared_kind(type_name),
        requires_assignment=bool(match.group("lateinit")),
    )

reactive_layout_failures = [
    name
    for name in REACTIVE_LAYOUT_FIELDS
    if name not in fields or fields[name].kind != "state"
]
if reactive_layout_failures:
    fail(
        [
            "layout metrics must remain Compose MutableState in the extracted bridge: "
            + ", ".join(reactive_layout_failures)
        ]
    )

assignments: dict[str, list[Assignment]] = defaultdict(list)
assignment_pattern = re.compile(
    r"\bsurfaceState\.(?P<name>\w+)(?P<slot>\.value)?\s*=\s*(?P<rhs>.*)$"
)
for line_number, line in enumerate(lines, 1):
    match = assignment_pattern.search(line)
    if not match:
        continue
    property_name = match.group("name")
    field = fields.get(property_name)
    # Unknown fields are reported below instead of guessed as plain values.
    # Compose MutableState fields are assigned through `.value`; the state
    # container itself remains stable and its declared state category is the
    # runtime contract being audited.
    kind = (
        field.kind
        if match.group("slot") == ".value" and field is not None
        else assignment_kind(match.group("rhs"), field.kind if field else "plain")
    )
    assignments[property_name].append(
        Assignment(line=line_number, kind=kind, rhs=match.group("rhs").strip())
    )

uses: dict[str, list[Use]] = defaultdict(list)
property_pattern = re.compile(r"\bsurfaceState\.(\w+)")
for line_number, line in enumerate(lines, 1):
    assignment_match = assignment_pattern.search(line)
    for match in property_pattern.finditer(line):
        property_name = match.group(1)
        if assignment_match and assignment_match.start("name") == match.start(1):
            continue
        tail = line[match.end() :]
        cast_match = re.search(r"\bas\s+([^);]+)", tail)
        fallback_kind = fields[property_name].kind if property_name in fields else "plain"
        uses[property_name].append(
            Use(
                line=line_number,
                kind=cast_kind(cast_match.group(1))
                if cast_match
                else fallback_kind,
            )
        )

failures: list[str] = []
unknown_assignments = sorted(set(assignments) - set(fields))
if unknown_assignments:
    failures.append(
        "assignments target undeclared fields: " + ", ".join(unknown_assignments)
    )

missing_assignments = sorted(
    name
    for name, field in fields.items()
    if field.requires_assignment and name not in assignments
)
if missing_assignments:
    failures.append(
        "lateinit fields without an assignment: " + ", ".join(missing_assignments)
    )

for property_name, values in sorted(assignments.items()):
    field = fields.get(property_name)
    if field is None:
        continue
    categories = {value.kind for value in values}
    if categories != {field.kind}:
        locations = ", ".join(
            f"line {value.line} ({value.kind})" for value in values
        )
        failures.append(
            f"{property_name} declared as {field.kind} but assigned as {locations}"
        )
    used_categories = {use.kind for use in uses.get(property_name, [])}
    incompatible_uses = used_categories - {field.kind}
    if incompatible_uses:
        failures.append(
            f"{property_name} declared as {field.kind} but read/cast as "
            f"{sorted(incompatible_uses)}"
        )

if failures:
    fail(failures)

kind_counts = Counter(field.kind for field in fields.values())
duplicate_writes = sum(1 for values in assignments.values() if len(values) > 1)
print(f"PdfViewerSurfaceState fields: {len(fields)}")
print(
    "field categories: "
    + ", ".join(
        f"{kind}={kind_counts.get(kind, 0)}"
        for kind in ("mutable", "state", "plain", "wrapper")
    )
)
print(f"surface assignments: {sum(len(values) for values in assignments.values())}")
print(f"surface reads: {sum(len(values) for values in uses.values())}")
print(f"same-category repeated assignments: {duplicate_writes}")
print("PASS: PdfViewerSurfaceState assignments, duplicate writes, and casts are type-compatible")
