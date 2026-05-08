package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    style: TextStyle = MaterialTheme.typography.bodySmall
) {
    val document = remember(markdown) { ReaderMarkdownParser.parse(markdown) }
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        document.blocks.forEachIndexed { index, block ->
            when (block) {
                is ReaderMarkdownBlock.Heading -> {
                    val headingStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        text = block.text.markdownInlineAnnotatedString(),
                        style = headingStyle,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                is ReaderMarkdownBlock.Paragraph -> {
                    Text(text = block.text.markdownInlineAnnotatedString(), style = style)
                }

                is ReaderMarkdownBlock.Quote -> {
                    Text(
                        text = block.text.markdownInlineAnnotatedString(),
                        style = style,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    )
                }

                is ReaderMarkdownBlock.CodeBlock -> {
                    Surface(
                        color = colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
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
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (block.ordered) "${itemIndex + 1}." else "-",
                                    style = style,
                                    color = colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = item.markdownInlineAnnotatedString(),
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

@Composable
private fun String.markdownInlineAnnotatedString(): AnnotatedString {
    val colorScheme = MaterialTheme.colorScheme
    return remember(this, colorScheme.primary, colorScheme.surfaceVariant) {
        buildAnnotatedString {
            appendMarkdownInline(
                text = this@markdownInlineAnnotatedString,
                codeStyle = SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                linkStyle = SpanStyle(
                    color = colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
            )
        }
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
