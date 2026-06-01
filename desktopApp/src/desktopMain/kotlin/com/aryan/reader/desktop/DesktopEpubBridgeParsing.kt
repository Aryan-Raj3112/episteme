package com.aryan.reader.desktop

import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.toStableReaderPositionCfi
import com.aryan.reader.shared.ui.SharedNativeReaderLinkClick
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.event.KeyEvent as AwtKeyEvent
import java.net.URLDecoder

internal data class DesktopReaderPosition(
    val pageIndex: Int,
    val locator: ReaderLocator?
)

internal data class DesktopReaderHighlightClick(
    val highlightId: String
)

internal data class DesktopEpubLinkClick(
    val href: String,
    val chapterIndex: Int?,
    val text: String? = null,
    val chapterId: String? = null,
    val chapterHref: String? = null,
    val source: String = "bridge"
)

internal fun SharedNativeReaderLinkClick.toDesktopEpubLinkClick(): DesktopEpubLinkClick {
    return DesktopEpubLinkClick(
        href = href,
        chapterIndex = chapterIndex,
        text = text,
        source = "native"
    )
}

internal data class DesktopEpubHandledLink(
    val href: String,
    val handledAtMs: Long
)

internal enum class DesktopReaderSelectionAction {
    DEFINE,
    SPEAK,
    SEARCH,
    PALETTE
}

internal enum class DesktopReaderKeyNavigation {
    NEXT,
    PREVIOUS,
    FIRST,
    LAST,
    SEARCH,
    NEXT_SEARCH,
    EXIT_FULLSCREEN
}

internal fun AwtKeyEvent.desktopReaderKeyNavigationOrNull(
    fullscreen: Boolean,
    rightToLeftPagination: Boolean = false
): DesktopReaderKeyNavigation? {
    if (id != AwtKeyEvent.KEY_PRESSED) return null
    if (fullscreen && keyCode == AwtKeyEvent.VK_ESCAPE) {
        return DesktopReaderKeyNavigation.EXIT_FULLSCREEN
    }
    if (isControlDown && keyCode == AwtKeyEvent.VK_F) {
        return DesktopReaderKeyNavigation.SEARCH
    }
    if (isControlDown && keyCode == AwtKeyEvent.VK_G) {
        return DesktopReaderKeyNavigation.NEXT_SEARCH
    }
    return when (keyCode) {
        AwtKeyEvent.VK_RIGHT -> if (rightToLeftPagination) {
            DesktopReaderKeyNavigation.PREVIOUS
        } else {
            DesktopReaderKeyNavigation.NEXT
        }
        AwtKeyEvent.VK_LEFT -> if (rightToLeftPagination) {
            DesktopReaderKeyNavigation.NEXT
        } else {
            DesktopReaderKeyNavigation.PREVIOUS
        }
        AwtKeyEvent.VK_PAGE_DOWN -> DesktopReaderKeyNavigation.NEXT
        AwtKeyEvent.VK_PAGE_UP -> DesktopReaderKeyNavigation.PREVIOUS
        AwtKeyEvent.VK_HOME -> DesktopReaderKeyNavigation.FIRST
        AwtKeyEvent.VK_END -> DesktopReaderKeyNavigation.LAST
        else -> null
    }
}

internal data class DesktopReaderSelectionActionPayload(
    val action: DesktopReaderSelectionAction,
    val text: String,
    val locator: ReaderLocator? = null
)

