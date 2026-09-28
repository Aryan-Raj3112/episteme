package com.aryan.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aryan.reader.data.RecentFileItem
import java.io.File
import kotlin.math.absoluteValue

@Composable
fun ThemedBookCover(
    item: RecentFileItem,
    modifier: Modifier = Modifier,
    contentDescription: String? = item.displayName,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val coverFile = remember(item.coverImagePath) {
        item.coverImagePath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }
    }
    // Stable request identity: without remember, every recomposition builds a
    // new ImageRequest and Coil restarts the load (flicker + decode churn).
    // Coil sizes the decode to the laid-out AsyncImage automatically.
    val coverRequest = remember(coverFile) {
        coverFile?.let {
            ImageRequest.Builder(context)
                .data(it)
                .memoryCacheKey(it.path)
                .crossfade(true)
                .allowHardware(true)
                .build()
        }
    }

    Box(modifier = modifier) {
        // Generated art is only a fallback: composing it under an opaque
        // cover wastes a full gradient + text draw on every card.
        if (coverRequest != null) {
            AsyncImage(
                model = coverRequest,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            GeneratedBookCover(item = item, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun GeneratedBookCover(
    item: RecentFileItem,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val seed = remember(item.bookId, item.displayName) { item.coverColorSeed() }
    val base = generatedBookCoverColor(item)
    val accent = remember(colorScheme, seed) {
        val accentOptions = listOf(
            colorScheme.primary,
            colorScheme.secondary,
            colorScheme.tertiary,
            colorScheme.inversePrimary
        )
        accentOptions[(seed / 7) % accentOptions.size]
    }
    val title = remember(item.customName, item.title, item.displayName) { item.coverTitle() }
    val author = remember(item.author) { item.coverAuthor() }
    val coverGradient = remember(colorScheme, base, accent) {
        Brush.linearGradient(
            colors = listOf(
                lerp(base, colorScheme.surface, 0.06f),
                lerp(base, accent, 0.16f),
                lerp(colorScheme.surfaceContainerHighest, base, 0.34f)
            )
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .background(coverGradient)
            .border(0.5.dp, colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        val compact = maxWidth < 80.dp
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(if (compact) 7.dp else 10.dp)
                .background(accent.copy(alpha = 0.42f))
                .align(Alignment.CenterStart)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(if (compact) 5.dp else 7.dp)
                .align(Alignment.TopEnd)
                .offset(y = if (compact) 8.dp else 12.dp)
                .background(colorScheme.surface.copy(alpha = 0.30f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.48f)
                .height(if (compact) 4.dp else 6.dp)
                .align(Alignment.BottomStart)
                .offset(x = if (compact) 12.dp else 18.dp, y = if (compact) (-10).dp else (-16).dp)
                .background(accent.copy(alpha = 0.26f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (compact) 12.dp else 18.dp,
                    top = if (compact) 10.dp else 18.dp,
                    end = if (compact) 8.dp else 14.dp,
                    bottom = if (compact) 10.dp else 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = colorScheme.onSurface,
                fontSize = if (compact) 12.sp else 17.sp,
                lineHeight = if (compact) 14.sp else 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (author != null && !compact) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(1.dp)
                        .background(accent.copy(alpha = 0.55f))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = author,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The stable tonal colour used by the generated cover when no artwork is available. */
@Composable
internal fun generatedBookCoverColor(item: RecentFileItem): Color {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme, item.bookId, item.displayName) {
        val baseOptions = listOf(
            colorScheme.primaryContainer,
            colorScheme.secondaryContainer,
            colorScheme.tertiaryContainer,
            lerp(colorScheme.primary, colorScheme.surface, 0.30f),
            lerp(colorScheme.secondary, colorScheme.surface, 0.26f)
        )
        baseOptions[item.coverColorSeed() % baseOptions.size]
    }
}

private fun RecentFileItem.coverColorSeed(): Int {
    val hash = (bookId.ifBlank { displayName }).hashCode()
    return if (hash == Int.MIN_VALUE) 0 else hash.absoluteValue
}

private fun RecentFileItem.coverTitle(): String {
    return customName
        ?.takeIf { it.isNotBlank() }
        ?: title?.takeIf { it.isNotBlank() && !it.equals("content", ignoreCase = true) }
        ?: displayName.substringBeforeLast('.', missingDelimiterValue = displayName)
}

private fun RecentFileItem.coverAuthor(): String? {
    return author
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
}
