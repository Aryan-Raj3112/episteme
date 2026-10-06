package com.aryan.reader.shared.reader

/**
 * EPUB 3 Media Overlays: pre-recorded narration synchronized to the text, described by an
 * `application/smil+xml` document linked from the package manifest.
 *
 * Two things here are deliberately separated:
 *
 * - **[SharedMediaOverlayIndex] is cheap.** It is built from the OPF alone — one XML pass, already
 *   being done for every book. It answers "does this book have narration, how long is it, who read
 *   it", which is what a library badge and a player's total-time readout need.
 * - **[SharedMediaOverlayDocument] is expensive.** Parsing a SMIL body means reading one archive
 *   entry per spine item, and a well-produced book has one clip per *line*. The reference book has
 *   2315 clips across 155 documents. So bodies are parsed per chapter on demand behind a cache, not
 *   eagerly. Only the index is built at load time.
 *
 * Discovery is a pure addition: `EPUB RS §9` requires a reader that does not support overlays to
 * ignore both the manifest `media-overlay` attribute and the smil items, so a book that has them
 * and a reader that ignores them is a valid combination with no fallback path to get wrong.
 *
 * @see parseSharedClockValueMs for the clock grammar, which is not one format.
 */

/**
 * One `<par>`: a stretch of text and the audio that plays alongside it.
 *
 * Coordinates are in milliseconds from the start of [audioPath]. A `null` [clipEndMs] means "to the
 * end of the media", per `RS §9.2.2`, and must be clamped by the player against the real duration —
 * it cannot be resolved here without decoding the audio.
 */
data class SharedMediaOverlayClip(
    /** The `par`'s own id, for diagnostics. Never used for matching. */
    val parId: String?,
    /**
     * Position in playback order within its document, assigned at parse time.
     *
     * Not derivable later without an `indexOf` over the list, and batch anchor resolution needs it
     * per clip; the ordinal is already in hand where the clips are built.
     */
    val clipIndex: Int,
    /** Archive path of the text document this clip narrates, fragment stripped. */
    val textHref: String,
    /** Fragment from `text/@src`, i.e. the element to highlight. Null when the src has no fragment. */
    val elementId: String?,
    /** Archive path of the audio, or null for a TTS `par` (a `text` with no `audio`). */
    val audioPath: String?,
    val clipBeginMs: Long,
    val clipEndMs: Long?,
    /**
     * The `par`'s own `epub:type` values merged with those of every ancestor `seq`, with any
     * namespace prefix stripped. Inherited because a publisher marks a whole run of page breaks by
     * typing the enclosing `seq`, and a player that only read the `par` would skip nothing.
     */
    val epubTypes: Set<String>,
    /**
     * How many `seq` elements enclose this `par`. `0` means the `par` sits directly under `body`,
     * which real files do (`SmokeTestFXL`'s `basic_tests.smil` does exactly that).
     */
    val seqDepth: Int,
) {
    /** True when this clip spans an EOF-clamped region and the player must clamp it. */
    val runsToEndOfMedia: Boolean get() = clipEndMs == null

    /** True when there is no audio, so the fragment is to be spoken by a TTS engine instead. */
    val isNarrationless: Boolean get() = audioPath == null

    /**
     * Skippability is what a user is *offered*: "this is a footnote, do you want to hear it".
     * `RS §9.4` lists `footnote`, `endnote` and `pagebreak`.
     */
    val isSkippable: Boolean
        get() = epubTypes.any { it in SharedMediaOverlaySkippableTypes }

    /**
     * Escapability is where a fragment may be left and re-entered — a table, list, figure or aside
     * whose audio runs long. `RS §9.4`.
     */
    val isEscapable: Boolean
        get() = epubTypes.any { it in SharedMediaOverlayEscapableTypes }
}

/** `RS §9.4` skippable types, local names, namespace prefix already stripped. */
val SharedMediaOverlaySkippableTypes: Set<String> = setOf("footnote", "endnote", "pagebreak")

/** `RS §9.4` escapable types, local names, namespace prefix already stripped. */
val SharedMediaOverlayEscapableTypes: Set<String> = setOf("table", "list", "figure", "aside")

