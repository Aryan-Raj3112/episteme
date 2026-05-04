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
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
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
import kotlinx.coroutines.delay

object AppDestinations {
    const val MAIN_ROUTE = "main"
    const val PDF_VIEWER_ROUTE = "pdf_viewer"
    const val EPUB_READER_ROUTE = "epub_reader"
    const val PRO_SCREEN_ROUTE = "pro_screen"
    const val FEEDBACK_SCREEN_ROUTE = "feedback_screen_route"
    const val SUPPORT_PROJECT_SCREEN_ROUTE = "support_project_screen_route"
    const val FONTS_SCREEN_ROUTE = "fonts_screen_route"
    const val AI_SETTINGS_SCREEN_ROUTE = "ai_settings_screen_route"
}

private fun NavHostController.isReadyForBackStackChange(): Boolean {
    return currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED
}

private suspend fun NavHostController.awaitReadyForBackStackChange() {
    while (!isReadyForBackStackChange()) {
        delay(32)
    }
}

private fun NavHostController.navigateSingleTopTo(route: String) {
    if (!isReadyForBackStackChange()) {
        Timber.d("Skipping navigation to $route because the current entry is not resumed yet.")
        return
    }

    try {
        navigate(route) {
            launchSingleTop = true
            popUpTo(graph.startDestinationId) {
                saveState = false
            }
        }
    } catch (e: IllegalStateException) {
        Timber.w(e, "Navigation to $route ignored because the back stack is mid-transition.")
    }
}

private fun NavHostController.navigateToMain() {
    navigateSingleTopTo(AppDestinations.MAIN_ROUTE)
}

private fun NavHostController.navigateIfReady(route: String) {
    if (currentDestination?.route == route) return
    navigateSingleTopTo(route)
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

private suspend fun NavHostController.syncRouteTo(route: String) {
    awaitReadyForBackStackChange()
    if (currentDestination?.route != route) {
        if (route == AppDestinations.MAIN_ROUTE) {
            navigateToMain()
        } else {
            navigateSingleTopTo(route)
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

    LaunchedEffect(currentRoute, uiState.selectedFileType, uiState.isLoading, uiState.selectedEpubBook, uiState.selectedPdfUri) {
        if (!uiState.isLoading) {
            when (uiState.selectedFileType) {
                FileType.PDF, FileType.CBZ, FileType.CBR, FileType.CB7 -> {
                    if (uiState.selectedPdfUri != null) {
                        if (currentRoute != AppDestinations.PDF_VIEWER_ROUTE) {
                            navController.syncRouteTo(AppDestinations.PDF_VIEWER_ROUTE)
                        }
                    }
                }
                FileType.EPUB, FileType.MOBI, FileType.MD, FileType.TXT, FileType.HTML, FileType.FB2, FileType.DOCX, FileType.ODT, FileType.FODT -> {
                    if (uiState.selectedEpubBook != null) {
                        if (currentRoute != AppDestinations.EPUB_READER_ROUTE) {
                            navController.syncRouteTo(AppDestinations.EPUB_READER_ROUTE)
                        }
                    }
                }
                null -> {
                    if (currentRoute == AppDestinations.PDF_VIEWER_ROUTE || currentRoute == AppDestinations.EPUB_READER_ROUTE) {
                        navController.syncRouteTo(AppDestinations.MAIN_ROUTE)
                    }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = AppDestinations.MAIN_ROUTE) {
        composable(AppDestinations.MAIN_ROUTE) {
            Timber.d("Navigating to Main Screen (${AppDestinations.MAIN_ROUTE}).")
            MainScreen(
                viewModel = viewModel,
                windowSizeClass = windowSizeClass,
                navController = navController
            )
        }

        // PDF Viewer Screen Composable
        composable(route = AppDestinations.PDF_VIEWER_ROUTE) {
            Timber.d("Navigating to PDF Viewer Screen (${AppDestinations.PDF_VIEWER_ROUTE}).")
            val pdfUri = uiState.selectedPdfUri
            val initialPage = uiState.initialPageInBook
            val initialBookmarksJson = uiState.initialBookmarksJson

            val bookId =
                uiState.recentFiles.find { it.uriString == uiState.selectedPdfUri.toString() }?.bookId

            if (pdfUri != null) {
                Timber.i("Displaying PDF Viewer for URI: $pdfUri, initialPage: $initialPage")
                Box(modifier = Modifier.fillMaxSize()) {
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
                                viewModel.saveBookmarks(bookId, bookmarksJson)
                            } else {
                                Timber.w("Could not find bookId to save PDF bookmarks for URI: ${uiState.selectedPdfUri}")
                            }
                        },
                        onNavigateToPro = {
                            navController.navigateIfReady(AppDestinations.PRO_SCREEN_ROUTE)
                        },
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
                                navController.navigateIfReady(AppDestinations.PRO_SCREEN_ROUTE)
                            },
                            onRenderModeChange = viewModel::setRenderMode,
                            customFonts = customFonts,
                            onImportFont = viewModel::importFont,
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
        composable(route = AppDestinations.PRO_SCREEN_ROUTE) {
            ProScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStackIfReady() }
            )
        }

        composable(route = AppDestinations.FEEDBACK_SCREEN_ROUTE) {
            FeedbackScreen(
                navController = navController
            )
        }

        composable(route = AppDestinations.SUPPORT_PROJECT_SCREEN_ROUTE) {
            SupportProjectScreen(
                navController = navController
            )
        }

        composable(route = AppDestinations.FONTS_SCREEN_ROUTE) {
            FontsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStackIfReady() }
            )
        }

        composable(route = AppDestinations.AI_SETTINGS_SCREEN_ROUTE) {
            AiSettingsScreen(
                onBackClick = { navController.popBackStackIfReady() }
            )
        }
    }
}
