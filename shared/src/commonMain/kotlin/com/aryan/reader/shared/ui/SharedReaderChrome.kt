package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderHtmlDocumentBuilder
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.SharedReaderTextAlign

@Composable
fun SharedScreenScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
        content()
    }
}

@Composable
fun SharedReaderScreen(
    session: ReaderSessionState,
    readerEngine: ReaderEngine,
    onSessionChange: (ReaderSessionState) -> Unit,
    onOpenEpub: () -> Unit,
    onOpenPdf: () -> Unit,
    readerContent: @Composable ColumnScope.(html: String, background: Color) -> Unit
) {
    val readerState = session.reader
    val page = readerState.currentPage
    val settings = readerState.settings
    val background = if (settings.darkMode) Color(0xFF171A17) else Color(0xFFFFFCF5)

    SharedScreenScaffold(
        title = readerState.book.title,
        subtitle = listOfNotNull(readerState.book.author, page?.chapterTitle).joinToString(" - "),
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenEpub) {
                    Text("Open EPUB")
                }
                TextButton(onClick = onOpenPdf) {
                    Text("Open PDF")
                }
                Text("${readerState.progress.toInt()}%")
                IconButton(onClick = { onSessionChange(readerEngine.toggleBookmark(session)) }) {
                    Icon(
                        if (session.currentBookmark == null) Icons.Default.BookmarkBorder else Icons.Default.Bookmark,
                        contentDescription = "Bookmark"
                    )
                }
                TextButton(
                    onClick = {
                        onSessionChange(session.copy(reader = readerState.copy(settings = settings.copy(darkMode = !settings.darkMode))))
                    }
                ) {
                    Text(if (settings.darkMode) "Light" else "Dark")
                }
            }
        }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.DirectionRight || event.key == Key.PageDown -> {
                            onSessionChange(readerEngine.next(session))
                            true
                        }

                        event.key == Key.DirectionLeft || event.key == Key.PageUp -> {
                            onSessionChange(readerEngine.previous(session))
                            true
                        }

                        event.key == Key.MoveHome -> {
                            onSessionChange(readerEngine.goToPage(session, 0))
                            true
                        }

                        event.key == Key.MoveEnd -> {
                            onSessionChange(readerEngine.goToPage(session, readerState.pages.lastIndex))
                            true
                        }

                        event.isCtrlPressed && event.key == Key.G -> {
                            onSessionChange(readerEngine.nextSearchResult(session))
                            true
                        }

                        else -> false
                    }
                }
                .focusable()
        ) {
            SharedReaderSidebar(
                session = session,
                onSearchChange = { onSessionChange(readerEngine.search(session, it)) },
                onPreviousSearchResult = { onSessionChange(readerEngine.previousSearchResult(session)) },
                onNextSearchResult = { onSessionChange(readerEngine.nextSearchResult(session)) },
                onGoToChapter = { onSessionChange(readerEngine.goToChapter(session, it)) },
                onGoToPage = { onSessionChange(readerEngine.goToPage(session, it)) }
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SharedReaderSettingsBar(
                    session = session,
                    readerEngine = readerEngine,
                    onSessionChange = onSessionChange
                )

                val html = if (settings.readingMode == ReaderReadingMode.VERTICAL) {
                    ReaderHtmlDocumentBuilder.verticalDocument(
                        book = readerState.book,
                        settings = settings,
                        searchQuery = session.searchQuery
                    )
                } else {
                    ReaderHtmlDocumentBuilder.pageDocument(
                        book = readerState.book,
                        page = page,
                        settings = settings,
                        searchQuery = session.searchQuery
                    )
                }
                readerContent(html, background)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slider(
                        value = if (readerState.pages.size <= 1) 0f else readerState.currentPageIndex.toFloat() / readerState.pages.lastIndex,
                        onValueChange = { progress -> onSessionChange(readerEngine.goToProgress(session, progress)) },
                        enabled = readerState.pages.size > 1
                    )
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            enabled = readerState.canGoPrevious,
                            onClick = { onSessionChange(readerEngine.previous(session)) }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null)
                            Text("Previous")
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (settings.readingMode == ReaderReadingMode.VERTICAL) {
                                "Continuous mode - page ${readerState.currentPageIndex + 1} of ${readerState.pages.size}"
                            } else {
                                "Page ${readerState.currentPageIndex + 1} of ${readerState.pages.size}"
                            }
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            enabled = readerState.canGoNext,
                            onClick = { onSessionChange(readerEngine.next(session)) }
                        ) {
                            Text("Next")
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedReaderSettingsBar(
    session: ReaderSessionState,
    readerEngine: ReaderEngine,
    onSessionChange: (ReaderSessionState) -> Unit
) {
    val settings = session.reader.settings
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = settings.readingMode == ReaderReadingMode.PAGINATED,
                onClick = {
                    onSessionChange(readerEngine.updateSettings(session, settings.copy(readingMode = ReaderReadingMode.PAGINATED)))
                },
                label = { Text("Pages") }
            )
            FilterChip(
                selected = settings.readingMode == ReaderReadingMode.VERTICAL,
                onClick = {
                    onSessionChange(readerEngine.updateSettings(session, settings.copy(readingMode = ReaderReadingMode.VERTICAL)))
                },
                label = { Text("Vertical") }
            )
            FilterChip(
                selected = settings.textAlign == SharedReaderTextAlign.START,
                onClick = { onSessionChange(readerEngine.updateSettings(session, settings.copy(textAlign = SharedReaderTextAlign.START))) },
                label = { Text("Left") }
            )
            FilterChip(
                selected = settings.textAlign == SharedReaderTextAlign.JUSTIFY,
                onClick = { onSessionChange(readerEngine.updateSettings(session, settings.copy(textAlign = SharedReaderTextAlign.JUSTIFY))) },
                label = { Text("Justify") }
            )
            FilterChip(
                selected = settings.textAlign == SharedReaderTextAlign.CENTER,
                onClick = { onSessionChange(readerEngine.updateSettings(session, settings.copy(textAlign = SharedReaderTextAlign.CENTER))) },
                label = { Text("Center") }
            )
            listOf("Default", "Serif", "Sans", "Mono").forEach { family ->
                FilterChip(
                    selected = settings.fontFamily == family,
                    onClick = { onSessionChange(readerEngine.updateSettings(session, settings.copy(fontFamily = family))) },
                    label = { Text(family) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Font ${settings.fontSize}")
            Slider(
                value = settings.fontSize.toFloat(),
                onValueChange = { value ->
                    onSessionChange(readerEngine.updateSettings(session, settings.copy(fontSize = value.toInt())))
                },
                valueRange = 14f..30f,
                modifier = Modifier.width(140.dp)
            )
            Text("Margin ${settings.margin}")
            Slider(
                value = settings.margin.toFloat(),
                onValueChange = { value ->
                    onSessionChange(readerEngine.updateSettings(session, settings.copy(margin = value.toInt())))
                },
                valueRange = 16f..112f,
                modifier = Modifier.width(140.dp)
            )
            Text("Spacing ${settings.lineSpacing.formatTwoDecimals()}")
            Slider(
                value = settings.lineSpacing,
                onValueChange = { value ->
                    onSessionChange(readerEngine.updateSettings(session, settings.copy(lineSpacing = value)))
                },
                valueRange = 1.1f..2.1f,
                modifier = Modifier.width(140.dp)
            )
            Text("Width ${settings.pageWidth}")
            Slider(
                value = settings.pageWidth.toFloat(),
                onValueChange = { value ->
                    onSessionChange(readerEngine.updateSettings(session, settings.copy(pageWidth = value.toInt())))
                },
                valueRange = 520f..1100f,
                modifier = Modifier.width(140.dp)
            )
        }
    }
}

@Composable
private fun SharedReaderSidebar(
    session: ReaderSessionState,
    onSearchChange: (String) -> Unit,
    onPreviousSearchResult: () -> Unit,
    onNextSearchResult: () -> Unit,
    onGoToChapter: (Int) -> Unit,
    onGoToPage: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        LazyColumn(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Contents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(session.reader.book.chapters.indices.toList()) { index ->
                val chapter = session.reader.book.chapters[index]
                val selected = session.reader.currentPage?.chapterIndex == index
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onGoToChapter(index) }
                ) {
                    Text(
                        chapter.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Bookmarks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (session.bookmarks.isEmpty()) {
                item {
                    Text("No bookmarks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(session.bookmarks, key = { it.id }) { bookmark ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onGoToPage(bookmark.pageIndex) }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth()
                        ) {
                            Text(bookmark.chapterTitle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(bookmark.preview, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Search", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = session.searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text("Find in book") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (session.searchQuery.isNotBlank() && session.searchResults.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${session.activeSearchResultIndex + 1} of ${session.searchResults.size}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onPreviousSearchResult) {
                            Text("Prev")
                        }
                        TextButton(onClick = onNextSearchResult) {
                            Text("Next")
                        }
                    }
                }
            }
            if (session.searchQuery.isNotBlank() && session.searchResults.isEmpty()) {
                item {
                    Text("No matches", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(session.searchResults, key = { "${it.pageIndex}_${it.preview}" }) { result ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onGoToPage(result.pageIndex) }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Page ${result.pageIndex + 1} - ${result.chapterTitle}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(result.preview, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun Float.formatTwoDecimals(): String {
    val scaled = (this * 100).toInt()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}
