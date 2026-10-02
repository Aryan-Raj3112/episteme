#!/usr/bin/env python3
"""Generate the Episteme app icon from branding/app-icon/source/<variant>.svg.

The source file is a 728x554 "mock window" composition. Three of its layers are
presentation-only and are dropped here:

  * the full-canvas backdrop (gradients ``a``/``b``) -- reused as the icon background
  * the stroked outer frame  ``rect 133,81 458x444 rx=104``
  * the window drop shadow   ``rect 192,151 338x319 rx=60`` + ``filter c``

What survives is the mark: a rounded square (clip rect x192 y151 w338 h319 rx60)
holding the four fan/wave paths, painted with gradients d/e/g/h/j/k/m.

Outputs (all regenerated, safe to run repeatedly):

  branding/app-icon/episteme-icon.svg                     1024 square, iOS/desktop/Play
  branding/app-icon/episteme-icon-android-background.svg   108vp adaptive background (flat)
  branding/app-icon/episteme-icon-android-foreground.svg   108vp adaptive foreground (the fan)
  branding/app-icon/episteme-icon-monochrome.svg           108vp Android 13+ themed icon
  branding/app-icon/preview/*.png                          masked previews

Rasterisation goes through headless Chrome; launcher masks are applied in Pillow
because Chrome's headless SVG renderer silently drops nested clip-path groups.
"""

from __future__ import annotations

import argparse
import json
import math
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
ICON_DIR = ROOT / "branding" / "app-icon"
SOURCE_DIR = ICON_DIR / "source"
PREVIEW_DIR = ICON_DIR / "preview"

# ----------------------------------------------------------------- source geometry
SRC_W, SRC_H = 728.0, 554.0
MARK_X, MARK_Y, MARK_W, MARK_H, MARK_RX = 192.0, 151.0, 338.0, 319.0, 60.0
MARK_CX = MARK_X + MARK_W / 2          # 361
MARK_CY = MARK_Y + MARK_H / 2          # 310.5

# gradient ids lifted out of the source, in dependency-free blocks
CARD_GRADIENT_IDS = ("d", "e")                        # the cream field and its sheen
WAVE_UPPER_LEFT = "g"
WAVE_LOWER_LEFT = "h"
WAVE_UPPER_RIGHT = "j"
WAVE_LOWER_RIGHT = "k"
WAVE_SHADE = "m"
WAVE_GRADIENT_IDS = (WAVE_UPPER_LEFT, WAVE_LOWER_LEFT, WAVE_UPPER_RIGHT,
                     WAVE_LOWER_RIGHT, WAVE_SHADE)
MARK_GRADIENT_IDS = CARD_GRADIENT_IDS + WAVE_GRADIENT_IDS
MARK_CLIP_ID = "f"

# The wave paths are authored *oversized* and rely on the mark's clipPath to trim them.
# Three different baselines are drawn -- y=480 for the two left paths, y=474 for the
# lower-right one -- and the right-hand paths reach x=534..536 while the field stops at
# x=530. The clip flattened every baseline onto y=470 and cut the overhang away; that is
# what made the two pages meet at a single point at the bottom centre. The card is gone,
# so the clip is gone with it, and the trim has to be baked into the geometry instead --
# otherwise the left page sits visibly lower than the right and a sliver of gradient
# sticks out past the artwork on the far right.
FAN_TRIM = (
    ("M188 316c66 0 126 35 159 154V480H188z", "M188 316c66 0 126 35 159 154H188z"),
    ("M188 383c67 0 124 30 159 87V480H188z", "M188 383c67 0 124 30 159 87H188z"),
    ("M347 470c37-70 98-106 187-106v110H347Z", "M347 470c37-70 98-106 187-106v106H347Z"),
    ("M350 352h186v123H350z", "M350 352h184v118H350z"),
)

# ----------------------------------------------------------------- output geometry
BLEED = 319.0                       # square source window that becomes the icon
MASTER = 1024                        # iOS / Play Store / desktop master canvas
ANDROID = 108                         # adaptive icon viewport
PREVIEW_PX = 512
# The mark is 338x319, i.e. 5.9% wider than tall. It is fitted with a uniform scale
# ("cover") so the artwork is never distorted; the surplus width is cropped off by the
# canvas. The crop only ever eats card edge -- the waves are already clipped to the
# card -- and every launcher rounds the corners anyway.
FIELD_COLOR = "#FFFFFF"                # the icon's field; the previous icon had a solid bg too
FAN_WIDTH_FRACTION = 0.75              # fan width as a fraction of the *visible* icon (max that fits the 33dp safe radius)
ANDROID_VISIBLE_DP = 72                # inner 72 of the 108dp adaptive viewport
MONO_GLYPH_FILL = 66 / 108             # themed glyph inside the documented 66dp safe zone
MONO_STYLE = "wedge"                     # "wedge" | "arcs" | "waves" | "field2" | "fanfield"
# Gap between the themed glyph's arcs, in source units (the bleed window is BLEED wide).
# ~4% of the icon: still open at 64px, and not so wide that the arcs stop reading as a
# book at 40px. Compared side by side at 6 / 10 / 14 / 18 in preview/monochrome-arcs.png.
MONO_ARC_GAP = 14.0
MONO_STYLES = ("wedge", "fanfield", "arcs", "waves", "field2")   # compare-sheet order
# styles whose geometry is trimmed to the bleed window, so no clip is needed
CLIP_FREE_MONO_STYLES = frozenset({"wedge"})

CHROME_CANDIDATES = (
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "google-chrome",
    "chromium",
    "chromium-browser",
)


# ================================================================= parsing
def _blocks(text: str, pattern: str) -> dict[str, str]:
    return {m.group(1): m.group(0) for m in re.finditer(pattern, text, re.S)}


