package com.aryan.reader.paginatedreader

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.aryan.reader.SearchResult
import com.aryan.reader.epub.EpubBook
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private class FakePaginator(
    initiallyLoading: Boolean,
    initialPageCount: Int,
    initialGeneration: Int
) : IPaginator {
    override var isLoading by mutableStateOf(initiallyLoading)
    override var totalPageCount by mutableIntStateOf(initialPageCount)
    override var generation by mutableIntStateOf(initialGeneration)
    override val pageShiftRequest: Flow<Int> = emptyFlow()

    var lastNavigatedHref: String? = null
    var lastNavigatedChapter: String? = null

    override fun getPageContent(pageIndex: Int): Page? = null
    override fun getChapterPathForPage(pageIndex: Int): String? = null
    override fun getPlainTextForChapter(chapterIndex: Int): String? = null

    override fun navigateToHref(
        currentChapterAbsPath: String,
        href: String,
        onNavigationComplete: (pageIndex: Int) -> Unit
    ) {
        lastNavigatedChapter = currentChapterAbsPath
        lastNavigatedHref = href
    }

    override fun findPageForSearchResult(
        result: SearchResult,
        onResult: (pageIndex: Int) -> Unit
    ) = Unit

    override fun findPageForAnchor(
        chapterIndex: Int,
        anchor: String?,
        onResult: (pageIndex: Int) -> Unit
    ) = Unit

    override fun findPageForCfi(chapterIndex: Int, cfi: String, onResult: (pageIndex: Int) -> Unit) = Unit
    override fun findPageForCfiAndOffset(chapterIndex: Int, cfi: String, charOffset: Int): Int? = null
    override fun findChapterIndexForPage(pageIndex: Int): Int? = null
    override fun getCfiForPage(pageIndex: Int): String? = null
    override fun onUserScrolledTo(pageIndex: Int) = Unit
    override fun getActiveAnchorForPage(pageIndex: Int, tocAnchors: List<String>): String? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
class PaginatedReaderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: PaginatedReaderViewModel
    private lateinit var fakePaginator: FakePaginator

    @Before
    fun setUp() {
        viewModel = PaginatedReaderViewModel()
        fakePaginator = FakePaginator(
            initiallyLoading = true,
            initialPageCount = 0,
            initialGeneration = 0
        )
        viewModel.setPaginatorForTest(fakePaginator)
    }

    @Test
    fun uiState_reflectsPaginatorInitialState() = runTest {
        val initialState = viewModel.uiState.value
        assertThat(initialState.isLoading).isTrue()
        assertThat(initialState.totalPageCount).isEqualTo(0)
        assertThat(initialState.generation).isEqualTo(0)
    }

    @Test
    fun uiState_updatesWhenPaginatorIsLoadingChanges() = runTest {
        assertThat(viewModel.uiState.value.isLoading).isTrue()

        fakePaginator.isLoading = false
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun uiState_updatesWhenPaginatorTotalPageCountChanges() = runTest {
        assertThat(viewModel.uiState.value.totalPageCount).isEqualTo(0)

        fakePaginator.totalPageCount = 123
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.totalPageCount).isEqualTo(123)
    }

    @Test
    fun uiState_updatesWhenPaginatorGenerationChanges() = runTest {
        assertThat(viewModel.uiState.value.generation).isEqualTo(0)

        fakePaginator.generation = 5
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.generation).isEqualTo(5)
    }

    @Test
    fun onLinkClick_callsPaginatorNavigateToHrefWithCorrectArguments() {
        val currentChapter = "chapter1.xhtml"
        val href = "#section2"

        viewModel.onLinkClick(currentChapter, href) {}

        assertThat(fakePaginator.lastNavigatedChapter).isEqualTo(currentChapter)
        assertThat(fakePaginator.lastNavigatedHref).isEqualTo(href)
    }

    @Test
    fun initialize_whenPaginatorAlreadySet_keepsExistingPaginator() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val existingPaginator = viewModel.paginator

        viewModel.initialize(
            book = EpubBook(
                fileName = "test.epub",
                title = "Test Book",
                author = "Test Author",
                language = "en",
                coverImage = null
            ),
            textMeasurer = mockk<TextMeasurer>(relaxed = true),
            textConstraints = Constraints(maxWidth = 1080, maxHeight = 1920),
            textStyle = TextStyle.Default,
            density = Density(1f),
            isDarkTheme = false,
            themeBackgroundColor = Color.White,
            themeTextColor = Color.Black,
            context = context,
            initialChapterToPaginate = 0,
            mathMLRenderer = mockk<MathMLRenderer>(relaxed = true),
            paragraphGapMultiplier = 1.0f
        )
        advanceUntilIdle()

        assertThat(viewModel.paginator).isSameInstanceAs(existingPaginator)
    }
}