/**
 * Every clip for one spine item, in playback order.
 *
 * [spineItemIndex] lives here rather than on each clip because a clip cannot exist outside its
 * document; duplicating it per clip would let the two disagree.
 */
data class SharedMediaOverlayDocument(
    val spineItemIndex: Int,
    /** The `epub:textref` of the enclosing `body`/`seq`, resolved to an archive path. */
    val textHref: String?,
    /** Flattened `par`s in document order — nesting collapsed, ancestor types merged in. */
    val clips: List<SharedMediaOverlayClip>,
    /** `media:duration` for this overlay, from the OPF meta that refines this document's smil id. */
    val declaredDurationMs: Long?,
) {
    val isEmpty: Boolean get() = clips.isEmpty()

    /**
     * True when the document has nothing to play. A malformed SMIL parses to null rather than to an
     * empty document, so this only fires for a genuinely empty `<body/>`.
     */
    val isPlayable: Boolean get() = clips.any { it.audioPath != null }
}

/**
 * The package-level overlay facts, built without reading a single SMIL body.
 *
 * This is the only part built at book-load time. See the type-level note on why.
 */
data class SharedMediaOverlayIndex(
    /** spine item index -> the SMIL entry that narrates it. Only items that have one appear. */
    val smilPathBySpineItem: Map<Int, String>,
    /** spine item index -> smil **manifest id**, so `media:duration refines="#id"` can be matched. */
    val smilIdBySpineItem: Map<Int, String>,
    /** `<meta property="media:duration">` with no `refines`, i.e. the whole book's runtime. */
    val totalDurationMs: Long?,
    /** `<meta property="media:narrator">`. */
    val narrator: String?,
    /** `<meta property="media:active-class">` — the publisher CSS class for the spoken fragment. */
    val activeClass: String?,
    /** `<meta property="media:playback-active-class">`, applied to the document element. */
    val playbackActiveClass: String?,
    /** spine item index -> its own declared duration, from the refining `media:duration` metas. */
    val declaredDurationMsBySpineItem: Map<Int, Long>,
) {
    val hasOverlays: Boolean get() = smilPathBySpineItem.isNotEmpty()

    fun documentFor(spineItemIndex: Int): SharedMediaOverlayDocument? =
        smilPathBySpineItem[spineItemIndex]?.let {
            SharedMediaOverlayDocument(
                spineItemIndex = spineItemIndex,
                textHref = null,
                clips = emptyList(),
                declaredDurationMs = declaredDurationMsBySpineItem[spineItemIndex]
            )
        }

    companion object {
        val EMPTY = SharedMediaOverlayIndex(
            smilPathBySpineItem = emptyMap(),
            smilIdBySpineItem = emptyMap(),
            totalDurationMs = null,
            narrator = null,
            activeClass = null,
            playbackActiveClass = null,
            declaredDurationMsBySpineItem = emptyMap()
        )
    }
}

/**
 * Builds the package-level index from the parsed manifest, spine and OPF metas.
 *
 * Reads only data the package loader already has, so this costs no extra I/O.
 *
 * @param manifest manifest item id -> item, with [MobileEpubManifestItem.mediaOverlay] populated.
 * @param spineIds spine item ids in document order; the index into this list is the spine item index.
 * @param metaElements the OPF `<meta>` elements.
 */
