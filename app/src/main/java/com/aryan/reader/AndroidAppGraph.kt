package com.aryan.reader

import android.content.Context
import com.aryan.reader.data.AndroidBookArtifactStore
import com.aryan.reader.data.AndroidBookStore
import com.aryan.reader.data.AndroidFolderMirrorStore
import com.aryan.reader.data.AndroidLegacyMigrationStore
import com.aryan.reader.data.CloudflareRepository
import com.aryan.reader.data.FeedbackRepository
import com.aryan.reader.data.FirestoreRepository
import com.aryan.reader.data.FontsRepository
import com.aryan.reader.data.GoogleDriveRepository
import com.aryan.reader.data.RecentFilesRepository
import com.aryan.reader.data.RemoteConfigRepository
import com.aryan.reader.epub.EpubParser
import com.aryan.reader.epub.Fb2Parser
import com.aryan.reader.epub.MobiParser
import com.aryan.reader.epub.OdtParser
import com.aryan.reader.epub.SingleFileImporter
import com.aryan.reader.paginatedreader.data.BookCacheDatabase
import com.aryan.reader.pdf.PdfRichTextRepository
import com.aryan.reader.pdf.data.PageLayoutRepository
import com.aryan.reader.pdf.data.PdfAnnotationRepository
import com.aryan.reader.pdf.data.PdfHighlightRepository
import com.aryan.reader.pdf.data.PdfTextBoxRepository
import com.aryan.reader.pdf.data.PdfTextRepository
import com.aryan.reader.shared.LibraryMutationController
import java.util.UUID

/** Android composition root. Feature bindings move here as their shared controllers become production-owned. */
internal class AndroidAppGraph(context: Context) {
    val authRepository = AuthRepository(context)
    private val recentFilesRepository = RecentFilesRepository(context)
    val bookStore: AndroidBookStore = recentFilesRepository
    val folderMirrorStore: AndroidFolderMirrorStore = recentFilesRepository
    val bookArtifactStore: AndroidBookArtifactStore = recentFilesRepository
    val legacyMigrationStore: AndroidLegacyMigrationStore = recentFilesRepository
    val libraryStore = AndroidLibraryMutationStore(context)
    val pdfTextRepository by lazy { PdfTextRepository(context) }
    val bookCacheDao by lazy { BookCacheDatabase.getDatabase(context).bookCacheDao() }
    val epubParser by lazy { EpubParser(context) }
    val mobiParser by lazy { MobiParser(context) }
    val fb2Parser by lazy { Fb2Parser(context) }
    val odtParser by lazy { OdtParser(context) }
    val singleFileImporter by lazy { SingleFileImporter(context) }
    val bookImporter by lazy { BookImporter(context) }
    val epubMetadataFileEditor by lazy { EpubMetadataFileEditor(context) }
    val pageLayoutRepository by lazy { PageLayoutRepository(context) }
    val pdfRichTextRepository by lazy { PdfRichTextRepository(context) }
    val pdfTextBoxRepository by lazy { PdfTextBoxRepository(context) }
    val pdfHighlightRepository by lazy { PdfHighlightRepository(context) }
    val pdfAnnotationRepository by lazy { PdfAnnotationRepository(context) }
    val firestoreRepository = FirestoreRepository()
    val googleDriveRepository = GoogleDriveRepository()
    val cloudflareRepository = CloudflareRepository()
    val remoteConfigRepository = RemoteConfigRepository()
    val feedbackRepository = FeedbackRepository(context)
    val fontsRepository = FontsRepository(context)

    fun libraryMutationController(
        onShelfChanged: suspend (String) -> Unit,
    ): LibraryMutationController = LibraryMutationController(
        store = libraryStore,
        newId = { UUID.randomUUID().toString() },
        nowMillis = System::currentTimeMillis,
        onShelfChanged = onShelfChanged,
    )
}
