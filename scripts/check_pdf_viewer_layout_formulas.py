#!/usr/bin/env python3
"""Guard the reader inset formulas while PdfViewerScreen is split into composables.

The layout values are deliberately kept in PdfViewerScreen for now because they are
shared by the document viewport and chrome.  This source-level check protects the
behavioral contract against an accidental padding change during further extraction.
It compares the current formula blocks with the last committed Android benchmark and
also exercises the small visibility truth table used by the formulas.
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_PATH = ROOT / "app/src/main/java/com/aryan/reader/pdf/PdfViewerScreen.kt"
SOURCE_REPO_PATH = "app/src/main/java/com/aryan/reader/pdf/PdfViewerScreen.kt"


def normalize(block: str) -> str:
    """Ignore formatting and comments while comparing formula blocks."""

    block = re.sub(r"//.*", "", block)
    block = re.sub(r"/\*.*?\*/", "", block, flags=re.DOTALL)
    return re.sub(r"\s+", "", block)


def committed_source() -> str:
    return subprocess.check_output(
        ["git", "show", f"HEAD:{SOURCE_REPO_PATH}"],
        cwd=ROOT,
        text=True,
    )


def block_between(source: str, start: str, end: str) -> str:
    start_index = source.find(start)
    if start_index < 0:
        raise AssertionError(f"missing formula anchor: {start}")
    end_index = source.find(end, start_index + len(start))
    if end_index < 0:
        raise AssertionError(f"missing formula terminator: {end}")
    return source[start_index:end_index]


def assert_contains(source: str, pattern: str, label: str) -> None:
    if re.search(pattern, source, flags=re.DOTALL) is None:
        raise AssertionError(f"missing {label}: /{pattern}/")


def assert_same_as_benchmark(current: str, benchmark: str, start: str, end: str, label: str) -> None:
    current_block = normalize(block_between(current, start, end))
    benchmark_block = normalize(block_between(benchmark, start, end))
    if current_block != benchmark_block:
        raise AssertionError(
            f"{label} differs from the committed Android benchmark\n"
            f"current:   {current_block}\n"
            f"benchmark: {benchmark_block}"
        )


def visibility_truth_table() -> None:
    """Keep the SYNC-mode contract explicit: bars contribute only when shown."""

    def effective_nav_bar(mode: str, bars_visible: bool, nav: int) -> int:
        return nav if mode == "DEFAULT" or (mode == "SYNC" and bars_visible) else 0

    assert effective_nav_bar("DEFAULT", False, 126) == 126
    assert effective_nav_bar("DEFAULT", True, 126) == 126
    assert effective_nav_bar("SYNC", False, 126) == 0
    assert effective_nav_bar("SYNC", True, 126) == 126
    assert effective_nav_bar("IMMERSIVE", True, 126) == 0


def main() -> int:
    current = SOURCE_PATH.read_text()
    benchmark = committed_source()

    # These are the exact blocks that used to live together in the monolithic
    # composable. They must remain behaviorally identical after extraction.
    formula_blocks = (
        (
            "targetVerticalHeaderHeight",
            "val targetVerticalHeaderHeight = remember(",
            "val verticalHeaderHeight by animateDpAsState",
        ),
        (
            "targetTopOverlayInset",
            "val targetTopOverlayInset = remember(",
            "val topOverlayInset by animateDpAsState",
        ),
        (
            "verticalFooterHeight",
            "val verticalFooterHeight by remember(",
            "var errorMessage by remember",
        ),
        (
            "bottomScrollLimitPx",
            "val bottomScrollLimitPx = remember(",
            "val topScrollLimitPx =",
        ),
    )
    for label, start, end in formula_blocks:
        assert_same_as_benchmark(current, benchmark, start, end, label)

    assert_contains(current, r"val\s+showStandardBars\s*=\s*showBars\s*&&\s*!isEditMode", "standard-bar visibility")
    assert_contains(
        current,
        r"systemUiMode\s*==\s*SystemUiMode\.DEFAULT\s*\|\|\s*\(systemUiMode\s*==\s*SystemUiMode\.SYNC\s*&&\s*showStandardBars\)",
        "SYNC effective navigation-bar condition",
    )
    assert_contains(current, r"contentWindowInsets\s*=\s*WindowInsets\(0,\s*0,\s*0,\s*0\)", "scaffold inset opt-out")
    assert_contains(current, r"dockHeight\s*\+\s*if\s*\(systemUiMode\s*==\s*SystemUiMode\.DEFAULT\)\s*statusBarHeightDp", "top edit-dock status inset")
    assert_contains(current, r"dockHeight\s*\+\s*if\s*\(systemUiMode\s*==\s*SystemUiMode\.DEFAULT\)\s*with\(density\)\s*\{\s*navBarHeight\.toDp\(\)\s*\}", "bottom edit-dock navigation inset")
    assert_contains(current, r"if\s*\(!showStandardBars\)\s*\{\s*0\.dp\s*\}\s*else\s*\{\s*var\s+inset\s*=\s*56\.dp", "top overlay hidden-bars branch")

    visibility_truth_table()
    print("PASS: PdfViewerScreen inset/padding formulas match HEAD benchmark")
    print("PASS: DEFAULT/SYNC/hidden-bar effective navigation truth table")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, subprocess.CalledProcessError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
