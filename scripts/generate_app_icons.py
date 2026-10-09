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
  branding/app-icon/AppIconArtwork.kt                      the fan as Kotlin, for the in-app icon
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
# Width of the themed glyph's fan, in dp of the 108dp viewport. Deliberately the *same*
# optical size as the colour foreground (72dp visible x FAN_WIDTH_FRACTION = 54dp), so a
# themed icon never shows a bigger mark than the full-colour one. Well inside both the
# 72dp the mask reveals and the 33dp safe radius, so no mask crops it.
MONO_GLYPH_DP = 54.0
MONO_STYLE = "arcs"                   # "arcs" | "waves" | "arcs_field" | "fanfield" | "wedge" | "field2"
# Total width of the wedge opening between each upper and lower arc of the themed glyph,
# in source units. Split symmetrically, so each arc moves MONO_ARC_GAP / 2. The fan is 346
# units wide, so 6 is ~1.1dp of the 66dp glyph: a hairline that still reads at 96px and
# closes to a single silhouette by ~48px. Widen it too far and the arcs stop reading as
# one fan; the wedge is also what makes the shape recognisable as four bands at all.
MONO_ARC_GAP = 6.0
MONO_STYLES = ("arcs_field", "fanfield", "wedge", "arcs", "waves", "field2")  # compare-sheet order

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
    # `waves` so that `wave_elements` (which reads the group) and `mono_geometry` (which
    # reads the rendered pixels) cannot drift apart. Every entry must match: if the artwork is
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


def _taper(c: tuple, delta: float, side: int) -> tuple:
    """Offset a cubic by ``delta`` at its start, tapering to nothing at its end.

    This is what opens the wedge between two bands. ``_offset`` pins both endpoints and
    only bows the middle, so the gap it leaves is widest mid-curve and closes at *both*
    ends -- the wrong shape entirely. The bands meet at the spine, so the gap has to be
    widest out at the edge and close to a point where the pages turn.

    Each control point is scaled by its own Bezier parameter (0, 1/3, 2/3, 1), which
    tapers linearly in parameter rather than arc length. Over a few units that is
    indistinguishable, and it stays exact rather than needing subdivision.
    """
    p0, p1, p2, p3 = c
    normals = []
    for a, b in ((p0, p1), (p1, p2), (p2, p3)):
        dx, dy = b[0] - a[0], b[1] - a[1]
        length = math.hypot(dx, dy) or 1.0
        normals.append((-dy / length * side, dx / length * side))
    # Offset by the full delta at p0 and nothing at p3, so the wedge is widest at the
    # outer edge and closes to a point at the spine. Control points 1 and 2 sit at Bezier
    # parameters 1/3 and 2/3, hence the linear 2/3, 1/3 weights.
    return (
        (p0[0] + normals[0][0] * delta, p0[1] + normals[0][1] * delta),
        (p1[0] + normals[0][0] * delta * 2 / 3, p1[1] + normals[0][1] * delta * 2 / 3),
        (p2[0] + normals[1][0] * delta / 3, p2[1] + normals[1][1] * delta / 3),
        p3,
    )


