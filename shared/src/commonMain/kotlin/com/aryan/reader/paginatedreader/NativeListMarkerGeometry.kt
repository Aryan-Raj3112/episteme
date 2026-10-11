package com.aryan.reader.paginatedreader

/**
 * List-item marker geometry, single-sourced for both native stacks (B1 step 3,
 * list block kind).
 *
 * Android renders `ListItemBlock` markers in two branches
 * (`PaginatedReaderContent.kt`, paginated + vertical) and shared renders
 * `SemanticList` markers in `SharedNativeVerticalRendering.kt`. The slot was
 * already equal on both (32dp area, 8dp trailing padding); only the marker
 * image scale had drifted (0.8 Android vs 0.85 shared). Android is the
 * benchmark, so 0.8 wins.
 *
 * Deliberately not unified: the marker *text* style. Android sets the ambient
 * screen style with end alignment; shared resolves the item's own style (which
 * inherits the list item's font, per the CSS `::marker` rule). Shared is
 * spec-correct there and changing it would regress styled lists, while
 * changing Android's shipped look needs a product decision. Recorded, not
 * papered over.
 */

/** Marker slot width, in dp. */
const val NATIVE_LIST_MARKER_AREA_WIDTH_DP = 32

/** Trailing padding inside the marker slot, in dp. */
const val NATIVE_LIST_MARKER_END_PADDING_DP = 8

/** Marker image size as a fraction of the ambient font size. */
const val NATIVE_LIST_MARKER_IMAGE_SCALE = 0.8f

/**
 * Marker image size for a base font size. Each stack passes its own base
 * (Android the ambient `textStyle`, shared the native base) — only the scale
 * is shared, matching the header-scale split where the factor is common and
 * the base stays platform-specific.
 */
fun nativeListMarkerImageSizeSp(fontSizeSp: Float): Float =
    fontSizeSp * NATIVE_LIST_MARKER_IMAGE_SCALE
