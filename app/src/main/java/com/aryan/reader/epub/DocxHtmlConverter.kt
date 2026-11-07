package com.aryan.reader.epub

import com.aryan.reader.shared.office.SharedOfficeOxide
import org.zwobble.mammoth.DocumentConverter
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile

/**
 * DOCX → HTML for [SingleFileImporter].
 *
 * Tries the native `office_oxide` bridge first and falls back to mammoth
 * (`org.zwobble.mammoth`) when the bridge is unavailable, does not support the
 * file, or fails on it. Mammoth stays until parity is proven — see
 * `docs/office-oxide-integration-plan.md` — so every failure path here must end
 * in mammoth, never in a user-facing error the old path would have survived.
 *
 * Images are requested embedded (`data:` URIs), matching mammoth's default
 * output: the downstream `parseHtml` safelist already allows `data:` image
 * sources, so either engine's fragment flows through the same pipeline.
 */
internal object DocxHtmlConverter {

    /**
     * Flip to false to force the legacy mammoth path — the A/B switch for
     * parity testing. Volatile so a debug setting could toggle it at runtime.
     */
    @Volatile
    var preferOfficeOxide: Boolean = true

    /**
     * The native call, as a seam so JVM unit tests can fake it: the real bridge
     * needs a device, its `.so` only ships in the APK.
     */
    internal fun interface OxideRenderer {
        /** Returns the HTML fragment, or null when the bridge cannot read [bytes]. */
        fun render(bytes: ByteArray): String?
    }

    private val sharedOxide = OxideRenderer { bytes ->
        if (!SharedOfficeOxide.supports("docx")) {
            null
        } else {
            SharedOfficeOxide.renderHtml(bytes, "docx", embedImages = true)
        }
    }

    private val sharedDocOxide = OxideRenderer { bytes ->
        if (!SharedOfficeOxide.supports("doc")) {
            null
        } else {
            SharedOfficeOxide.renderHtml(bytes, "doc", embedImages = true)
        }
    }

    /**
     * Raster image parts worth gating on. office_oxide only Base64-embeds
     * png/jpeg/gif (anything else has no MIME in its IR and degrades to alt
     * text); mammoth embeds the raw bytes whatever the type — so for bmp/webp
     * the fallback wins too.
     */
    private val RASTER_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

    /**
     * Converts an already-validated DOCX file to an HTML fragment. Never returns
     * blank: mammoth is the last resort, and allocation failures surface as the
     * same too-large error the old path threw.
     */
    fun convert(
        sourceDocxFile: File,
        originalBookNameHint: String,
        oxide: OxideRenderer = sharedOxide,
    ): String {
        if (preferOfficeOxide) {
            val viaOxide = renderViaOfficeOxide(sourceDocxFile, originalBookNameHint, oxide)
            if (!viaOxide.isNullOrBlank() &&
                !dropsEmbeddedImages(sourceDocxFile, viaOxide, originalBookNameHint)
            ) {
                return collapseRedundantBreaks(viaOxide)
            }
        } else {
            Timber.tag("FileOpenPerf")
                .d("[DOCX] office_oxide disabled by flag; using mammoth | file=$originalBookNameHint")
        }
        return renderViaMammoth(sourceDocxFile, originalBookNameHint)
            .let(::collapseRedundantBreaks)
    }

    /**
     * Converts an already-validated legacy DOC (CFB binary) file to an HTML
     * fragment. Oxide-only by necessity: mammoth cannot parse CFB, and there
     * was no legacy DOC path to regress — so failure surfaces as the same
     * clean too-large/unreadable error shape the DOCX path uses, never a raw
     * exception. There is no image gate here either: with no fallback engine,
     * a gate could only turn a readable document into an error.
     */
    fun convertDoc(
        sourceDocFile: File,
        originalBookNameHint: String,
        oxide: OxideRenderer = sharedDocOxide,
    ): String {
        if (!preferOfficeOxide) {
            Timber.tag("FileOpenPerf")
                .d("[DOC] office_oxide disabled by flag; no legacy DOC path exists | file=$originalBookNameHint")
        } else {
            val viaOxide = renderViaOfficeOxide(sourceDocFile, originalBookNameHint, oxide, tag = "DOC")
            if (!viaOxide.isNullOrBlank()) {
                val cleaned = stripUnrenderableImages(collapseRedundantBreaks(viaOxide), originalBookNameHint)
                return cleaned
            }
        }
        throw IllegalStateException("This DOC file could not be opened on this device.")
    }

    private fun renderViaOfficeOxide(
        sourceDocxFile: File,
        originalBookNameHint: String,
        oxide: OxideRenderer,
        tag: String = "DOCX",
    ): String? {
        val started = System.currentTimeMillis()
        return try {
            val bytes = sourceDocxFile.readBytes()
            val html = oxide.render(bytes)
            if (html.isNullOrBlank()) {
                Timber.tag("FileOpenPerf").d(
                    "[$tag] office_oxide could not read file " +
                        "| file=$originalBookNameHint inBytes=${bytes.size}",
                )
                null
            } else {
                Timber.tag("FileOpenPerf").d(
                    "[$tag] office_oxide conversion done " +
                        "| inBytes=${bytes.size} outChars=${html.length} " +
                        "imgTags=${html.split("<img").size - 1} " +
                        "elapsed=${System.currentTimeMillis() - started}ms",
                )
                Timber.tag(DOCX_IMAGE_DEBUG_TAG).d(
                    "[$tag] oxide | inBytes=${bytes.size} outChars=${html.length} " +
                        "imgTags=${html.split("<img").size - 1}",
                )
                html
            }
        } catch (oom: OutOfMemoryError) {
            Timber.e(oom, "$tag conversion ran out of memory for $originalBookNameHint")
            throw IllegalStateException("This $tag file is too large to open safely on this device.")
        } catch (error: Exception) {
            Timber.tag("FileOpenPerf")
                .w(error, "[$tag] office_oxide failed | file=$originalBookNameHint")
            null
        }
    }