def parse_source(path: pathlib.Path) -> dict:
    text = path.read_text()
    defs = text.split("<defs>", 1)[1].split("</defs>", 1)[0]

    linear = _blocks(defs, r'<linearGradient id="(\w+)".*?</linearGradient>')
    radial = _blocks(defs, r'<radialGradient id="(\w+)".*?</radialGradient>')
    clip = _blocks(defs, r'<clipPath id="(\w+)">(.*?)</clipPath>')

    missing = [i for i in MARK_GRADIENT_IDS if i not in linear and i not in radial]
    if missing:
        raise SystemExit(f"{path.name}: missing mark gradients {missing}")
    if MARK_CLIP_ID not in clip:
        raise SystemExit(f"{path.name}: missing clipPath #{MARK_CLIP_ID}")

    group = re.search(r'<g clip-path="url\(#%s\)">(.*?)</g>' % MARK_CLIP_ID, text, re.S)
    if not group:
        raise SystemExit(f"{path.name}: missing the mark group")

    # Bake the clip's trim into the path data. Applied to the group text rather than to
    # `waves` so that `wave_elements` (which reads the group) and `mono_band_paths` (which
    # reads `waves`) cannot drift apart. Every entry must match: if the artwork is
    # redrawn, a stale table means the fan silently loses its common baseline again, which
    # is a subtle enough regression to ship by accident.
    inner = group.group(1)
    for old, new in FAN_TRIM:
        if old not in inner:
            raise SystemExit(
                f"{path.name}: FAN_TRIM is stale -- no path matches {old!r}. The wave "
                "geometry changed; re-derive the trim or drop the entry."
            )
        inner = inner.replace(old, new)

    waves = {}
    for element in re.findall(r"<path\b[^>]*/>", inner):
        fill = re.search(r'fill="url\(#(\w+)\)"', element)
        if fill and fill.group(1) in WAVE_GRADIENT_IDS:
            waves[fill.group(1)] = re.search(r'\sd="([^"]+)"', element).group(1)

    return {
        "linear": linear,
        "radial": radial,
        "mark_inner": inner.strip(),
        "waves": waves,
    }


# ------------------------------------------------------------- path helpers
_PATH_TOKEN = re.compile(r"[MmCcLlHhVvZz]|[-+]?(?:\d*\.\d+|\d+)(?:[eE][-+]?\d+)?")


def path_cubics(d: str) -> list[tuple]:
    """Every cubic of a path, as absolute 4-point tuples.

    The wave paths are ``M``, one or two cubics, and straight closing edges, so this only
    understands that much of the grammar -- straight segments are skipped because they only
    move the current point, and anything else raises rather than silently mis-reading the
    artwork.
    """
    tokens = _PATH_TOKEN.findall(d)
    cmd = None
    x = y = 0.0
    out: list[tuple] = []
    i = 0
    while i < len(tokens):
        if tokens[i].isalpha():
            cmd = tokens[i]
            i += 1
            if cmd in "Zz":
                break
        if cmd is None:
            raise SystemExit(f"path does not start with a command: {d!r}")
        rel = cmd.islower()
        head = cmd.upper()
        if head == "M":
            nx, ny = float(tokens[i]), float(tokens[i + 1])
            i += 2
            x, y = (x + nx, y + ny) if rel else (nx, ny)
            cmd = "l" if rel else "L"
        elif head == "L":
            nx, ny = float(tokens[i]), float(tokens[i + 1])
            i += 2
            x, y = (x + nx, y + ny) if rel else (nx, ny)
        elif head == "H":
            nx = float(tokens[i])
            i += 1
            x = x + nx if rel else nx
        elif head == "V":
            ny = float(tokens[i])
            i += 1
            y = y + ny if rel else ny
        elif head == "C":
            v = [float(t) for t in tokens[i:i + 6]]
            i += 6
            if rel:
                v = [x + v[0], y + v[1], x + v[2], y + v[3], x + v[4], y + v[5]]
            out.append(((x, y), (v[0], v[1]), (v[2], v[3]), (v[4], v[5])))
            x, y = v[4], v[5]
        else:
            raise SystemExit(f"unsupported path command {cmd!r} in {d!r}")
    if not out:
        raise SystemExit(f"no cubic found in {d!r}")
    return out


def _f(v: float) -> str:
    return f"{v:.2f}".rstrip("0").rstrip(".")


def _curve(c: tuple) -> str:
    return "C" + " ".join(f"{_f(x)} {_f(y)}" for x, y in c[1:])


def _curve_rev(c: tuple) -> str:
    return "C" + " ".join(f"{_f(x)} {_f(y)}" for x, y in (c[2], c[1], c[0]))


def _lerp(a: tuple, b: tuple, t: float) -> tuple:
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def _split_cubic(c: tuple, t: float) -> tuple[tuple, tuple]:
    """de Casteljau subdivision into the cubic before `t` and the one after."""
    p0, p1, p2, p3 = c
    a, b, d = _lerp(p0, p1, t), _lerp(p1, p2, t), _lerp(p2, p3, t)
    e, f = _lerp(a, b, t), _lerp(b, d, t)
    mid = _lerp(e, f, t)
    return (p0, a, e, mid), (mid, f, d, p3)


def _clip_cubic(c: tuple, limit: float, keep: str) -> tuple | None:
    """The contiguous part of cubic `c` whose x stays on one side of `limit`.

    `keep="min"` keeps x <= limit, `"max"` keeps x >= limit. Each wave curve is monotonic
    in x -- but not all of them run left to right -- so whether the surviving portion is a
    prefix or a suffix depends on the curve's direction. Bisecting the subdivision
    parameter for the crossing is exact to floating point. Returns None if no part of the
    curve qualifies.
    """
    inside = (lambda x: x <= limit) if keep == "min" else (lambda x: x >= limit)
    if inside(c[0][0]) and inside(c[3][0]):
        return c
    if not inside(c[0][0]) and not inside(c[3][0]):
        return None
    prefix = (c[3][0] >= c[0][0]) == (keep == "min")
    lo, hi = 0.0, 1.0
    for _ in range(64):
        mid = (lo + hi) / 2
        # prefix: walk lo up to the last parameter still inside; suffix: walk hi down to
        # the first one. Comparing against `prefix` keeps the rule single-sourced.
        if inside(_split_cubic(c, mid)[1][0][0]) == prefix:
            lo = mid
        else:
            hi = mid
    return _split_cubic(c, hi)[0] if prefix else _split_cubic(c, lo)[1]


