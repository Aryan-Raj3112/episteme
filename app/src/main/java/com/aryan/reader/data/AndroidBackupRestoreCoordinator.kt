package com.aryan.reader.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Paths whose contents are durable user state rather than a generated cache.
 * Keep these paths named so backup rules can use an allowlist without wildcards.
 */
internal object AndroidBookArtifactPaths {
    private const val ANNOTATIONS_DIR = "annotations"
    private const val RICH_TEXT_DIR = "rich_doc"
    private const val DERIVED_DIR = "derived"
    private const val REFLOW_DIR = "reflow"

    /**
     * ext4/f2fs allow 255 bytes per name; the atomic writer appends `.new`
     * / `.bak`, so cap well below that, measured in UTF-8 bytes (titles
     * routinely contain multibyte characters).
     */
    internal const val MAX_SIDECAR_NAME_BYTES = 200

    private val unsafeFileCharacters = Regex("[^a-zA-Z0-9._-]")

    fun richTextFile(filesDir: File, bookId: String): File = File(
        File(File(filesDir, ANNOTATIONS_DIR), RICH_TEXT_DIR),
        sidecarName(prefix = "rich_doc_", sanitizedId = safeBookId(bookId), stableKey = bookId),
    )

    fun reflowFile(filesDir: File, bookId: String): File = File(
        File(File(filesDir, DERIVED_DIR), REFLOW_DIR),
        sidecarName(
            prefix = "",
            sanitizedId = safeBookId(bookId),
            stableKey = bookId,
            suffix = "_reflow",
            extension = ".html",
        ),
    )

    /**
     * Builds a filesystem-safe sidecar name for [stableKey] (usually the
     * book ID). Names that fit keep their exact legacy spelling, so existing
     * sidecars stay addressable; over-long names fall back to a truncated
     * human-readable head plus a SHA-256 digest, which stays deterministic
     * across read/write/delete/sync. [sanitizedId] must already have
     * separators removed; only the length is enforced here.
     */
    fun sidecarName(
        prefix: String,
        sanitizedId: String,
        stableKey: String,
        suffix: String = "",
        extension: String = ".json",
    ): String {
        val legacy = "$prefix$sanitizedId$suffix$extension"
        if (legacy.toByteArray(Charsets.UTF_8).size <= MAX_SIDECAR_NAME_BYTES) return legacy
        val digest = sha256Hex(stableKey).take(16)
        val fixedBytes = (prefix + "_" + digest + suffix + extension)
            .toByteArray(Charsets.UTF_8).size
        val head = sanitizedId.truncateToUtf8Bytes((MAX_SIDECAR_NAME_BYTES - fixedBytes).coerceAtLeast(0))
        val headPart = head.takeIf { it.isNotEmpty() }?.let { "${it}_" }.orEmpty()
        return "$prefix$headPart$digest$suffix$extension"
    }

    fun safeBookId(bookId: String): String = bookId.replace(unsafeFileCharacters, "_")

    private fun sha256Hex(value: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (byte in digest) append("%02x".format(byte))
        }
    }

    /** Truncates to at most [maxBytes] UTF-8 bytes without splitting a code point. */
    private fun String.truncateToUtf8Bytes(maxBytes: Int): String {
        if (maxBytes <= 0) return ""
        var used = 0
        var end = 0
        while (end < length) {
            val codePoint = codePointAt(end)
            val width = when {
                codePoint < 0x80 -> 1
                codePoint < 0x800 -> 2
                codePoint < 0x10000 -> 3
                else -> 4
            }
            if (used + width > maxBytes) break
            used += width
            end += Character.charCount(codePoint)
        }
        return substring(0, end)
    }
}

