package com.aryan.reader.pdf

import android.content.Context
import androidx.annotation.OptIn
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

@OptIn(UnstableApi::class)
@Suppress("unused")
internal fun saveTtsMode(context: Context, mode: TtsPlaybackManager.TtsMode) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString(TTS_MODE_KEY, mode.name) }
}
