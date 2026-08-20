package com.aryan.reader.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/**
 * The arrangement of the two document panes in the PDF workspace.
 *
 * VERTICAL means two side-by-side panes separated by a vertical divider.
 * HORIZONTAL means two stacked panes separated by a horizontal divider.
 *
 * This is the user's preferred arrangement. The adaptive layout policy may
 * temporarily resolve it to the other orientation when the available space
 * cannot satisfy both panes' minimum readable size.
 */
enum class PdfSplitOrientation {
    VERTICAL,
    HORIZONTAL,
}

enum class PdfSplitPane {
    PRIMARY,
    SECONDARY,
}

/** The presentation selected by [PdfSplitWorkspaceState.resolveLayout]. */
enum class PdfSplitPresentation {
    SINGLE,
    SPLIT,
}

/**
 * Portable identity for a PDF that is assigned to a workspace slot.
 * Platform document handles and renderer state stay outside this model.
 *
 * [sessionId] is intentionally ephemeral. It changes whenever a document is
 * attached to a pane and is never written to durable storage. Renderers can
 * use it to reject callbacks belonging to a document that has since been
 * replaced in the same pane.
 */
data class PdfSplitPaneState(
    val bookId: String,
    val uriString: String,
    val sessionId: Long = UnassignedPdfSplitSessionId,
) {
    val canonicalBookId: String
        get() = bookId.trim()

    val canonicalUriString: String
        get() = canonicalizePdfUri(uriString)

    /** A diagnostic/persistence-friendly key; use [samePdfDocument] for matching. */
    val canonicalIdentity: String
        get() = "book:$canonicalBookId|uri:$canonicalUriString"
}

/**
 * Shared split workspace state. It contains only document identities and
 * reader-host coordination; platform file handles, renderer caches, and
 * viewport state belong to the pane host that consumes this model.
 */
data class PdfSplitWorkspaceState(
    val orientation: PdfSplitOrientation = PdfSplitOrientation.VERTICAL,
    val primary: PdfSplitPaneState? = null,
    val secondary: PdfSplitPaneState? = null,
    val focusedPane: PdfSplitPane = PdfSplitPane.PRIMARY,
    /** Kept as a source-compatible current-orientation value for existing UI. */
    val dividerFraction: Float = DefaultPdfSplitDividerFraction,
    /** Divider position used when [orientation] is [PdfSplitOrientation.VERTICAL]. */
    val verticalDividerFraction: Float = dividerFraction,
    /** Divider position used when [orientation] is [PdfSplitOrientation.HORIZONTAL]. */
    val horizontalDividerFraction: Float = dividerFraction,
    /** Monotonic in-memory revision used to reject stale workspace actions. */
    val revision: Long = 0L,
) {
    val isOpen: Boolean
        get() = primary != null

    val isSplit: Boolean
        get() = primary != null && secondary != null

    /** The document currently holding focus, independent of its slot name. */
    val focusedDocument: PdfSplitPaneState?
        get() = pane(focusedPane)

    /** The document a split-workspace close should return to. */
    val exitTargetDocument: PdfSplitPaneState?
        get() = focusedDocument ?: primary ?: secondary

    fun pane(id: PdfSplitPane): PdfSplitPaneState? = when (id) {
        PdfSplitPane.PRIMARY -> primary
        PdfSplitPane.SECONDARY -> secondary
    }

    fun dividerFractionFor(orientation: PdfSplitOrientation): Float = when (orientation) {
        PdfSplitOrientation.VERTICAL -> verticalDividerFraction
        PdfSplitOrientation.HORIZONTAL -> horizontalDividerFraction
    }

    /** Returns true only when the pane still owns the supplied renderer session. */
    fun isCurrentPaneSession(pane: PdfSplitPane, sessionId: Long): Boolean {
        return this.pane(pane)?.sessionId == sessionId && sessionId != UnassignedPdfSplitSessionId
    }

    /**
     * Repairs externally restored or hand-built state without assigning new
     * renderer sessions. Reducers and persistence restoration add fresh
     * sessions at their boundaries.
     */
    fun sanitized(): PdfSplitWorkspaceState {
        val cleanPrimary = primary?.sanitized()
        val cleanSecondary = secondary
            ?.sanitized()
            ?.takeUnless { candidate -> candidate.samePdfDocument(cleanPrimary) }

        // A secondary-only state cannot be rendered as a split workspace. Make
        // the surviving document the primary pane so callers never observe an
        // "open=false, secondary!=null" invalid state.
        val resolvedPrimary = cleanPrimary ?: cleanSecondary
        val resolvedSecondary = if (cleanPrimary == null) null else cleanSecondary
        val resolvedFocusedPane = if (resolvedSecondary == null) {
            PdfSplitPane.PRIMARY
        } else {
            focusedPane
        }
        val safeVertical = safeDividerFraction(verticalDividerFraction)
        val safeHorizontal = safeDividerFraction(horizontalDividerFraction)

        return copy(
            primary = resolvedPrimary,
            secondary = resolvedSecondary,
            focusedPane = resolvedFocusedPane,
            dividerFraction = safeDividerFraction(
                when (orientation) {
                    PdfSplitOrientation.VERTICAL -> safeVertical
                    PdfSplitOrientation.HORIZONTAL -> safeHorizontal
                },
            ),
            verticalDividerFraction = safeVertical,
            horizontalDividerFraction = safeHorizontal,
            revision = revision.coerceAtLeast(0L),
        )
    }

    /**
     * Creates new pane sessions after durable restoration. This invalidates
     * every renderer callback from the previous process/session by design.
     */
    fun withFreshSessions(): PdfSplitWorkspaceState {
        val clean = sanitized()
        return clean.copy(
            primary = clean.primary?.copy(
                sessionId = generatedPdfSplitSessionId(0L, PdfSplitPane.PRIMARY),
            ),
            secondary = clean.secondary?.copy(
                sessionId = generatedPdfSplitSessionId(0L, PdfSplitPane.SECONDARY),
            ),
            revision = 0L,
        )
    }
}