# ----------------------------------------------------------------- gradients
def parse_gradient(src: dict, gradient_id: str) -> dict:
    """A gradient definition as the in-app artwork consumes it.

    Only the two shapes Compose can express directly: a user-space linear
    gradient, and a radial whose ``gradientTransform`` is a diagonal scale
    (the artwork's shade). A rotated or sheared radial fails loudly --
    approximating it silently would change the artwork.
    """
    block = src["linear"].get(gradient_id) or src["radial"][gradient_id]
    stops = []
    for m in re.finditer(r"<stop\b([^>]*)/>", block):
        attrs = m.group(1)
        offset = float(re.search(r'offset="([\d.]+)"', attrs).group(1))
        colour = re.search(r'stop-color="(#[0-9a-fA-F]+)"', attrs).group(1)
        opacity = re.search(r'stop-opacity="([\d.]+)"', attrs)
        stops.append((offset, colour, float(opacity.group(1)) if opacity else 1.0))
    if not stops:
        raise SystemExit(f"gradient {gradient_id} has no stops")

    def attr(name, default=None):
        m = re.search(r'%s="([-\d.]+)"' % name, block)
        return float(m.group(1)) if m else default

    if "linearGradient" in block:
        for name in ("x1", "y1", "x2", "y2"):
            if attr(name) is None:
                raise SystemExit(f"linear gradient {gradient_id} lacks {name}")
        return {"kind": "linear",
                "from": (attr("x1"), attr("y1")),
                "to": (attr("x2"), attr("y2")),
                "stops": stops}

    transform = re.search(r'gradientTransform="matrix\(([^)]+)\)"', block)
    if not transform:
        raise SystemExit(
            f"radial gradient {gradient_id} has no matrix gradientTransform; "
            "the in-app artwork only knows diagonal ones")
    a, b, c, d, e, f = (float(v) for v in
                        transform.group(1).replace(",", " ").split())
    if b != 0.0 or c != 0.0:
        raise SystemExit(
            f"radial gradient {gradient_id} rotates or shears "
            f"({transform.group(1).strip()}); only a diagonal scale is supported")
    return {"kind": "radial",
            "center": (attr("cx", 0.0), attr("cy", 0.0)),
            "radius": attr("r", 1.0),
            "matrix": (a, d, e, f),
            "stops": stops}


def transform_path_data(d: str, a: float, d_scale: float,
                        e: float, f: float) -> str:
    """Apply the diagonal affine ``x' = a*x + e, y' = d*y + f`` to a path.

    The shade layer is a rectangle, so only M/L/H/V (absolute or relative)
    and Z occur; a curve or arc fails loudly instead of being dropped.
    Relative commands are resolved against the point in the path's *own*
    coordinates and emitted as transformed deltas, so the result stays a
    valid path in the target space.
    """
    tokens = _PATH_TOKEN.findall(d)
    out = []
    cmd = None
    ox = oy = 0.0            # current point in the path's own coordinates
    x = y = 0.0              # ... and in the target space
    start_ox = start_oy = 0.0
    start_x = start_y = 0.0
    i = 0
    while i < len(tokens):
        if tokens[i].isalpha():
            cmd = tokens[i]
            i += 1
            if cmd in "Zz":
                ox, oy = start_ox, start_oy
                x, y = start_x, start_y
                out.append("Z")
                continue
            out.append(cmd)
        if cmd is None:
            raise SystemExit(f"path does not start with a command: {d!r}")
        rel = cmd.islower()
        head = cmd.upper()
        if head in ("M", "L"):
            nx, ny = float(tokens[i]), float(tokens[i + 1])
            i += 2
            if rel:
                nx, ny = ox + nx, oy + ny
            px, py = a * nx + e, d_scale * ny + f
            if head == "M":
                start_ox, start_oy = nx, ny
                start_x, start_y = px, py
            if rel:
                out.append(f"{px - x:.4f} {py - y:.4f}")
            else:
                out.append(f"{px:.4f} {py:.4f}")
            ox, oy, x, y = nx, ny, px, py
            if head == "M":
                cmd = "l" if rel else "L"
        elif head == "H":
            nx = float(tokens[i])
            i += 1
            if rel:
                nx += ox
            px = a * nx + e
            out.append(f"{px - x:.4f}" if rel else f"{px:.4f}")
            ox, x = nx, px
        elif head == "V":
            ny = float(tokens[i])
            i += 1
            if rel:
                ny += oy
            py = d_scale * ny + f
            out.append(f"{py - y:.4f}" if rel else f"{py:.4f}")
            oy, y = ny, py
        else:
            raise SystemExit(f"unsupported path command {cmd!r} in {d!r}")
    return " ".join(out)


