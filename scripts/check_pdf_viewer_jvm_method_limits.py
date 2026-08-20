#!/usr/bin/env python3
"""Guard the JVM shape of the Compose PDF reader before DEX/R8 packaging.

This deliberately works on the Kotlin compiler's class output, so it does not
need an APK (or an emulator).  Compose can turn one large source lambda into a
method whose captured-parameter/register shape is rejected by ART even when
Kotlin compilation succeeds.  Keep the reader entry points and generated
reader lambdas comfortably below those limits.
"""

from __future__ import annotations

import struct
import sys
from dataclasses import dataclass
from pathlib import Path


DEFAULT_CLASS = Path(
    "app/build/intermediates/built_in_kotlinc/ossDebug/"
    "compileOssDebugKotlin/classes/com/aryan/reader/pdf/PdfViewerScreenKt.class"
)
SOURCE = Path(__file__).resolve().parents[1] / (
    "app/src/main/java/com/aryan/reader/pdf/PdfViewerScreen.kt"
)

# These are intentionally lower than the VM hard limits.  The Compose
# compiler/R8 pipeline adds synthetic parameters and temporaries later.
MAX_READER_METHOD_CODE_BYTES = 34_000
MAX_READER_LAMBDA_PARAMETER_SLOTS = 96
MAX_READER_LAMBDA_CODE_BYTES = 8_000


@dataclass(frozen=True)
class MethodInfo:
    name: str
    descriptor: str
    code_bytes: int
    max_stack: int
    max_locals: int


class ClassReader:
    def __init__(self, data: bytes) -> None:
        self.data = data
        self.offset = 0

    def u1(self) -> int:
        value = self.data[self.offset]
        self.offset += 1
        return value

    def u2(self) -> int:
        value = struct.unpack_from(">H", self.data, self.offset)[0]
        self.offset += 2
        return value

    def u4(self) -> int:
        value = struct.unpack_from(">I", self.data, self.offset)[0]
        self.offset += 4
        return value

    def skip(self, count: int) -> None:
        self.offset += count


def read_utf8_constants(reader: ClassReader) -> list[str | None]:
    if reader.u4() != 0xCAFEBABE:
        raise ValueError("not a JVM class file")
    reader.skip(4)  # minor + major
    count = reader.u2()
    constants: list[str | None] = [None] * count
    index = 1
    while index < count:
        tag = reader.u1()
        if tag == 1:  # CONSTANT_Utf8
            length = reader.u2()
            constants[index] = reader.data[
                reader.offset : reader.offset + length
            ].decode("utf-8", errors="replace")
            reader.skip(length)
        elif tag in (3, 4):  # int/float
            reader.skip(4)
        elif tag in (5, 6):  # long/double occupy two CP entries
            reader.skip(8)
            index += 1
        elif tag in (7, 8, 16, 19, 20):  # class/string/method type/module/package
            reader.skip(2)
        elif tag in (9, 10, 11, 12, 17, 18):  # refs/name+type/dynamic
            reader.skip(4)
        elif tag == 15:  # method handle
            reader.skip(3)
        else:
            raise ValueError(f"unsupported constant-pool tag {tag}")
        index += 1
    return constants


def skip_attributes(reader: ClassReader, constants: list[str | None]) -> None:
    for _ in range(reader.u2()):
        reader.u2()  # attribute name index
        reader.skip(reader.u4())


def read_methods(data: bytes) -> list[MethodInfo]:
    reader = ClassReader(data)
    constants = read_utf8_constants(reader)
    reader.skip(6)  # access flags, this class, super class
    for _ in range(reader.u2()):
        reader.u2()  # interface index
    for _ in range(reader.u2()):  # fields
        reader.skip(6)
        skip_attributes(reader, constants)

    methods: list[MethodInfo] = []
    for _ in range(reader.u2()):
        reader.skip(2)  # access flags
        name = constants[reader.u2()] or "<unknown>"
        descriptor = constants[reader.u2()] or "<unknown>"
        code_bytes = 0
        max_stack = 0
        max_locals = 0
        for _ in range(reader.u2()):
            attribute_name = constants[reader.u2()] or ""
            attribute_length = reader.u4()
            attribute_end = reader.offset + attribute_length
            if attribute_name == "Code":
                max_stack = reader.u2()
                max_locals = reader.u2()
                code_bytes = reader.u4()
                reader.skip(code_bytes)
                reader.skip(8 * reader.u2())  # exception table
                skip_attributes(reader, constants)
            else:
                reader.offset = attribute_end
            if reader.offset != attribute_end:
                reader.offset = attribute_end
        methods.append(MethodInfo(name, descriptor, code_bytes, max_stack, max_locals))
    return methods


