package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderMarkdownBlock
import com.aryan.reader.shared.ReaderMarkdownParser

@Composable
fun SharedMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    /**
     * Android parity (AiDefinitionPopup / AiResultContentView TTS highlight):
     * currently-spoken chunk. Matching ranges get the primaryContainer
     * background and are brought into view while TTS plays.
     */
    highlightText: String? = null
) {
    val document = remember(markdown) { ReaderMarkdownParser.parse(markdown) }
    val colorScheme = MaterialTheme.colorScheme
    val highlight = highlightText?.takeIf { it.isNotBlank() }
    val highlightColor = colorScheme.primaryContainer
    val inlineCodeBackground = colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val inlineLinkColor = colorScheme.primary
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        document.blocks.forEachIndexed { index, block ->
            when (block) {
                is ReaderMarkdownBlock.Heading -> {
                    val headingStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    val annotated = remember(block.text, highlight, inlineCodeBackground, inlineLinkColor) {
                        block.text.toInlineAnnotatedString(inlineCodeBackground, inlineLinkColor)
                            .withHighlight(highlight, highlightColor)
                    }
                    val contains = highlight != null &&
                        annotated.text.contains(highlight, ignoreCase = false)
                    val requester = remember(index) { BringIntoViewRequester() }
                    if (contains) {
                        LaunchedEffect(highlight) { requester.bringIntoView() }
                    }
                    Text(
                        text = annotated,
                        style = headingStyle,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.bringIntoViewRequester(requester)
                    )
                }

                is ReaderMarkdownBlock.Paragraph -> {
                    val annotated = remember(block.text, highlight, inlineCodeBackground, inlineLinkColor) {
                        block.text.toInlineAnnotatedString(inlineCodeBackground, inlineLinkColor)
                            .withHighlight(highlight, highlightColor)
                    }
                    val contains = highlight != null && annotated.text.contains(highlight)
                    val requester = remember(index) { BringIntoViewRequester() }
                    if (contains) {
                        LaunchedEffect(highlight) { requester.bringIntoView() }
                    }
                    Text(
                        text = annotated,
                        style = style,
                        modifier = Modifier.bringIntoViewRequester(requester)
                    )
                }

                is ReaderMarkdownBlock.Quote -> {
                    val annotated = remember(block.text, highlight, inlineCodeBackground, inlineLinkColor) {
                        block.text.toInlineAnnotatedString(inlineCodeBackground, inlineLinkColor)
                            .withHighlight(highlight, highlightColor)
                    }
                    val contains = highlight != null && annotated.text.contains(highlight)
                    val requester = remember(index) { BringIntoViewRequester() }
                    if (contains) {
                        LaunchedEffect(highlight) { requester.bringIntoView() }
                    }
                    Text(
                        text = annotated,
                        style = style,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                            .bringIntoViewRequester(requester)
                    )
                }

                is ReaderMarkdownBlock.CodeBlock -> {
                    val contains = highlight != null && block.text.contains(highlight)
                    val requester = remember(index) { BringIntoViewRequester() }
                    if (contains) {
                        LaunchedEffect(highlight) { requester.bringIntoView() }
                    }
                    Surface(
                        color = colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requester)
                    ) {
                        Text(
                            text = block.text,
                            style = style.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                is ReaderMarkdownBlock.ListItems -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEachIndexed { itemIndex, item ->
                            val annotated = remember(item, highlight, inlineCodeBackground, inlineLinkColor) {
                                item.toInlineAnnotatedString(inlineCodeBackground, inlineLinkColor)
                                    .withHighlight(highlight, highlightColor)
                            }
                            val contains = highlight != null && annotated.text.contains(highlight)
                            val requester = remember(index, itemIndex) { BringIntoViewRequester() }
                            if (contains) {
                                LaunchedEffect(highlight) { requester.bringIntoView() }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.bringIntoViewRequester(requester)
                            ) {
                                Text(
                                    text = if (block.ordered) "${itemIndex + 1}." else "-",
                                    style = style,
                                    color = colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = annotated,
                                    style = style,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
        if (document.blocks.isEmpty() && markdown.isNotBlank()) {
            Text(text = markdown, style = style)
        }
    }
}

private fun AnnotatedString.withHighlight(highlight: String?, color: Color): AnnotatedString {
    if (highlight.isNullOrBlank()) return this
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (true) {
        val found = text.indexOf(highlight, from)
        if (found < 0) break
        ranges += found until found + highlight.length
        from = found + highlight.length
        if (from >= text.length) break
    }
    if (ranges.isEmpty()) return this
    return buildAnnotatedString {
        append(this@withHighlight)
        ranges.forEach { range -> addStyle(SpanStyle(background = color), range.first, range.last + 1) }
    }
}

private fun String.toInlineAnnotatedString(codeBackground: Color, linkColor: Color): AnnotatedString {
    return buildAnnotatedString {
        appendMarkdownInline(
            text = this@toInlineAnnotatedString,
            codeStyle = SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = codeBackground
            ),
            linkStyle = SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline
            )
        )
    }
}

private fun AnnotatedString.Builder.appendMarkdownInline(
    text: String,
    codeStyle: SpanStyle,
    linkStyle: SpanStyle
) {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("`", index) -> {
                val end = text.indexOf('`', startIndex = index + 1)
                if (end > index) {
                    withStyle(codeStyle) { append(text.substring(index + 1, end)) }
                    index = end + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }

            text.startsWith("**", index) -> {
                val end = text.indexOf("**", startIndex = index + 2)
                if (end > index) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendMarkdownInline(text.substring(index + 2, end), codeStyle, linkStyle)
                    }
                    index = end + 2
                } else {
                    append(text[index])
                    index += 1
                }
            }

            text.startsWith("*", index) -> {
                val end = text.indexOf('*', startIndex = index + 1)
                if (end > index) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendMarkdownInline(text.substring(index + 1, end), codeStyle, linkStyle)
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }

            text[index] == '[' -> {
                val labelEnd = text.indexOf("](", startIndex = index + 1)
                val urlEnd = if (labelEnd > index) text.indexOf(')', startIndex = labelEnd + 2) else -1
                if (labelEnd > index && urlEnd > labelEnd) {
                    withStyle(linkStyle) {
                        appendMarkdownInline(text.substring(index + 1, labelEnd), codeStyle, linkStyle)
                    }
                    index = urlEnd + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }

            else -> {
                append(text[index])
                index += 1
            }
        }
    }
}
