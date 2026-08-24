/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader

import android.os.Build
import timber.log.Timber
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.aryan.reader.epubreader.EpubReaderScreen
import com.aryan.reader.feedback.FeedbackScreen
import com.aryan.reader.feedback.SupportProjectScreen
import com.aryan.reader.pdf.PdfViewerScreen
import com.aryan.reader.pdf.PdfSplitPdfPicker
import com.aryan.reader.pdf.PdfSplitReaderScreen
import com.aryan.reader.shared.ReaderFeatureSurface
import com.aryan.reader.shared.PdfSplitOrientation
import com.aryan.reader.shared.PdfSplitPaneState
import com.aryan.reader.shared.samePdfDocument
import com.aryan.reader.shared.ui.SharedMobileAppDestination
import com.aryan.reader.tts.ReaderTtsMiniBar
import com.aryan.reader.tts.readerTtsMiniBarBottomPaddingDp
import com.aryan.reader.tts.shouldShowReaderTtsMiniBar
import kotlinx.coroutines.delay

fun shouldInterceptAppNavBack(
    currentRoute: String?,
    hasPreviousBackStackEntry: Boolean,
    isCurrentEntryResumed: Boolean
): Boolean {
    if (!hasPreviousBackStackEntry) return false
    val isNonMainBackStackRoute = currentRoute != null &&
        SharedMobileAppDestination.fromRoute(currentRoute) != SharedMobileAppDestination.MAIN
    if (!isCurrentEntryResumed) return isNonMainBackStackRoute
    return isNonMainBackStackRoute
}

fun shouldSyncSelectedFileRoute(currentRoute: String?): Boolean {
    return currentRoute == null ||
        SharedMobileAppDestination.fromRoute(currentRoute)?.participatesInSelectedFileSync == true
}

private fun NavHostController.isReadyForBackStackChange(): Boolean {
    return currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED
}

private suspend fun NavHostController.awaitReadyForBackStackChange() {
    while (!isReadyForBackStackChange()) {
        delay(32)
    }
}

private fun NavHostController.navigateSingleTopTo(destination: SharedMobileAppDestination, popUpToStart: Boolean = false) {
    if (!isReadyForBackStackChange()) {
        Timber.d("Skipping navigation to ${destination.route} because the current entry is not resumed yet.")
        return
    }

    try {
        navigate(destination.route) {
            launchSingleTop = true
            if (popUpToStart) {
                popUpTo(graph.startDestinationId) {
                    saveState = false
                }
            }
        }
    } catch (e: IllegalStateException) {
        Timber.w(e, "Navigation to ${destination.route} ignored because the back stack is mid-transition.")
    }
}

private fun NavHostController.navigateToMain() {
    navigateSingleTopTo(SharedMobileAppDestination.MAIN, popUpToStart = true)
}

internal fun NavHostController.navigateIfReady(destination: SharedMobileAppDestination, popUpToStart: Boolean = false) {
    if (currentDestination?.route == destination.route) return
    navigateSingleTopTo(destination, popUpToStart = popUpToStart)
}

private fun NavHostController.popBackStackIfReady(): Boolean {
    if (!isReadyForBackStackChange()) {
        Timber.d("Skipping popBackStack because the current entry is not resumed yet.")
        return false
    }

    return try {
        popBackStack()
    } catch (e: IllegalStateException) {
        Timber.w(e, "popBackStack ignored because the back stack is mid-transition.")
        false
    }
}

