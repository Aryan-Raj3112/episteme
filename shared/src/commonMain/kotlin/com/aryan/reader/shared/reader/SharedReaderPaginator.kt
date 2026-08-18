package com.aryan.reader.shared.reader

/**
 * Abstracts EPUB page generation strategies for the shared reader.
 *
 * [SimplePaginator] produces fast character-count estimates for vertical and
 * webview surfaces, while [SharedMeasuredEpubPaginator] measures real typography
 * against the device viewport (with an optional persistent cache) for the
 * native paginated surface. Android remains the behavioral benchmark.
 */
interface SharedReaderPaginator {
    suspend fun paginate(
        book: SharedEpubBook,
        settings: ReaderSettings,
        viewport: ReaderViewportSpec,
        readCache: Boolean = true
    ): List<ReaderPage>
}