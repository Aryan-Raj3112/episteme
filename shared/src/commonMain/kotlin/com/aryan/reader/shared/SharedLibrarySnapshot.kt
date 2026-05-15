package com.aryan.reader.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import com.aryan.reader.shared.pdf.SharedPdfReaderViewport
import com.aryan.reader.shared.reader.ReaderBookmark
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedReaderTextAlign

data class SharedLibrarySnapshot(
    val books: List<BookItem> = emptyList(),
    val shelfRecords: List<ShelfRecord> = emptyList(),
    val shelfRefs: List<BookShelfRef> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val customFonts: List<CustomFontItem> = emptyList(),
    val syncedFolders: List<SyncedFolder> = emptyList(),
    val recentFilesLimit: Int = 12,
    val isTabsEnabled: Boolean = true,
    val openTabIds: List<String> = emptyList(),
    val activeTabBookId: String? = null,
    val pinnedHomeBookIds: Set<String> = emptySet(),
    val pinnedLibraryBookIds: Set<String> = emptySet(),
    val useStrictFileFilter: Boolean = false,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val appContrastOption: AppContrastOption = AppContrastOption.STANDARD,
    val appTextDimFactorLight: Float = 1.0f,
    val appTextDimFactorDark: Float = 1.0f,
    val appSeedColor: Color? = null,
    val customAppThemes: List<CustomAppTheme> = emptyList(),
    val readerDefaultSettings: ReaderSettings = ReaderSettings(),
    val pdfReaderDefaultSettings: ReaderSettings = ReaderSettings(themeId = "no_theme"),
    val readerToolbarPreferences: ReaderToolbarPreferences = ReaderToolbarPreferences(),
    val readerHighlightPalette: ReaderHighlightPalette = ReaderHighlightPalette(),
    val pdfHighlighterPalette: SharedPdfHighlighterPalette = SharedPdfHighlighterPalette(),
    val readerTtsReplacementPreferences: ReaderTtsReplacementPreferences = ReaderTtsReplacementPreferences()
)

object SharedLibrarySnapshotJson {
    private const val SCHEMA_VERSION = 19

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun decodeOrEmpty(rawJson: String): SharedLibrarySnapshot {
        val root = runCatching {
            json.parseToJsonElement(rawJson).jsonObject
        }.getOrNull() ?: return SharedLibrarySnapshot()

        val schemaVersion = root.int("schemaVersion", 1)
        val openTabIds = root.stringArray("openTabIds")
        val readerDefaultSettings = root["readerDefaultSettings"]
            ?.takeUnless { it is JsonNull }
            ?.asReaderSettingsOrNull()
            ?: ReaderSettings()
        return SharedLibrarySnapshot(
            books = root.array("books")
                .mapNotNull { it.asBookItemOrNull() }
                .migrateLegacyRecentState(schemaVersion, openTabIds),
            shelfRecords = root.array("shelves").mapNotNull { it.asShelfRecordOrNull() },
            shelfRefs = root.array("bookShelfRefs").mapNotNull { it.asBookShelfRefOrNull() },
            tags = root.array("tags").mapNotNull { it.asTagOrNull() },
            customFonts = root.array("customFonts").mapNotNull { it.asCustomFontItemOrNull() },
            syncedFolders = root.array("syncedFolders").mapNotNull { it.asSyncedFolderOrNull() },
            recentFilesLimit = root.int("recentFilesLimit", 12),
            isTabsEnabled = root.boolean("isTabsEnabled", true),
            openTabIds = openTabIds,
            activeTabBookId = root.string("activeTabBookId"),
            pinnedHomeBookIds = root.stringArray("pinnedHomeBookIds").toSet(),
            pinnedLibraryBookIds = root.stringArray("pinnedLibraryBookIds").toSet(),
            useStrictFileFilter = root.boolean("useStrictFileFilter", false),
            appThemeMode = root.string("appThemeMode")
                ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.SYSTEM,
            appContrastOption = root.string("appContrastOption")
                ?.let { runCatching { AppContrastOption.valueOf(it) }.getOrNull() }
                ?: AppContrastOption.STANDARD,
            appTextDimFactorLight = root.float("appTextDimFactorLight")
                ?: root.float("appTextDimFactor")
                ?: 1.0f,
            appTextDimFactorDark = root.float("appTextDimFactorDark")
                ?: root.float("appTextDimFactor")
                ?: 1.0f,
            appSeedColor = root.int("appSeedColor")?.let { Color(it) },
            customAppThemes = root.array("customAppThemes").mapNotNull { it.asCustomAppThemeOrNull() },
            readerDefaultSettings = readerDefaultSettings.migrateLegacyDefaultReadingMode(schemaVersion),
            pdfReaderDefaultSettings = root["pdfReaderDefaultSettings"]
                ?.takeUnless { it is JsonNull }
                ?.asReaderSettingsOrNull()
                ?: ReaderSettings(themeId = "no_theme"),
            readerToolbarPreferences = root["readerToolbarPreferences"]
                ?.takeUnless { it is JsonNull }
                ?.asReaderToolbarPreferencesOrNull()
                ?: ReaderToolbarPreferences(),
            readerHighlightPalette = root["readerHighlightPalette"]
                ?.takeUnless { it is JsonNull }
                ?.asReaderHighlightPaletteOrNull()
                ?: ReaderHighlightPalette(),
            pdfHighlighterPalette = root["pdfHighlighterPalette"]
                ?.takeUnless { it is JsonNull }
                ?.asSharedPdfHighlighterPaletteOrNull()
                ?: SharedPdfHighlighterPalette(),
            readerTtsReplacementPreferences = root["readerTtsReplacementPreferences"]
                ?.takeUnless { it is JsonNull }
                ?.let { ReaderTtsReplacementPreferencesJson.fromJsonElement(it) }
                ?: ReaderTtsReplacementPreferences()
        )
    }

