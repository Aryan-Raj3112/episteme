package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import com.aryan.reader.shared.pdf.PdfZoomSize
import com.aryan.reader.shared.pdf.shouldResetPdfZoomForOrientationChange

@Composable
internal fun SharedMobilePdfOrientationZoomResetEffect(
    viewport: IntSize,
    verticalDocumentMode: Boolean,
    onReset: () -> Unit,
) {
    var previousViewport by remember { mutableStateOf<PdfZoomSize?>(null) }
    LaunchedEffect(viewport, verticalDocumentMode) {
        val current = PdfZoomSize(viewport.width.toFloat(), viewport.height.toFloat())
        if (shouldResetPdfZoomForOrientationChange(previousViewport, current, !verticalDocumentMode)) onReset()
        previousViewport = current
    }
}