fun sharedMediaOverlayIndex(
    manifest: Map<String, MobileEpubManifestItem>,
    spineIds: List<String>,
    metaElements: List<MobileEpubMetaElement>
): SharedMediaOverlayIndex {
    val smilItemById = manifest.values.filter { it.mediaType.equals(SmilMediaType, ignoreCase = true) }
        .associateBy { it.id }
    // Manifest ids are the target of media:duration refines, so the reverse map is needed twice.
    val spineItemById = manifest

    val smilPathBySpineItem = LinkedHashMap<Int, String>()
    val smilIdBySpineItem = LinkedHashMap<Int, String>()
    spineIds.forEachIndexed { spineItemIndex, id ->
        val overlayId = spineItemById[id]?.mediaOverlay?.trim()?.takeIf(String::isNotEmpty) ?: return@forEachIndexed
        val smil = smilItemById[overlayId] ?: return@forEachIndexed
        smilPathBySpineItem[spineItemIndex] = smil.absPath
        smilIdBySpineItem[spineItemIndex] = smil.id
    }

    val spineItemIndexBySmilId = smilIdBySpineItem.entries.associate { (spineItemIndex, smilId) -> smilId to spineItemIndex }
    val declaredDurationMsBySpineItem = LinkedHashMap<Int, Long>()
    var totalDurationMs: Long? = null
    var narrator: String? = null
    var activeClass: String? = null
    var playbackActiveClass: String? = null

    for (meta in metaElements) {
        val property = meta.property?.trim()?.takeIf(String::isNotEmpty) ?: continue
        val refinesId = meta.refines?.trim()?.removePrefix("#")?.takeIf(String::isNotEmpty)
        // A property meta carries its value as text content; a refines-scoped one may use `content`.
        val value = meta.text?.trim()?.takeIf(String::isNotEmpty) ?: meta.content?.trim()
        when (property) {
            "media:duration" -> {
                val ms = value?.let(::parseSharedClockValueMs) ?: continue
                val target = refinesId?.let(spineItemIndexBySmilId::get)
                if (target != null) declaredDurationMsBySpineItem[target] = ms else totalDurationMs = ms
            }
            "media:narrator" -> narrator = value ?: narrator
            "media:active-class" -> activeClass = value ?: activeClass
            "media:playback-active-class" -> playbackActiveClass = value ?: playbackActiveClass
        }
    }

    return SharedMediaOverlayIndex(
        smilPathBySpineItem = smilPathBySpineItem,
        smilIdBySpineItem = smilIdBySpineItem,
        totalDurationMs = totalDurationMs,
        narrator = narrator,
        activeClass = activeClass,
        playbackActiveClass = playbackActiveClass,
        declaredDurationMsBySpineItem = declaredDurationMsBySpineItem
    )
}

/** The SMIL media type, as it appears in a manifest `media-type`. */
const val SmilMediaType = "application/smil+xml"

/**
 * One flattened `par`, before it is bound to a spine item.
 *
 * Private because [parseSharedMediaOverlayXml] is the only way to build one, and building one by
 * hand is how a path ends up unresolved.
 */
private class SharedMediaOverlayParsedPar(
    val parId: String?,
    val textHref: String,
    val elementId: String?,
    val audioPath: String?,
    val hasAudio: Boolean,
    val clipBeginMs: Long,
    val clipEndMs: Long?,
    val epubTypes: Set<String>,
    val seqDepth: Int
)

/**
 * Parses one SMIL body into a document bound to [spineItemIndex].
 *
 * Returns null for anything unparseable — a malformed overlay must cost that chapter its narration,
 * never the whole book. `RS §9.2` gives a reader no obligation to recover from a broken overlay,
 * and failing the entire open on one bad file would be a far worse outcome.
 *
 * @param raw the SMIL document text.
 * @param smilEntryPath the archive path of the SMIL, used to resolve relative `src` values.
 * @param spineItemIndex the spine item this overlay narrates.
 * @param declaredDurationMs `media:duration` for this overlay, if the OPF declared one.
 */