    fun encode(snapshot: SharedLibrarySnapshot): String {
        val root = JsonObject(
            mapOf(
                "schemaVersion" to JsonPrimitive(SCHEMA_VERSION),
                "books" to JsonArray(snapshot.books.map { it.toJsonObject() }),
                "shelves" to JsonArray(snapshot.shelfRecords.map { it.toJsonObject() }),
                "bookShelfRefs" to JsonArray(snapshot.shelfRefs.map { it.toJsonObject() }),
                "tags" to JsonArray(snapshot.tags.map { it.toJsonObject() }),
                "customFonts" to JsonArray(snapshot.customFonts.map { it.toJsonObject() }),
                "syncedFolders" to JsonArray(snapshot.syncedFolders.map { it.toJsonObject() }),
                "recentFilesLimit" to JsonPrimitive(snapshot.recentFilesLimit),
                "isTabsEnabled" to JsonPrimitive(snapshot.isTabsEnabled),
                "openTabIds" to snapshot.openTabIds.asJsonArray(),
                "activeTabBookId" to snapshot.activeTabBookId.asJson(),
                "pinnedHomeBookIds" to snapshot.pinnedHomeBookIds.toList().asJsonArray(),
                "pinnedLibraryBookIds" to snapshot.pinnedLibraryBookIds.toList().asJsonArray(),
                "useStrictFileFilter" to JsonPrimitive(snapshot.useStrictFileFilter),
                "appThemeMode" to JsonPrimitive(snapshot.appThemeMode.name),
                "appContrastOption" to JsonPrimitive(snapshot.appContrastOption.name),
                "appTextDimFactorLight" to JsonPrimitive(snapshot.appTextDimFactorLight),
                "appTextDimFactorDark" to JsonPrimitive(snapshot.appTextDimFactorDark),
                "appSeedColor" to snapshot.appSeedColor.asJson(),
                "customAppThemes" to JsonArray(snapshot.customAppThemes.map { it.toJsonObject() }),
                "readerDefaultSettings" to snapshot.readerDefaultSettings.asJson(),
                "pdfReaderDefaultSettings" to snapshot.pdfReaderDefaultSettings.asJson(),
                "readerToolbarPreferences" to snapshot.readerToolbarPreferences.sanitized().toJsonObject(),
                "readerHighlightPalette" to snapshot.readerHighlightPalette.sanitized().toJsonObject(),
                "pdfHighlighterPalette" to snapshot.pdfHighlighterPalette.sanitized().toJsonObject(),
                "readerTtsReplacementPreferences" to ReaderTtsReplacementPreferencesJson.toJsonElement(
                    snapshot.readerTtsReplacementPreferences,
                )
            )
        )
        return json.encodeToString(JsonElement.serializer(), root)
    }
}

