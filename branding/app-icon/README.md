# App icon

Single, fixed app icon (blue variant). Generated — do not hand-edit the SVGs here.

```sh
python3 scripts/generate_app_icons.py            # regenerate SVGs + previews
python3 scripts/generate_app_icons.py --svg-only
python3 scripts/generate_app_icons.py --source ember.svg   # any variant in source/
```

## Design

A flat white field with the fan/wave artwork centred on it — the same structure as the
icon this replaces, which was a solid background plus a separate glyph.

- **Android** is the canonical form: `background` is a solid fill, `foreground` carries
  only the artwork, `monochrome` the themed glyph. `background` and `monochrome` are
  VectorDrawables; the foreground is a PNG (see *Rasterisation*).
- **iOS / desktop / Play** are the same composition flattened into one opaque square. The
  field is full-bleed there because those platforms reject transparency, but the fan keeps
  the same optical size, sized as a fraction of the *visible* area so Android (72 of 108dp
  visible) and iOS (all of it) match despite very different canvases.

The source artwork's card is a rounded rectangle (rx=60) and its cream field is dropped:
the fan is drawn on the flat field instead. There is no backdrop, frame or drop shadow.

Nothing bakes in corner rounding. The launcher supplies the outline, so the icon does not
change shape when the OS changes its mask.

## Source

`source/blue_default.svg` is the original artwork, copied verbatim. It is a 728x554
*mock window* composition, so most of it is presentation-only:

| Source layer | Verdict |
| --- | --- |
| `path url(#a)` + `path url(#b)`, full canvas | backdrop — **dropped** |
| `rect 133,81 458x444 rx=104` (stroked) | window frame — dropped |
| `rect 192,151 338x319 rx=60` + `filter #c` | window drop shadow — dropped |
| `rect 320,300 220x180 fill="url(#sh)"` | stray artifact present only in the blue variant — dropped |
| `<g clip-path="url(#f)">` | **the mark** — kept |

## Outputs

| File | Use |
| --- | --- |
| `episteme-icon.svg` | 1024 square master — iOS, macOS/Windows/Linux desktop, Play Store |
| `episteme-icon-android-background.svg` | 108vp adaptive background (flat fill) |
| `episteme-icon-android-foreground.svg` | 108vp adaptive foreground (the fan) |
| `episteme-icon-monochrome.svg` | 108vp Android 13+ themed icon |
| `ic_launcher_background.xml`, `ic_launcher_monochrome.xml` | the two flat layers, as Android VectorDrawables |
| `AppIconArtwork.kt` | the fan as Kotlin — paths plus gradient geometry, for the icon drawn *inside* the app |
| `png/ios/` | 14 AppIcon slots, opaque RGB, no baked rounding |
| `png/android/mipmap-*/` | legacy `ic_launcher` + `ic_launcher_round`, 48dp x 5 densities |
| `png/android/adaptive/` | raster foreground layer, 108dp x 5 densities |
| `png/desktop/` | 16–1024 PNG ladder, `episteme.ico` (7 sizes), `episteme.icns` |
| `png/store/` | `play-store-512.png`, `app-store-1024.png` |
| `png/branding/` | README header + favicon ladder, 16–512 |
| `preview/*.png` | masked previews |

## Geometry

The fan's bounding box is **measured**, not hardcoded: `fan_bbox` rasterises the wave
paths at 1:1 and reads back the alpha bounds (`188, 260, 534, 470` in source units —
control points would overshoot and constants would rot on the next artwork change). It
is placed by a uniform scale, so nothing is ever distorted.

The fan is sized as a fraction of the **visible** icon rather than of the viewport
(`FAN_WIDTH_FRACTION`), so Android (72 of 108dp visible) and iOS (all of it) get the same
optical size despite very different canvases. `0.75` is the largest value whose bounding
half-diagonal (32.2dp) still clears the 33dp safe radius; `verify()` fails the build if
the artwork change pushes it past that.

**`FAN_TRIM` bakes the artwork's own trim into the path data.** The wave paths are
authored oversized and rely on the mark's `clipPath`: three different baselines are drawn
(y=480 for the two left paths, y=474 for the lower-right one) and the right-hand paths
reach x=534 while the field stops at x=530. The clip flattened every baseline onto y=470
and cut the overhang, which is what made the two pages meet at a single point at the
bottom centre. The card is gone, so the clip went with it — leaving the left page sitting
visibly lower than the right and a sliver of gradient past the far right edge. The trim
is applied once, in `parse_source`, to the group text, so `wave_elements` (which reads the
group) and `mono_geometry` (which measures rendered pixels) cannot drift apart. A stale
entry raises rather than silently losing the baseline.

**The themed glyph bleeds past the mask.** The launcher mask is what gives a themed icon
its outline, so a glyph that stops short of it shows the themed background in a band
between its own straight edges and the mask's curve — gaps on every side. `mono_geometry`
therefore maps the field to cover the whole 108dp viewport, and centres the knocked-out
fan on the *measured* fan bounds rather than the old card's centre (which sat 54 source
units too high, riding the artwork ~11dp below the middle). The fan itself is sized to
`MONO_GLYPH_DP = 66`, putting its left and right tips on the 33dp safe radius while
staying inside the 72dp the mask reveals. `verify()` fails if either drifts.