def mono_arc_paths(src: dict, gap: float) -> str:
    """The four wave bands as four disjoint arcs, separated by a wedge of width `gap`.

    Each band is rebuilt from its own boundary curve rather than reusing the artwork's
    paths, because those overlap (the lower-left band is drawn on top of the upper-left
    one) and a monochrome layer has to be a clean union of disjoint shapes.

    The shared boundary between an upper and a lower band is split *symmetrically*:
    each arc is pushed `gap / 2` away from it, so both keep their weight instead of one
    absorbing the whole separation. Combined with `_taper` -- full offset at the outer
    edge, nothing at the spine -- the result is a wedge that opens outwards and closes to
    a point where the pages meet.
    """
    upper_l = path_cubics(src["waves"][WAVE_UPPER_LEFT])[0]
    lower_l = path_cubics(src["waves"][WAVE_LOWER_LEFT])[0]
    upper_r = path_cubics(src["waves"][WAVE_UPPER_RIGHT])
    lower_r = path_cubics(src["waves"][WAVE_LOWER_RIGHT])
    if len(upper_r) != 2:
        raise SystemExit(f"expected 2 cubics in the upper-right wave, got {len(upper_r)}")

    half = gap / 2
    # the bands are closed along the fan's own baseline, which FAN_TRIM put at y=470
    floor = bleed_rect()[1] + bleed_rect()[2]
    # left boundary runs outer -> spine, right boundary spine -> outer; `side` picks which
    # way is "away from the band above"
    up_l = _taper(lower_l, half, -1)
    dn_l = _taper(lower_l, half, 1)
    up_r = _taper(upper_r[1], half, 1)
    dn_r = _taper(upper_r[1], half, -1)
    return " ".join([
        # upper-left: out along its own top curve, back along the boundary lifted clear
        f"M{_f(upper_l[0][0])} {_f(upper_l[0][1])} {_curve(upper_l)} {_curve_rev(up_l)} Z",
        # lower-left: under the boundary dropped clear, closed along the baseline
        f"M{_f(dn_l[0][0])} {_f(dn_l[0][1])} {_curve(dn_l)} H{_f(dn_l[0][0])} Z",
        # upper-right: out along its own top curve, down the outer edge, back along the
        # boundary lifted clear
        f"M{_f(upper_r[0][0][0])} {_f(upper_r[0][0][1])} {_curve(upper_r[0])} "
        f"L{_f(up_r[0][0])} {_f(up_r[0][1])} {_curve(up_r)} Z",
        # lower-right: under the boundary dropped clear, closed along the baseline
        f"M{_f(dn_r[3][0])} {_f(dn_r[3][1])} {_curve_rev(dn_r)} "
        f"V{_f(floor)} H{_f(dn_r[3][0])} Z",
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


def svg_doc(size: float, defs: str, body: str) -> str:
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size:g}" height="{size:g}" '
            f'viewBox="0 0 {size:g} {size:g}" fill="none">\n'
            f"<defs>\n{defs}\n</defs>\n{body}\n</svg>\n")


