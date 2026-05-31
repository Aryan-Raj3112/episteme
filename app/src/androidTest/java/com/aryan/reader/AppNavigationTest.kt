package com.aryan.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ReaderFeatureSurface
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFileCapabilities
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    @Test
    fun appDestinations_useStableReaderRoutes() {
        assertThat(AppDestinations.MAIN_ROUTE).isEqualTo("main")
        assertThat(AppDestinations.PDF_VIEWER_ROUTE).isEqualTo("pdf_viewer")
        assertThat(AppDestinations.EPUB_READER_ROUTE).isEqualTo("epub_reader")
    }

    @Test
    fun androidReaderSurface_mapsPdfBackedTypesToPdfViewer() {
        val mappedSurfaces = listOf(
            FileType.PDF,
            FileType.CBZ,
            FileType.CBR,
            FileType.CB7,
            FileType.CBT,
            FileType.PPTX
        ).associateWith { it.readerSurfaceOnAndroid() }

        assertThat(mappedSurfaces).containsExactly(
            FileType.PDF, ReaderFeatureSurface.PDF_VIEWER,
            FileType.CBZ, ReaderFeatureSurface.PDF_VIEWER,
            FileType.CBR, ReaderFeatureSurface.PDF_VIEWER,
            FileType.CB7, ReaderFeatureSurface.PDF_VIEWER,
            FileType.CBT, ReaderFeatureSurface.PDF_VIEWER,
            FileType.PPTX, ReaderFeatureSurface.PDF_VIEWER
        )
    }

    @Test
    fun androidReaderSurface_mapsTextBackedTypesToEpubReader() {
        val mappedSurfaces = listOf(
            FileType.EPUB,
            FileType.MOBI,
            FileType.MD,
            FileType.TXT,
            FileType.HTML,
            FileType.FB2,
            FileType.DOCX,
            FileType.ODT,
            FileType.FODT
        ).associateWith { it.readerSurfaceOnAndroid() }

        assertThat(mappedSurfaces).containsExactly(
            FileType.EPUB, ReaderFeatureSurface.EPUB_READER,
            FileType.MOBI, ReaderFeatureSurface.EPUB_READER,
            FileType.MD, ReaderFeatureSurface.EPUB_READER,
            FileType.TXT, ReaderFeatureSurface.EPUB_READER,
            FileType.HTML, ReaderFeatureSurface.EPUB_READER,
            FileType.FB2, ReaderFeatureSurface.EPUB_READER,
            FileType.DOCX, ReaderFeatureSurface.EPUB_READER,
            FileType.ODT, ReaderFeatureSurface.EPUB_READER,
            FileType.FODT, ReaderFeatureSurface.EPUB_READER
        )
    }

    @Test
    fun androidReaderSurface_returnsNullForUnknownFileType() {
        assertThat(FileType.UNKNOWN.readerSurfaceOnAndroid()).isNull()
    }

    @Test
    fun appNavBackInterceptor_onlyHandlesResumedNonReaderBackStackEntries() {
        assertThat(
            shouldInterceptAppNavBack(
                currentRoute = AppDestinations.PRO_SCREEN_ROUTE,
                hasPreviousBackStackEntry = true,
                isCurrentEntryResumed = true
            )
        ).isTrue()
        assertThat(
            shouldInterceptAppNavBack(
                currentRoute = AppDestinations.MAIN_ROUTE,
                hasPreviousBackStackEntry = true,
                isCurrentEntryResumed = true
            )
        ).isFalse()
        assertThat(
            shouldInterceptAppNavBack(
                currentRoute = AppDestinations.PDF_VIEWER_ROUTE,
                hasPreviousBackStackEntry = true,
                isCurrentEntryResumed = true
            )
        ).isFalse()
        assertThat(
            shouldInterceptAppNavBack(
                currentRoute = AppDestinations.PRO_SCREEN_ROUTE,
                hasPreviousBackStackEntry = false,
                isCurrentEntryResumed = true
            )
        ).isFalse()
        assertThat(
            shouldInterceptAppNavBack(
                currentRoute = AppDestinations.PRO_SCREEN_ROUTE,
                hasPreviousBackStackEntry = true,
                isCurrentEntryResumed = false
            )
        ).isFalse()
    }

    private fun FileType.readerSurfaceOnAndroid(): ReaderFeatureSurface? {
        return SharedFileCapabilities.surfaceFor(this, ReaderPlatform.ANDROID)
    }
}