def mono_band_paths(src: dict) -> tuple[str, str]:
    """The two pale wave bands, trimmed to the bleed window so no clip is needed.

    The source paths overhang the window by 4-10 units. An SVG hides that behind a
    `clip-path`; a VectorDrawable cannot, because it has no per-path clip -- only a
    whole-group one whose coordinate space is not worth relying on across devices.
    Trimming the geometry instead keeps one path definition valid in both formats.

    Only the curve *ends* move: the straight closing edges are already axis aligned, so
    these shapes are exact rather than approximated.
    """
    x0, _, w = bleed_rect()
    x1 = x0 + w

    upper_l = path_cubics(src["waves"][WAVE_UPPER_LEFT])
    upper_r = path_cubics(src["waves"][WAVE_UPPER_RIGHT])
    if len(upper_l) != 1 or len(upper_r) != 2:
        raise SystemExit(
            "expected 1 cubic in the upper-left wave and 2 in the upper-right one, "
            f"got {len(upper_l)} and {len(upper_r)}"
        )

    # C1 runs left to right, so trimming it to x >= x0 leaves the tail. C3 runs left to
    # right and trims to x <= x1 leaving the head. C4 runs *right to left*, so trimming it
    # to x <= x1 leaves its tail -- which is exactly the segment that closes the band.
    left = _clip_cubic(upper_l[0], x0, "max")
    right = _clip_cubic(upper_r[0], x1, "min")
    closing = _clip_cubic(upper_r[1], x1, "min")
    if left is None or right is None or closing is None:
        raise SystemExit("a pale wave falls entirely outside the bleed window")

    return (f"M{_f(left[0][0])} {_f(left[0][1])} {_curve(left)} H{_f(x0)} Z",
            f"M{_f(right[0][0])} {_f(right[0][1])} {_curve(right)} "
            f"V{_f(closing[0][1])} {_curve(closing)} Z")


def _offset(c: tuple, delta: float, side: int) -> tuple:
    """Shift a cubic's two interior control points along their normals.

    The cheap control-polygon approximation of a curve offset. Indistinguishable from a
    true offset at the few-percent gaps used here, and it needs no arc-length maths.
    """
    p0, p1, p2, p3 = c

    def normal(a, b):
        dx, dy = b[0] - a[0], b[1] - a[1]
        length = math.hypot(dx, dy) or 1.0
        return (-dy / length * side, dx / length * side)

    n1, n2 = normal(p0, p1), normal(p3, p2)
    return (p0,
            (p1[0] + n1[0] * delta, p1[1] + n1[1] * delta),
            (p2[0] + n2[0] * delta, p2[1] + n2[1] * delta),
            p3)


def mono_arc_paths(src: dict, gap: float) -> str:
    """The four wave bands as four separate closed shapes, separated by a `gap`-wide line.

    Each band is rebuilt from its own boundary curve rather than reusing the artwork's
    paths, because those overlap (the lower-left band is drawn on top of the upper-left
    one) and a monochrome layer has to be a clean union of disjoint shapes. The gap is
    carved by offsetting the *shared* curve inside the upper band, so it opens towards
    the outer edge and pinches shut at the spine -- the way pages do in a real book.
    """
    upper_l = path_cubics(src["waves"][WAVE_UPPER_LEFT])[0]
    lower_l = path_cubics(src["waves"][WAVE_LOWER_LEFT])[0]
    upper_r = path_cubics(src["waves"][WAVE_UPPER_RIGHT])[0]
    lower_r = path_cubics(src["waves"][WAVE_LOWER_RIGHT])[0]
    left_x, left_y = lower_l[0]
    right_x, right_y = lower_r[0]
    # the lower bands are closed along the bleed window's bottom edge
    floor = bleed_rect()[1] + bleed_rect()[2]
    return " ".join([
        # upper-left: out along its own top curve, back along the offset shared curve
        f"M{_f(upper_l[0][0])} {_f(upper_l[0][1])} {_curve(upper_l)} "
        f"{_curve_rev(_offset(lower_l, gap, -1))} Z",
        # lower-left: under the shared curve, closed along the floor
        f"M{_f(left_x)} {_f(left_y)} {_curve(lower_l)} H{_f(left_x)} V{_f(left_y)} Z",
        # upper-right: out along its own top curve, drop, back along the offset one
        f"M{_f(upper_r[0][0])} {_f(upper_r[0][1])} {_curve(upper_r)} "
        f"V{_f(lower_r[3][1])} {_curve_rev(_offset(lower_r, gap, 1))} Z",
        # lower-right: under the shared curve, closed along the floor
        f"M{_f(right_x)} {_f(right_y)} {_curve(lower_r)} H{_f(right_x)} V{_f(floor)} Z",
    ])



# ================================================================= emission
def mark_defs(src: dict) -> str:
    blocks = [src["linear"].get(i) or src["radial"][i] for i in MARK_GRADIENT_IDS]
    blocks.append(bleed_clip())
    return "\n".join(b.rstrip() for b in blocks)


def bleed_rect() -> tuple[float, float, float]:
    """The square window of source artwork that becomes the icon.

    The mark's card is 338x319, so a 319x319 window is taken through its centre and the
    extra 19 units of width are discarded. Everything outside it is cropped away, which
    is why the card's own rounded corners (rx=60) are *not* part of the icon: the icon
    bleeds to all four edges and the launcher supplies the outline. That also keeps the
    artwork opaque end to end, which iOS and both stores require.
    """
    return MARK_CX - BLEED / 2, MARK_CY - BLEED / 2, BLEED


def bleed_clip() -> str:
    x, y, w = bleed_rect()
    return f'  <clipPath id="bleed"><rect x="{x:g}" y="{y:g}" width="{w:g}" height="{w:g}"/></clipPath>'


def wave_elements(src: dict) -> list[str]:
    """The four fan/wave paths as authored.

    ``blue_default.svg`` also carries a ``fill="url(#sh)"`` rect inside the clip group that
    the other variants do not; only known paint servers are kept, so all seven variants
    stay structurally identical.
    """
    return [e for e in re.findall(r"<(?:rect|path)\b[^>]*/>", src["mark_inner"])
            if (m := re.search(r'fill="url\(#(\w+)\)"', e)) and m.group(1) in WAVE_GRADIENT_IDS]


def fan_bbox(src: dict, tmp: pathlib.Path) -> tuple[float, float, float, float]:
    """Bounding box of the wave paths, in source units, measured by rasterising them.

    Deriving it from the Bézier control points would overshoot (control polygons bulge
    past their curves) and hardcoding it would silently rot the next time the artwork
    changes. Measuring the rendered alpha is exact and cheap at build time.
    """
    # The gradients have to travel with the paths or the fills resolve to nothing. The
    # viewBox is square and 1:1 with the render, because a viewBox that does not match the
    # window aspect gets letterboxed and every measured coordinate picks up the offset.
    doc = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{SRC_W:g}" height="{SRC_W:g}" '
           f'viewBox="0 0 {SRC_W:g} {SRC_W:g}" fill="none">'
           f"<defs>{mark_defs(src)}</defs>"
           + "".join(wave_elements(src))
           + "</svg>")
    img = rasterise(doc, round(SRC_W), tmp / "fan-bbox.png")
    box = img.getchannel("A").getbbox()
    if box is None:
        raise SystemExit("the wave paths rendered empty")
    return box  # 1px == 1 source unit at this size