private fun JsonObject.array(name: String): List<JsonElement> {
    return runCatching { this[name]?.jsonArray?.toList().orEmpty() }.getOrDefault(emptyList())
}

private fun JsonObject.stringArray(name: String): List<String> {
    return array(name).mapNotNull { element ->
        runCatching { element.jsonPrimitive.content }.getOrNull()
    }
}

private fun JsonObject.intArray(name: String): List<Int> {
    return array(name).mapNotNull { element ->
        runCatching { element.jsonPrimitive.intOrNull }.getOrNull()
    }
}

private fun JsonObject.string(name: String): String? {
    return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content }.getOrNull()
}

private fun JsonObject.long(name: String, fallback: Long = 0L): Long {
    return runCatching { this[name]?.jsonPrimitive?.longOrNull }.getOrNull() ?: fallback
}

private fun JsonObject.nullableLong(name: String): Long? {
    return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.longOrNull }.getOrNull()
}

private fun JsonObject.int(name: String): Int? {
    return runCatching { this[name]?.jsonPrimitive?.content?.toIntOrNull() }.getOrNull()
}

private fun JsonObject.int(name: String, fallback: Int): Int {
    return int(name) ?: fallback
}

private fun JsonObject.float(name: String): Float? {
    return runCatching { this[name]?.jsonPrimitive?.floatOrNull }.getOrNull()
}

private fun JsonObject.double(name: String): Double? {
    return runCatching { this[name]?.jsonPrimitive?.doubleOrNull }.getOrNull()
}

private fun JsonObject.boolean(name: String, fallback: Boolean): Boolean {
    return runCatching { this[name]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: fallback
}

private fun List<BookItem>.migrateLegacyRecentState(schemaVersion: Int, openTabIds: List<String>): List<BookItem> {
    if (schemaVersion >= 3) return this
    val openedBookIds = openTabIds.toSet()
    return map { book ->
        if (book.isRecent && !book.hasReaderFootprint(openedBookIds)) {
            book.copy(isRecent = false)
        } else {
            book
        }
    }
}

private fun BookItem.hasReaderFootprint(openedBookIds: Set<String>): Boolean {
    return id in openedBookIds ||
        lastPageIndex != null ||
        readerPosition != null ||
        (progressPercentage ?: 0f) > 0f ||
        readerSettings != null ||
        readerBookmarks.isNotEmpty() ||
        readerHighlights.isNotEmpty() ||
        pdfReaderViewport != null
}

private fun ReaderSettings.migrateLegacyDefaultReadingMode(schemaVersion: Int): ReaderSettings {
    if (schemaVersion >= 17 || readingMode != ReaderReadingMode.PAGINATED) return this
    val oldDefaultSettings = ReaderSettings(readingMode = ReaderReadingMode.PAGINATED)
    return if (this == oldDefaultSettings) copy(readingMode = ReaderReadingMode.VERTICAL) else this
}

private fun JsonElement.asBookItemOrNull(): BookItem? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    val id = obj.string("id") ?: return null
    val displayName = obj.string("displayName") ?: return null
    val type = obj.string("type")?.let { runCatching { FileType.valueOf(it) }.getOrNull() } ?: FileType.UNKNOWN
    return BookItem(
        id = id,
        path = obj.string("path"),
        type = type,
        displayName = displayName,
        timestamp = obj.long("timestamp"),
        coverImagePath = obj.string("coverImagePath"),
        title = obj.string("title"),
        author = obj.string("author"),
        description = obj.string("description"),
        originalTitle = obj.string("originalTitle"),
        originalAuthor = obj.string("originalAuthor"),
        originalSeriesName = obj.string("originalSeriesName"),
        originalSeriesIndex = obj.double("originalSeriesIndex"),
        originalDescription = obj.string("originalDescription"),
        progressPercentage = obj.float("progressPercentage"),
        isRecent = obj.boolean("isRecent", true),
        fileSize = obj.long("fileSize"),
        fileContentModifiedTimestamp = obj.long("fileContentModifiedTimestamp"),
        sourceFolder = obj.string("sourceFolder"),
        folderTextMetadataParsed = obj.boolean("folderTextMetadataParsed", false),
        seriesName = obj.string("seriesName"),
        seriesIndex = obj.double("seriesIndex"),
        tags = obj.array("tags").mapNotNull { it.asTagOrNull() },
        lastPageIndex = obj.int("lastPageIndex"),
        readerPosition = obj["readerPosition"]?.takeUnless { it is JsonNull }?.asReaderLocatorOrNull(),
        readerSettings = obj["readerSettings"]?.takeUnless { it is JsonNull }?.asReaderSettingsOrNull(),
        readerBookmarks = obj.array("readerBookmarks").mapNotNull { it.asReaderBookmarkOrNull() },
        readerHighlights = obj.array("readerHighlights").mapNotNull { it.asReaderHighlightOrNull() },
        pdfReaderViewport = obj["pdfReaderViewport"]?.takeUnless { it is JsonNull }?.asSharedPdfReaderViewportOrNull()
    )
}