fun parseSharedMediaOverlayXml(
    raw: String,
    smilEntryPath: String,
    spineItemIndex: Int,
    declaredDurationMs: Long? = null
): SharedMediaOverlayDocument? {
    val root = parseSharedXmlDocument(raw) ?: return null
    // `parseSharedXmlDocument` returns the root element itself when the document is well formed, so
    // `<smil>` is the node we hold rather than a descendant of a document wrapper. Both shapes have
    // to be accepted: a wrapper appears when the parser hoists a leading comment or PI.
    val smil = root.takeIf { it.localName == "smil" } ?: root.firstDescendant("smil") ?: return null
    val body = smil.children.firstOrNull { it.localName == "body" }
        ?: smil.firstDescendant("body")
        ?: return null

    // `epub:textref` is required on `seq` and optional on `body`. The ReadBeyond books put it on the
    // `seq` and leave `body` bare, so a body-only read finds nothing in half the real corpus. The
    // first `seq` wins as the document's text document; it is only a fallback for a bare `#fragment`
    // `src`, so the choice is harmless when every `par` spells its path out.
    val bodyTextRef = body.attributeByLocalName("textref")
        ?.let { sharedMediaOverlayResolveTextPath(smilEntryPath, it) }
        ?: body.descendants("seq").firstNotNullOfOrNull { seq ->
            seq.attributeByLocalName("textref")?.let { sharedMediaOverlayResolveTextPath(smilEntryPath, it) }
        }

    val pars = mutableListOf<SharedMediaOverlayParsedPar>()
    // Document order is playback order, so a pre-order walk with inherited type/depth is what the
    // flattening needs. `seqDepth` starts at 0 for a `par` directly under `body`.
    fun walk(node: SharedXmlDocumentNode, inheritedTypes: Set<String>, depth: Int) {
        for (child in node.children) {
            when (child.localName) {
                "seq" -> {
                    val own = child.sharedMediaOverlayTypes()
                    walk(child, inheritedTypes + own, depth + 1)
                }
                "par" -> child.sharedMediaOverlayParsePar(smilEntryPath, bodyTextRef, inheritedTypes, depth)
                    ?.let(pars::add)
                else -> Unit
            }
        }
    }
    walk(body, emptySet(), 0)

    if (pars.isEmpty()) {
        // A `<body/>` with no `par` is legal but has nothing to play. Distinguish it from a parse
        // failure: an empty document is a fact about the book, not a reason to drop the book.
        return SharedMediaOverlayDocument(
            spineItemIndex = spineItemIndex,
            textHref = bodyTextRef,
            clips = emptyList(),
            declaredDurationMs = declaredDurationMs
        )
    }

    return SharedMediaOverlayDocument(
        spineItemIndex = spineItemIndex,
        textHref = bodyTextRef,
        clips = pars.mapIndexed { index, par ->
            SharedMediaOverlayClip(
                parId = par.parId,
                clipIndex = index,
                textHref = par.textHref,
                elementId = par.elementId,
                audioPath = par.audioPath,
                clipBeginMs = par.clipBeginMs,
                clipEndMs = par.clipEndMs,
                epubTypes = par.epubTypes,
                seqDepth = par.seqDepth
            )
        },
        declaredDurationMs = declaredDurationMs
    )
}

private fun SharedXmlDocumentNode.sharedMediaOverlayTypes(): Set<String> =
    attributeByLocalName("type")
        ?.split(WHITESPACE_REGEX)
        ?.mapNotNull { token -> token.substringAfter(':').trim().lowercase().takeIf(String::isNotEmpty) }
        ?.toSet()
        .orEmpty()

private fun SharedXmlDocumentNode.sharedMediaOverlayParsePar(
    smilEntryPath: String,
    bodyTextRef: String?,
    inheritedTypes: Set<String>,
    depth: Int
): SharedMediaOverlayParsedPar? {
    // `text` is required by RS §9.2.1. A `par` without one cannot be highlighted, so it is skipped
    // rather than guessed at.
    val text = children.firstOrNull { it.localName == "text" } ?: return null
    val textSrc = text.attributeByLocalName("src")?.trim()?.takeIf(String::isNotEmpty) ?: return null

    val fragment = textSrc.substringAfter('#', missingDelimiterValue = "").takeIf(String::isNotEmpty)
    val pathPart = textSrc.substringBefore('#')
    // A bare `#fragment` means "the document this overlay narrates", per the textref of the
    // enclosing body/seq. Producers do emit it.
    val textHref = sharedMediaOverlayResolveTextPath(smilEntryPath, pathPart)
        ?: bodyTextRef
        ?: return null

    val audio = children.firstOrNull { it.localName == "audio" }
    val audioSrc = audio?.attributeByLocalName("src")?.trim()?.takeIf(String::isNotEmpty)
    val audioPath = audioSrc?.let { sharedMediaOverlayResolvePath(smilEntryPath, it) }

    // `RS §9.2.2`, and the two cases must not be conflated: an *absent* `clipBegin` is 0 and an absent
    // `clipEnd` means "to the end of the media", but a *present and malformed* one is a broken file.
    // Silently reading a malformed `clipBegin` as 0 would start the wrong paragraph, so that drops
    // the `par`; an absent one must not be mistaken for a malformed one.
    val clipBeginMs = when (val raw = audio?.attributeByLocalName("clipbegin")) {
        null -> 0L
        else -> parseSharedClockValueMs(raw) ?: return null
    }
    val clipEndMs = when (val raw = audio?.attributeByLocalName("clipend")) {
        null -> null
        else -> parseSharedClockValueMs(raw) ?: return null
    }

    return SharedMediaOverlayParsedPar(
        parId = attributeByLocalName("id")?.trim()?.takeIf(String::isNotEmpty),
        textHref = textHref,
        elementId = fragment,
        audioPath = audioPath,
        hasAudio = audio != null,
        clipBeginMs = clipBeginMs,
        clipEndMs = clipEndMs,
        epubTypes = inheritedTypes + sharedMediaOverlayTypes(),
        seqDepth = depth
    )
}