internal fun PdfSplitPaneState.sanitized(): PdfSplitPaneState? {
    val cleanBookId = bookId.trim()
    val cleanUri = uriString.trim()
    return if (cleanBookId.isBlank() || cleanUri.isBlank()) {
        null
    } else {
        copy(bookId = cleanBookId, uriString = cleanUri)
    }
}

/**
 * Matches a document by either stable library id or canonical URI. Book IDs
 * are preferred by callers, while URI matching handles imported/legacy rows
 * whose IDs may differ but still point at the same file.
 */
fun PdfSplitPaneState.samePdfDocument(other: PdfSplitPaneState?): Boolean {
    if (other == null) return false
    val sameBookId = canonicalBookId.isNotBlank() && canonicalBookId == other.canonicalBookId
    val sameUri = canonicalUriString.isNotBlank() && canonicalUriString == other.canonicalUriString
    return sameBookId || sameUri
}

/**
 * Canonicalizes only URI components whose comparison semantics are stable
 * across platforms. The scheme and authority are case-insensitive; path/query
 * casing and percent encoding are intentionally preserved. Fragments identify
 * a location inside a PDF, not a different document.
 */
fun canonicalizePdfUri(rawUri: String): String {
    val trimmed = rawUri.trim()
    if (trimmed.isBlank()) return ""
    val withoutFragment = trimmed.substringBefore('#')
    val schemeSeparator = withoutFragment.indexOf("://")
    if (schemeSeparator <= 0) return withoutFragment

    val scheme = withoutFragment.substring(0, schemeSeparator).lowercase()
    val rest = withoutFragment.substring(schemeSeparator + 3)
    val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' }
    val authority = if (authorityEnd < 0) rest else rest.substring(0, authorityEnd)
    val suffix = if (authorityEnd < 0) "" else rest.substring(authorityEnd)
    return scheme + "://" + authority.lowercase() + suffix
}

sealed interface PdfSplitWorkspaceAction {
    data class Open(
        val primary: PdfSplitPaneState,
        val secondary: PdfSplitPaneState,
        val orientation: PdfSplitOrientation = PdfSplitOrientation.VERTICAL,
    ) : PdfSplitWorkspaceAction

    data class FocusChanged(
        val pane: PdfSplitPane,
        val expectedRevision: Long? = null,
        val expectedSessionId: Long? = null,
    ) : PdfSplitWorkspaceAction

