package com.aryan.reader.shared

import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderDefaultSettingsStateTest {

    @Test
    fun `epub reader defaults to vertical mode`() {
        assertEquals(ReaderReadingMode.VERTICAL, ReaderSettings().readingMode)
    }

    @Test
    fun `reader default settings reducer updates shared state`() {
        val defaults = ReaderSettings(
            fontSize = 24,
            readingMode = ReaderReadingMode.VERTICAL,
            textAlign = SharedReaderTextAlign.JUSTIFY,
            themeId = "sepia"
        )

        val state = SharedReaderScreenState()
            .reduce(AppAction.ReaderDefaultSettingsChanged(defaults))

        assertEquals(defaults, state.readerDefaultSettings)
    }

    @Test
    fun `pdf reader default settings reducer updates separate shared state`() {
        val epubDefaults = ReaderSettings(themeId = "sepia")
        val pdfDefaults = ReaderSettings(themeId = "reverse")

        val state = SharedReaderScreenState(readerDefaultSettings = epubDefaults)
            .reduce(AppAction.PdfReaderDefaultSettingsChanged(pdfDefaults))

        assertEquals(epubDefaults, state.readerDefaultSettings)
        assertEquals(pdfDefaults, state.pdfReaderDefaultSettings)
    }

    @Test
    fun `reader default settings persist in shared snapshot json`() {
        val defaults = ReaderSettings(
            fontSize = 21,
            lineSpacing = 1.8f,
            margin = 72,
            readingMode = ReaderReadingMode.VERTICAL,
            textAlign = SharedReaderTextAlign.CENTER,
            pageWidth = 920,
            fontFamily = "Serif",
            themeId = "dark",
            textureId = "paper",
            textureAlpha = 0.25f
        )

        val decoded = SharedLibrarySnapshotJson.decodeOrEmpty(
            SharedLibrarySnapshotJson.encode(
                SharedLibrarySnapshot(readerDefaultSettings = defaults)
            )
        )

        assertEquals(defaults, decoded.readerDefaultSettings)
    }

    @Test
    fun `pdf reader default settings persist separately in shared snapshot json`() {
        val epubDefaults = ReaderSettings(themeId = "sepia")
        val pdfDefaults = ReaderSettings(themeId = "reverse")

        val decoded = SharedLibrarySnapshotJson.decodeOrEmpty(
            SharedLibrarySnapshotJson.encode(
                SharedLibrarySnapshot(
                    readerDefaultSettings = epubDefaults,
                    pdfReaderDefaultSettings = pdfDefaults
                )
            )
        )

        assertEquals(epubDefaults, decoded.readerDefaultSettings)
        assertEquals(pdfDefaults, decoded.pdfReaderDefaultSettings)
    }
}