/** Resolves a `text/@src` path (no fragment) against the SMIL's own directory. */
private fun sharedMediaOverlayResolveTextPath(smilEntryPath: String, pathPart: String): String? =
    pathPart.trim().takeIf(String::isNotEmpty)?.let { sharedMediaOverlayResolvePath(smilEntryPath, it) }

/**
 * Resolves a reference relative to the SMIL's directory.
 *
 * Delegates to `safeEpubPathOrNull`, which normalizes `.` / `..` and rejects traversal, rather than
 * re-deriving path arithmetic — an archive entry path that escaped the container is a zip-slip
 * vector, not a formatting detail.
 *
 * Remote and inline references are rejected outright rather than truncated at the scheme colon.
 * A `src="https://cdn.example.com/a.mp3"` truncated to `https` would resolve to a nonsense archive
 * entry; returning null keeps the clip's audio unresolved, which is visible and honest.
 */
private fun sharedMediaOverlayResolvePath(ownerPath: String, reference: String): String? {
    val trimmed = reference.trim().substringBefore('#').substringBefore('?').trim()
    if (trimmed.isEmpty()) return null
    if (REMOTE_REFERENCE_REGEX.containsMatchIn(trimmed)) return null
    val decoded = trimmed.percentDecodeEpubPath().takeIf(String::isNotBlank) ?: return null
    val base = ownerPath.substringBeforeLast('/', missingDelimiterValue = "")
    return safeEpubPathOrNull(
        if (decoded.startsWith('/')) decoded.removePrefix("/") else if (base.isBlank()) decoded else "$base/$decoded"
    )
}

/**
 * A `scheme:` prefix on a reference. `a:b.mp3` is legal in a path, so the check is for a real scheme
 * shape rather than any colon — which is also why it is applied before, not after, normalization.
 */
private val REMOTE_REFERENCE_REGEX = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

private val WHITESPACE_REGEX = Regex("\\s+")

/**
 * Parses an EPUB clock value to milliseconds, or null when it is not a clock value.
 *
 * This is `EPUB RS §9` "Clock value" (its own H.4), and it is **not** one format. The grammar:
 *
 * ```
 * clock-value        ::= full-clock-value | partial-clock-value | timecount-value
 * full-clock-value   ::= hour ":" minute ":" second (fractional-second | ":" milliseconds)?
 * partial-clock-value::= minute ":" second (fractional-second | ":" milliseconds)?
 * timecount-value    ::= timecount metric?
 * timecount          ::= digits | digits "." digits
 * metric             ::= "h" | "min" | "s" | "ms"
 * ```
 *
 * Things worth knowing, all of which occur in real files:
 *
 * - **Hours are not fixed-width and not capped at 24.** `124:59:36` is a legal clock value; a
 *   parser that validates hours as `2DIGIT` rejects real audiobooks.
 * - **`h:mm:ss:mmm` exists** as an alternative to a fractional second: `1:02:03:004` is one hour,
 *   two minutes, three seconds and four milliseconds. Treating the third field as `seconds.fraction`
 *   alone silently loses the last component.
 * - **A bare number is seconds**, not milliseconds: `12.345` is 12.345 seconds. Only the explicit
 *   `ms` metric means milliseconds.
 * - `ms` must be matched before `s`, or `2345ms` parses as 2345 seconds.
 *
 * Returns null rather than 0 for an unparseable value so a caller can distinguish "no timing given"
 * from "timing given and malformed" — the two need different handling (`RS §9.2.2`).
 */
