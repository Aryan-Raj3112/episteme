package com.aryan.reader.shared.pdf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The portable envelope used when a PDF's reader state travels with the
 * annotation sidecar.
 *
 * Android historically uploaded a bundle containing `ink`, `text`, `layout`,
 * `textBoxes`, and `highlights`.  The fields in this envelope deliberately
 * keep those names and add only optional state metadata.  Older Android
 * readers therefore ignore the state fields, while newer Android and iOS
 * readers can restore settings, inserted pages, rich text, and annotations
 * from the same Drive file.
 */
data class SharedPdfCloudSidecarPayload(
    val version: Int,
    val bookId: String?,
    val sourceFingerprint: String?,
    val modifiedTimestamp: Long,
    val readerState: SharedPdfReaderState?,
    val annotations: List<SharedPdfAnnotation>,
    val richTextDocumentJson: String?
)

/** Raw sidecar carried alongside a library snapshot while cloud transport runs. */
data class SharedPdfCloudSidecarSnapshot(
    val bookId: String,
    val timestamp: Long,
    val data: String
)

object SharedPdfCloudSidecarCodec {
    /** Android's current bundle is version 2; version 3 adds reader state. */
    const val CURRENT_VERSION = 3
    const val DRIVE_FILE_PREFIX = "annotation_"

    const val KEY_VERSION = "version"
    const val KEY_BOOK_ID = "bookId"
    const val KEY_SOURCE_FINGERPRINT = "sourceFingerprint"
    const val KEY_READER_STATE = "pdfReaderState"
    const val KEY_READER_STATE_MODIFIED_TIMESTAMP = "pdfReaderStateModifiedTimestamp"
    const val KEY_RICH_TEXT = "text"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /**
     * Encodes a state payload while retaining unrelated sidecar fields (for
     * example deletion tombstones or a platform-specific layout file).
     */
    fun encode(
        bookId: String,
        state: SharedPdfReaderState,
        sourceFingerprint: String? = null,
        modifiedTimestamp: Long = 0L,
        existingDataJson: String? = null
    ): String {
        val root = parseRoot(existingDataJson).toMutableMap()
        root[KEY_VERSION] = JsonPrimitive(CURRENT_VERSION)
        root[KEY_BOOK_ID] = JsonPrimitive(bookId)
        sourceFingerprint?.takeIf(String::isNotBlank)?.let {
            root[KEY_SOURCE_FINGERPRINT] = JsonPrimitive(it)
        } ?: root.remove(KEY_SOURCE_FINGERPRINT)
        root[KEY_READER_STATE_MODIFIED_TIMESTAMP] = JsonPrimitive(modifiedTimestamp.coerceAtLeast(0L))

        val stateElement = stateElement(state)
        root[KEY_READER_STATE] = stateElement
        root[SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATIONS] =
            SharedPdfAnnotationSidecarCodec.encodeAnnotationsElement(state.annotations)

        if (state.richTextDocumentJson.isBlank()) {
            root.remove(KEY_RICH_TEXT)
        } else {
            parseElementOrNull(state.richTextDocumentJson)?.let { root[KEY_RICH_TEXT] = it }
        }

        // Expand the canonical annotation list into Android's legacy arrays.
        // This is intentionally last so the state metadata survives the
        // compatibility conversion unchanged.
        val withLegacy = SharedPdfAnnotationSidecarCodec.legacyAndroidDataJsonFromCanonical(
            encodeObject(JsonObject(root))
        )
        return encodeObject(parseRoot(withLegacy))
    }

    fun decode(
        rawDataJson: String?,
        fallbackPageCount: Int = 1,
        fallbackPageIndex: Int = 0
    ): SharedPdfCloudSidecarPayload? {
        val root = parseRootOrNull(rawDataJson) ?: return null
        val storedState = decodeStateElement(
            element = root[KEY_READER_STATE],
            fallbackPageCount = fallbackPageCount,
            fallbackPageIndex = fallbackPageIndex
        )
        val hasAnnotationPayload = root.hasAnnotationPayload()
        val annotations = if (hasAnnotationPayload) {
            SharedPdfAnnotationSidecarCodec.annotationsFromData(root)
        } else {
            storedState?.annotations.orEmpty()
        }
        val richText = richTextDocumentJson(root)
        val state = storedState?.let { stateValue ->
            stateValue.copy(
                annotations = if (hasAnnotationPayload) annotations else stateValue.annotations,
                richTextDocumentJson = richText ?: stateValue.richTextDocumentJson
            ).coerced()
        }
        return SharedPdfCloudSidecarPayload(
            version = root.longOrNull(KEY_VERSION)?.toInt()
                ?: root.intOrNull(KEY_VERSION)
                ?: 1,
            bookId = root.stringOrNull(KEY_BOOK_ID),
            sourceFingerprint = root.stringOrNull(KEY_SOURCE_FINGERPRINT),
            modifiedTimestamp = root.longOrNull(KEY_READER_STATE_MODIFIED_TIMESTAMP)
                ?: 0L,
            readerState = state,
            annotations = annotations,
            richTextDocumentJson = richText
        )
    }