private fun JsonElement.asShelfRecordOrNull(): ShelfRecord? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    return ShelfRecord(
        id = obj.string("id") ?: return null,
        name = obj.string("name") ?: return null,
        isSmart = obj.boolean("isSmart", false),
        smartRulesJson = obj.string("smartRulesJson")
    )
}

private fun JsonElement.asBookShelfRefOrNull(): BookShelfRef? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    return BookShelfRef(
        bookId = obj.string("bookId") ?: return null,
        shelfId = obj.string("shelfId") ?: return null,
        addedAt = obj.long("addedAt")
    )
}

private fun JsonElement.asTagOrNull(): Tag? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    return Tag(
        id = obj.string("id") ?: return null,
        name = obj.string("name") ?: return null,
        color = runCatching {
            obj["color"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.toIntOrNull()
        }.getOrNull()
    )
}

private fun JsonElement.asCustomFontItemOrNull(): CustomFontItem? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    return CustomFontItem(
        id = obj.string("id") ?: return null,
        displayName = obj.string("displayName") ?: return null,
        fileName = obj.string("fileName") ?: return null,
        fileExtension = obj.string("fileExtension") ?: return null,
        path = obj.string("path") ?: return null,
        timestamp = obj.long("timestamp"),
        isDeleted = obj.boolean("isDeleted", false)
    )
}

private fun JsonElement.asSyncedFolderOrNull(): SyncedFolder? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    return SyncedFolder(
        uriString = obj.string("uriString") ?: return null,
        name = obj.string("name") ?: return null,
        lastScanTime = obj.long("lastScanTime"),
        allowedFileTypes = obj.stringArray("allowedFileTypes")
            .mapNotNull { runCatching { FileType.valueOf(it) }.getOrNull() }
            .filter { it in SharedFileCapabilities.knownFileTypes }
            .toSet()
            .ifEmpty { SharedFileCapabilities.knownFileTypes }
    )
}

private fun JsonElement.asCustomAppThemeOrNull(): CustomAppTheme? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    return CustomAppTheme(
        id = obj.string("id") ?: return null,
        name = obj.string("name") ?: return null,
        seedColor = obj.int("seedColor")?.let { Color(it) } ?: return null
    )
}