There are exactly two acceptable shapes here, and the broken one in between is what the
earlier versions shipped. A themed icon has no artwork of its own — the launcher supplies
the tint and the mask — so the glyph must either:

- **bleed past the mask** (`"arcs_field"`, `"fanfield"`, `"wedge"`, `"field2"`), letting
  the mask alone decide the outline; or
- **have no field at all** (`"waves"`, `"arcs"`), so the themed background shows through
  around a free mark.

What it must never do is stop short — a field that ends inside the mask shows its own
straight edges against the themed background, which is the gap this replaces.

Chosen: `MONO_STYLE = "arcs"` — the fan as four discrete arcs, free-standing, with a
narrow transparent wedge between each upper/lower pair. This is the only part of the mark
that survives the tint as *structure* rather than tone: a monochrome layer has just two
tones, so the pale/saturated distinction is gone, but four separated shapes are not.

Two details make the wedges read correctly:

- **Split symmetrically.** The shared boundary between an upper and a lower band is offset
  `MONO_ARC_GAP / 2` each way, so both arcs keep their weight. Offsetting only the upper
  band (the earlier behaviour) thins it while the lower one keeps full thickness, which
  reads as an uneven notch rather than a division.
- **Tapered, not bowed.** `_taper` offsets a curve by the full amount at its start and
  nothing at its end, so each wedge is widest at the outer edge and closes to a point at
  the spine where the pages meet. A constant `_offset` pins both endpoints and bows the gap
  open in the *middle* — the wrong shape entirely.

The alternatives stay as constants in `preview/monochrome-candidates.png`: `"waves"` (one
fused silhouette, no internal division), and the field-bearing `"arcs_field"`,
`"fanfield"`, `"wedge"`, `"field2"` for when the plate should fill instead of float. The
unit test asserts the no-edge-in-between rule above rather than any one shape.

**No clip anywhere in the glyph.** It ships as a VectorDrawable, which has no per-path
clip — only a whole-group `<clip-path>`, whose coordinate space relative to the group's own
transform is not worth relying on across devices. Since the field covers the whole
viewport, the bands only ever cut *into* it, so nothing needs trimming.

That vector also serves as the TTS notification's small icon (`TtsService`), which is why
it must stay a vector — a raster would be both wrong semantically and wasteful in the
status bar.

**The fan itself, with its gradients, is what the app draws as its own icon in-app**
(`AppIconArtwork.kt`, drawn by `AppIcon` in Compose). The launcher's foreground is a
PNG because its gradients cannot be expressed in a `VectorDrawable`; the in-app icon
has no such constraint — Compose can paint any gradient — so the artwork ships as
Kotlin instead: the same wave paths, the same placement (re-anchored at the fan's
bounding-box origin, with the viewport cropped to it so it fills whatever slot it is
given), and every gradient's geometry in source coordinates. The radial shade's
`gradientTransform` is a diagonal scale, so the layer is drawn inside that transform
with its path counter-transformed into gradient space — path and gradient compose
back onto the artwork exactly.

The colours in `AppIconArtwork.kt` are the artwork's own, and stay that way: `AppIcon`
repaints every gradient stop in the app theme at runtime, keeping each stop's lightness
and adopting the theme primary's hue and saturation. That keeps the pale-to-deep ladder
that gives the fan its reading while the icon tracks light/dark and any dynamic or custom
seed colour — the same thing the old monochrome mark did, but in the icon's own colours
instead of a flat tint. The plate behind the fan is a themed surface, which is what makes
it read as an icon rather than as loose artwork.

Two things keep the pair honest. The artwork and the launcher foreground are generated
from one `wave_elements`, so the fan cannot drift between them; and
`AndroidLauncherIconContractTest` asserts the relationship directly — every wave path
present verbatim, the viewport equal to the fan's measured size in the shipped
foreground, and the placement scale equal to the themed glyph's.

Legacy Android icons have the launcher mask baked in (`squircle` for `ic_launcher`,
`circle` for `ic_launcher_round`) because API < 26 launchers do not mask. Everything else
ships unmasked.

## Toolchain notes

Rasterisation uses headless Chrome (`--headless=new --screenshot`). Launcher masks are
applied afterwards in Pillow, **not** in SVG: Chrome's headless renderer silently drops
groups nested under a `clip-path`, so an SVG-side mask produces blank tiles.

Everything is downsampled with LANCZOS from a single 4x supersampled render of the
master, so the curves are identical at every size rather than re-rasterised per size.

Only the *flat* layers ship as `VectorDrawable`: the background (a solid fill) and the
monochrome glyph (a single colour). The foreground is deliberately not one of them — it
carries the wave gradients, two of which are radials positioned by a gradient transform
that `VectorDrawable` cannot express, so approximating it would quietly change the
artwork. It ships as density-bucketed PNGs instead. Legacy launcher icons are PNGs too,
so the shipped file is byte-identical to the reviewed asset with no re-encode step that
could drop the alpha channel the baked-in outline depends on.

## Verification

`generate_app_icons.py` exits non-zero if any of these stop holding: store icons at
exactly 1024/512, every `AppIcon.appiconset/Contents.json` slot present, every PNG
square, alpha present only where it is meant to be, and the themed glyph's field covering
the viewport with its fan inside the mask.

