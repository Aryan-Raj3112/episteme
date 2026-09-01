package com.aryan.reader.pdf

import android.net.Uri
import com.aryan.reader.logCloudAnnotationSyncTrace
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfAnnotationRepository
import com.aryan.reader.pdf.data.PdfHighlightRepository
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.PdfTextBoxRepository
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

internal data class PdfReaderSaveSnapshot(
    val bookId: String?,
    val annotationSession: SharedPdfAnnotationSessionState,
    val isDocumentReady: Boolean,
    val initialRestorationComplete: Boolean,
    val pdfUri: Uri,
    val annotations: Map<Int, List<PdfAnnotation>>,
    val textBoxes: List<PdfTextBox>,
    val highlights: List<PdfUserHighlight>,
    val bookmarks: Set<PdfBookmark>,
    val totalPages: Int,
    val currentPage: Int,
    val pendingRestorePage: Int?
)

internal class PdfReaderPersistence(
    private val annotationRepository: PdfAnnotationRepository,
    private val textBoxRepository: PdfTextBoxRepository,
    private val highlightRepository: PdfHighlightRepository,
    private val onBookmarksChanged: (String) -> Unit,
    private val onSavePosition: suspend (Uri, Int, Int) -> Unit,
    private val onSidecarsCommitted: suspend (bookId: String, reason: String, immediate: Boolean) -> Unit
) {
    private val saveMutex = Mutex()
    private val lastSavedHashes = IntArray(5) { -1 }

    fun recordLoadedSidecars(
        annotations: Map<Int, List<PdfAnnotation>>,
        textBoxes: List<PdfTextBox>,
        highlights: List<PdfUserHighlight>
    ) {
        lastSavedHashes[0] = annotations.hashCode()
        lastSavedHashes[1] = textBoxes.hashCode()
        lastSavedHashes[2] = highlights.hashCode()
    }

    fun loadedSidecarHashesLabel(): String = lastSavedHashes.copyOfRange(0, 3).joinToString()

    suspend fun save(snapshot: PdfReaderSaveSnapshot, force: Boolean) {
        val bookId = snapshot.bookId ?: return
        if (!snapshot.isDocumentReady && !force) {
            Timber.tag("PdfPositionDebug").w("UI: Save ignored. Document not ready.")
            return
        }

        val page = pdfPageToPersist(
            initialRestorationComplete = snapshot.initialRestorationComplete,
            currentPage = snapshot.currentPage,
            pendingRestorePage = snapshot.pendingRestorePage
        )
        if (!snapshot.initialRestorationComplete) {
            Timber.tag("PdfPositionDebug").i(
                "UI: Save during restoration | Using restoreTarget: ${snapshot.pendingRestorePage ?: 0} " +
                    "(CurrentUI: ${snapshot.currentPage})"
            )
        }
        Timber.tag("PdfPositionDebug").v(
            "UI: Save logic | Choosing: $page (UI: ${snapshot.currentPage}, " +
                "Target: ${snapshot.pendingRestorePage ?: 0}, Done: ${snapshot.initialRestorationComplete})"
        )

        val annotationsHash = snapshot.annotations.hashCode()
        val boxesHash = snapshot.textBoxes.hashCode()
        val highlightsHash = snapshot.highlights.hashCode()
        val bookmarksHash = snapshot.bookmarks.hashCode()
        val canSaveSidecars = snapshot.annotationSession.canUseFor(bookId)

        withContext(NonCancellable) {
            saveMutex.withLock {
                withContext(Dispatchers.IO) {
                    var sidecarsSaved = false
                    if (canSaveSidecars) {
                        if (annotationsHash != lastSavedHashes[0]) {
                            logCloudAnnotationSyncTrace {
                                "android.reader.save_ink book=$bookId force=$force oldHash=${lastSavedHashes[0]} " +
                                    "newHash=$annotationsHash pages=${snapshot.annotations.keys.sorted()} " +
                                    "count=${snapshot.annotations.values.sumOf { it.size }}"
                            }
                            annotationRepository.saveAnnotations(bookId, snapshot.annotations)
                            lastSavedHashes[0] = annotationsHash
                            sidecarsSaved = true
                        } else if (force) {
                            logCloudAnnotationSyncTrace {
                                "android.reader.save_ink_noop book=$bookId force=true hash=$annotationsHash"
                            }
                        }
                        if (boxesHash != lastSavedHashes[1]) {
                            logCloudAnnotationSyncTrace {
                                "android.reader.save_textboxes book=$bookId force=$force oldHash=${lastSavedHashes[1]} " +
                                    "newHash=$boxesHash count=${snapshot.textBoxes.size}"
                            }
                            textBoxRepository.saveTextBoxes(bookId, snapshot.textBoxes)
                            lastSavedHashes[1] = boxesHash
                            sidecarsSaved = true
                        } else if (force) {
                            logCloudAnnotationSyncTrace {
                                "android.reader.save_textboxes_noop book=$bookId force=true hash=$boxesHash"
                            }
                        }
                        if (highlightsHash != lastSavedHashes[2]) {
                            logCloudAnnotationSyncTrace {
                                "android.reader.save_highlights book=$bookId force=$force oldHash=${lastSavedHashes[2]} " +
                                    "newHash=$highlightsHash count=${snapshot.highlights.size}"
                            }
                            highlightRepository.saveHighlights(bookId, snapshot.highlights)
                            lastSavedHashes[2] = highlightsHash
                            sidecarsSaved = true
                        } else if (force) {
                            logCloudAnnotationSyncTrace {
                                "android.reader.save_highlights_noop book=$bookId force=true hash=$highlightsHash"
                            }
                        }
                    } else {
                        Timber.tag("PdfTabSync").d(
                            "Skipping PDF sidecar save for $bookId; loaded sidecars belong to " +
                                snapshot.annotationSession.bookId
                        )
                    }

                    if (force || bookmarksHash != lastSavedHashes[3]) {
                        val bookmarksJson = serializePdfBookmarksToJson(snapshot.bookmarks)
                        withContext(Dispatchers.Main) { onBookmarksChanged(bookmarksJson) }
                        lastSavedHashes[3] = bookmarksHash
                    }

                    if (force || page != lastSavedHashes[4]) {
                        Timber.tag("PdfPositionDebug").d(
                            "UI: COMMIT SAVE | Page: $page | Total: ${snapshot.totalPages} | Force: $force"
                        )
                        if (snapshot.totalPages > 0) {
                            withContext(Dispatchers.Main) {
                                onSavePosition(snapshot.pdfUri, page, snapshot.totalPages)
                            }
                        }
                        lastSavedHashes[4] = page
                    }
                    if (sidecarsSaved || (force && canSaveSidecars)) {
                        logCloudAnnotationSyncTrace {
                            "android.reader.sidecar_export_queue book=$bookId force=$force changed=$sidecarsSaved"
                        }
                        onSidecarsCommitted(
                            bookId,
                            if (force) "lifecycle_flush" else "debounced_save",
                            force,
                        )
                    }
                }
            }
        }
    }

    suspend fun persistInk(
        bookId: String?,
        annotationSession: SharedPdfAnnotationSessionState,
        annotations: Map<Int, List<PdfAnnotation>>,
        deletedAnnotations: Collection<PdfAnnotation>,
        reason: String
    ) {
        bookId ?: return
        if (!annotationSession.canUseFor(bookId)) {
            logCloudAnnotationSyncTrace {
                "android.reader.persist_ink_skip book=$bookId reason=$reason " +
                    "loadedSidecarBook=${annotationSession.bookId}"
            }
            return
        }
        val deletedIds = deletedAnnotations.mapNotNull { it.id.takeIf(String::isNotBlank) }.toSet()
        withContext(NonCancellable) {
            saveMutex.withLock {
                withContext(Dispatchers.IO) {
                    if (deletedIds.isNotEmpty()) {
                        annotationRepository.markAnnotationsDeleted(bookId, deletedIds)
                    }
                    annotationRepository.saveAnnotations(bookId, annotations)
                    lastSavedHashes[0] = annotations.hashCode()
                }
            }
        }
        logCloudAnnotationSyncTrace {
            "android.reader.persist_ink book=$bookId reason=$reason count=${annotations.values.sumOf { it.size }} " +
                "deletedIds=${deletedIds.sorted()}"
        }
        onSidecarsCommitted(bookId, reason, false)
    }
}