    /** Decodes only the durable reader state from a sidecar payload. */
    fun decodeReaderState(
        rawDataJson: String?,
        fallbackPageCount: Int = 1,
        fallbackPageIndex: Int = 0
    ): SharedPdfReaderState? = decode(
        rawDataJson = rawDataJson,
        fallbackPageCount = fallbackPageCount,
        fallbackPageIndex = fallbackPageIndex
    )?.readerState

    /**
     * Merges two bundles using the same annotation/tombstone rules as Android.
     * Reader settings are last-writer-wins by their explicit timestamp; equal
     * timestamps use [preferRemoteOnConflict].  Annotation additions and
     * deletions still merge independently, so changing a setting cannot drop
     * a concurrent highlight.
     */
    fun merge(
        localDataJson: String,
        remoteDataJson: String,
        preferRemoteOnConflict: Boolean,
        fallbackPageCount: Int = 1,
        fallbackPageIndex: Int = 0
    ): String {
        val mergedAnnotations = SharedPdfAnnotationSidecarCodec.mergeAnnotationDataJson(
            localDataJson = localDataJson,
            remoteDataJson = remoteDataJson,
            preferRemoteOnConflict = preferRemoteOnConflict
        )
        val localRoot = parseRoot(localDataJson)
        val remoteRoot = parseRoot(remoteDataJson)
        val mergedRoot = parseRoot(mergedAnnotations).toMutableMap()
        val localPayload = decode(localDataJson, fallbackPageCount, fallbackPageIndex)
        val remotePayload = decode(remoteDataJson, fallbackPageCount, fallbackPageIndex)
        val useRemoteState = chooseRemoteState(
            local = localPayload,
            remote = remotePayload,
            preferRemoteOnConflict = preferRemoteOnConflict
        )
        // A legacy state object can decode successfully while omitting the
        // bookmarks field (which kotlinx.serialization then defaults to an
        // empty list). Keep the other side's bookmarks in that case; an
        // explicitly encoded empty list still represents an intentional clear.
        val selectedIsRemote = when {
            useRemoteState && remotePayload?.readerState != null -> true
            !useRemoteState && localPayload?.readerState != null -> false
            remotePayload?.readerState != null -> true
            else -> false
        }
        val selectedState = if (selectedIsRemote) remotePayload?.readerState else localPayload?.readerState
        val fallbackState = if (selectedIsRemote) localPayload?.readerState else remotePayload?.readerState
        val selectedRoot = if (selectedIsRemote) remoteRoot else localRoot

        if (selectedState != null) {
            val hasAnnotationPayload = JsonObject(mergedRoot).hasAnnotationPayload()
            val mergedAnnotationsList = if (hasAnnotationPayload) {
                SharedPdfAnnotationSidecarCodec.annotationsFromData(JsonObject(mergedRoot))
            } else {
                selectedState.annotations
            }
            val mergedRichText = richTextDocumentJson(JsonObject(mergedRoot))
                ?: selectedState.richTextDocumentJson
            val mergedBookmarks = if (
                !selectedRoot.hasReaderStateBookmarksField() &&
                !fallbackState?.bookmarks.isNullOrEmpty()
            ) {
                fallbackState?.bookmarks.orEmpty()
            } else {
                selectedState.bookmarks
            }
            mergedRoot[KEY_READER_STATE] = stateElement(
                selectedState.copy(
                    bookmarks = mergedBookmarks,
                    annotations = mergedAnnotationsList,
                    richTextDocumentJson = mergedRichText
                )
            )
            val selectedTimestamp = if (useRemoteState) {
                remotePayload?.modifiedTimestamp ?: 0L
            } else {
                localPayload?.modifiedTimestamp ?: 0L
            }
            mergedRoot[KEY_READER_STATE_MODIFIED_TIMESTAMP] = JsonPrimitive(selectedTimestamp)
        }

        val preferredRoot = if (useRemoteState) remoteRoot else localRoot
        val fallbackRoot = if (useRemoteState) localRoot else remoteRoot
        mergedRoot[KEY_VERSION] = JsonPrimitive(
            maxOf(
                localRoot.intOrNull(KEY_VERSION) ?: 1,
                remoteRoot.intOrNull(KEY_VERSION) ?: 1,
                CURRENT_VERSION.takeIf { mergedRoot[KEY_READER_STATE] != null } ?: 1
            )
        )
        val mergedBookId = preferredRoot[KEY_BOOK_ID]
            ?: fallbackRoot[KEY_BOOK_ID]
            ?: mergedRoot[KEY_BOOK_ID]
        if (mergedBookId != null) mergedRoot[KEY_BOOK_ID] = mergedBookId

        val mergedFingerprint = preferredRoot[KEY_SOURCE_FINGERPRINT]
            ?: fallbackRoot[KEY_SOURCE_FINGERPRINT]
            ?: mergedRoot[KEY_SOURCE_FINGERPRINT]
        if (mergedFingerprint != null) mergedRoot[KEY_SOURCE_FINGERPRINT] = mergedFingerprint

        return encodeObject(JsonObject(mergedRoot.filterValues { it !is JsonNull }))
    }