def fan_geometry(src: dict, tmp: pathlib.Path, canvas: float, visible: float) -> dict:
    """Scale and centre the fan inside the visible part of `canvas`.

    The fan is sized as a fraction of the *visible* icon rather than of the viewport, so
    Android (72 of 108dp visible) and iOS (all of it) end up with the same optical size
    despite very different canvases.
    """
    x0, y0, x1, y1 = fan_bbox(src, tmp)
    width, height = x1 - x0, y1 - y0
    scale = visible * FAN_WIDTH_FRACTION / width
    placed_w, placed_h = width * scale, height * scale
    tx = canvas / 2 - (x0 + width / 2) * scale
    ty = canvas / 2 - (y0 + height / 2) * scale
    return {
        "transform": f"translate({tx:.4f} {ty:.4f}) scale({scale:.5f})",
        "scale": scale,
        "placed": (placed_w, placed_h),
        # conservative: the true extremes sit inside the bounding box
        "half_diagonal": math.hypot(placed_w, placed_h) / 2,
    }


def fan_markup(src: dict, geometry: dict) -> str:
    """The wave paths, scaled and centred on the field.

    No clip: ``FAN_TRIM`` has already baked the mark clip's trim into the path data, so
    the fan carries its own silhouette and needs no container to cut it.
    """
    return (f'  <g transform="{geometry["transform"]}">\n    '
            + "\n    ".join(wave_elements(src))
            + "\n  </g>")


def fit_transform(canvas: float, fill: float = 1.0) -> str:
    """Place the bleed window exactly on a square ``canvas``.

    A uniform scale -- the artwork is never distorted. ``fill`` shrinks the result about
    the centre; the themed-icon glyph uses it to stay inside the 66dp safe zone.
    """
    s = canvas * fill / BLEED
    tx = canvas / 2 - MARK_CX * s
    ty = canvas / 2 - MARK_CY * s
    return f"translate({tx:.3f} {ty:.3f}) scale({s:.5f})"


def svg_doc(size: float, defs: str, body: str) -> str:
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size:g}" height="{size:g}" '
            f'viewBox="0 0 {size:g} {size:g}" fill="none">\n'
            f"<defs>\n{defs}\n</defs>\n{body}\n</svg>\n")


def monochrome_body(src: dict, style: str | None = None) -> str:
    """Cream field with the two pale wave bands knocked out (even-odd).

    The chosen shape. A themed icon is one flat colour, so the design loses every
    internal boundary, and this composition holds up best under that: the field keeps the
    glyph reading as the icon's square silhouette at any size, while the two knocked-out
    bands leave enough of the fan visible to say "book" rather than "blank page".

    Alternatives, all kept as constants and shown in ``preview/monochrome-candidates.png``:

    ``"arcs"``   the four bands rebuilt from their own boundary curves and separated by a
                 carved gap -- four distinct shapes, but the gaps pinch to nothing at the
                 spine and the result reads as clutter.
    ``"waves"``  the four bands merged into one solid fan; the book reads, the square
                 silhouette does not.
    ``"field2"`` field minus the two *saturated* bands -- bolder at 40px, but a banner.

    The knock-out paths overhang the bleed window (4 units past the left edge, 4 past the
    right, 10 past the bottom). A monochrome layer is one path rather than a clipped
    group, so the clip is what keeps that overhang from showing up as slivers.
    """
    pale_l = "M188 316c66 0 126 35 159 154V480H188Z"
    pale_r = "M347 470c3-123 71-210 187-210v104c-89 0-150 36-187 106"
    deep_l = "M188 383c67 0 124 30 159 87V480H188Z"
    deep_r = "M347 470c37-70 98-106 187-106v110H347Z"
    x, y, w = bleed_rect()
    window = f"M{x:g} {y:g} H{x + w:g} V{y + w:g} H{x:g} Z"

    style = style or MONO_STYLE
    if style == "wedge":
        left, right = mono_band_paths(src)
        return _mono_svg(f"{window} {left} {right}", rule="evenodd")
    if style == "arcs":
        return _mono_svg(mono_arc_paths(src, MONO_ARC_GAP), rule="nonzero")
    if style == "waves":
        return _mono_svg(f"{pale_l} {pale_r} {deep_r}", rule="nonzero")
    if style == "field2":
        return _mono_svg(f"{window} {deep_l} {deep_r}", rule="evenodd")
    if style == "fanfield":
        # the field with the whole fan knocked out, echoing the colour icon's shape
        return _mono_svg(f"{window} {deep_l} {deep_r} {pale_l} {pale_r}", rule="evenodd")
    raise SystemExit(f"unknown MONO_STYLE {style!r}")


def _mono_svg(d: str, rule: str) -> str:
    return (
        f"{bleed_clip()}\n"
        f'  <g transform="{fit_transform(ANDROID, MONO_GLYPH_FILL)}">\n'
        f'    <path d="{d}" fill="#FFFFFFFF" fill-rule="{rule}" clip-rule="evenodd" '
        f'clip-path="url(#bleed)"/>\n'
        f"  </g>"
    )


def build_svgs(src: dict, tmp: pathlib.Path) -> dict[str, str]:
    md = mark_defs(src)
    master_geo = fan_geometry(src, tmp, MASTER, MASTER)
    fg_geo = fan_geometry(src, tmp, ANDROID, ANDROID_VISIBLE_DP)
    return {
        # iOS / Play Store / desktop: the whole square is the icon, so the field is drawn
        # full-bleed here and the fan sits inside it at the same optical size Android gets
        "episteme-icon.svg": svg_doc(
            MASTER, md,
            f'  <rect width="{MASTER:g}" height="{MASTER:g}" fill="{FIELD_COLOR}"/>\n'
            + fan_markup(src, master_geo)),
        # Android adaptive: a flat background and a foreground carrying only the artwork,
        # which is the conventional structure. A full-bleed background with no foreground
        # is fragile -- launchers have been seen drawing such a background into the inner
        # 72dp rather than the whole 108dp, which shrinks the icon.
        "episteme-icon-android-background.svg": svg_doc(
            ANDROID, "",
            f'  <rect width="{ANDROID:g}" height="{ANDROID:g}" fill="{FIELD_COLOR}"/>'),
        "episteme-icon-android-foreground.svg": svg_doc(
            ANDROID, md, fan_markup(src, fg_geo)),
        "episteme-icon-monochrome.svg": svg_doc(ANDROID, "", monochrome_body(src)),
    }