    /**
     * True when the DOCX carries raster images but the HTML has no `<img`: the
     * engine dropped them — office_oxide 0.1.13 does exactly this for
     * align-positioned floating anchors (`wp:anchor` with `<wp:align>` instead
     * of `<wp:posOffset>`), while mammoth embeds them. Falling back keeps the
     * better render; when upstream stops dropping images the gate stops firing
     * on its own.
     */
    private fun dropsEmbeddedImages(
        sourceDocxFile: File,
        html: String,
        originalBookNameHint: String,
    ): Boolean {
        if (html.contains("<img")) {
            return false
        }
        val expected = countEmbeddedRasterImages(sourceDocxFile)
        Timber.tag(DOCX_IMAGE_DEBUG_TAG).d(
            "[DOCX] gate | mediaParts=$expected oxideImgTags=0 file=$originalBookNameHint",
        )
        if (expected > 0) {
            Timber.tag("FileOpenPerf").d(
                "[DOCX] office_oxide dropped $expected embedded image(s); falling back to mammoth " +
                    "| file=$originalBookNameHint",
            )
            return true
        }
        return false
    }

    /**
     * Counts raster image parts in the package without decompressing anything —
     * a central-directory scan only. Native charts (`word/charts/`) are not
     * images: neither engine can rasterize them, and office_oxide's chart-text
     * extraction is the better fallback already.
     */
    private fun countEmbeddedRasterImages(sourceDocxFile: File): Int {
        try {
            ZipFile(sourceDocxFile).use { zip ->
                var count = 0
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) {
                        continue
                    }
                    val name = entry.name.lowercase()
                    if (name.startsWith("word/media/") &&
                        RASTER_IMAGE_EXTENSIONS.any { name.endsWith(".$it") }
                    ) {
                        count++
                    }
                }
                return count
            }
        } catch (error: Exception) {
            // Never let the gate itself fail the import: unreadable archives are
            // already rejected by import validation, and mammoth reports the rest.
            Timber.tag("FileOpenPerf")
                .w(error, "[DOCX] image gate could not scan archive; skipping gate")
            return 0
        }
    }

    private fun renderViaMammoth(sourceDocxFile: File, originalBookNameHint: String): String {
        val started = System.currentTimeMillis()
        try {
            val html = sourceDocxFile.inputStream().use { stream ->
                DocumentConverter().convertToHtml(stream).value ?: ""
            }
            Timber.tag("FileOpenPerf").d(
                "[DOCX] mammoth conversion done " +
                    "| outChars=${html.length} elapsed=${System.currentTimeMillis() - started}ms",
            )
            Timber.tag(DOCX_IMAGE_DEBUG_TAG).d(
                "[DOCX] mammoth | outChars=${html.length} imgTags=${html.split("<img").size - 1}",
            )
            return html
        } catch (oom: OutOfMemoryError) {
            Timber.e(oom, "DOCX conversion ran out of memory for $originalBookNameHint")
            throw IllegalStateException("This DOCX file is too large to open safely on this device.")
        }
    }
}

/**
 * Collapses runs of horizontal rules to one. office_oxide recovers an empty
 * bordered paragraph as `<hr />`, so a stack of empty paragraphs (routine
 * around chart drawings and section gaps) becomes a stack of rules that reads
 * as heavy banding around the content. A single rule keeps the break semantics.
 * Mammoth never emits rules from DOCX content, so it passes through unchanged.
 */
internal fun collapseRedundantBreaks(html: String): String =
    HR_RUN_REGEX.replace(html, "<hr />")

private val HR_RUN_REGEX = Regex("<hr(\\s*/)?>(\\s*<hr(\\s*/)?>)+", RegexOption.IGNORE_CASE)

/**
 * Drops `<img>` tags whose payload nothing in this stack can rasterize: vector
 * metafiles (`x-emf`, `x-wmf`) and TIFF. Legacy DOC pictures surface in whatever
 * format the writer stored — LibreOffice conversions wrap charts as EMF — and
 * a broken-image icon is worse than clean absence. Raster formats
 * (png/jpeg/gif/bmp) pass through untouched. DOC path only: DOCX renders never
 * emit these MIMEs.
 */
internal fun stripUnrenderableImages(html: String, fileHintForLog: String = ""): String {
    var stripped = 0
    val cleaned = UNRENDERABLE_IMG_REGEX.replace(html) {
        stripped++
        ""
    }
    if (stripped > 0) {
        Timber.tag(DOCX_IMAGE_DEBUG_TAG).d(
            "[DOC] unrenderableImgs=$stripped removed (metafile/tiff) file=$fileHintForLog",
        )
    }
    return cleaned
}

private val UNRENDERABLE_IMG_REGEX =
    Regex("""<img\b[^>]*src="data:image/(x-emf|x-wmf|tiff)[^>]*>""", RegexOption.IGNORE_CASE)