internal fun String.readerHighlightClickOrNull(): DesktopReaderHighlightClick? {
    fun parse(rawJson: String): DesktopReaderHighlightClick? = runCatching {
        val obj = Json.parseToJsonElement(rawJson).jsonObject
        val highlightId = obj["id"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: obj["highlightId"]
                ?.takeUnless { it is JsonNull }
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        DesktopReaderHighlightClick(highlightId)
    }.getOrNull()

    parse(this)?.let { return it }
    return runCatching {
        Json.parseToJsonElement(this).jsonPrimitive.contentOrNull
    }.getOrNull()?.let { parse(it) }
}

internal fun String.readerSelectionActionOrNull(): DesktopReaderSelectionActionPayload? {
    fun parse(rawJson: String): DesktopReaderSelectionActionPayload? = runCatching {
        val obj = Json.parseToJsonElement(rawJson).jsonObject
        val text = obj["text"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        val action = when (
            obj["action"]
                ?.takeUnless { it is JsonNull }
                ?.jsonPrimitive
                ?.contentOrNull
                ?.lowercase()
        ) {
            "define" -> DesktopReaderSelectionAction.DEFINE
            "speak" -> DesktopReaderSelectionAction.SPEAK
            "web-search", "search" -> DesktopReaderSelectionAction.SEARCH
            "palette" -> DesktopReaderSelectionAction.PALETTE
            else -> return@runCatching null
        }
        val locator = obj["locator"]
            ?.takeUnless { it is JsonNull }
            ?.jsonObject
            ?.let { locatorObj ->
                ReaderLocator(
                    chapterIndex = locatorObj["chapterIndex"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
                    chapterId = locatorObj["chapterId"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull,
                    href = locatorObj["href"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull,
                    pageIndex = locatorObj["pageIndex"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
                    startOffset = locatorObj["startOffset"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
                    endOffset = locatorObj["endOffset"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
                    blockIndex = locatorObj["blockIndex"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
                    charOffset = locatorObj["charOffset"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
                    textQuote = locatorObj["textQuote"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull,
                    cfi = locatorObj["cfi"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull?.toStableReaderPositionCfi()
                )
            }
        DesktopReaderSelectionActionPayload(action, text, locator)
    }.getOrNull()

    parse(this)?.let { return it }
    return runCatching {
        Json.parseToJsonElement(this).jsonPrimitive.contentOrNull
    }.getOrNull()?.let { parse(it) }
}

internal fun String.readerSelectionDebugMessageOrNull(): String? {
    fun parse(rawJson: String): String? = runCatching {
        Json.parseToJsonElement(rawJson)
            .jsonObject["message"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    parse(this)?.let { return it }
    return runCatching {
        Json.parseToJsonElement(this).jsonPrimitive.contentOrNull
    }.getOrNull()?.let { parse(it) }
}

internal fun String.readerPaginationLogMessageOrNull(): String? {
    fun parse(rawJson: String): String? = runCatching {
        Json.parseToJsonElement(rawJson)
            .jsonObject["message"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    parse(this)?.let { return it }
    return runCatching {
        Json.parseToJsonElement(this).jsonPrimitive.contentOrNull
    }.getOrNull()?.let { parse(it) }
}

internal fun String.readerPositionOrNull(): DesktopReaderPosition? {
    fun parse(rawJson: String): DesktopReaderPosition? = runCatching {
        val obj = Json.parseToJsonElement(rawJson).jsonObject
        val pageIndex = obj["pageIndex"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.intOrNull
            ?: return@runCatching null
        val locator = ReaderLocator(
            chapterIndex = obj["chapterIndex"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
            chapterId = obj["chapterId"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull,
            href = obj["href"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull,
            pageIndex = pageIndex,
            startOffset = obj["startOffset"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
            endOffset = obj["endOffset"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
            blockIndex = obj["blockIndex"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
            charOffset = obj["charOffset"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
            textQuote = obj["textQuote"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull,
            cfi = obj["cfi"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull?.toStableReaderPositionCfi()
        )
        DesktopReaderPosition(pageIndex, locator)
    }.getOrNull()

    parse(this)?.let { return it }
    return runCatching {
        Json.parseToJsonElement(this).jsonPrimitive.contentOrNull
    }.getOrNull()?.let { parse(it) }
}

internal fun String.readerKeyNavigationOrNull(): DesktopReaderKeyNavigation? {
    fun parse(rawJson: String): DesktopReaderKeyNavigation? = runCatching {
        val action = Json.parseToJsonElement(rawJson)
            .jsonObject["action"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return@runCatching null
        when (action) {
            "next" -> DesktopReaderKeyNavigation.NEXT
            "previous" -> DesktopReaderKeyNavigation.PREVIOUS
            "first" -> DesktopReaderKeyNavigation.FIRST
            "last" -> DesktopReaderKeyNavigation.LAST
            "search" -> DesktopReaderKeyNavigation.SEARCH
            "nextSearch" -> DesktopReaderKeyNavigation.NEXT_SEARCH
            "exitFullscreen" -> DesktopReaderKeyNavigation.EXIT_FULLSCREEN
            else -> null
        }
    }.getOrNull()

    parse(this)?.let { return it }
    return runCatching {
        Json.parseToJsonElement(this).jsonPrimitive.contentOrNull
    }.getOrNull()?.let { parse(it) }
}

internal fun String.readerLinkClickOrNull(): DesktopEpubLinkClick? {
    fun parse(rawJson: String): DesktopEpubLinkClick? = runCatching {
        val obj = Json.parseToJsonElement(rawJson).jsonObject
        val href = obj["href"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        DesktopEpubLinkClick(
            href = href,
            chapterIndex = obj["chapterIndex"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
            text = obj["text"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull,
            chapterId = obj["chapterId"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull,
            chapterHref = obj["chapterHref"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
        )
    }.getOrNull()

    parse(this)?.let { return it }
    return runCatching {
        Json.parseToJsonElement(this).jsonPrimitive.contentOrNull
    }.getOrNull()?.let { parse(it) }
}

internal fun String.readerLinkClickFromIntercept(): DesktopEpubLinkClick? {
    val trimmed = trim()
    if (trimmed.startsWith("readerlink:", ignoreCase = true)) {
        logEpubLink("request_intercept_readerlink raw=\"${trimmed.logPreview()}\"")
        val payload = trimmed.substringAfter("?", missingDelimiterValue = "")
            .split('&')
            .firstOrNull { it.substringBefore("=").equals("payload", ignoreCase = true) }
            ?.substringAfter("=", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
        if (payload == null) {
            logEpubLink("request_intercept_readerlink_ignored reason=missing_payload")
            return null
        }
        val decoded = runCatching {
            URLDecoder.decode(payload, Charsets.UTF_8.name())
        }.getOrElse {
            logEpubLink("request_intercept_payload_decode_failed error=\"${it.message.orEmpty().logPreview()}\"")
            return null
        }
        val link = decoded.readerLinkClickOrNull()?.copy(source = "request")
        if (link == null) {
            logEpubLink("request_intercept_readerlink_ignored reason=parse_failed payload=\"${decoded.logPreview()}\"")
        }
        return link
    }
    return readerHrefFromIntercept()?.let { href ->
        DesktopEpubLinkClick(
            href = href,
            chapterIndex = null,
            source = "request"
        )
    }
}

private fun String.readerHrefFromIntercept(): String? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null
    if (trimmed.equals("about:blank", ignoreCase = true)) return null
    if (trimmed.startsWith("file:/", ignoreCase = true)) return null
    if (trimmed.startsWith("about:blank#", ignoreCase = true)) return "#${trimmed.substringAfter('#')}"
    if (trimmed.startsWith("data:", ignoreCase = true)) return null
    if (trimmed.startsWith("blob:", ignoreCase = true)) return null
    return trimmed
}

internal fun ReaderLocator.toReaderLocatorJson(): String {
    return buildString {
        append("{")
        val values = buildList {
            chapterIndex?.let { add("\"chapterIndex\":$it") }
            chapterId?.let { add("\"chapterId\":${it.toJsonStringLiteral()}") }
            href?.let { add("\"href\":${it.toJsonStringLiteral()}") }
            pageIndex?.let { add("\"pageIndex\":$it") }
            startOffset?.let { add("\"startOffset\":$it") }
            endOffset?.let { add("\"endOffset\":$it") }
            blockIndex?.let { add("\"blockIndex\":$it") }
            charOffset?.let { add("\"charOffset\":$it") }
            cfi?.let { add("\"cfi\":${it.toJsonStringLiteral()}") }
            textQuote?.let { add("\"textQuote\":${it.toJsonStringLiteral()}") }
        }
        append(values.joinToString(","))
        append("}")
    }
}

private fun String.toJsonStringLiteral(): String {
    val builder = StringBuilder("\"")
    forEach { char ->
        when (char) {
            '\\' -> builder.append("\\\\")
            '"' -> builder.append("\\\"")
            '\n' -> builder.append("\\n")
            '\r' -> builder.append("\\r")
            '\t' -> builder.append("\\t")
            '\b' -> builder.append("\\b")
            '\u000C' -> builder.append("\\f")
            else -> {
                if (char.code < 0x20) {
                    builder.append("\\u")
                    builder.append(char.code.toString(16).padStart(4, '0'))
                } else {
                    builder.append(char)
                }
            }
        }
    }
    builder.append('"')
    return builder.toString()
}
