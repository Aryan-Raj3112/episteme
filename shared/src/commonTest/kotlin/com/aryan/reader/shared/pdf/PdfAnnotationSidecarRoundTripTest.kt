package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The folder-plane annotation sidecar must be idempotent under the exact
 * round trip the Android client performs on every export.
 *
 * `RecentFilesRepository.syncLocalAnnotationsToFolder` does, per export:
 *
 *  1. read local artifact files into legacy keys (`ink`, `textBoxes`, ...)
 *  2. `canonicalizeDataJson` -> adds the canonical `pdfAnnotations` array
 *  3. `saveAnnotationSidecarToAppStorage` -> `mergeAnnotationDataJson` against
 *     whatever sidecar is already in app storage, then writes the wrapper
 *
 * Step 3 reads back the sidecar it wrote in step 3 of the *previous* export, so
 * any duplication introduced by steps 1-2 compounds once per export. A
 * two-device session showed a payload growing 3122 -> 35451 -> 46851 -> 94783
 * bytes in 30 seconds with no user edits, which is this loop.
 *
 * These tests pin that a repeated export converges instead of compounding.
 */
class PdfAnnotationSidecarRoundTripTest {

    private fun ink(id: String, page: Int, points: Int = 6) = SharedPdfAnnotation(
        id = id,
        pageIndex = page,
        kind = PdfAnnotationKind.INK,
        tool = PdfInkTool.PEN,
        points = (0 until points).map { index ->
            PdfPagePoint(x = 10f + index * 3f, y = 20f + index * 2f, timestamp = 1_700_000_000_000L + index)
        },
        colorArgb = 0xFF1F6FEB.toInt(),
        strokeWidth = 3f,
    )

    private fun highlight(id: String, page: Int) = SharedPdfAnnotation(
        id = id,
        pageIndex = page,
        kind = PdfAnnotationKind.HIGHLIGHT,
        tool = PdfInkTool.HIGHLIGHTER,
        bounds = PdfPageBounds(0.1f, 0.2f, 0.5f, 0.24f),
        text = "highlighted text $id",
        colorArgb = 0xFFFFEB3B.toInt(),
        rangeStartIndex = page * 10,
        rangeEndIndex = page * 10 + 4,
    )

    /**
     * One device's export: canonicalize the legacy payload the artifact files
     * would produce, then merge against the sidecar already on disk. This is
     * `saveAnnotationSidecarToAppStorage` reduced to its two codec calls.
     */
    private fun exportOnce(
        localAnnotations: List<SharedPdfAnnotation>,
        existingSidecarDataJson: String?,
    ): String {
        // Stands in for the artifact files: the canonical store as JSON, which
        // is what `putJsonSafe` reads back off disk on the Android side.
        val artifactPayload = buildString {
            append('{')
            append("\"${SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATIONS}\":")
            append(SharedPdfAnnotationSidecarCodec.encodeAnnotationsElement(localAnnotations).toString())
            append('}')
        }
        val legacyPayload = SharedPdfAnnotationSidecarCodec.legacyAndroidDataJsonFromCanonical(
            SharedPdfAnnotationSidecarCodec.canonicalizeDataJson(artifactPayload),
        )
        return if (existingSidecarDataJson == null) {
            legacyPayload
        } else {
            SharedPdfAnnotationSidecarCodec.mergeAnnotationDataJson(
                localDataJson = legacyPayload,
                remoteDataJson = existingSidecarDataJson,
                preferRemoteOnConflict = false,
            )
        }
    }

    @Test
    fun repeatedExportOfUnchangedAnnotationsDoesNotGrowThePayload() {
        val annotations = listOf(
            ink("ink_1", 3),
            ink("ink_2", 7),
            highlight("hl_1", 11),
        )

        var sidecar: String? = null
        val sizes = mutableListOf<Int>()
        repeat(6) {
            sidecar = exportOnce(annotations, sidecar)
            sizes += sidecar!!.length
        }

        // The first export establishes the payload; every later export must
        // reproduce it exactly. Any growth here is a per-export duplication
        // bug, and it compounds without bound in a live two-device session.
        val settled = sizes.drop(1)
        assertEquals(
            settled.distinct(),
            listOf(settled.first()),
            "payload kept growing across identical exports: $sizes",
        )
    }

    @Test
    fun repeatedExportKeepsTheAnnotationCountStable() {
        val annotations = listOf(ink("ink_1", 3), highlight("hl_1", 11))

        var sidecar: String? = null
        repeat(6) {
            sidecar = exportOnce(annotations, sidecar)
            assertEquals(
                annotations.size,
                SharedPdfAnnotationSidecarCodec.annotationCountFromDataJson(sidecar!!),
                "annotation count drifted on export ${it + 1}: ${sidecar!!.length} bytes",
            )
        }
    }

