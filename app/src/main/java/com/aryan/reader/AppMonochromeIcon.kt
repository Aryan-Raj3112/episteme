package com.aryan.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/**
 * The app's own icon, drawn as a single monochrome mark on a plate the app theme colours.
 *
 * Every colour the launcher icon ships with is a decision the app's theme has already made
 * differently: the icon is blue on white, hard-coded, baked into a raster at five densities.
 * That is fine on a home screen, where it sits on the launcher's own background, and wrong
 * everywhere inside the app -- in particular it ignores light/dark and any dynamic or custom
 * seed colour the user has picked. So the in-app icon is not the launcher icon at all: it is
 * the *monochrome* layer, the same one Android 13 uses for themed icons, tinted from
 * [MaterialTheme] and sat on a themed plate so it still reads as an icon rather than as
 * loose artwork.
 *
 * [R.drawable.ic_app_mark] is that monochrome glyph with its viewport cropped to the artwork.
 * `ic_launcher_monochrome` cannot be used directly: it is deliberately fieldless and centred
 * in a 108dp viewport, because the launcher is what draws the plate behind it. Sized to that
 * viewport, the mark would render at 54/108 of its own width -- a 16x10dp speck in a 32dp
 * avatar. Cropped, it fills whatever slot it is given. Both are generated from one source by
 * `scripts/generate_app_icons.py`, so the two cannot drift apart.
 *
 * Android-only because the mark is an Android drawable; iOS has no in-app app-icon slot to
 * keep in step with this.
 */
@Composable
internal fun AppMonochromeIcon(
    contentDescription: String,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(percent = LAUNCHER_CORNER_PERCENT),
    plateColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    markColor: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(plateColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_app_mark),
            contentDescription = contentDescription,
            tint = markColor,
            // height comes from the painter's own aspect ratio, so the mark is never
            // distorted and the asset stays the single source of its proportions
            modifier = Modifier.fillMaxWidth(MARK_WIDTH_FRACTION),
        )
    }
}

/**
 * The mark's width as a fraction of the plate's.
 *
 * Set by the tightest slot rather than by taste at the largest one. The mark's bounding box
 * is 54:32.8, so at 32dp the whole plate only clears a circle's inscribed square -- 22.6dp --
 * at 0.68 or below; past that the extremities cross the curve and the plate stops reading as
 * a circle. On the larger plates the same fraction simply leaves more room.
 */
private const val MARK_WIDTH_FRACTION = 0.68f

/** The launcher's own mask, so an in-app plate reads as the icon rather than a rounded box. */
private const val LAUNCHER_CORNER_PERCENT = 31