    data class OrientationChanged(
        val orientation: PdfSplitOrientation,
        val expectedRevision: Long? = null,
    ) : PdfSplitWorkspaceAction

    data class DividerChanged(
        val fraction: Float,
        /** Lets a gesture commit to the orientation it started in. */
        val orientation: PdfSplitOrientation? = null,
        val expectedRevision: Long? = null,
    ) : PdfSplitWorkspaceAction

    data class PaneOpened(
        val pane: PdfSplitPane,
        val document: PdfSplitPaneState,
        val expectedRevision: Long? = null,
        val expectedSessionId: Long? = null,
    ) : PdfSplitWorkspaceAction

    data class PaneClosed(
        val pane: PdfSplitPane,
        val expectedRevision: Long? = null,
        val expectedSessionId: Long? = null,
    ) : PdfSplitWorkspaceAction

    data object PanesSwapped : PdfSplitWorkspaceAction

    data object Closed : PdfSplitWorkspaceAction
}

/**
 * Pure reducer for workspace coordination. Optional revision/session guards
 * are intentionally part of actions so asynchronous platform events can be
 * safely ignored after a pane replacement or restoration.
 */
fun PdfSplitWorkspaceState.reduce(action: PdfSplitWorkspaceAction): PdfSplitWorkspaceState {
    if (!action.acceptsRevision(revision)) return this

    val candidate = when (action) {
        is PdfSplitWorkspaceAction.Open -> {
            val cleanPrimary = action.primary.sanitized() ?: return this
            val cleanSecondary = action.secondary.sanitized()
                ?.takeUnless { it.samePdfDocument(cleanPrimary) }
            PdfSplitWorkspaceState(
                orientation = action.orientation,
                primary = cleanPrimary.copy(
                    sessionId = generatedPdfSplitSessionId(nextWorkspaceRevision(revision), PdfSplitPane.PRIMARY),
                ),
                secondary = cleanSecondary?.copy(
                    sessionId = generatedPdfSplitSessionId(nextWorkspaceRevision(revision), PdfSplitPane.SECONDARY),
                ),
                focusedPane = PdfSplitPane.PRIMARY,
                verticalDividerFraction = verticalDividerFraction,
                horizontalDividerFraction = horizontalDividerFraction,
            )
        }

        is PdfSplitWorkspaceAction.FocusChanged -> {
            val current = pane(action.pane) ?: return this
            if (
                action.expectedSessionId != null &&
                current.sessionId != action.expectedSessionId
            ) {
                return this
            }
            copy(focusedPane = action.pane)
        }

        is PdfSplitWorkspaceAction.OrientationChanged -> copy(
            orientation = action.orientation,
            dividerFraction = dividerFractionFor(action.orientation),
        )

        is PdfSplitWorkspaceAction.DividerChanged -> {
            val changedOrientation = action.orientation ?: orientation
            val safeFraction = safeDividerFraction(action.fraction)
            when (changedOrientation) {
                PdfSplitOrientation.VERTICAL -> copy(
                    dividerFraction = safeFraction,
                    verticalDividerFraction = safeFraction,
                )

                PdfSplitOrientation.HORIZONTAL -> copy(
                    dividerFraction = safeFraction,
                    horizontalDividerFraction = safeFraction,
                )
            }
        }

        is PdfSplitWorkspaceAction.PaneOpened -> {
            val cleanDocument = action.document.sanitized() ?: return this
            val current = pane(action.pane)
            if (
                action.expectedSessionId != null &&
                current?.sessionId != action.expectedSessionId
            ) {
                return this
            }
            val other = pane(action.pane.other())
            if (cleanDocument.samePdfDocument(other)) return this
            if (current?.samePdfDocument(cleanDocument) == true) return this

            val sessionDocument = cleanDocument.copy(
                sessionId = generatedPdfSplitSessionId(nextWorkspaceRevision(revision), action.pane),
            )
            when (action.pane) {
                PdfSplitPane.PRIMARY -> copy(primary = sessionDocument)
                PdfSplitPane.SECONDARY -> copy(secondary = sessionDocument)
            }
        }

        is PdfSplitWorkspaceAction.PaneClosed -> {
            val current = pane(action.pane) ?: return this
            if (
                action.expectedSessionId != null &&
                current.sessionId != action.expectedSessionId
            ) {
                return this
            }
            when (action.pane) {
                PdfSplitPane.PRIMARY -> secondary?.let {
                    copy(
                        primary = it,
                        secondary = null,
                        focusedPane = PdfSplitPane.PRIMARY,
                    )
                } ?: copy(
                    primary = null,
                    secondary = null,
                    focusedPane = PdfSplitPane.PRIMARY,
                )

                PdfSplitPane.SECONDARY -> copy(
                    secondary = null,
                    focusedPane = if (focusedPane == PdfSplitPane.SECONDARY) {
                        PdfSplitPane.PRIMARY
                    } else {
                        focusedPane
                    },
                )
            }
        }

        is PdfSplitWorkspaceAction.PanesSwapped -> {
            if (secondary == null) {
                return this
            }
            copy(
                primary = secondary,
                secondary = primary,
                // Focus belongs to the document, so it follows that document
                // into its new slot rather than staying on the old slot.
                focusedPane = focusedPane.other(),
            )
        }

        is PdfSplitWorkspaceAction.Closed -> copy(
            primary = null,
            secondary = null,
            focusedPane = PdfSplitPane.PRIMARY,
        )
    }

    return commitWorkspaceTransition(this, candidate)
}

