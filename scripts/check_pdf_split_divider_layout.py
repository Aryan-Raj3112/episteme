#!/usr/bin/env python3
"""Guard the Android split divider's stable interaction/preview contract.

The divider's visual node is allowed to follow the local drag preview, but the
narrow pointer/accessibility target must remain anchored to the committed layout
until release.  This source-level check keeps that distinction from being
collapsed during future Compose refactors and prevents a full-screen pointer
overlay from stealing reader gestures.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aryan/reader/pdf/PdfSplitReaderScreen.kt"


def require(source: str, pattern: str, label: str) -> None:
    if re.search(pattern, source, flags=re.DOTALL) is None:
        raise AssertionError(f"missing {label}: /{pattern}/")


def main() -> int:
    source = SOURCE.read_text()

    require(source, r"val\s+dividerSemanticsModifier\s*=\s*Modifier\s*\.semantics", "separate divider semantics")
    require(source, r"val\s+dividerPointerModifier\s*=\s*Modifier\.pointerInput", "separate divider pointer input")
    require(source, r"val\s+dividerInteractionModifier\s*=\s*dividerSemanticsModifier\.then\(dividerPointerModifier\)", "combined stable interaction modifier")
    if "fillMaxSize().then(dividerPointerModifier)" in source:
        raise AssertionError("divider pointer input must not be attached to a full-screen overlay")
    if source.count(".then(dividerPointerModifier)") != 1:
        raise AssertionError("pointer input must only be combined into the stable divider interaction modifier")
    if source.count(".then(dividerInteractionModifier)") != 3:
        raise AssertionError("vertical LTR/RTL and horizontal layouts must all attach the narrow interaction target")
    require(
        source,
        r"\.offset\(x\s*=\s*interactionDividerOffset\).*?\.width\(PdfSplitDividerTouchTarget\).*?"
        r"\.height\(dividerHeight\).*?\.then\(dividerInteractionModifier\)",
        "vertical narrow stable interaction target",
    )
    require(
        source,
        r"\.offset\(y\s*=\s*interactionDividerOffset\).*?\.height\(PdfSplitDividerTouchTarget\).*?"
        r"\.width\(dividerWidth\).*?\.then\(dividerInteractionModifier\)",
        "horizontal narrow stable interaction target",
    )

    require(
        source,
        r"val\s+dividerAbsoluteStartPx\s*=.*?plan\.firstPaneSizePx",
        "committed divider pointer origin",
    )
    require(source, r"val\s+visualDividerOffset\s*=.*?framePlan\.firstPaneSizePx", "preview divider visual position")
    require(source, r"val\s+interactionDividerOffset\s*=.*?plan\.firstPaneSizePx", "committed divider interaction position")
    require(source, r"down\.consume\(\)", "divider stream ownership")
    require(source, r"event\.type\s*==\s*PointerEventType\.Release", "release-only commit handling")
    require(source, r"currentDividerAbsoluteStartPx\.value\s*\+\s*pointerAxis", "local down coordinate mapped to absolute axis")
    require(source, r"currentDividerAbsoluteStartPx\.value\s*\+\s*change\.position\.x", "vertical local drag coordinate mapped to absolute axis")
    require(source, r"currentDividerAbsoluteStartPx\.value\s*\+\s*change\.position\.y", "horizontal local drag coordinate mapped to absolute axis")
    require(source, r"pointerPositionPx\s*=\s*absolutePointer", "absolute pointer fraction mapping")

    print("PASS: split divider keeps visual preview separate from stable pointer/accessibility interaction")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as error:
        print(f"FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
