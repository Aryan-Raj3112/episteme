// AppNavigationTest.kt
package com.aryan.reader

import android.net.Uri
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.epub.EpubBook
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var navController: TestNavHostController
    private val fakeUiState = MutableStateFlow(ReaderScreenState())

    // Mock ViewModel that uses the fake state
    private val fakeViewModel: MainViewModel = object : MainViewModel(
        ApplicationProvider.getApplicationContext()
    ) {
        override val uiState = fakeUiState
        override fun clearSelectedFile() {
            fakeUiState.value = fakeUiState.value.copy(
                selectedFileType = null,
                selectedPdfUri = null,
                selectedEpubBook = null
            )
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Before
    fun setup() {
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            AppNavigation(
                navController = navController,
                windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(400.dp, 800.dp)),
                viewModel = fakeViewModel
            )
        }
    }

    @Test
    fun appNavigation_defaultStartDestination_isMainRoute() {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        assertEquals(AppDestinations.MAIN_ROUTE, currentRoute)
    }

    @Test
    fun appNavigation_whenPdfSelected_navigatesToPdfViewer() {
        // Trigger state change
        fakeUiState.value = ReaderScreenState(
            selectedFileType = FileType.PDF,
            selectedPdfUri = Uri.parse("content://dummy.pdf")
        )

        // Let compose recompose and run LaunchedEffect
        composeTestRule.waitForIdle()

        val currentRoute = navController.currentBackStackEntry?.destination?.route
        assertEquals(AppDestinations.PDF_VIEWER_ROUTE, currentRoute)
    }

    @Test
    fun appNavigation_whenPptxSelected_navigatesToPdfViewer() {
        fakeUiState.value = ReaderScreenState(
            selectedFileType = FileType.PPTX,
            selectedPdfUri = Uri.parse("content://dummy.pptx")
        )

        composeTestRule.waitForIdle()

        val currentRoute = navController.currentBackStackEntry?.destination?.route
        assertEquals(AppDestinations.PDF_VIEWER_ROUTE, currentRoute)
    }

    @Test
    fun appNavigation_whenEpubSelected_navigatesToEpubReader() {
        // Trigger state change
        fakeUiState.value = ReaderScreenState(
            selectedFileType = FileType.EPUB,
            selectedEpubBook = EpubBook(
                fileName = "dummy.epub",
                title = "Dummy Book",
                author = "Author",
                language = "en",
                coverImage = null
            )
        )

        composeTestRule.waitForIdle()

        val currentRoute = navController.currentBackStackEntry?.destination?.route
        assertEquals(AppDestinations.EPUB_READER_ROUTE, currentRoute)
    }

    @Test
    fun appNavigation_whenFileCleared_navigatesBackToMain() {
        // First, navigate to PDF viewer
        fakeUiState.value = ReaderScreenState(
            selectedFileType = FileType.PDF,
            selectedPdfUri = Uri.parse("content://dummy.pdf")
        )
        composeTestRule.waitForIdle()
        assertEquals(AppDestinations.PDF_VIEWER_ROUTE, navController.currentBackStackEntry?.destination?.route)

        // Then, trigger the clear action (simulating onNavigateBack)
        fakeViewModel.clearSelectedFile()
        composeTestRule.waitForIdle()

        val currentRoute = navController.currentBackStackEntry?.destination?.route
        assertEquals(AppDestinations.MAIN_ROUTE, currentRoute)
    }

    @Test
    fun appNavigation_whenUnknownFileTypeSelected_navigatesBackToMain() {
        fakeUiState.value = ReaderScreenState(
            selectedFileType = FileType.PDF,
            selectedPdfUri = Uri.parse("content://dummy.pdf")
        )
        composeTestRule.waitForIdle()
        assertEquals(AppDestinations.PDF_VIEWER_ROUTE, navController.currentBackStackEntry?.destination?.route)

        fakeUiState.value = ReaderScreenState(selectedFileType = FileType.UNKNOWN)
        composeTestRule.waitForIdle()

        assertEquals(AppDestinations.MAIN_ROUTE, navController.currentBackStackEntry?.destination?.route)
    }
}