private fun PdfSplitWorkspaceAction.acceptsRevision(currentRevision: Long): Boolean = when (this) {
    is PdfSplitWorkspaceAction.Open -> true
    is PdfSplitWorkspaceAction.FocusChanged -> expectedRevision == null || expectedRevision == currentRevision
    is PdfSplitWorkspaceAction.OrientationChanged -> expectedRevision == null || expectedRevision == currentRevision
    is PdfSplitWorkspaceAction.DividerChanged -> expectedRevision == null || expectedRevision == currentRevision
    is PdfSplitWorkspaceAction.PaneOpened -> expectedRevision == null || expectedRevision == currentRevision
    is PdfSplitWorkspaceAction.PaneClosed -> expectedRevision == null || expectedRevision == currentRevision
    PdfSplitWorkspaceAction.PanesSwapped -> true
    PdfSplitWorkspaceAction.Closed -> true
}

private fun commitWorkspaceTransition(
    current: PdfSplitWorkspaceState,
    candidate: PdfSplitWorkspaceState,
): PdfSplitWorkspaceState {
    val currentClean = current.sanitized()
    val nextClean = candidate.sanitized().copy(revision = current.revision)
    return if (nextClean == currentClean) {
        currentClean
    } else {
        nextClean.copy(revision = nextWorkspaceRevision(current.revision))
    }
}

private fun PdfSplitPane.other(): PdfSplitPane = when (this) {
    PdfSplitPane.PRIMARY -> PdfSplitPane.SECONDARY
    PdfSplitPane.SECONDARY -> PdfSplitPane.PRIMARY
}

private fun safeDividerFraction(value: Float): Float {
    return if (value.isFinite()) {
        value.coerceIn(MinimumPdfSplitDividerFraction, MaximumPdfSplitDividerFraction)
    } else {
        DefaultPdfSplitDividerFraction
    }
}

private fun generatedPdfSplitSessionId(revision: Long, pane: PdfSplitPane): Long {
    val safeRevision = revision.coerceAtLeast(0L).coerceAtMost((Long.MAX_VALUE - 2L) / 2L)
    // Negative generated values cannot collide with positive platform tokens.
    return -((safeRevision * 2L) + pane.ordinal + 1L)
}

private fun nextWorkspaceRevision(revision: Long): Long {
    return if (revision == Long.MAX_VALUE) Long.MAX_VALUE else revision + 1L
}

/** Minimum dimensions are passed by the platform in real pixels at layout time. */
data class PdfSplitLayoutPlan(
    val presentation: PdfSplitPresentation,
    val orientation: PdfSplitOrientation,
    val firstPaneSizePx: Int,
    val secondPaneSizePx: Int,
    val dividerPositionPx: Int,
    val dividerFraction: Float,
) {
    val isSplit: Boolean
        get() = presentation == PdfSplitPresentation.SPLIT
}

/**
 * Resolves the current state into a concrete layout using actual available
 * dimensions. The preferred orientation is attempted first; if it cannot fit
 * both minimum pane sizes, the other orientation is attempted before falling
 * back to a single focused pane. This fallback is temporary and does not
 * mutate the user's preferred orientation.
 */