private fun BookItem.toJsonObject(): JsonObject {
    return JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "path" to path.asJson(),
            "type" to JsonPrimitive(type.name),
            "displayName" to JsonPrimitive(displayName),
            "timestamp" to JsonPrimitive(timestamp),
            "coverImagePath" to coverImagePath.asJson(),
            "title" to title.asJson(),
            "author" to author.asJson(),
            "description" to description.asJson(),
            "originalTitle" to originalTitle.asJson(),
            "originalAuthor" to originalAuthor.asJson(),
            "originalSeriesName" to originalSeriesName.asJson(),
            "originalSeriesIndex" to originalSeriesIndex.asJson(),
            "originalDescription" to originalDescription.asJson(),
            "progressPercentage" to progressPercentage.asJson(),
            "isRecent" to JsonPrimitive(isRecent),
            "fileSize" to JsonPrimitive(fileSize),
            "fileContentModifiedTimestamp" to JsonPrimitive(fileContentModifiedTimestamp),
            "sourceFolder" to sourceFolder.asJson(),
            "folderTextMetadataParsed" to JsonPrimitive(folderTextMetadataParsed),
            "seriesName" to seriesName.asJson(),
            "seriesIndex" to seriesIndex.asJson(),
            "tags" to JsonArray(tags.map { it.toJsonObject() }),
            "lastPageIndex" to lastPageIndex.asJson(),
            "readerPosition" to readerPosition.asJson(),
            "readerSettings" to readerSettings.asJson(),
            "readerBookmarks" to JsonArray(readerBookmarks.map { it.toJsonObject() }),
            "readerHighlights" to JsonArray(readerHighlights.map { it.toJsonObject() }),
            "pdfReaderViewport" to pdfReaderViewport.asJson()
        )
    )
}

private fun ShelfRecord.toJsonObject(): JsonObject {
    return JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "name" to JsonPrimitive(name),
            "isSmart" to JsonPrimitive(isSmart),
            "smartRulesJson" to smartRulesJson.asJson()
        )
    )
}

private fun BookShelfRef.toJsonObject(): JsonObject {
    return JsonObject(
        mapOf(
            "bookId" to JsonPrimitive(bookId),
            "shelfId" to JsonPrimitive(shelfId),
            "addedAt" to JsonPrimitive(addedAt)
        )
    )
}

private fun Tag.toJsonObject(): JsonObject {
    return JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "name" to JsonPrimitive(name),
            "color" to color.asJson()
        )
    )
}

private fun CustomFontItem.toJsonObject(): JsonObject {
    return JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "displayName" to JsonPrimitive(displayName),
            "fileName" to JsonPrimitive(fileName),
            "fileExtension" to JsonPrimitive(fileExtension),
            "path" to JsonPrimitive(path),
            "timestamp" to JsonPrimitive(timestamp),
            "isDeleted" to JsonPrimitive(isDeleted)
        )
    )
}

private fun SyncedFolder.toJsonObject(): JsonObject {
    return JsonObject(
        mapOf(
            "uriString" to JsonPrimitive(uriString),
            "name" to JsonPrimitive(name),
            "lastScanTime" to JsonPrimitive(lastScanTime),
            "allowedFileTypes" to allowedFileTypes
                .filter { it in SharedFileCapabilities.knownFileTypes }
                .map { it.name }
                .sorted()
                .asJsonArray()
        )
    )
}

private fun CustomAppTheme.toJsonObject(): JsonObject {
    return JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "name" to JsonPrimitive(name),
            "seedColor" to JsonPrimitive(seedColor.toArgb())
        )
    )
}

