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
  only the artwork, `monochrome` the themed glyph. All three are VectorDrawables.
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
| `ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` | the same three layers as Android VectorDrawables |
| `png/ios/` | 14 AppIcon slots, opaque RGB, no baked rounding |
| `png/android/mipmap-*/` | legacy `ic_launcher` + `ic_launcher_round`, 48dp x 5 densities |
| `png/android/adaptive/` | raster foreground layer, 108dp x 5 densities |
| `png/desktop/` | 16–1024 PNG ladder, `episteme.ico` (7 sizes), `episteme.icns` |
| `png/store/` | `play-store-512.png`, `app-store-1024.png` |
| `png/branding/` | README header + favicon ladder, 16–512 |
| `preview/*.png` | masked previews |

## Geometry

The fan's bounding box is **measured**, not hardcoded: `fan_bbox` rasterises the wave
paths at 1:1 and reads back the alpha bounds (`188, 260, 536, 480` in source units —
control points would overshoot and constants would rot on the next artwork change). It
is placed by a uniform scale, so nothing is ever distorted.

The fan is sized as a fraction of the **visible** icon rather than of the viewport
(`FAN_WIDTH_FRACTION`), so Android (72 of 108dp visible) and iOS (all of it) get the same
optical size despite very different canvases. `0.75` is the largest value whose bounding
half-diagonal (32.2dp) still clears the 33dp safe radius; `verify()` fails the build if
the artwork change pushes it past that.

A **319x319** window through that centre is what becomes the icon (`BLEED`); the extra 19
units of card width are discarded. It is placed with a uniform scale, so nothing is ever
distorted, and the window is re-emitted as a square clip path plus a square cream field.
The wave paths' original overshoot — 4 units past the left edge, 4 past the right, 10
past the bottom — is trimmed by that clip rather than drawn as tabs in the corners.

- **iOS / desktop / Play / legacy** — `fill = 1.0`, the window spans the canvas exactly.
- **Monochrome** — `fill = 66/108`, the glyph's short side spans 66vp of the 108vp
  viewport. AOSP sets `SAFEZONE_SCALE = 66f/72f` in `AdaptiveIconDrawable`, and both
  layers are 108dp with the inner 72dp visible, so anything wider gets masked away.
  `verify()` fails the build if this ever exceeds the safe zone.

Monochrome shape (`MONO_STYLE = "wedge"`): the cream field with the two pale wave bands
knocked out, `fill-rule="evenOdd"`. The field keeps the glyph reading as the icon's square
silhouette at any size while the knocked-out bands leave enough of the fan visible to say
"book". All four candidates are in `preview/monochrome-candidates.png`; the others are
constants (`"arcs"`, `"waves"`, `"field2"`).

Android tints this layer with a *single* colour, so the glyph has exactly two tones —
solid and gap. That rules out four shades, but not four shapes, so `"arcs"` (four
separated bands, carved gap via `_offset`) is available; it reads as clutter at 40px,
which is why `"wedge"` won.

**The glyph geometry is pre-trimmed to the bleed window** (`mono_band_paths`), because it
ships as a VectorDrawable and VectorDrawable has no per-path clip — only a whole-group
`<clip-path>`, whose coordinate space relative to the group's own transform is not worth
relying on across devices. `_clip_cubic` bisects the subdivision parameter for the exact
crossing point, and handles both curve directions (the upper-right band's closing curve
runs right-to-left). Only curve *ends* move, so the shapes are exact rather than
approximated, and no clip is needed in either format. `CLIP_FREE_MONO_STYLES` lists the
styles that qualify; `vector_monochrome` refuses the others rather than emit a vector
whose clip semantics I cannot verify.

That vector also serves as the TTS notification's small icon (`TtsService`), which is why
it must stay a vector — a raster would be both wrong semantically and wasteful in the
status bar.

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
square, alpha present **only** on the monochrome layers, and the themed glyph within the
66dp safe zone.