fun parseSharedClockValueMs(raw: String): Long? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    return if (':' in value) sharedColonClockMs(value) else sharedTimecountClockMs(value)
}

/** The `full-clock-value` and `partial-clock-value` branches: anything with a colon in it. */
private fun sharedColonClockMs(value: String): Long? {
    val parts = value.split(':')
    if (parts.size !in 2..4) return null
    if (parts.any { it.isEmpty() }) return null

    // Which field holds the seconds depends on the form: the 2- and 3-part values end in
    // `second[.fraction]`, while the 4-part value ends in a standalone `milliseconds` field and
    // keeps `second` one position earlier. Reading the last field as seconds in every case turns
    // `1:02:03:004` into 1h2m**4**s, an hour-second error that still looks like a plausible time.
    if (parts.size == 4) {
        val hours = parts[0].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val minutes = parts[1].toLongOrNull()?.takeIf { it in 0..59 } ?: return null
        val seconds = parts[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        val millis = parts[3].toLongOrNull()?.takeIf { it in 0..999 } ?: return null
        return ((hours * 60L + minutes) * 60L + seconds) * 1000L + millis
    }

    val last = parts.last()
    val dot = last.indexOf('.')
    val seconds = (if (dot >= 0) last.substring(0, dot) else last).toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    val fractionMs = if (dot >= 0) sharedFractionalSecondsToMillis(last.substring(dot + 1)) ?: return null else 0L

    return if (parts.size == 2) {
        // minute ":" second [fractional]
        val minutes = parts[0].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        (minutes * 60L + seconds) * 1000L + fractionMs
    } else {
        // hour ":" minute ":" second [fractional]
        val hours = parts[0].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val minutes = parts[1].toLongOrNull()?.takeIf { it in 0..59 } ?: return null
        ((hours * 60L + minutes) * 60L + seconds) * 1000L + fractionMs
    }
}

/** The `timecount-value` branch: a number with an optional metric, defaulting to seconds. */
private fun sharedTimecountClockMs(value: String): Long? {
    // `ms` is matched before `s`, or `2345ms` reads as 2345 seconds.
    val (number, unitMillis) = when {
        value.endsWith("ms", ignoreCase = true) -> value.dropLast(2) to 1L
        value.endsWith("min", ignoreCase = true) -> value.dropLast(3) to 60_000L
        value.endsWith("h", ignoreCase = true) -> value.dropLast(1) to 3_600_000L
        value.endsWith("s", ignoreCase = true) -> value.dropLast(1) to 1_000L
        else -> value to 1_000L
    }
    val dot = number.indexOf('.')
    if (dot < 0) {
        val count = number.toLongOrNull()?.takeIf { it >= 0 } ?: return null
        return count * unitMillis
    }
    val whole = number.substring(0, dot).toLongOrNull()?.takeIf { it >= 0 } ?: return null
    val fractionMs = sharedFractionalSecondsToMillis(number.substring(dot + 1)) ?: return null
    return whole * unitMillis + fractionMs
}

/**
 * Converts a fractional-seconds digit string to whole milliseconds.
 *
 * Only the first three digits are significant, so `.5` is 500ms and `.78` is 780ms. Rejects a
 * non-digit rather than truncating, because `.` followed by nothing is not a clock value.
 */
private fun sharedFractionalSecondsToMillis(fractionText: String): Long? {
    if (fractionText.isEmpty() || !fractionText.all { it.isDigit() }) return null
    val millis = fractionText.take(3).padEnd(3, '0').toLongOrNull() ?: return null
    return millis
}