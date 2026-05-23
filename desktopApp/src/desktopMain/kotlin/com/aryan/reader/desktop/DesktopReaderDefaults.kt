package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReaderFeatureSurface
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings

internal const val DesktopReaderDefaultsVersion = 1

internal enum class DesktopReaderSettingsEngine {
    TEXT,
    PDF
}

internal val DesktopDefaultTextReaderSettings = ReaderSettings(
    readingMode = ReaderReadingMode.PAGINATED,
    pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE
)

internal val DesktopDefaultPdfReaderSettings = ReaderSettings(
    themeId = "no_theme",
    readingMode = ReaderReadingMode.PAGINATED,
    pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE
)

internal fun FileType.desktopReaderSettingsEngine(): DesktopReaderSettingsEngine? {
    return when (SharedFileCapabilities.surfaceFor(this, ReaderPlatform.DESKTOP)) {
        ReaderFeatureSurface.PDF_VIEWER -> DesktopReaderSettingsEngine.PDF
        ReaderFeatureSurface.EPUB_READER,
        ReaderFeatureSurface.TEXT_READER -> DesktopReaderSettingsEngine.TEXT
        null -> null
    }
}

internal fun BookItem.usesDesktopReaderSettingsEngine(engine: DesktopReaderSettingsEngine): Boolean {
    return type.desktopReaderSettingsEngine() == engine
}

internal fun List<BookItem>.withDesktopReaderEngineSettings(
    engine: DesktopReaderSettingsEngine,
    settings: ReaderSettings
): List<BookItem> {
    return map { book ->
        if (book.usesDesktopReaderSettingsEngine(engine)) {
            book.copy(readerSettings = settings)
        } else {
            book
        }
    }
}

internal fun SharedReaderScreenState.withDesktopReaderEngineDefaultSettings(
    engine: DesktopReaderSettingsEngine,
    settings: ReaderSettings
): SharedReaderScreenState {
    val engineSettings = if (engine == DesktopReaderSettingsEngine.PDF) {
        settings.toDesktopPdfReaderSettings()
    } else {
        settings
    }
    return when (engine) {
        DesktopReaderSettingsEngine.TEXT -> copy(
            readerDefaultSettings = engineSettings,
            rawLibraryBooks = rawLibraryBooks.withDesktopReaderEngineSettings(engine, engineSettings)
        )
        DesktopReaderSettingsEngine.PDF -> copy(
            pdfReaderDefaultSettings = engineSettings,
            rawLibraryBooks = rawLibraryBooks.withDesktopReaderEngineSettings(engine, engineSettings)
        )
    }
}

internal fun ReaderSettings.toDesktopPdfDisplayMode(): PdfDisplayMode {
    return when (readingMode) {
        ReaderReadingMode.PAGINATED -> PdfDisplayMode.PAGINATION
        ReaderReadingMode.VERTICAL -> PdfDisplayMode.VERTICAL_SCROLL
    }
}

internal fun PdfDisplayMode.toDesktopReaderReadingMode(): ReaderReadingMode {
    return when (this) {
        PdfDisplayMode.PAGINATION -> ReaderReadingMode.PAGINATED
        PdfDisplayMode.VERTICAL_SCROLL -> ReaderReadingMode.VERTICAL
    }
}