fun PdfSplitWorkspaceState.resolveLayout(
    availableWidthPx: Int,
    availableHeightPx: Int,
    minPaneWidthPx: Int = DefaultPdfSplitMinPaneWidthPx,
    minPaneHeightPx: Int = DefaultPdfSplitMinPaneHeightPx,
    dividerThicknessPx: Int = DefaultPdfSplitDividerThicknessPx,
): PdfSplitLayoutPlan {
    val width = availableWidthPx.coerceAtLeast(0)
    val height = availableHeightPx.coerceAtLeast(0)
    if (!isSplit || width == 0 || height == 0) {
        return singlePaneLayout(width = width, height = height, orientation = orientation)
    }

    val safeMinWidth = minPaneWidthPx.coerceAtLeast(1)
    val safeMinHeight = minPaneHeightPx.coerceAtLeast(1)
    val safeDivider = dividerThicknessPx.coerceAtLeast(0)
    val resolvedOrientation = sequenceOf(orientation, orientation.other())
        .firstOrNull { candidate ->
            val axis = if (candidate == PdfSplitOrientation.VERTICAL) width else height
            val minimum = if (candidate == PdfSplitOrientation.VERTICAL) safeMinWidth else safeMinHeight
            val crossAxis = if (candidate == PdfSplitOrientation.VERTICAL) height else width
            val crossMinimum = if (candidate == PdfSplitOrientation.VERTICAL) safeMinHeight else safeMinWidth
            axis >= (minimum * 2L + safeDivider).coerceAtMost(Int.MAX_VALUE.toLong()) &&
                crossAxis >= crossMinimum
        }
        ?: return singlePaneLayout(width = width, height = height, orientation = orientation)

    val axis = if (resolvedOrientation == PdfSplitOrientation.VERTICAL) width else height
    val minimum = if (resolvedOrientation == PdfSplitOrientation.VERTICAL) safeMinWidth else safeMinHeight
    val divider = safeDivider.coerceAtMost((axis - 1).coerceAtLeast(0))
    val contentAxis = (axis - divider).coerceAtLeast(0)
    val requestedFraction = safeDividerFraction(dividerFractionFor(resolvedOrientation))
    val first = (contentAxis * requestedFraction).roundToInt()
        .coerceIn(minimum, (contentAxis - minimum).coerceAtLeast(minimum))
    val second = (contentAxis - first).coerceAtLeast(0)
    return PdfSplitLayoutPlan(
        presentation = PdfSplitPresentation.SPLIT,
        orientation = resolvedOrientation,
        firstPaneSizePx = first,
        secondPaneSizePx = second,
        dividerPositionPx = first,
        dividerFraction = if (contentAxis == 0) DefaultPdfSplitDividerFraction else {
            first.toFloat() / contentAxis.toFloat()
        },
    )
}

private fun singlePaneLayout(
    width: Int,
    height: Int,
    orientation: PdfSplitOrientation,
): PdfSplitLayoutPlan {
    val axis = if (orientation == PdfSplitOrientation.VERTICAL) width else height
    return PdfSplitLayoutPlan(
        presentation = PdfSplitPresentation.SINGLE,
        orientation = orientation,
        firstPaneSizePx = axis,
        secondPaneSizePx = 0,
        dividerPositionPx = axis,
        dividerFraction = 1f,
    )
}

private fun PdfSplitOrientation.other(): PdfSplitOrientation = when (this) {
    PdfSplitOrientation.VERTICAL -> PdfSplitOrientation.HORIZONTAL
    PdfSplitOrientation.HORIZONTAL -> PdfSplitOrientation.VERTICAL
}