    fun isCompatiblePayload(rawDataJson: String?): Boolean {
        val root = parseRootOrNull(rawDataJson) ?: return false
        return root.hasAnnotationPayload() ||
            root[KEY_READER_STATE] != null ||
            root[KEY_RICH_TEXT] != null ||
            root[KEY_VERSION] != null
    }

    /** Android and iOS use the same appDataFolder filename. */
    fun driveFileName(bookId: String): String = "$DRIVE_FILE_PREFIX$bookId.json"

    private fun chooseRemoteState(
        local: SharedPdfCloudSidecarPayload?,
        remote: SharedPdfCloudSidecarPayload?,
        preferRemoteOnConflict: Boolean
    ): Boolean {
        if (remote?.readerState == null) return false
        if (local?.readerState == null) return true
        return when {
            remote.modifiedTimestamp > local.modifiedTimestamp -> true
            remote.modifiedTimestamp < local.modifiedTimestamp -> false
            else -> preferRemoteOnConflict
        }
    }

    private fun stateElement(state: SharedPdfReaderState): JsonElement {
        return parseElementOrNull(SharedPdfReaderStateSerializer.encode(state))
            ?: JsonObject(emptyMap())
    }

    private fun richTextDocumentJson(root: JsonObject): String? {
        val element = root[KEY_RICH_TEXT] ?: return null
        if (element is JsonNull) return null
        return if (element is JsonPrimitive) {
            element.contentOrNull?.takeIf(String::isNotBlank)
        } else {
            encodeObject(element)
        }
    }

    private fun decodeStateElement(
        element: JsonElement?,
        fallbackPageCount: Int,
        fallbackPageIndex: Int
    ): SharedPdfReaderState? {
        if (element == null || element is JsonNull) return null
        val raw = if (element is JsonPrimitive) {
            element.contentOrNull
        } else {
            encodeObject(element)
        }
        return SharedPdfReaderStateSerializer.decode(
            raw = raw,
            fallbackPageCount = fallbackPageCount,
            fallbackPageIndex = fallbackPageIndex
        )
    }

    private fun parseRoot(raw: String?): JsonObject {
        return parseRootOrNull(raw) ?: JsonObject(emptyMap())
    }

    private fun parseRootOrNull(raw: String?): JsonObject? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val parsed = json.parseToJsonElement(raw).jsonObject
            parsed["data"]?.takeUnless { it is JsonNull }?.jsonObject ?: parsed
        }.getOrNull()
    }

    private fun parseElementOrNull(raw: String): JsonElement? {
        return runCatching { json.parseToJsonElement(raw) }.getOrNull()
    }

    private fun JsonObject.hasReaderStateBookmarksField(): Boolean {
        val stateElement = this[KEY_READER_STATE] ?: return false
        val stateObject = if (stateElement is JsonPrimitive) {
            stateElement.contentOrNull?.let(::parseElementOrNull)
        } else {
            stateElement
        }
        return (stateObject as? JsonObject)?.containsKey("bookmarks") == true
    }

    private fun encodeObject(element: JsonElement): String =
        json.encodeToString(JsonElement.serializer(), element)

    private fun JsonObject.hasAnnotationPayload(): Boolean {
        return containsKey(SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATIONS) ||
            containsKey(SharedPdfAnnotationSidecarCodec.KEY_LEGACY_INK) ||
            containsKey(SharedPdfAnnotationSidecarCodec.KEY_LEGACY_TEXT_BOXES) ||
            containsKey(SharedPdfAnnotationSidecarCodec.KEY_LEGACY_HIGHLIGHTS)
    }

    private fun JsonObject.stringOrNull(key: String): String? = runCatching {
        this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.takeIf(String::isNotBlank)

    private fun JsonObject.intOrNull(key: String): Int? = runCatching {
        this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull
    }.getOrNull()

    private fun JsonObject.longOrNull(key: String): Long? = runCatching {
        this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.longOrNull
    }.getOrNull()
}