def descriptor_parameter_slots(descriptor: str) -> int:
    if not descriptor.startswith("(") or ")" not in descriptor:
        return 0
    body = descriptor[1 : descriptor.index(")")]
    slots = 0
    index = 0
    while index < len(body):
        token = body[index]
        if token == "[":
            while index < len(body) and body[index] == "[":
                index += 1
            if index < len(body) and body[index] == "L":
                index = body.index(";", index) + 1
            else:
                index += 1
            slots += 1
        elif token == "L":
            index = body.index(";", index) + 1
            slots += 1
        elif token in "DJ":
            slots += 2
            index += 1
        else:
            slots += 1
            index += 1
    return slots


def fail(messages: list[str]) -> None:
    for message in messages:
        print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


class_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_CLASS
if not class_path.is_file():
    fail([f"compiled class not found: {class_path}"])
if not SOURCE.is_file():
    fail([f"source file not found: {SOURCE}"])

try:
    methods = read_methods(class_path.read_bytes())
except (IndexError, ValueError, struct.error) as error:
    fail([f"could not parse {class_path}: {error}"])

source = SOURCE.read_text(encoding="utf-8")
state_start = source.find("private class PdfViewerSurfaceState {")
state_end = source.find("private class PdfViewerMutableValue", state_start)
if state_start < 0 or state_end < 0:
    fail(["could not locate PdfViewerSurfaceState boundaries"])
state_source = source[state_start:state_end]
if "Any?" in state_source:
    fail(["PdfViewerSurfaceState still contains an untyped Any? bridge field"])

viewport_start = source.find(
    "private fun androidx.compose.foundation.layout.BoxWithConstraintsScope."
    "PdfViewerDocumentViewport("
)
chrome_start = source.find(
    "private fun androidx.compose.foundation.layout.BoxWithConstraintsScope."
    "PdfViewerChromeSurface(",
    viewport_start,
)
if viewport_start < 0 or chrome_start < 0:
    fail(["could not locate document/chrome composable boundaries"])
viewport_source = source[viewport_start:chrome_start]
for toolbar_name in (
    "showBars",
    "showStandardBars",
    "pdfSliderChromeVisible",
    "isPdfTabStripVisible",
    "showTopTabStrip",
):
    if toolbar_name in viewport_source:
        fail(
            [
                f"document viewport still reads toolbar-only state {toolbar_name}; "
                "keep chrome invalidations out of document rendering"
            ]
        )

methods_by_name = {method.name: method for method in methods}
entry_names = (
    "PdfViewerScreen",
    "PdfViewerDocumentSetup",
    "PdfViewerScreenContent",
    "PdfViewerScreenOverlays",
    "PdfViewerSurfaceContent",
    "PdfViewerDocumentViewport",
    "PdfViewerChromeSurface",
    "PdfViewerChromeMusicianAndIndicators",
    "PdfViewerChromeNavigation",
    "PdfViewerChromeBottomAndEditing",
    "PdfViewerChromeTts",
    "PdfViewerPaginationPage",
    "PdfViewerReaderSurface",
)
failures: list[str] = []
for name in entry_names:
    method = methods_by_name.get(name)
    if method is None:
        failures.append(f"missing compiled reader method {name}")
    elif method.code_bytes > MAX_READER_METHOD_CODE_BYTES:
        failures.append(
            f"{name} has {method.code_bytes} JVM code bytes "
            f"(limit {MAX_READER_METHOD_CODE_BYTES})"
        )

lambda_methods = [
    method
    for method in methods
    if method.name.startswith("PdfViewer") and "$lambda$" in method.name
]
for method in lambda_methods:
    slots = descriptor_parameter_slots(method.descriptor)
    if slots > MAX_READER_LAMBDA_PARAMETER_SLOTS:
        failures.append(
            f"{method.name} has {slots} captured parameter slots "
            f"(limit {MAX_READER_LAMBDA_PARAMETER_SLOTS})"
        )
    if method.code_bytes > MAX_READER_LAMBDA_CODE_BYTES:
        failures.append(
            f"{method.name} has {method.code_bytes} JVM code bytes "
            f"(limit {MAX_READER_LAMBDA_CODE_BYTES})"
        )

if failures:
    fail(failures)

print(f"compiled reader methods: {len(methods)}")
for name in entry_names:
    method = methods_by_name[name]
    print(
        f"{name}: code={method.code_bytes} bytes, "
        f"stack={method.max_stack}, locals={method.max_locals}"
    )
if lambda_methods:
    largest_lambda = max(
        lambda_methods,
        key=lambda method: descriptor_parameter_slots(method.descriptor),
    )
    print(
        "largest generated reader lambda: "
        f"{largest_lambda.name} "
        f"params={descriptor_parameter_slots(largest_lambda.descriptor)}, "
        f"code={largest_lambda.code_bytes} bytes"
    )
else:
    print("generated reader lambdas: none")
print("PASS: reader JVM methods and generated lambda captures are verifier-safe")