def vector_layers(src: dict) -> dict[str, str]:
    """The Android layers that vectorise exactly.

    Only the flat ones. The background is a solid fill and the monochrome glyph is a single
    colour, so both are one small file each with no density buckets. The *foreground* is
    deliberately not among them: it carries the wave gradients, two of which are radials
    positioned by a gradient transform that ``VectorDrawable`` cannot express, so
    approximating it would quietly change the artwork. It ships as density-bucketed PNGs.
    """
    return {
        "ic_launcher_background.xml": (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- Generated by scripts/generate_app_icons.py. Flat field behind the fan. -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            f'  <path android:pathData="M0,0 H108 V108 H0 Z" android:fillColor="{FIELD_COLOR}"/>\n'
            f"</vector>\n"
        ),
        "ic_launcher_monochrome.xml": _vector_group(_vector_mono(src)),
    }


def _vector_group(inner: str) -> str:
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- Generated by scripts/generate_app_icons.py. Flat colour so the launcher can\n"
            "     tint it; the glyph spans the 66dp safe zone of the 108dp viewport and its\n"
            "     geometry is pre-trimmed, so it needs no clip. -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            + inner
            + "</vector>\n")


def _vector_mono(src: dict) -> str:
    body = monochrome_body(src)
    d = re.search(r'<path d="([^"]+)"', body).group(1)
    rule = re.search(r'fill-rule="(\w+)"', body).group(1)
    t = re.search(r'<g transform="translate\(([-\d.]+) ([-\d.]+)\) scale\(([\d.]+)\)"', body)
    tx, ty, scale = (float(t.group(i)) for i in (1, 2, 3))
    evenodd = ' android:fillType="evenOdd"' if rule == "evenodd" else ""
    return (f'  <group android:scaleX="{scale:.5f}" android:scaleY="{scale:.5f}"\n'
            f'      android:translateX="{tx:.3f}" android:translateY="{ty:.3f}">\n'
            f'    <path android:pathData="{d}" android:fillColor="#FFFFFFFF"{evenodd}/>\n'
            f"  </group>\n")


# ================================================================= rasterising
def chrome() -> str:
    for c in CHROME_CANDIDATES:
        found = shutil.which(c) or (c if pathlib.Path(c).exists() else None)
        if found:
            return found
    raise SystemExit("no Chrome/Chromium found; install one or set CHROME_PATH")


def rasterise(svg_text: str, size: int, out_png: pathlib.Path) -> Image.Image:
    svg_text = re.sub(r'width="[\d.]+" height="[\d.]+"', f'width="{size}" height="{size}"',
                      svg_text, count=1)
    src_file = out_png.with_suffix(".render.svg")
    src_file.write_text(svg_text)
    try:
        subprocess.run([chrome(), "--headless=new", "--disable-gpu", "--hide-scrollbars",
                        "--force-device-scale-factor=1", f"--window-size={size},{size}",
                        "--default-background-color=00000000",
                        f"--screenshot={out_png}", str(src_file)],
                       capture_output=True, check=True)
    finally:
        src_file.unlink(missing_ok=True)
    return Image.open(out_png).convert("RGBA")


def mask(size: int, kind: str, exponent: float = 4.6, visible: float = 1.0) -> Image.Image:
    """A launcher mask. `visible` is the fraction of the canvas the launcher reveals:
    1.0 for iOS and desktop (the OS masks the whole icon) and 72/108 for an Android adaptive
    layer, where the outer 18dp on each side is reserved for parallax and mask cropping.
    """
    s = size * 4
    m = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(m)
    r = s * visible / 2
    inset = s / 2 - r
    if kind == "circle":
        d.ellipse([inset, inset, s - inset - 1, s - inset - 1], fill=255)
    elif kind == "squircle":
        pts = []
        for i in range(720):
            t = 2 * math.pi * i / 720
            c, si = math.cos(t), math.sin(t)
            pts.append((s / 2 + r * math.copysign(abs(c) ** (2 / exponent), c),
                        s / 2 + r * math.copysign(abs(si) ** (2 / exponent), si)))
        d.polygon(pts, fill=255)
    elif kind == "rounded":
        d.rounded_rectangle([inset, inset, s - inset - 1, s - inset - 1],
                            radius=int(r * 2 * 0.2237), fill=255)
    else:
        d.rectangle([0, 0, s, s], fill=255)
    return m.resize((size, size), Image.LANCZOS)


def masked(icon: Image.Image, kind: str, plate=None, exponent: float = 4.6,
           visible: float = 1.0) -> Image.Image:
    """Apply a launcher mask to a square icon.

    `plate` defaults to transparent, which is what shipped launcher assets need -- the mask
    shape must be the icon's own outline, and anything outside it has to stay see-through
    so the launcher's wallpaper shows. Pass a colour only for preview sheets.
    """
    out = Image.new("RGBA", icon.size, (plate or (0, 0, 0)) + (255 if plate else 0,))
    out.paste(icon, (0, 0), mask(icon.width, kind, exponent, visible))
    return out


# ================================================================= raster assets
# Size tables follow the platform docs. Both stores reject an alpha channel on the
# marketing icon, and iOS rejects baked-in corner rounding -- the OS masks the icon.
# (px, filename) pairs, covering every entry AppIcon.appiconset/Contents.json references.
# A dict cannot be used: several slots share a pixel size (40px is both 20x20@2x and
# 40x40@1x, 120px is both 40x40@3x and 60x60@2x).
PREVIEW_PLATE = (92, 100, 112)   # neutral backdrop for preview sheets only
LEGACY_ICON_NAMES = frozenset({"ic_launcher.png", "ic_launcher_round.png",
                                 "ic_launcher_foreground.png"})

IOS_SIZES = (
    (20, "Icon-App-20x20@1x.png"), (40, "Icon-App-20x20@2x.png"), (60, "Icon-App-20x20@3x.png"),
    (29, "Icon-App-29x29@1x.png"), (58, "Icon-App-29x29@2x.png"), (87, "Icon-App-29x29@3x.png"),
    (40, "Icon-App-40x40@1x.png"), (80, "Icon-App-40x40@2x.png"), (120, "Icon-App-40x40@3x.png"),
    (120, "Icon-App-60x60@2x.png"), (180, "Icon-App-60x60@3x.png"),
    (152, "Icon-App-76x76@2x.png"), (167, "Icon-App-83.5x83.5@2x.png"),
    (1024, "Icon-App-1024x1024@1x.png"),
)
# legacy launcher icons are 48dp and are NOT masked by the OS, so the shape is baked in
ANDROID_DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
ANDROID_LEGACY_DP = 48
ANDROID_ADAPTIVE_DP = 108
DESKTOP_SIZES = (16, 24, 32, 48, 64, 128, 256, 512, 1024)
ICO_SIZES = (16, 24, 32, 48, 64, 128, 256)
SUPERSAMPLE = 4                      # render the master at 4x, then LANCZOS down