    @Test
    fun legacyMirrorDoesNotDuplicateTheCanonicalArray() {
        // The sidecar intentionally carries both the canonical array and the
        // legacy arrays so old readers can read it. A merge must not append a
        // second canonical array or re-nest legacy arrays, which is what turns
        // one payload into several.
        val annotations = listOf(ink("ink_1", 3), highlight("hl_1", 11))
        val payload = exportOnce(annotations, null)

        assertEquals(
            1,
            Regex("\"${SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATIONS}\"").findAll(payload).count(),
            "canonical array appears more than once",
        )
        assertTrue(
            payload.contains(SharedPdfAnnotationSidecarCodec.KEY_LEGACY_INK),
            "legacy mirror missing; old readers would drop annotations",
        )
    }

    /**
     * Stands in for reading the artifact files back off disk: decode whatever
     * `importAnnotationBundle` would have written and return the annotations.
     */
    private fun annotationsRecoveredFrom(legacyDataJson: String): List<SharedPdfAnnotation> {
        val data = kotlinx.serialization.json.Json.parseToJsonElement(legacyDataJson)
        val obj = data as kotlinx.serialization.json.JsonObject
        val decoded = SharedPdfAnnotationSidecarCodec.decodeAnnotationsElement(
            obj[SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATIONS]
                ?: return emptyList(),
        )
        // `importAnnotationBundle` also writes the legacy arrays separately;
        // those are re-derived from the canonical set on the way out, so the
        // canonical array is the authoritative round-trip surface.
        return decoded
    }

    @Test
    fun importThenExportCycleDoesNotGrowThePayload() {
        // This is the real cross-device cycle, and the one a live session ran:
        //
        //   device A exports -> device B imports (writes legacy artifact files)
        //   -> device B exports (reads those files back, merges with the
        //      sidecar it was just handed)
        //
        // The second device has made no edits, so every round must converge.
        // A two-device session showed 3122 -> 35451 -> 46851 -> 94783 bytes
        // with no user edits, which means the import/export cycle itself
        // compounds.
        val annotations = listOf(ink("ink_1", 3), highlight("hl_1", 11))

        // Device A publishes.
        var deviceSidecar = exportOnce(annotations, null)

        val sizes = mutableListOf(deviceSidecar.length)
        repeat(5) {
            // Device B imports: only the legacy keys land on disk, exactly as
            // `importAnnotationBundle` writes them.
            val legacyOnDisk = SharedPdfAnnotationSidecarCodec.legacyAndroidDataJsonFromCanonical(
                deviceSidecar,
            )

            // Device B exports with no edits of its own: the annotations it
            // just imported come back off disk, merged against the sidecar.
            val importedBack = annotationsRecoveredFrom(legacyOnDisk)
            assertEquals(
                annotations.size,
                importedBack.size,
                "import lost annotations on round ${it + 1}",
            )
            deviceSidecar = exportOnce(importedBack, deviceSidecar)
            sizes += deviceSidecar.length
        }

        assertEquals(
            sizes.distinct(),
            listOf(sizes.first()),
            "import/export cycle kept growing the payload: $sizes",
        )
    }

    @Test
    fun mergingTwoDevicesSidecarsUnionsAnnotationsWithoutGrowth() {
        // Device A has one ink stroke, device B has one highlight. Each merges
        // the other's sidecar; the result must be the union, and a second merge
        // round must be a no-op.
        val fromA = exportOnce(listOf(ink("ink_1", 3)), null)
        val fromB = exportOnce(listOf(highlight("hl_1", 11)), null)

        val aThenB = SharedPdfAnnotationSidecarCodec.mergeAnnotationDataJson(
            localDataJson = fromA,
            remoteDataJson = fromB,
            preferRemoteOnConflict = false,
        )
        assertEquals(
            2,
            SharedPdfAnnotationSidecarCodec.annotationCountFromDataJson(aThenB),
            "merge dropped one device's annotations",
        )

        val mergedAgain = SharedPdfAnnotationSidecarCodec.mergeAnnotationDataJson(
            localDataJson = fromB,
            remoteDataJson = aThenB,
            preferRemoteOnConflict = false,
        )
        assertEquals(
            2,
            SharedPdfAnnotationSidecarCodec.annotationCountFromDataJson(mergedAgain),
            "re-merge changed the annotation set",
        )
        // Convergence, not byte equality: re-merging a device payload into an
        // already-merged union may legitimately shrink it, because the union's
        // legacy mirrors are recomputed from the canonical set instead of the
        // exporting device's partial ones. What must never happen is growth,
        // which is what compounds across a live two-device session.
        assertTrue(
            mergedAgain.length <= aThenB.length,
            "re-merge grew the payload: ${aThenB.length} -> ${mergedAgain.length}",
        )
    }
}