internal object AndroidBackupRestoreCoordinator {
    private const val TAG = "BackupRestore"

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("android-backup-restore"),
    )

    /**
     * File migration is synchronous so the database reconciliation sees the
     * new artifact locations. The database scan is kept off the application
     * startup thread and is idempotent on every process start.
     */
    fun start(context: Context) {
        val appContext = context.applicationContext
        val moved = migrateLegacyRootArtifacts(appContext.filesDir)
        if (moved > 0) {
            Timber.tag(TAG).i("Migrated $moved legacy root artifact(s) into named directories")
        }
        scope.launch {
            runCatching { reconcileDatabase(appContext) }
                .onFailure { Timber.tag(TAG).e(it, "Backup restore reconciliation failed") }
        }
    }

    /**
     * Moves legacy dynamic root files into backup-rule-addressable directories.
     * Existing destinations are never overwritten; this avoids losing a file
     * written by a newer app process during an upgrade race.
     */
    internal fun migrateLegacyRootArtifacts(filesDir: File): Int {
        val entries = filesDir.listFiles().orEmpty()
        var moved = 0
        entries.filter { it.isFile && it.name.startsWith("rich_doc_") && it.name.endsWith(".json") }
            .forEach { source ->
                val destination = File(
                    File(File(filesDir, "annotations"), "rich_doc").apply { mkdirs() },
                    source.name,
                )
                if (moveIfDestinationAbsent(source, destination)) moved++
            }
        entries.filter { it.isFile && it.name.endsWith("_reflow.html") }
            .forEach { source ->
                val destination = File(
                    File(File(filesDir, "derived"), "reflow").apply { mkdirs() },
                    source.name,
                )
                if (moveIfDestinationAbsent(source, destination)) moved++
            }
        return moved
    }

    private fun moveIfDestinationAbsent(source: File, destination: File): Boolean {
        if (!source.exists() || destination.exists()) return false
        destination.parentFile?.mkdirs()
        if (source.renameTo(destination)) return true
        val sourceLength = source.length()
        val temporary = runCatching {
            File.createTempFile(".backup-restore-", ".tmp", destination.parentFile)
        }.getOrNull() ?: return false
        return try {
            source.inputStream().use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (temporary.length() != sourceLength || destination.exists()) {
                false
            } else {
                temporary.renameTo(destination).also { renamed ->
                    if (renamed) source.delete()
                }
            }
        } catch (_: Exception) {
            false
        } finally {
            temporary.delete()
        }
    }

    private suspend fun reconcileDatabase(context: Context) {
        val database = AppDatabase.getDatabase(context)
        val entities = database.recentFileDao().getAllFiles()
            .filterNot { it.isDeleted }
        val repairs = buildList {
            entities.forEach { entity ->
                if (entity.bookId.endsWith("_reflow")) {
                    val reflowRepair = reflowRepair(context.filesDir, entity)
                    add(reflowRepair ?: Repair.MarkUnavailable(entity.bookId))
                    return@forEach
                }
                if (isMissingExcludedPrivateFile(context.filesDir, entity.uriString)) {
                    add(Repair.MarkUnavailable(entity.bookId))
                }
            }
        }
        if (repairs.isEmpty()) return

        val now = System.currentTimeMillis()
        database.withTransaction {
            repairs.forEach { repair ->
                when (repair) {
                    is Repair.UpdateUri -> database.recentFileDao().updateBookUri(
                        bookId = repair.bookId,
                        uriString = repair.uriString,
                        timestamp = now,
                    )

                    is Repair.MarkUnavailable -> database.recentFileDao().markUnavailable(repair.bookId)
                }
            }
        }
        Timber.tag(TAG).i(
            "Reconciled ${repairs.size} restored library row(s): " +
                "available=${repairs.count { it is Repair.UpdateUri }} " +
                "missing=${repairs.count { it is Repair.MarkUnavailable }}",
        )
    }

    private fun reflowRepair(filesDir: File, entity: RecentFileEntity): Repair.UpdateUri? {
        val expected = AndroidBookArtifactPaths.reflowFile(filesDir, entity.bookId.removeSuffix("_reflow"))
        if (!expected.isFile) return null
        val expectedUri = Uri.fromFile(expected).toString()
        if (entity.uriString == expectedUri && entity.isAvailable) return null
        return Repair.UpdateUri(entity.bookId, expectedUri)
    }

    /**
     * Only absence of an app-private original/derived file is definitive.
     * SAF/content providers can be temporarily unmounted or permission-gated;
     * never turn those transient states into a destructive availability edit.
     */
    internal fun isMissingExcludedPrivateFile(filesDir: File, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() != "file") return false
        return isMissingExcludedPrivatePath(filesDir, uri.path)
    }

    internal fun isMissingExcludedPrivatePath(filesDir: File, path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        val excludedRoots = listOf(
            File(filesDir, "books"),
            File(filesDir, "audiobooks"),
            File(filesDir, "derived/reflow"),
        ).mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
        if (excludedRoots.none { file.isWithin(it) }) return false
        return !file.isFile
    }

    private fun File.isWithin(root: File): Boolean {
        val rootPath = root.path.trimEnd(File.separatorChar) + File.separator
        return path == root.path || path.startsWith(rootPath)
    }

    private sealed interface Repair {
        data class UpdateUri(val bookId: String, val uriString: String) : Repair
        data class MarkUnavailable(val bookId: String) : Repair
    }
}