private fun String?.asJson(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
private fun Float?.asJson(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
private fun Double?.asJson(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
private fun Int?.asJson(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
private fun Long?.asJson(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
private fun Color?.asJson(): JsonElement = this?.let { JsonPrimitive(it.toArgb()) } ?: JsonNull

private fun List<String>.asJsonArray(): JsonArray {
    return JsonArray(map { JsonPrimitive(it) })
}

private fun List<Int>.asIntJsonArray(): JsonArray {
    return JsonArray(map { JsonPrimitive(it) })
}

private fun JsonElement.asReaderSettingsOrNull(): ReaderSettings? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    val defaults = ReaderSettings()
    return ReaderSettings(
        fontSize = obj.int("fontSize") ?: defaults.fontSize,
        lineSpacing = obj.float("lineSpacing") ?: defaults.lineSpacing,
        margin = obj.int("margin") ?: defaults.margin,
        darkMode = obj.boolean("darkMode", defaults.darkMode),
        readingMode = obj.string("readingMode")
            ?.let { runCatching { ReaderReadingMode.valueOf(it) }.getOrNull() }
            ?: defaults.readingMode,
        textAlign = obj.string("textAlign")
            ?.let { runCatching { SharedReaderTextAlign.valueOf(it) }.getOrNull() }
            ?: defaults.textAlign,
        pageWidth = obj.int("pageWidth") ?: defaults.pageWidth,
        fontFamily = obj.string("fontFamily") ?: defaults.fontFamily,
        paragraphSpacing = obj.float("paragraphSpacing") ?: defaults.paragraphSpacing,
        imageScale = obj.float("imageScale") ?: defaults.imageScale,
        horizontalMargin = obj.int("horizontalMargin"),
        verticalMargin = obj.int("verticalMargin"),
        themeId = obj.string("themeId"),
        textureId = obj.string("textureId"),
        textureAlpha = obj.float("textureAlpha") ?: defaults.textureAlpha,
        customFontPath = obj.string("customFontPath"),
        backgroundColorArgb = obj.nullableLong("backgroundColorArgb"),
        textColorArgb = obj.nullableLong("textColorArgb"),
        systemUiMode = obj.string("systemUiMode")
            ?.let { runCatching { SystemUiMode.valueOf(it) }.getOrNull() }
            ?: defaults.systemUiMode,
        pageInfoMode = obj.string("pageInfoMode")
            ?.let { runCatching { PageInfoMode.valueOf(it) }.getOrNull() }
            ?: defaults.pageInfoMode,
        pageInfoPosition = obj.string("pageInfoPosition")
            ?.let { runCatching { PageInfoPosition.valueOf(it) }.getOrNull() }
            ?: defaults.pageInfoPosition,
        pageSpreadMode = obj.string("pageSpreadMode")
            ?.let { runCatching { ReaderPageSpreadMode.valueOf(it) }.getOrNull() }
            ?: defaults.pageSpreadMode,
        pdfVerticalPageGapVisible = obj.boolean(
            "pdfVerticalPageGapVisible",
            defaults.pdfVerticalPageGapVisible
        ),
        pdfPageNumberOverlayVisible = obj.boolean(
            "pdfPageNumberOverlayVisible",
            defaults.pdfPageNumberOverlayVisible
        ),
        seamlessChapterNavigation = obj.boolean("seamlessChapterNavigation", defaults.seamlessChapterNavigation),
        chapterTurnDragMultiplier = obj.float("chapterTurnDragMultiplier") ?: defaults.chapterTurnDragMultiplier
    )
}

private fun JsonElement.asReaderToolbarPreferencesOrNull(): ReaderToolbarPreferences? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    val order = obj.stringArray("toolOrder").mapNotNull(ReaderTool::fromId)
    val bottomToolIds = if (obj["bottomToolIds"] == null) {
        ReaderToolbarPreferences.defaultBottomToolIds
    } else {
        obj.stringArray("bottomToolIds").toSet()
    }
    return ReaderToolbarPreferences(
        hiddenToolIds = obj.stringArray("hiddenToolIds").toSet(),
        toolOrder = order.ifEmpty { ReaderTool.entries.toList() },
        bottomToolIds = bottomToolIds
    ).sanitized()
}

private fun JsonElement.asReaderHighlightPaletteOrNull(): ReaderHighlightPalette? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    val colors = obj.stringArray("colorIds")
        .mapNotNull { colorId -> HighlightColor.entries.firstOrNull { it.id == colorId || it.name == colorId } }
    return ReaderHighlightPalette(colors = colors).sanitized()
}

private fun JsonElement.asSharedPdfHighlighterPaletteOrNull(): SharedPdfHighlighterPalette? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    return SharedPdfHighlighterPalette(colors = obj.intArray("colorsArgb")).sanitized()
}

private fun JsonElement.asSharedPdfReaderViewportOrNull(): SharedPdfReaderViewport? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    val defaults = SharedPdfReaderViewport()
    return SharedPdfReaderViewport(
        pageIndex = obj.int("pageIndex") ?: defaults.pageIndex,
        displayMode = obj.string("displayMode")
            ?.let { runCatching { PdfDisplayMode.valueOf(it) }.getOrNull() }
            ?: defaults.displayMode,
        zoom = obj.float("zoom") ?: defaults.zoom,
        horizontalScrollOffset = obj.int("horizontalScrollOffset") ?: defaults.horizontalScrollOffset,
        paginatedVerticalScrollOffset = obj.int("paginatedVerticalScrollOffset")
            ?: defaults.paginatedVerticalScrollOffset,
        verticalFirstPageIndex = obj.int("verticalFirstPageIndex") ?: defaults.verticalFirstPageIndex,
        verticalFirstPageScrollOffset = obj.int("verticalFirstPageScrollOffset")
            ?: defaults.verticalFirstPageScrollOffset
    )
}

