package com.aryan.reader

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudFolderSyncLogTest {
    @Test
    fun transferClassifierRecognizesOnlyCloudFolderFailures() {
        val transfer = CloudFolderTransferException(
            stage = "download",
            category = "network",
            statusCategory = "network",
        )
        val drive = CloudFolderDriveException(
            httpStatusCode = 503,
            bodyCategory = "backend",
            statusCategory = "network",
        )

        assertTrue(isCloudFolderTransferFailure(transfer))
        assertTrue(isCloudFolderTransferFailure(RuntimeException("wrapped", drive)))
        assertFalse(isCloudFolderTransferFailure(IOException("ordinary library failure")))
    }

    /**
     * Known-answer coverage for the hex encoder that replaced the per-byte
     * `"%02x".format(...)` formatter (crashlytics-triage #72).
     *
     * SHA-256("abc") = ba7816bf8f01cfea... carries digest bytes above 0x7F
     * (0xBA, 0x8F, ...), so a signed-byte regression would either change the
     * characters or drop to a single digit. The trimmed input pins the
     * normalization too.
     */
    @Test
    fun safeIdIsTheLowercaseSha256HexOfTheTrimmedValue() {
        assertEquals("ba7816bf8f01", cloudFolderSafeId("  abc  "))

        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            cloudFolderSafeId("hello", length = 64),
        )
    }

    /** The sidecar payload hash is the same lowercase-hex SHA-256. */
    @Test
    fun sidecarPayloadInfoHashesTheUtf8PayloadAsLowercaseHex() {
        val info = cloudFolderSidecarPayloadInfo("""{"version":2}""")

        assertEquals(13, info?.bytes)
        assertEquals(2, info?.schema)
        assertEquals(
            "sha256:732322c128ed67841ef5aa42c7cd04ffcd74a61b2d17d710cd502b713d62930d",
            info?.sha256,
        )
    }

    /**
     * The crash path builds both tokens from the same values
     * (`cloudFolderOperationId(...)` then `cloudFolderSyncCorrelationId(...)`),
     * so the operation token must stay derivable from the correlation one.
     */
    @Test
    fun operationIdIsTheCorrelationIdWithOperationPrefix() {
        val correlation = cloudFolderSyncCorrelationId("reader-close", "book-1", "content://books/1")

        assertEquals(
            "op_$correlation",
            cloudFolderOperationId("reader-close", "book-1", "content://books/1"),
        )
    }

    /**
     * Regression pin: the OOM's failing allocation was the `StringBuilder`
     * `String.format("%02x", byte)` created per digest byte. The per-byte
     * formatter idiom is still widespread elsewhere in the tree, so this file
     * must not drift back to it.
     */
    @Test
    fun digestHexUsesTheAllocationLightEncoder() {
        val source = sourceFile("com/aryan/reader/CloudFolderSyncLog.kt").readText()

        assertFalse(
            "cloud folder log helpers must not hex-format per byte via String.format",
            source.contains("%02x"),
        )
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("app/src/main/java/$relativePath"),
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Unable to locate $relativePath from ${File(".").absolutePath}")
    }
}