def mono_geometry(src: dict, tmp: pathlib.Path, glyph_dp: float) -> dict:
    """Place the themed glyph: fan centred on the viewport, field bleeding past the mask.

    Two things the previous version got wrong, both visible on a real themed icon:

    * It centred on the *card's* centre (MARK_CY), but the fan's own centre sits 54 source
      units lower, so the glyph rode ~11dp below the middle and left a visible gap above
      the artwork while the bottom looked crowded.
    * It sized the glyph to 66dp and stopped. The launcher mask reveals 72dp, so a glyph
      that stops at 66dp shows the themed background in a band between its straight edges
      and the mask's curve -- the "gaps on top, bottom and sides" in the screenshot. The
      mask is there to supply the outline, so the field must bleed *past* it and never
      present an edge of its own inside the visible area.

    The field is therefore a rectangle covering the whole 108dp viewport in source units,
    while the knocked-out fan stays at ``glyph_dp``. Same transform for both, because a
    monochrome layer is a single even-odd path and cannot draw two shapes at different
    scales -- so the field is enlarged in path data instead.
    """
    x0, y0, x1, y1 = fan_bbox(src, tmp)
    width, height = x1 - x0, y1 - y0
    scale = glyph_dp / width
    cx, cy = x0 + width / 2, y0 + height / 2
    half = ANDROID / 2 / scale          # half the viewport, in source units
    field = (f"M{cx - half:g} {cy - half:g} H{cx + half:g} "
             f"V{cy + half:g} H{cx - half:g} Z")
    return {
        "transform": (f"translate({ANDROID / 2 - cx * scale:.3f} {ANDROID / 2 - cy * scale:.3f}) "
                      f"scale({scale:.5f})"),
        "field": field,
        # the fan's true extremities, for the safe-zone check in verify()
        "reach_dp": max(width / 2, height / 2) * scale,
        "placed": (width * scale, height * scale),
        # the untransformed bounding box and the scale that produced `placed`, so a caller
        # can re-anchor the same paths at an origin instead of the viewport centre
        "origin": (x0, y0),
        "scale": scale,
    }


def monochrome_body(src: dict, tmp: pathlib.Path, style: str | None = None) -> str:
    """The themed glyph: a solid field with the fan knocked out (even-odd).

    A themed icon is one flat colour, so the design loses every internal boundary. The
    composition that survives that best is the colour icon's own: field behind, fan in
    front, the fan now reading as a hole. Because the field bleeds past the mask, the
    themed icon fills its plate completely instead of floating inside it.

    Alternatives, all kept as constants and shown in ``preview/monochrome-candidates.png``:

    ``"fanfield"`` the field minus the whole fan as one fused hole. Fills, but it loses the
                 four-arc reading the colour artwork has.
    ``"wedge"``  field minus the two *pale* bands only; the solid part keeps reading as a
                 page, but it depends on the pale/deep distinction the tint has thrown away.
    ``"field2"`` field minus the two *saturated* bands -- bolder at 40px, but a banner.
    ``"arcs"``   the same four bands without the field, so the mark floats inside the mask
                 with the themed background showing around it.
    ``"waves"``  the bands merged into one solid fan; the book reads, but there is no
                 field, so the same gap problem comes straight back.

    The band paths come from ``src`` rather than being written out here, so they inherit
    ``FAN_TRIM`` and cannot drift away from the foreground's geometry again.
    """
    style = style or MONO_STYLE
    pale_l = src["waves"][WAVE_UPPER_LEFT]
    pale_r = src["waves"][WAVE_UPPER_RIGHT]
    deep_l = src["waves"][WAVE_LOWER_LEFT]
    deep_r = src["waves"][WAVE_LOWER_RIGHT]
    geo = mono_geometry(src, tmp, MONO_GLYPH_DP)

    if style == "arcs_field":
        # the field with the fan knocked out as four separate arcs: fills the mask like
        # "fanfield", but the bands keep the artwork's four-arc reading that a single
        # fused hole loses
        d = f'{geo["field"]} {mono_arc_paths(src, MONO_ARC_GAP)}'
    elif style == "fanfield":
        d = f'{geo["field"]} {deep_l} {deep_r} {pale_l} {pale_r}'
    elif style == "wedge":
        d = f'{geo["field"]} {pale_l} {pale_r}'
    elif style == "field2":
        d = f'{geo["field"]} {deep_l} {deep_r}'
    elif style == "waves":
        d = f"{pale_l} {pale_r} {deep_r}"
    elif style == "arcs":
        d = mono_arc_paths(src, MONO_ARC_GAP)
    else:
        raise SystemExit(f"unknown MONO_STYLE {style!r}")
    rule = "evenodd" if style in ("arcs_field", "fanfield", "wedge", "field2") else "nonzero"
    # No clip: the field spans the viewport, so the bands only ever cut into it.
    return (f'  <g transform="{geo["transform"]}">\n'
            f'    <path d="{d}" fill="#FFFFFFFF" fill-rule="{rule}" clip-rule="evenodd"/>\n'
            f"  </g>")


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
        "episteme-icon-monochrome.svg": svg_doc(ANDROID, "", monochrome_body(src, tmp)),
    }


