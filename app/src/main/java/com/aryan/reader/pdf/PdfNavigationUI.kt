package com.aryan.reader.pdf

import com.aryan.reader.shared.ui.SharedMobilePdfPageScrubbingOverlay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aryan.reader.R

@Composable
internal fun PageScrubbingAnimation(
    pageLabel: String
) {
    SharedMobilePdfPageScrubbingOverlay(label = pageLabel)
}

@Composable
internal fun BookmarkButton(
    isBookmarked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    com.aryan.reader.shared.ui.SharedReaderBookmarkButton(
        isBookmarked = isBookmarked,
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(id = R.drawable.bookmark),
            contentDescription = stringResource(R.string.content_desc_bookmark),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
internal fun ZoomPercentageIndicator(
    percentage: Int,
    onResetZoomClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f)
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "$percentage%",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(Color.White.copy(alpha = 0.5f))
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Reset Zoom Button
            Icon(
                painter = painterResource(id = R.drawable.zoom_out),
                contentDescription = stringResource(R.string.content_desc_reset_zoom),
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onResetZoomClick)
            )
        }
    }
}
