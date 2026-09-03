package com.aryan.reader.pdf

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.tts.TtsPlaybackManager

internal typealias SaveMode = com.aryan.reader.shared.SaveMode

typealias SearchHighlightMode = com.aryan.reader.shared.SearchHighlightMode

internal sealed interface HistoryAction {
    data class Add(val pageIndex: Int, val annotation: PdfAnnotation) : HistoryAction
    data class Remove(val items: Map<Int, List<PdfAnnotation>>) : HistoryAction
}

internal typealias DockLocation = com.aryan.reader.shared.DockLocation

internal typealias DisplayMode = com.aryan.reader.shared.PdfDisplayMode

/** Height of the main pen annotation dock; shared so stacked chrome (e.g. a top-docked text dock) can clear it. */
internal val PdfMainDockHeight: Dp = 64.dp

/** Height of the text formatting dock bar; used for drag seeding and floating clamps. */
internal val PdfTextDockHeight: Dp = 48.dp

@OptIn(UnstableApi::class)
@Suppress("unused")
internal fun saveTtsMode(context: Context, mode: TtsPlaybackManager.TtsMode) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString(TTS_MODE_KEY, mode.name) }
}