def vector_layers(src: dict, tmp: pathlib.Path) -> dict[str, str]:
    """The Android layers that vectorise exactly.

    Only the flat ones. The background is a solid fill and the monochrome glyph is a single
    colour, so both are one small file each with no density buckets. The *foreground* is
    deliberately not among them: it carries the wave gradients, two of which are radials
    positioned by a gradient transform that ``VectorDrawable`` cannot express, so
    approximating it would quietly change the artwork. It ships as density-bucketed PNGs.

    The in-app icon is not an Android layer at all any more: it is the fan drawn by
    ``AppIcon`` in Compose, from ``AppIconArtwork.kt`` (see ``app_icon_artwork``).
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
        "ic_launcher_monochrome.xml": _vector_group(_vector_mono(src, tmp)),
        "AppIconArtwork.kt": app_icon_artwork(src, tmp),
    }


def _vector_group(inner: str) -> str:
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- Generated by scripts/generate_app_icons.py. Flat colour so the launcher can\n"
            "     tint it. The field covers the whole viewport so the launcher's mask supplies\n"
            "     the outline and no edge of ours shows inside it; the fan is knocked out at the\n"
            "     66dp safe size. Geometry is pre-trimmed, so no clip is needed. -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            + inner
            + "</vector>\n")


def _mono_body(src: dict, tmp: pathlib.Path) -> tuple[str, str]:
    """The themed glyph's path data and its fill rule."""
    body = monochrome_body(src, tmp)
    return (re.search(r'<path d="([^"]+)"', body).group(1),
            re.search(r'fill-rule="(\w+)"', body).group(1))


def _mono_group(d: str, rule: str, scale: float, tx: float, ty: float) -> str:
    """A VectorDrawable group carrying the themed glyph's single flat-coloured path."""
    evenodd = ' android:fillType="evenOdd"' if rule == "evenodd" else ""
    return (f'  <group android:scaleX="{scale:.5f}" android:scaleY="{scale:.5f}"\n'
            f'      android:translateX="{tx:.3f}" android:translateY="{ty:.3f}">\n'
            f'    <path android:pathData="{d}" android:fillColor="#FFFFFFFF"{evenodd}/>\n'
            f"  </group>\n")


def _vector_mono(src: dict, tmp: pathlib.Path) -> str:
    d, rule = _mono_body(src, tmp)
    t = re.search(r'<g transform="translate\(([-\d.]+) ([-\d.]+)\) scale\(([\d.]+)\)"',
                  monochrome_body(src, tmp))
    tx, ty, scale = (float(t.group(i)) for i in (1, 2, 3))
    return _mono_group(d, rule, scale, tx, ty)


# ================================================================= in-app artwork
def _kotlin_float(v: float) -> str:
    return f"{v:.4f}".rstrip("0").rstrip(".") + "f"


def _kotlin_colour(hex_colour: str) -> str:
    return f"Color(0xFF{hex_colour[1:].upper()})"