private suspend fun NavHostController.syncRouteTo(destination: SharedMobileAppDestination) {
    awaitReadyForBackStackChange()
    if (currentDestination?.route != destination.route) {
        if (destination == SharedMobileAppDestination.MAIN) {
            navigateToMain()
        } else {
            navigateSingleTopTo(destination)
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun AppNavigation(
    navController: NavHostController,
    windowSizeClass: WindowSizeClass,
    viewModel: MainViewModel
) {
    Timber.d("AppNavigation composable invoked.")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val ttsController = viewModel.ttsController
    val ttsState by ttsController.ttsState.collectAsStateWithLifecycle()
    var showPdfSplitPicker by remember { mutableStateOf(false) }
    val currentDestination = SharedMobileAppDestination.fromRoute(currentRoute)
    val defaultPdfSplitOrientation = if (
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    ) {
        PdfSplitOrientation.HORIZONTAL
    } else {
        PdfSplitOrientation.VERTICAL
    }
    val isOnReaderRoute = currentDestination?.isReader == true
    val showTtsMiniBar = shouldShowReaderTtsMiniBar(
        ttsState = ttsState,
        isOnReaderRoute = isOnReaderRoute
    )
    val miniBarBottomPadding = readerTtsMiniBarBottomPaddingDp(
        isOnMainRoute = currentDestination == SharedMobileAppDestination.MAIN
    ).dp
    val shouldInterceptBack = shouldInterceptAppNavBack(
        currentRoute = currentRoute,
        hasPreviousBackStackEntry = navController.previousBackStackEntry != null,
        isCurrentEntryResumed = currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED
    )
    val shouldHandleReaderBack = shouldInterceptBack ||
        (currentDestination == SharedMobileAppDestination.PDF_VIEWER && uiState.pdfSplitWorkspace.isOpen)

    LaunchedEffect(currentRoute, uiState.selectedFileType, uiState.isLoading, uiState.selectedEpubBook, uiState.selectedPdfUri) {
        if (!uiState.isLoading && shouldSyncSelectedFileRoute(currentRoute)) {
            when (uiState.selectedFileType?.readerSurfaceOnAndroid()) {
                ReaderFeatureSurface.PDF_VIEWER -> {
                    if (uiState.selectedPdfUri != null) {
                        if (currentDestination != SharedMobileAppDestination.PDF_VIEWER) {
                            navController.syncRouteTo(SharedMobileAppDestination.PDF_VIEWER)
                        }
                    }
                }
                ReaderFeatureSurface.EPUB_READER,
                ReaderFeatureSurface.TEXT_READER -> {
                    if (uiState.selectedEpubBook != null) {
                        if (currentDestination != SharedMobileAppDestination.EPUB_READER) {
                            navController.syncRouteTo(SharedMobileAppDestination.EPUB_READER)
                        }
                    }
                }
                null -> {
                    if (currentDestination?.isReader == true) {
                        navController.syncRouteTo(SharedMobileAppDestination.MAIN)
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BackHandler(enabled = shouldHandleReaderBack) {
            when (currentDestination) {
                SharedMobileAppDestination.PDF_VIEWER -> {
                    if (uiState.pdfSplitWorkspace.isOpen) {
                        viewModel.closePdfSplitWorkspace()
                    } else {
                        viewModel.clearSelectedFile()
                    }
                }
                SharedMobileAppDestination.EPUB_READER -> viewModel.clearSelectedFile()
                else -> navController.popBackStackIfReady()
            }
        }

        NavHost(navController = navController, startDestination = SharedMobileAppDestination.MAIN.route) {
        composable(SharedMobileAppDestination.MAIN.route) {
            Timber.d("Navigating to Main Screen (${SharedMobileAppDestination.MAIN.route}).")
            if (uiState.isTemporaryExternalOpen) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                MainScreen(
                    viewModel = viewModel,
                    windowSizeClass = windowSizeClass,
                    navController = navController
                )
            }
        }

        // PDF Viewer Screen Composable
        composable(route = SharedMobileAppDestination.PDF_VIEWER.route) {
            Timber.d("Navigating to PDF Viewer Screen (${SharedMobileAppDestination.PDF_VIEWER.route}).")
            val pdfUri = uiState.selectedPdfUri
            val initialPage = uiState.initialPageInBook
            val initialBookmarksJson = uiState.initialBookmarksJson

            val bookId =
                uiState.selectedBookId
                    ?: uiState.allRecentFiles.find { it.uriString == uiState.selectedPdfUri.toString() }?.bookId
                    ?: uiState.rawLibraryFiles.find { it.uriString == uiState.selectedPdfUri.toString() }?.bookId
                    ?: uiState.recentFiles.find { it.uriString == uiState.selectedPdfUri.toString() }?.bookId

            if (pdfUri != null) {
                val navItem = bookId?.let { id ->
                    uiState.allRecentFiles.find { it.bookId == id }
                        ?: uiState.rawLibraryFiles.find { it.bookId == id }
                        ?: uiState.recentFiles.find { it.bookId == id }
                }
                Timber.tag(PDF_RENAME_TRACE_TAG).i(
                    "navigation.pdf routeBookId=$bookId selectedBookId=${uiState.selectedBookId} " +
                        "uri=$pdfUri displayName=${navItem?.displayName} title=${navItem?.title} " +
                        "customName=${navItem?.customName} usePdfFileName=${uiState.usePdfFileNameAsDisplayName}"
                )
                Timber.i("Displaying PDF Viewer for URI: $pdfUri, initialPage: $initialPage")
                Box(modifier = Modifier.fillMaxSize()) {
                    if (uiState.pdfSplitWorkspace.isOpen) {
                        PdfSplitReaderScreen(
                            workspace = uiState.pdfSplitWorkspace,
                            availablePdfs = uiState.rawLibraryFiles.filter {
                                it.type == FileType.PDF && !it.isDeleted && it.isAvailable
                            },
                            isProUser = uiState.isProUser,
                            usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName,
                            viewModel = viewModel,
                            onFocusPane = { pane, sessionId ->
                                viewModel.focusPdfSplitPane(
                                    pane = pane,
                                    expectedSessionId = sessionId,
                                )
                            },
                            onClosePane = { pane, sessionId ->
                                val remainsOpen = viewModel.closePdfSplitPane(
                                    pane = pane,
                                    expectedSessionId = sessionId,
                                )
                                if (!remainsOpen) viewModel.clearSelectedFile()
                            },
                            onCloseWorkspace = viewModel::closePdfSplitWorkspace,
                            onSwapPanes = viewModel::swapPdfSplitPanes,
                            onOrientationChange = viewModel::setPdfSplitOrientation,
                            onDividerChange = { fraction, orientation, revision ->
                                viewModel.setPdfSplitDividerFraction(
                                    fraction = fraction,
                                    orientation = orientation,
                                    expectedRevision = revision,
                                )
                            },
                            onOpenDocument = { selectedBookId, targetPane, sessionId, revision ->
                                viewModel.openPdfSplitPane(
                                    bookId = selectedBookId,
                                    targetPane = targetPane,
                                    expectedRevision = revision,
                                    expectedSessionId = sessionId,
                                )
                            },
                            onNavigateToPro = {
                                navController.navigateIfReady(SharedMobileAppDestination.PRO, popUpToStart = true)
                            },
                        )
                    } else {
                        PdfViewerScreen(
                            pdfUri = pdfUri,
                            initialPage = initialPage,
                            initialBookmarksJson = initialBookmarksJson,
                            isProUser = uiState.isProUser,
                            onNavigateBack = {
                                Timber.d("Back action triggered from PDF Viewer.")
                                viewModel.clearSelectedFile()
                            },
                            onSavePosition = viewModel::savePdfReadingPosition,
                            onBookmarksChanged = { bookmarksJson ->
                                if (bookId != null) {
                                    viewModel.saveBookmarks(
                                        bookId = bookId,
                                        bookmarksJson = bookmarksJson,
                                        documentUri = pdfUri,
                                    )
                                } else {
                                    Timber.w("Could not find bookId to save PDF bookmarks for URI: ${uiState.selectedPdfUri}")
                                }
                            },
                            onNavigateToPro = {
                                navController.navigateIfReady(SharedMobileAppDestination.PRO, popUpToStart = true)
                            },
                            viewModel = viewModel,
                            onOpenSplit = { showPdfSplitPicker = true },
                        )
                    }

                    if (uiState.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    CustomTopBanner(bannerMessage = uiState.bannerMessage)
                }
                if (showPdfSplitPicker && !uiState.pdfSplitWorkspace.isOpen) {
                    PdfSplitPdfPicker(
                        availablePdfs = uiState.rawLibraryFiles.filter {
                                it.type == FileType.PDF &&
                                !it.isDeleted &&
                                it.isAvailable &&
                                it.uriString?.let { uri ->
                                    !PdfSplitPaneState(
                                        bookId = it.bookId,
                                        uriString = uri,
                                    ).samePdfDocument(
                                        PdfSplitPaneState(
                                            bookId = bookId.orEmpty(),
                                            uriString = uiState.selectedPdfUri?.toString().orEmpty(),
                                        ),
                                    )
                                } == true
                        },
                        usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName,
                        onDismiss = { showPdfSplitPicker = false },
                        onDocumentSelected = { item ->
                            showPdfSplitPicker = false
                            viewModel.openPdfSplit(item.bookId, defaultPdfSplitOrientation)
                        },
                    )
                }
            } else if (uiState.isLoading) {
                Timber.d("PDF URI is null but loading is in progress. Showing loading indicator.")
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Timber.w("PDF URI is null in ViewModel state while on PDF screen. Navigating back to Main.")
            }
        }

        // EPUB Reader Screen Composable
        composable(route = SharedMobileAppDestination.EPUB_READER.route) {
            Timber.d("Navigating to EPUB Reader Screen (${SharedMobileAppDestination.EPUB_READER.route}).")
            val epubBook = uiState.selectedEpubBook
            val isLoading = uiState.isLoading
            val errorMessage = uiState.errorMessage
            val initialLocator = uiState.initialLocator
            val initialCfi = uiState.initialCfi
            val initialBookmarksJson = uiState.initialBookmarksJson
            val renderMode = uiState.renderMode

            when {
                epubBook != null -> {
                    Timber.i("Displaying EPUB Reader for Book: ${epubBook.title}, initialLocator: $initialLocator")
                    val coverPath = uiState.recentFiles.find { it.uriString == uiState.selectedEpubUri.toString() }?.coverImagePath
                    val epubUri = uiState.selectedEpubUri
                    uiState.recentFiles.find { it.uriString == uiState.selectedEpubUri.toString() }?.bookId
                    val customFonts by viewModel.customFonts.collectAsStateWithLifecycle()

                    Box(modifier = Modifier.fillMaxSize()) {
                        EpubReaderScreen(
                            epubBook = epubBook,
                            renderMode = renderMode,
                            initialLocator = initialLocator,
                            initialCfi = initialCfi,
                            initialBookmarksJson = initialBookmarksJson,
                            isProUser = uiState.isProUser,
                            coverImagePath = coverPath,
                            onNavigateBack = {
                                Timber.d("Back action from EPUB Reader. Clearing selected file to navigate home.")
                                viewModel.clearSelectedFile()
                            },
                            onSavePosition = { locator, cfiForWebView, progress ->
                                Timber.d("Auto-saving EPUB position: Locator $locator, Progress $progress%")
                                epubUri?.let { uri ->
                                    viewModel.saveEpubReadingPosition(uri, locator, cfiForWebView, progress)
                                }
                            },
                            onBookmarksChanged = { bookmarksJson ->
                                val bookId = uiState.recentFiles.find { it.uriString == uiState.selectedEpubUri.toString() }?.bookId
                                if (bookId != null) {
                                    viewModel.saveBookmarks(bookId, bookmarksJson)
                                } else {
                                    Timber.w("Could not find bookId to save bookmarks for URI: ${uiState.selectedEpubUri}")
                                }
                            },
                            onNavigateToPro = {
                                navController.navigateIfReady(SharedMobileAppDestination.PRO, popUpToStart = true)
                            },
                            onRenderModeChange = viewModel::setRenderMode,
                            customFonts = customFonts,
                            onImportFonts = viewModel::importFonts,
                            viewModel = viewModel
                        )

                        if (uiState.isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        CustomTopBanner(bannerMessage = uiState.bannerMessage)
                    }
                }
                isLoading -> {
                    Timber.d("EPUB Reader: Showing loading indicator.")
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Timber.e("EPUB Reader: Showing error message - $errorMessage")
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(stringResource(R.string.error_message_format, errorMessage), color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.clearSelectedFile()
                        }) {
                            Text(stringResource(R.string.action_go_back))
                        }
                    }
                }
                else -> {
                    Timber.w("EPUB Book is null and not loading/error state on EPUB screen. Navigating back.")
                }
            }
        }
        composable(route = SharedMobileAppDestination.PRO.route) {
            ProScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStackIfReady() }
            )
        }

        composable(route = SharedMobileAppDestination.FEEDBACK.route) {
            FeedbackScreen(
                onNavigateBack = { navController.popBackStackIfReady() }
            )
        }

        composable(route = SharedMobileAppDestination.SUPPORT_PROJECT.route) {
            SupportProjectScreen(
                onNavigateBack = { navController.popBackStackIfReady() }
            )
        }

        composable(route = SharedMobileAppDestination.FONTS.route) {
            FontsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStackIfReady() }
            )
        }

        composable(route = SharedMobileAppDestination.AI_SETTINGS.route) {
            AiSettingsScreen(
                onBackClick = { navController.popBackStackIfReady() }
            )
        }

        composable(route = SharedMobileAppDestination.SETTINGS.route) {
            SettingsScreen(
                viewModel = viewModel,
                navController = navController,
                onBackClick = { navController.popBackStackIfReady() }
            )
        }
        }

        AnimatedVisibility(
            visible = showTtsMiniBar,
            enter = slideInVertically(animationSpec = tween(200)) { it } + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = miniBarBottomPadding)
        ) {
            ReaderTtsMiniBar(
                ttsController = ttsController,
                ttsState = ttsState,
                onOpenReader = {
                    ttsState.bookId?.let { bookId ->
                        viewModel.openTtsNotificationTarget(
                            bookId = bookId,
                            sourceCfi = ttsState.sourceCfi,
                            startOffset = ttsState.startOffsetInSource.takeIf { it >= 0 },
                            chapterIndex = ttsState.chapterIndex,
                            pageIndex = ttsState.pageIndex
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