def write_png(img: Image.Image, path: pathlib.Path, keep_alpha: bool) -> None:
    """Write a PNG, flattening to opaque RGB where the target requires it.

    Flattening is only safe on a fully opaque image: `convert("RGB")` on RGBA keeps the
    undefined colour of transparent pixels, which silently bakes black wedges into a
    store listing. iOS and Google Play both reject a transparent app icon outright, so
    assert rather than produce one.
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    if keep_alpha:
        img.save(path, format="PNG", optimize=True)
        return
    if img.mode == "RGBA":
        lo, hi = img.getchannel("A").getextrema()
        if lo != 255:
            raise SystemExit(f"{path.name}: refusing to flatten, alpha range {lo}-{hi}")
    img.convert("RGB").save(path, format="PNG", optimize=True)


def write_raster_sets(docs: dict[str, str], tmp: pathlib.Path) -> list[pathlib.Path]:
    """Every PNG/ICO/ICNS the platforms expect, downsampled from one supersampled render."""
    out_dir = ICON_DIR / "png"
    if out_dir.exists():
        shutil.rmtree(out_dir)
    written: list[pathlib.Path] = []

    master = rasterise(docs["episteme-icon.svg"], MASTER * SUPERSAMPLE, tmp / "hi.png")
    mono = rasterise(docs["episteme-icon-monochrome.svg"],
                     ANDROID_ADAPTIVE_DP * 4 * SUPERSAMPLE, tmp / "hi-mono.png")

    def at(img: Image.Image, size: int) -> Image.Image:
        return img.resize((size, size), Image.LANCZOS)

    # ---- iOS: opaque, square, no baked rounding (the OS applies the squircle)
    for size, name in IOS_SIZES:
        p = out_dir / "ios" / name
        write_png(at(master, size), p, keep_alpha=False)
        written.append(p)

    # ---- Android legacy launcher icons: shape baked in, API < 26 launchers do not mask
    for density, factor in ANDROID_DENSITIES.items():
        size = round(ANDROID_LEGACY_DP * factor)
        base = out_dir / "android" / f"mipmap-{density}"
        # alpha is required here: on API < 26 nothing masks the icon, so the baked-in
        # outline is the icon's edge and everything outside it must stay transparent
        write_png(masked(at(master, size), "squircle", exponent=4.0),
                  base / "ic_launcher.png", keep_alpha=True)
        write_png(masked(at(master, size), "circle"),
                  base / "ic_launcher_round.png", keep_alpha=True)
        written += [base / "ic_launcher.png", base / "ic_launcher_round.png"]

        # The adaptive foreground keeps the wave gradients, so it ships as a PNG per
        # density. The background and monochrome layers are flat and ship as
        # VectorDrawables instead (see vector_layers).
        adaptive = out_dir / "android" / "adaptive" / f"mipmap-{density}"
        write_png(rasterise(docs["episteme-icon-android-foreground.svg"],
                           round(ANDROID_ADAPTIVE_DP * factor), tmp / f"fg-{density}.png"),
                  adaptive / "ic_launcher_foreground.png", keep_alpha=True)
        written.append(adaptive / "ic_launcher_foreground.png")

    # ---- desktop: Linux hicolor / Flatpak / AUR, Windows .ico, macOS .icns
    for size in DESKTOP_SIZES:
        p = out_dir / "desktop" / f"episteme-{size}.png"
        write_png(at(master, size), p, keep_alpha=False)
        written.append(p)
    ico = out_dir / "desktop" / "episteme.ico"
    ico.parent.mkdir(parents=True, exist_ok=True)
    at(master, max(ICO_SIZES)).save(ico, format="ICO", sizes=[(s, s) for s in ICO_SIZES])
    written.append(ico)
    written += write_icns(master, out_dir / "desktop" / "episteme.icns", tmp)

    # ---- store listings
    play = out_dir / "store" / "play-store-512.png"
    write_png(at(master, 512), play, keep_alpha=False)
    written.append(play)
    appstore = out_dir / "store" / "app-store-1024.png"
    write_png(at(master, 1024), appstore, keep_alpha=False)
    written.append(appstore)

    # ---- branding: the header icon on the public README, and the website/favicon size.
    # Same 512 full-bleed square the store listings use; the existing README asset was a
    # 512x512 opaque square too, so it is a straight file swap with no markup change.
    for size in (512, 256, 128, 64, 32, 16):
        p = out_dir / "branding" / f"icon-{size}.png"
        write_png(at(master, size), p, keep_alpha=False)
        written.append(p)
    return written


# ================================================================= deploy
# Where each generated asset is consumed. Kept as one table so `--deploy` stays the only
# thing that knows the platform layouts.
ANDROID_RES = ROOT / "app/src/main/res"
IOS_APPICON = ROOT / "iosApp/Reader/Assets.xcassets/AppIcon.appiconset"
DESKTOP_RES = ROOT / "desktopApp/src/desktopMain/resources"
# superseded by the full-bleed adaptive layers, which carry their own artwork
# the legacy icons ship as PNG: byte-identical to the reviewed asset, with no re-encode
# step that could drop the alpha channel the baked-in outline depends on
OBSOLETE_ANDROID_FILES = tuple(
    f"mipmap-{density}/{name}.webp"
    for density in ANDROID_DENSITIES for name in ("ic_launcher", "ic_launcher_round")
) + tuple(
    # the background is a flat vector now; this raster is left over from the full-bleed
    # layout and would otherwise ship as dead weight
    f"mipmap-{density}/ic_launcher_background.png" for density in ANDROID_DENSITIES
)


def deploy() -> list[pathlib.Path]:
    """Copy the generated assets into the app. Idempotent."""
    src = ICON_DIR / "png"
    changed: list[pathlib.Path] = []

    def put(from_rel: str, to: pathlib.Path) -> None:
        data = (src / from_rel).read_bytes()
        if to.exists() and to.read_bytes() == data:
            return
        to.parent.mkdir(parents=True, exist_ok=True)
        to.write_bytes(data)
        changed.append(to)

    # Android: adaptive layers are vectors, legacy icons are density-bucketed PNGs
    for density in ANDROID_DENSITIES:
        put(f"android/adaptive/mipmap-{density}/ic_launcher_foreground.png",
            ANDROID_RES / f"mipmap-{density}/ic_launcher_foreground.png")
        put(f"android/mipmap-{density}/ic_launcher.png",
            ANDROID_RES / f"mipmap-{density}/ic_launcher.png")
        put(f"android/mipmap-{density}/ic_launcher_round.png",
            ANDROID_RES / f"mipmap-{density}/ic_launcher_round.png")

    for name in ("ic_launcher_background.xml", "ic_launcher_monochrome.xml"):
        put(f"../{name}", ANDROID_RES / "drawable" / name)
    put("store/play-store-512.png", ROOT / "app/src/main/ic_launcher-playstore.png")

    for _, name in IOS_SIZES:
        put(f"ios/{name}", IOS_APPICON / name)

    put("desktop/episteme.ico", DESKTOP_RES / "episteme.ico")
    put("desktop/episteme-512.png", DESKTOP_RES / "episteme_icon.png")
    icns = src / "desktop/episteme.icns"
    if icns.exists():
        put("desktop/episteme.icns", DESKTOP_RES / "episteme.icns")

    for name in OBSOLETE_ANDROID_FILES:
        stale = ANDROID_RES / name
        if stale.exists():
            stale.unlink()
            changed.append(stale)
    return changed



def write_icns(master: Image.Image, target: pathlib.Path, tmp: pathlib.Path) -> list[pathlib.Path]:
    """Build an .icns via macOS iconutil; skipped elsewhere."""
    if not shutil.which("iconutil"):
        return []
    set_dir = tmp / "episteme.iconset"
    set_dir.mkdir(parents=True, exist_ok=True)
    for base, size in ((16, 16), (32, 32), (128, 128), (256, 256), (512, 512)):
        master.resize((size, size), Image.LANCZOS).convert("RGB").save(
            set_dir / f"icon_{base}x{base}.png")
        master.resize((size * 2, size * 2), Image.LANCZOS).convert("RGB").save(
            set_dir / f"icon_{base}x{base}@2x.png")
    target.parent.mkdir(parents=True, exist_ok=True)
    r = subprocess.run(["iconutil", "-c", "icns", str(set_dir), "-o", str(target)],
                       capture_output=True)
    shutil.rmtree(set_dir, ignore_errors=True)
    if r.returncode != 0 or not target.exists():
        print(f"  warning: iconutil failed: {r.stderr.decode().strip()}", file=sys.stderr)
        return []
    return [target]


def sheet(tiles: list[tuple[Image.Image, str]], cols: int, out_png: pathlib.Path,
          title: str = "", label_h: int = 24, pad: int = 22,
          plate=(32, 36, 43)) -> None:
    tw, th = tiles[0][0].width, tiles[0][0].height
    rows = (len(tiles) + cols - 1) // cols
    head = 26 if title else 0
    W = cols * tw + (cols + 1) * pad
    H = head + rows * (th + label_h) + (rows + 1) * pad
    canvas = Image.new("RGB", (W, H), plate)
    dr = ImageDraw.Draw(canvas)
    if title:
        dr.text((pad + 2, 7), title, fill=(200, 200, 200))
    for i, (im, label) in enumerate(tiles):
        r, c = divmod(i, cols)
        x = pad + c * (tw + pad)
        y = pad + head + r * (th + label_h)
        flat = Image.new("RGBA", im.size, (0, 0, 0, 0))
        flat.paste(im, (0, 0), im)
        canvas.paste(flat, (x, y), flat)
        dr.text((x + 2, y + th + 6), label, fill=(212, 212, 212))
    canvas.save(out_png)


def themed(glyph: Image.Image, size: int, fg, bg) -> Image.Image:
    """Android 13+ themed icon: mono glyph over a plate in the user's theme colour."""
    s = size * 4
    out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(out).ellipse([0, 0, s - 1, s - 1], fill=bg + (255,))
    g = glyph.resize((int(s * 0.62), int(s * 0.62)), Image.LANCZOS)
    tint = Image.new("RGBA", g.size, fg + (255,))
    tint.putalpha(g.getchannel("A"))
    out.paste(tint, ((s - g.width) // 2, (s - g.height) // 2), tint)
    return out.resize((size, size), Image.LANCZOS)


# ================================================================= previews
def write_previews(docs: dict[str, str], src: dict, tmp: pathlib.Path) -> list[pathlib.Path]:
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    written: list[pathlib.Path] = []
    master = rasterise(docs["episteme-icon.svg"], MASTER, tmp / "master.png")
    # composite the two adaptive layers the way AdaptiveIconDrawable does
    bg_layer = docs["episteme-icon-android-background.svg"]
    fg_layer = docs["episteme-icon-android-foreground.svg"]
    adaptive_svg = (bg_layer
                    .replace("</defs>", fg_layer.split("<defs>", 1)[1].split("</defs>", 1)[0]
                             + "</defs>", 1)
                    .replace("</svg>", fg_layer.split("</defs>", 1)[1].rsplit("</svg>", 1)[0]
                             + "</svg>", 1))
    adaptive = rasterise(adaptive_svg, PREVIEW_PX, tmp / "adaptive.png")
    mono = rasterise(docs["episteme-icon-monochrome.svg"], PREVIEW_PX, tmp / "mono.png")

    jobs = [
        ("ios-1024.png", [(masked(master, "squircle", plate=PREVIEW_PLATE), "iOS 1024 (system squircle mask)")], 1,
         "", 6, 0),
        ("android-masks.png", [
            (masked(adaptive, "circle", plate=PREVIEW_PLATE, visible=ANDROID_VISIBLE_DP/ANDROID), "circle mask"),
            (masked(adaptive, "squircle", plate=PREVIEW_PLATE, exponent=4.0, visible=ANDROID_VISIBLE_DP/ANDROID), "Pixel squircle"),
            (masked(adaptive, "rounded", plate=PREVIEW_PLATE, visible=ANDROID_VISIBLE_DP/ANDROID), "rounded square"),
            (masked(adaptive, "square", plate=PREVIEW_PLATE, visible=ANDROID_VISIBLE_DP/ANDROID), "unmasked (API<26 legacy)"),
        ], 4, "Android adaptive icon, 108vp layers under each launcher mask", 24, 22),
        ("monochrome-themes.png", [
            (themed(mono, 128, (255, 255, 255), (36, 92, 168)), "blue theme"),
            (themed(mono, 128, (255, 255, 255), (18, 24, 33)), "dark theme"),
            (themed(mono, 128, (20, 26, 34), (233, 236, 240)), "light theme"),
            (themed(mono, 96, (255, 255, 255), (36, 92, 168)), "96px"),
            (themed(mono, 64, (255, 255, 255), (36, 92, 168)), "64px"),
            (themed(mono, 40, (255, 255, 255), (36, 92, 168)), "40px"),
        ], 6, "Android 13+ themed (monochrome) icon -- glyph inside the 66dp safe zone", 24, 20),
    ]
    for name, tiles, cols, title, label_h, pad in jobs:
        out = PREVIEW_DIR / name
        sheet(tiles, cols, out, title=title, label_h=label_h or 24, pad=pad)
        written.append(out)

    # every MONO_STYLE side by side, so the choice stays reviewable after regeneration
    compare = []
    for style in MONO_STYLES:
        variant = svg_doc(ANDROID, "", monochrome_body(src, style))
        g = rasterise(variant, PREVIEW_PX, tmp / f"mono-{style}.png")
        tiles = [(themed(g, 110, (255, 255, 255), (36, 92, 168)), "blue"),
                 (themed(g, 110, (255, 255, 255), (18, 24, 33)), "dark"),
                 (themed(g, 110, (20, 26, 34), (233, 236, 240)), "light")]
        tiles += [(themed(g, n, (255, 255, 255), (36, 92, 168)), f"{n}px") for n in (96, 64, 40)]
        for n, (img, text) in enumerate(tiles):
            mark = "*" if style == MONO_STYLE else ""
            compare.append((img, f"{style}{mark}  {text}" if n == 0 else text))
    out = PREVIEW_DIR / "monochrome-candidates.png"
    sheet(compare, 6, out,
          title="themed glyph variants -- * is MONO_STYLE", label_h=22, pad=16)
    written.append(out)

    for name, tiles, cols, title in [
        ("ios-sizes.png", [(masked(master, "squircle", plate=PREVIEW_PLATE).resize((s, s), Image.LANCZOS), f"{s}px")
                           for s in (180, 120, 60, 40)], 4, "iOS sizes"),
        ("android-sizes.png", [(masked(adaptive, "squircle", plate=PREVIEW_PLATE, exponent=4.0, visible=ANDROID_VISIBLE_DP/ANDROID)
                                .resize((s, s), Image.LANCZOS), f"{s}px")
                               for s in (96, 72, 48, 36)], 4, "Android launcher sizes"),
    ]:
        out = PREVIEW_DIR / name
        sheet(tiles, cols, out, title=title, label_h=22)
        written.append(out)
    return written


# ================================================================= self-check
def verify() -> list[str]:
    """Assert the platform rules the assets depend on, so they cannot silently regress."""
    problems: list[str] = []

    icon = Image.open(ICON_DIR / "png" / "store" / "app-store-1024.png")
    if icon.size != (1024, 1024):
        problems.append(f"App Store icon is {icon.size}, must be 1024x1024")
    play = Image.open(ICON_DIR / "png" / "store" / "play-store-512.png")
    if play.size != (512, 512):
        problems.append(f"Play listing icon is {play.size}, must be 512x512")

    referenced = {i["filename"] for i in json.loads(
        (ROOT / "iosApp/Reader/Assets.xcassets/AppIcon.appiconset/Contents.json").read_text()
    )["images"] if "filename" in i}
    shipped = {p.name for p in (ICON_DIR / "png" / "ios").iterdir()}
    if missing := referenced - shipped:
        problems.append(f"AppIcon set is missing {sorted(missing)}")

    for png in (ICON_DIR / "png").rglob("*.png"):
        img = Image.open(png)
        if img.size[0] != img.size[1]:
            problems.append(f"{png.name} is not square: {img.size}")
        # Only these may be transparent: the legacy launcher icons, whose baked-in outline
        # is their edge, and the adaptive foreground, which is artwork on a flat layer
        # behind it. Everything else must be opaque -- stores reject a transparent icon
        # outright, and a translucent field would let the wallpaper through the mask.
        wants_alpha = png.name in LEGACY_ICON_NAMES
        has_alpha = img.mode in ("RGBA", "LA") and img.getchannel("A").getextrema()[0] != 255
        if wants_alpha != has_alpha:
            problems.append(f"{png.name} transparency={has_alpha}, expected {wants_alpha}")

    # the themed glyph and the adaptive foreground must both stay inside the 66dp safe
    # circle of the 108dp viewport (AOSP SAFEZONE_SCALE = 66f / 72f)
    safe = 66 / 108
    if MONO_GLYPH_FILL > safe:
        problems.append(f"themed glyph at {MONO_GLYPH_FILL:.3f} exceeds the safe zone {safe:.3f}")
    fg = Image.open(ANDROID_RES / "mipmap-xxxhdpi/ic_launcher_foreground.png").convert("RGBA")
    box = fg.getchannel("A").getbbox()
    if box is None:
        problems.append("adaptive foreground is empty")
    else:
        reach = math.hypot(box[2] - box[0], box[3] - box[1]) / 2 / fg.width * ANDROID
        if reach > 33.0:
            problems.append(f"adaptive foreground reaches {reach:.1f}dp, past the 33dp radius")
    return problems


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--source", default="blue_default.svg", help="file in branding/app-icon/source")
    ap.add_argument("--svg-only", action="store_true", help="skip PNG previews and vectors")
    ap.add_argument("--deploy", action="store_true",
                    help="also copy the generated assets into app/, iosApp/ and desktopApp/")
    args = ap.parse_args()

    src = parse_source(SOURCE_DIR / args.source)
    with tempfile.TemporaryDirectory(prefix="episteme-icon-") as tmpdir:
        tmp = pathlib.Path(tmpdir)
        docs = build_svgs(src, tmp)
        for name, text in vector_layers(src).items():
            docs[name] = text
        for name, text in docs.items():
            (ICON_DIR / name).write_text(text)
            print(f"wrote {(ICON_DIR / name).relative_to(ROOT)}")

        if not args.svg_only:
            for p in write_previews(docs, src, tmp) + write_raster_sets(docs, tmp):
                print(f"wrote {p.relative_to(ROOT)}")

    if args.deploy:
        for p in deploy():
            kind = "removed" if not p.exists() else "updated"
            print(f"{kind} {p.relative_to(ROOT)}")

    problems = verify()
    for p in problems:
        print(f"FAIL {p}", file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