def app_icon_artwork(src: dict, tmp: pathlib.Path) -> str:
    """The fan artwork as Kotlin, for the icon drawn inside the app.

    The fan is placed exactly as the launcher's adaptive foreground places it
    (``MONO_GLYPH_DP`` wide, the fan's own bounding box), but re-anchored at
    that box's top-left so a Compose canvas sized to the artwork fills it --
    the same crop ``ic_app_mark`` used to apply to the monochrome glyph. The
    wave paths are the foreground's own, so the in-app icon and the launcher
    icon cannot drift apart.

    Gradients keep the artwork's colours and geometry in source coordinates;
    ``AppIcon`` repaints every stop in the app theme at runtime, so this file
    stays theme-free and only ever changes when the artwork does.
    """
    geo = mono_geometry(src, tmp, MONO_GLYPH_DP)
    x0, y0 = geo["origin"]
    scale = geo["scale"]
    width, height = geo["placed"]

    def stops_kotlin(stops):
        return ",\n".join(
            "                    AppIconStop("
            f"{_kotlin_float(offset)}, {_kotlin_colour(colour)}, "
            f"alpha = {_kotlin_float(alpha)})"
            for offset, colour, alpha in stops
        )

    layers = []
    for element in wave_elements(src):
        fill = re.search(r'fill="url\(#(\w+)\)"', element).group(1)
        d = re.search(r'\sd="([^"]+)"', element).group(1)
        gradient = parse_gradient(src, fill)
        stops = stops_kotlin(gradient["stops"])
        if gradient["kind"] == "linear":
            (x1, y1), (x2, y2) = gradient["from"], gradient["to"]
            paint = (
                "AppIconPaint.Linear(\n"
                f"                startX = {_kotlin_float(x1)},\n"
                f"                startY = {_kotlin_float(y1)},\n"
                f"                endX = {_kotlin_float(x2)},\n"
                f"                endY = {_kotlin_float(y2)},\n"
                f"                stops = listOf(\n{stops},\n"
                "                ),\n"
                "            )"
            )
        else:
            # Counter-transform the path into gradient space: the layer is drawn
            # inside the gradient's own transform, so path and gradient compose
            # back onto the artwork while the radial stays an exact ellipse.
            a, d_scale, e, f = gradient["matrix"]
            d = transform_path_data(d, 1 / a, 1 / d_scale, -e / a, -f / d_scale)
            cx, cy = gradient["center"]
            paint = (
                "AppIconPaint.Radial(\n"
                f"                centerX = {_kotlin_float(cx)},\n"
                f"                centerY = {_kotlin_float(cy)},\n"
                f"                radius = {_kotlin_float(gradient['radius'])},\n"
                f"                translateX = {_kotlin_float(e)},\n"
                f"                translateY = {_kotlin_float(f)},\n"
                f"                scaleX = {_kotlin_float(a)},\n"
                f"                scaleY = {_kotlin_float(d_scale)},\n"
                f"                stops = listOf(\n{stops},\n"
                "                ),\n"
                "            )"
            )
        layers.append(
            "AppIconLayer(\n"
            f'            pathData = "{d}",\n'
            f"            paint = {paint},\n"
            "        )"
        )

    return f'''package com.aryan.reader.shared.ui

// Generated by scripts/generate_app_icons.py -- do not edit by hand. The fan
// artwork of the app icon, in the source artwork's own coordinates: the same
// geometry the launcher's adaptive foreground ships, so the icon drawn inside
// the app cannot drift from the launcher icon. The colours are the artwork's
// own; AppIcon repaints every gradient stop in the app theme at runtime, so
// this file stays theme-free.

import androidx.compose.ui.graphics.Color

internal object AppIconArtwork {{
    /** The fan's placed size, in the dp the composable sizes its canvas to. */
    const val viewportWidth = {_kotlin_float(width)}
    const val viewportHeight = {_kotlin_float(height)}

    /** Source coordinates to viewport: the placement the launcher foreground uses. */
    const val sourceScale = {_kotlin_float(scale)}
    const val sourceTranslateX = {_kotlin_float(-x0 * scale)}
    const val sourceTranslateY = {_kotlin_float(-y0 * scale)}

    /** The fan's bands in the order the artwork paints them. */
    val layers: List<AppIconLayer> = listOf(
{chr(10).join("        " + layer + "," for layer in layers)}
    )
}}
'''


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
SHARED_UI = ROOT / "shared/src/commonMain/kotlin/com/aryan/reader/shared/ui"
IOS_APPICON = ROOT / "iosApp/Reader/Assets.xcassets/AppIcon.appiconset"
DESKTOP_RES = ROOT / "desktopApp/src/desktopMain/resources"
# the Play listing uploads this file verbatim, so it is deployed rather than kept as a
# hand-copied duplicate: a copy outside this table is exactly how the listing went on
# serving the old mark after the app itself moved to the new one
PLAY_LISTING_ICON = ROOT / "fastlane/metadata/android/en-US/images/icon.png"
# superseded by the full-colour adaptive layers, which carry their own artwork
# the legacy icons ship as PNG: byte-identical to the reviewed asset, with no re-encode
# step that could drop the alpha channel the baked-in outline depends on
OBSOLETE_ANDROID_FILES = tuple(
    f"mipmap-{density}/{name}.webp"
    for density in ANDROID_DENSITIES for name in ("ic_launcher", "ic_launcher_round")
) + tuple(
    # the background is a flat vector now; this raster is left over from the full-bleed
    # layout and would otherwise ship as dead weight
    f"mipmap-{density}/ic_launcher_background.png" for density in ANDROID_DENSITIES
) + (
    # the in-app icon is the fan drawn by AppIcon from AppIconArtwork.kt now; this
    # monochrome twin of the themed glyph has no consumer left
    "drawable/ic_app_mark.xml",
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

    def put_generated(name: str, to: pathlib.Path) -> None:
        """Copy a generated file that lives in the icon dir, not png/."""
        data = (ICON_DIR / name).read_bytes()
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
    put_generated("AppIconArtwork.kt", SHARED_UI / "AppIconArtwork.kt")
    put("store/play-store-512.png", ROOT / "app/src/main/ic_launcher-playstore.png")
    put("store/play-store-512.png", PLAY_LISTING_ICON)

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
        variant = svg_doc(ANDROID, "", monochrome_body(src, tmp, style))
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
def verify(src: dict, tmp: pathlib.Path) -> list[str]:
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

    # the themed glyph's fan must stay inside both the 72dp the mask reveals and the 33dp
    # safe radius (AOSP SAFEZONE_SCALE = 66f / 72f), or a mask crops the artwork
    fan_w, fan_h = mono_geometry(src, tmp, MONO_GLYPH_DP)["placed"]
    if fan_w > ANDROID_VISIBLE_DP or fan_h > ANDROID_VISIBLE_DP:
        problems.append(
            f"themed fan at {fan_w:.1f}x{fan_h:.1f}dp exceeds the {ANDROID_VISIBLE_DP}dp "
            "the mask reveals"
        )
    reach = max(fan_w, fan_h) / 2
    if reach > 33 + 0.01:
        problems.append(f"themed fan reaches {reach:.1f}dp, past the 33dp safe radius")
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
                    help="also copy the generated assets into app/, iosApp/, desktopApp/ "
                         "and fastlane/")
    args = ap.parse_args()

    src = parse_source(SOURCE_DIR / args.source)
    with tempfile.TemporaryDirectory(prefix="episteme-icon-") as tmpdir:
        tmp = pathlib.Path(tmpdir)
        docs = build_svgs(src, tmp)
        for name, text in vector_layers(src, tmp).items():
            docs[name] = text
        for name, text in docs.items():
            (ICON_DIR / name).write_text(text)
            print(f"wrote {(ICON_DIR / name).relative_to(ROOT)}")

        if not args.svg_only:
            for p in write_previews(docs, src, tmp) + write_raster_sets(docs, tmp):
                print(f"wrote {p.relative_to(ROOT)}")

        # inside the block: verify() re-measures the fan to check the themed glyph, so it
        # needs the scratch directory
        problems = verify(src, tmp)

    if args.deploy:
        for p in deploy():
            kind = "removed" if not p.exists() else "updated"
            print(f"{kind} {p.relative_to(ROOT)}")

    for p in problems:
        print(f"FAIL {p}", file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
