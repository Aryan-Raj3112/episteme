package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Bookmark toggle for the reader overlay: a fixed 48dp tap target that fades its icon in only
 * while the bookmark is set, with no ripple.
 *
 * The icon is a slot rather than a shared drawable on purpose — Android supplies its own
 * `R.drawable.bookmark` glyph (the EPUB and PDF readers use different bookmark icon strings), and
 * Android's artwork is the benchmark, so shared owns the interaction and each host owns the paint.
 */
@Composable
fun SharedReaderBookmarkButton(
    isBookmarked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .width(48.dp)
            .height(48.dp)
            .clip(RectangleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = isBookmarked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            icon()
        }
    }
}