object PdfSplitWorkspaceJson {
    private const val SCHEMA_VERSION = 1
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    /** Encodes durable workspace preferences and document assignments only. */
    fun encode(state: PdfSplitWorkspaceState): String {
        val clean = state.sanitized()
        val root = JsonObject(
            mapOf(
                "schemaVersion" to JsonPrimitive(SCHEMA_VERSION),
                "orientation" to JsonPrimitive(clean.orientation.name),
                "focusedPane" to JsonPrimitive(clean.focusedPane.name),
                "focusedBookId" to clean.focusedDocument?.canonicalBookId.asJson(),
                "focusedUriString" to clean.focusedDocument?.canonicalUriString.asJson(),
                "verticalDividerFraction" to JsonPrimitive(clean.verticalDividerFraction),
                "horizontalDividerFraction" to JsonPrimitive(clean.horizontalDividerFraction),
                // Keep a legacy key so older readers can inspect the payload.
                "dividerFraction" to JsonPrimitive(clean.dividerFraction),
                "primary" to clean.primary?.toJsonObject().orJsonNull(),
                "secondary" to clean.secondary?.toJsonObject().orJsonNull(),
            ),
        )
        return json.encodeToString(JsonElement.serializer(), root)
    }

    /** Invalid or incomplete payloads recover to a closed workspace. */
    fun decodeOrEmpty(rawJson: String?): PdfSplitWorkspaceState {
        if (rawJson.isNullOrBlank()) return PdfSplitWorkspaceState()
        val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull()
            ?: return PdfSplitWorkspaceState()
        val orientation = root.string("orientation")
            ?.let { runCatching { PdfSplitOrientation.valueOf(it) }.getOrNull() }
            ?: PdfSplitOrientation.VERTICAL
        val legacyDivider = root.float("dividerFraction") ?: DefaultPdfSplitDividerFraction
        val vertical = root.float("verticalDividerFraction") ?: legacyDivider
        val horizontal = root.float("horizontalDividerFraction") ?: legacyDivider
        val primary = root["primary"]?.asPaneOrNull()
        val secondary = root["secondary"]?.asPaneOrNull()
        val parsedFocusedPane = root.string("focusedPane")
            ?.let { runCatching { PdfSplitPane.valueOf(it) }.getOrNull() }
            ?: PdfSplitPane.PRIMARY
        val focusedBookId = root.string("focusedBookId")
        val focusedUri = root.string("focusedUriString")
        val focusProbe = if (!focusedBookId.isNullOrBlank() && !focusedUri.isNullOrBlank()) {
            PdfSplitPaneState(focusedBookId, focusedUri)
        } else {
            null
        }
        val parsed = PdfSplitWorkspaceState(
            orientation = orientation,
            primary = primary,
            secondary = secondary,
            focusedPane = when {
                focusProbe != null && primary?.samePdfDocument(focusProbe) == true -> PdfSplitPane.PRIMARY
                focusProbe != null && secondary?.samePdfDocument(focusProbe) == true -> PdfSplitPane.SECONDARY
                else -> parsedFocusedPane
            },
            dividerFraction = when (orientation) {
                PdfSplitOrientation.VERTICAL -> vertical
                PdfSplitOrientation.HORIZONTAL -> horizontal
            },
            verticalDividerFraction = vertical,
            horizontalDividerFraction = horizontal,
        )
        return parsed.sanitized().withFreshSessions()
    }

    private fun PdfSplitPaneState.toJsonObject(): JsonObject = JsonObject(
        mapOf(
            "bookId" to JsonPrimitive(canonicalBookId),
            "uriString" to JsonPrimitive(uriString.trim()),
        ),
    )

    private fun JsonElement.asPaneOrNull(): PdfSplitPaneState? {
        val objectValue = runCatching { jsonObject }.getOrNull() ?: return null
        val bookId = objectValue.string("bookId") ?: return null
        val uriString = objectValue.string("uriString") ?: return null
        return PdfSplitPaneState(bookId, uriString).sanitized()
    }

    private fun JsonObject.string(name: String): String? {
        return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content }
            .getOrNull()
    }

    private fun JsonObject.float(name: String): Float? {
        return runCatching { this[name]?.jsonPrimitive?.floatOrNull }.getOrNull()
    }

    private fun String?.asJson(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

    private fun JsonObject?.orJsonNull(): JsonElement = this ?: JsonNull
}

const val UnassignedPdfSplitSessionId = 0L
const val DefaultPdfSplitDividerFraction = 0.5f
const val MinimumPdfSplitDividerFraction = 0.25f
const val MaximumPdfSplitDividerFraction = 0.75f
const val DefaultPdfSplitMinPaneWidthPx = 280
const val DefaultPdfSplitMinPaneHeightPx = 320
const val DefaultPdfSplitDividerThicknessPx = 1