private fun JsonElement.asReaderBookmarkOrNull(): ReaderBookmark? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    val pageIndex = obj.int("pageIndex") ?: return null
    return ReaderBookmark(
        id = obj.string("id") ?: return null,
        pageIndex = pageIndex,
        chapterTitle = obj.string("chapterTitle") ?: "",
        preview = obj.string("preview") ?: "",
        locator = obj["locator"]
            ?.takeUnless { it is JsonNull }
            ?.asReaderLocatorOrNull()
            ?.withFallbacks(pageIndex = pageIndex, textQuote = obj.string("preview") ?: "")
            ?: ReaderLocator(
                pageIndex = pageIndex,
                textQuote = obj.string("preview") ?: ""
            )
    )
}

private fun JsonElement.asReaderHighlightOrNull(): UserHighlight? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    val cfi = obj.string("cfi") ?: return null
    val text = obj.string("text") ?: return null
    val chapterIndex = obj.int("chapterIndex") ?: return null
    val color = obj.string("colorId")
        ?.let { colorId -> HighlightColor.entries.firstOrNull { it.id == colorId } }
        ?: HighlightColor.YELLOW
    return UserHighlight(
        id = obj.string("id") ?: return null,
        cfi = cfi,
        text = text,
        color = color,
        chapterIndex = chapterIndex,
        note = obj.string("note")?.takeIf { it.isNotBlank() },
        locator = obj["locator"]
            ?.takeUnless { it is JsonNull }
            ?.asReaderLocatorOrNull()
            ?.withFallbacks(
                chapterIndex = chapterIndex,
                cfi = cfi,
                textQuote = text
            )
            ?: ReaderLocator.fromLegacy(
                chapterIndex = chapterIndex,
                cfi = cfi,
                textQuote = text
            )
    )
}

private fun JsonElement.asReaderLocatorOrNull(): ReaderLocator? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    return ReaderLocator(
        chapterIndex = obj.int("chapterIndex"),
        chapterId = obj.string("chapterId"),
        href = obj.string("href"),
        pageIndex = obj.int("pageIndex"),
        startOffset = obj.int("startOffset"),
        endOffset = obj.int("endOffset"),
        textQuote = obj.string("textQuote"),
        cfi = obj.string("cfi")
    )
}

private fun ReaderSettings?.asJson(): JsonElement {
    val settings = this ?: return JsonNull
    return JsonObject(
        mapOf(
            "fontSize" to JsonPrimitive(settings.fontSize),
            "lineSpacing" to JsonPrimitive(settings.lineSpacing),
            "margin" to JsonPrimitive(settings.margin),
            "darkMode" to JsonPrimitive(settings.darkMode),
            "readingMode" to JsonPrimitive(settings.readingMode.name),
            "textAlign" to JsonPrimitive(settings.textAlign.name),
            "pageWidth" to JsonPrimitive(settings.pageWidth),
            "fontFamily" to JsonPrimitive(settings.fontFamily),
            "paragraphSpacing" to JsonPrimitive(settings.paragraphSpacing),
            "imageScale" to JsonPrimitive(settings.imageScale),
            "horizontalMargin" to settings.horizontalMargin.asJson(),
            "verticalMargin" to settings.verticalMargin.asJson(),
            "themeId" to settings.themeId.asJson(),
            "textureId" to settings.textureId.asJson(),
            "textureAlpha" to JsonPrimitive(settings.textureAlpha),
            "customFontPath" to settings.customFontPath.asJson(),
            "backgroundColorArgb" to settings.backgroundColorArgb.asJson(),
            "textColorArgb" to settings.textColorArgb.asJson(),
            "systemUiMode" to JsonPrimitive(settings.systemUiMode.name),
            "pageInfoMode" to JsonPrimitive(settings.pageInfoMode.name),
            "pageInfoPosition" to JsonPrimitive(settings.pageInfoPosition.name),
            "pageSpreadMode" to JsonPrimitive(settings.pageSpreadMode.name),
            "pdfVerticalPageGapVisible" to JsonPrimitive(settings.pdfVerticalPageGapVisible),
            "pdfPageNumberOverlayVisible" to JsonPrimitive(settings.pdfPageNumberOverlayVisible),
            "seamlessChapterNavigation" to JsonPrimitive(settings.seamlessChapterNavigation),
            "chapterTurnDragMultiplier" to JsonPrimitive(settings.chapterTurnDragMultiplier)
        )
    )
}

