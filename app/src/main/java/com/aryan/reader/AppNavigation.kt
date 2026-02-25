/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
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
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader

import android.os.Build
import timber.log.Timber
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.aryan.reader.epubreader.EpubReaderScreen
import com.aryan.reader.feedback.FeedbackScreen
import com.aryan.reader.pdf.PdfViewerScreen

object AppDestinations {
    const val MAIN_ROUTE = "main"
    const val PDF_VIEWER_ROUTE = "pdf_viewer"
    const val EPUB_READER_ROUTE = "epub_reader"
    const val PRO_SCREEN_ROUTE = "pro_screen"
    const val FEEDBACK_SCREEN_ROUTE = "feedback_screen_route"
    const val FONTS_SCREEN_ROUTE = "fonts_screen_route"
}

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

    NavHost(navController = navController, startDestination = AppDestinations.MAIN_ROUTE) {
        composable(AppDestinations.MAIN_ROUTE) {
            Timber.d("Navigating to Main Screen (${AppDestinations.MAIN_ROUTE}).")
            MainScreen(
                viewModel = viewModel,
                windowSizeClass = windowSizeClass,
                navController = navController
            )

            LaunchedEffect(uiState.selectedFileType, uiState.isLoading, uiState.selectedEpubBook, uiState.selectedPdfUri) {
                if (!uiState.isLoading) {
                    when (uiState.selectedFileType) {
                        FileType.PDF -> {
                            if (uiState.selectedPdfUri != null) {
                                Timber.d("Navigating to PDF Viewer. Route: ${AppDestinations.PDF_VIEWER_ROUTE}")
                                if (navController.currentDestination?.route != AppDestinations.PDF_VIEWER_ROUTE) {
                                    navController.navigate(AppDestinations.PDF_VIEWER_ROUTE)
                                }
                            }
                        }
                        FileType.EPUB, FileType.MOBI, FileType.MD, FileType.TXT, FileType.HTML -> {
                            if (uiState.selectedEpubBook != null) {
                                Timber.d("Navigating to EPUB Reader for ${uiState.selectedFileType}. Route: ${AppDestinations.EPUB_READER_ROUTE}")
                                if (navController.currentDestination?.route != AppDestinations.EPUB_READER_ROUTE) {
                                    navController.navigate(AppDestinations.EPUB_READER_ROUTE)
                                }
                            } else if (uiState.selectedEpubUri != null && uiState.errorMessage == null) {
                                Timber.d("${uiState.selectedFileType} selected, waiting for parsing/loading before navigation.")
                            } else if (uiState.errorMessage != null) {
                                Timber.w("${uiState.selectedFileType} loading failed, staying on Home. Error: ${uiState.errorMessage}")
                            }
                        }
                        null -> {
                            if (navController.currentDestination?.route != AppDestinations.MAIN_ROUTE) {
                                Timber.d("File cleared, ensuring navigation back to Main Screen.")
                                navController.popBackStack(AppDestinations.MAIN_ROUTE, inclusive = false)
                            }
                        }
                    }
                }
            }
        }

        // PDF Viewer Screen Composable
        composable(route = AppDestinations.PDF_VIEWER_ROUTE) {
            Timber.d("Navigating to PDF Viewer Screen (${AppDestinations.PDF_VIEWER_ROUTE}).")
            val pdfUri = uiState.selectedPdfUri
            val initialPage = uiState.initialPageInBook
            val initialBookmarksJson = uiState.initialBookmarksJson

            val bookId = uiState.recentFiles.find { it.uriString == uiState.selectedPdfUri.toString() }?.bookId

            if (pdfUri != null) {
                Timber.i("Displaying PDF Viewer for URI: $pdfUri, initialPage: $initialPage")
                PdfViewerScreen(
                    pdfUri = pdfUri,
                    initialPage = initialPage,
                    initialBookmarksJson = initialBookmarksJson,
                    isProUser = uiState.isProUser,
                    pendingSyncUpdate = uiState.pendingSyncUpdate?.takeIf { it.bookId == bookId },
                    onClearPendingSyncUpdate = viewModel::clearPendingSyncUpdate,
                    onNavigateBack = {
                        Timber.d("Back action triggered from PDF Viewer.")
                        viewModel.clearSelectedFile()
                    },
                    onSavePosition = viewModel::savePdfReadingPosition,
                    onBookmarksChanged = { bookmarksJson ->
                        if (bookId != null) {
                            viewModel.saveBookmarks(bookId, bookmarksJson)
                        } else {
                            Timber.w("Could not find bookId to save PDF bookmarks for URI: ${uiState.selectedPdfUri}")
                        }
                    },
                    onNavigateToPro = {
                        navController.navigate(AppDestinations.PRO_SCREEN_ROUTE)
                    },
                    viewModel = viewModel
                )
            } else {
                Timber.w("PDF URI is null in ViewModel state while on PDF screen. Navigating back to Main.")
                LaunchedEffect(Unit) {
                    navController.popBackStack(AppDestinations.MAIN_ROUTE, inclusive = false)
                }
            }
        }

        // EPUB Reader Screen Composable
        composable(route = AppDestinations.EPUB_READER_ROUTE) {
            Timber.d("Navigating to EPUB Reader Screen (${AppDestinations.EPUB_READER_ROUTE}).")
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
                    val bookId = uiState.recentFiles.find { it.uriString == uiState.selectedEpubUri.toString() }?.bookId
                    val customFonts by viewModel.customFonts.collectAsStateWithLifecycle()

                    EpubReaderScreen(
                        epubBook = epubBook,
                        renderMode = renderMode,
                        initialLocator = initialLocator,
                        initialCfi = initialCfi,
                        initialBookmarksJson = initialBookmarksJson,
                        isProUser = uiState.isProUser,
                        coverImagePath = coverPath,
                        pendingSyncUpdate = uiState.pendingSyncUpdate?.takeIf { it.bookId == bookId },
                        onClearPendingSyncUpdate = viewModel::clearPendingSyncUpdate,
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
                            navController.navigate(AppDestinations.PRO_SCREEN_ROUTE)
                        },
                        onRenderModeChange = viewModel::setRenderMode,
                        customFonts = customFonts,
                        onImportFont = viewModel::importFont,
                    )
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
                        Text("Error: $errorMessage", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.clearSelectedFile()
                        }) {
                            Text("Go Back")
                        }
                    }
                }
                else -> {
                    Timber.w("EPUB Book is null and not loading/error state on EPUB screen. Navigating back.")
                    LaunchedEffect(Unit) {
                        navController.popBackStack(AppDestinations.MAIN_ROUTE, inclusive = false)
                    }
                }
            }
        }
        composable(route = AppDestinations.PRO_SCREEN_ROUTE) {
            ProScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(route = AppDestinations.FEEDBACK_SCREEN_ROUTE) {
            FeedbackScreen(
                navController = navController
            )
        }

        composable(route = AppDestinations.FONTS_SCREEN_ROUTE) {
            FontsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}