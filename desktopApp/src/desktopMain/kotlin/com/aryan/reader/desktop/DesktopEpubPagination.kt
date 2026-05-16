package com.aryan.reader.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.reader.ReaderLayoutSignature
import com.aryan.reader.shared.reader.ReaderViewportSpec
import com.aryan.reader.shared.reader.SharedEpubBook

internal data class DesktopEpubPaginationRequest(
    val bookId: String,
    val chapterSignature: Int,
    val layoutSignature: ReaderLayoutSignature,
    val viewport: ReaderViewportSpec,
    val density: DesktopEpubPaginationDensity,
    val cacheGeneration: Int
)

internal data class DesktopEpubPaginationDensity(
    val density: Float,
    val fontScale: Float
)

internal fun SharedEpubBook.desktopPaginationContentSignature(): Int {
    return chapters.fold(31 * id.hashCode() + css.hashCode()) { acc, chapter ->
        31 * acc +
            chapter.id.hashCode() +
            chapter.plainText.length +
            chapter.plainText.hashCode() +
            chapter.semanticBlocks.hashCode() +
            chapter.htmlContent.length +
            chapter.htmlContent.hashCode() +
            chapter.baseHref.orEmpty().hashCode()
    }
}

@Composable
internal fun DesktopEpubPaginationPreparing(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text(
                if (active) "Preparing pages" else "Measuring reader layout",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