private fun ReaderToolbarPreferences.toJsonObject(): JsonObject {
    val sanitized = sanitized()
    return JsonObject(
        mapOf(
            "hiddenToolIds" to sanitized.hiddenToolIds.toList().sorted().asJsonArray(),
            "toolOrder" to sanitized.toolOrder.map { it.id }.asJsonArray(),
            "bottomToolIds" to sanitized.bottomToolIds.toList().sorted().asJsonArray()
        )
    )
}

private fun ReaderHighlightPalette.toJsonObject(): JsonObject {
    return JsonObject(
        mapOf(
            "colorIds" to sanitized().colors.map { it.id }.asJsonArray()
        )
    )
}

private fun SharedPdfHighlighterPalette.toJsonObject(): JsonObject {
    return JsonObject(
        mapOf(
            "colorsArgb" to sanitized().colors.asIntJsonArray()
        )
    )
}

private fun SharedPdfReaderViewport?.asJson(): JsonElement {
    val viewport = this ?: return JsonNull
    return JsonObject(
        mapOf(
            "pageIndex" to JsonPrimitive(viewport.pageIndex),
            "displayMode" to JsonPrimitive(viewport.displayMode.name),
            "zoom" to JsonPrimitive(viewport.zoom),
            "horizontalScrollOffset" to JsonPrimitive(viewport.horizontalScrollOffset),
            "paginatedVerticalScrollOffset" to JsonPrimitive(viewport.paginatedVerticalScrollOffset),
            "verticalFirstPageIndex" to JsonPrimitive(viewport.verticalFirstPageIndex),
            "verticalFirstPageScrollOffset" to JsonPrimitive(viewport.verticalFirstPageScrollOffset)
        )
    )
}

private fun ReaderBookmark.toJsonObject(): JsonObject {
    return JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "pageIndex" to JsonPrimitive(pageIndex),
            "chapterTitle" to JsonPrimitive(chapterTitle),
            "preview" to JsonPrimitive(preview),
            "locator" to locator.toJsonObject()
        )
    )
}

private fun UserHighlight.toJsonObject(): JsonObject {
    return JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "cfi" to JsonPrimitive(cfi),
            "text" to JsonPrimitive(text),
            "colorId" to JsonPrimitive(color.id),
            "chapterIndex" to JsonPrimitive(chapterIndex),
            "note" to note.asJson(),
            "locator" to locator.toJsonObject()
        )
    )
}

private fun ReaderLocator.toJsonObject(): JsonObject {
    return JsonObject(
        buildMap {
            chapterIndex?.let { put("chapterIndex", JsonPrimitive(it)) }
            chapterId?.let { put("chapterId", JsonPrimitive(it)) }
            href?.let { put("href", JsonPrimitive(it)) }
            pageIndex?.let { put("pageIndex", JsonPrimitive(it)) }
            startOffset?.let { put("startOffset", JsonPrimitive(it)) }
            endOffset?.let { put("endOffset", JsonPrimitive(it)) }
            textQuote?.let { put("textQuote", JsonPrimitive(it)) }
            cfi?.let { put("cfi", JsonPrimitive(it)) }
        }
    )
}

private fun ReaderLocator?.asJson(): JsonElement {
    return this?.toJsonObject() ?: JsonNull
}
