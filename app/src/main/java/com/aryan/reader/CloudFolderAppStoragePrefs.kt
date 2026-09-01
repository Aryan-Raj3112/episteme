package com.aryan.reader

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.aryan.reader.shared.SyncedFolder
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Device-local records for cloud folders downloaded into app storage.
 *
 * These records intentionally live outside [SyncedFolderPrefs]. Older builds
 * do not know about app-managed cloud folders and must continue to see only
 * user-granted SAF roots. The account-scoped key also prevents a folder
 * downloaded for one account from appearing after an account switch.
 */
internal object CloudFolderAppStoragePrefs {
    private const val PREFS_NAME = "cloud_folder_sync"
    private const val KEY_PREFIX = "app_storage_roots_v1_"

    data class Entry(
        val rootId: String,
        val name: String,
        val lastScanTime: Long = 0L,
    ) {
        fun toSyncedFolder(filesDir: File): SyncedFolder {
            val root = cloudFolderAppRootDirectory(filesDir, rootId)
            return SyncedFolder(
                uriString = cloudFolderAppStorageFolderUriString(root),
                name = name.trim().ifBlank { "Cloud folder" },
                lastScanTime = lastScanTime.coerceAtLeast(0L),
                allowedFileTypes = ANDROID_SYNCABLE_FILE_TYPES,
                localSyncEnabled = true,
                cloudRootId = rootId,
                isAppManaged = true,
            )
        }
    }

    fun load(context: Context, accountId: String): List<Entry> {
        val key = keyFor(accountId) ?: return emptyList()
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key, null)
            ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val objectValue = array.optJSONObject(index) ?: continue
                    val rootId = objectValue.optString("rootId").trim()
                    if (rootId.isBlank()) continue
                    add(
                        Entry(
                            rootId = rootId,
                            name = objectValue.optString("name").trim().ifBlank { "Cloud folder" },
                            lastScanTime = objectValue.optLong("lastScanTime", 0L).coerceAtLeast(0L),
                        )
                    )
                }
            }
                .filter { entry ->
                    runCatching {
                        cloudFolderAppRootDirectory(context.filesDir, entry.rootId)
                    }.isSuccess
                }
                .distinctBy { it.rootId }
        } catch (error: Exception) {
            Timber.w(error, "Unable to read app-managed cloud-folder records")
            emptyList()
        }
    }

    fun upsert(
        context: Context,
        accountId: String,
        rootId: String,
        name: String,
        lastScanTime: Long = 0L,
    ) {
        val normalizedRootId = rootId.trim().takeIf { it.isNotBlank() } ?: return
        runCatching { cloudFolderAppRootDirectory(context.filesDir, normalizedRootId) }
            .getOrElse { return }
        val entries = load(context, accountId).toMutableList()
        val next = Entry(
            rootId = normalizedRootId,
            name = name.trim().ifBlank { "Cloud folder" },
            lastScanTime = lastScanTime.coerceAtLeast(0L),
        )
        val existingIndex = entries.indexOfFirst { it.rootId == normalizedRootId }
        if (existingIndex >= 0) entries[existingIndex] = next else entries += next
        save(context, accountId, entries)
    }

    /** Register a completed materialization without resetting its scan watermark. */
    fun ensure(
        context: Context,
        accountId: String,
        rootId: String,
        name: String,
    ) {
        val normalizedRootId = rootId.trim().takeIf { it.isNotBlank() } ?: return
        if (load(context, accountId).any { it.rootId == normalizedRootId }) return
        upsert(context, accountId, normalizedRootId, name)
    }

    fun updateLastScanTime(context: Context, accountId: String, rootId: String, timestamp: Long) {
        val normalizedRootId = rootId.trim().takeIf { it.isNotBlank() } ?: return
        val entries = load(context, accountId).map { entry ->
            if (entry.rootId == normalizedRootId) {
                entry.copy(lastScanTime = timestamp.coerceAtLeast(0L))
            } else {
                entry
            }
        }
        save(context, accountId, entries)
    }

    fun remove(context: Context, accountId: String, rootId: String) {
        val normalizedRootId = rootId.trim().takeIf { it.isNotBlank() } ?: return
        save(context, accountId, load(context, accountId).filterNot { it.rootId == normalizedRootId })
    }

    fun contains(context: Context, accountId: String, rootId: String): Boolean {
        val normalizedRootId = rootId.trim().takeIf { it.isNotBlank() } ?: return false
        return load(context, accountId).any { it.rootId == normalizedRootId }
    }

    /**
     * Resolve a synthetic app-storage folder URI back to its logical root.
     *
     * [File.toURI] has emitted both `file:/...` and `file:///...` forms across
     * Android/JVM implementations. Compare canonical file paths instead of
     * URI spelling, while still rejecting content/provider URIs entirely.
     */
    fun rootIdForUri(context: Context, accountId: String, uriString: String): String? {
        val normalizedUri = uriString.trim().takeIf { it.isNotBlank() } ?: return null
        val candidatePath = canonicalManagedFilePath(normalizedUri)
        return load(context, accountId)
            .firstOrNull { entry ->
                val root = runCatching {
                    cloudFolderAppRootDirectory(context.filesDir, entry.rootId).canonicalFile
                }.getOrNull() ?: return@firstOrNull false
                if (candidatePath != null) {
                    candidatePath == root
                } else {
                    entry.toSyncedFolder(context.filesDir).uriString == normalizedUri
                }
            }
            ?.rootId
    }

    /**
     * Resolve a file URI only when it is a strict descendant of the selected
     * account's registered app-private cloud-folder root.
     *
     * This is deliberately independent of [File.exists]: callers need the
     * canonical target even when it is missing so they can distinguish a
     * genuinely missing folder book from a malformed/out-of-root URI without
     * ever falling back to an arbitrary filesystem path.
     */
    fun resolveManagedFile(
        context: Context,
        accountId: String,
        sourceFolderUri: String,
        fileUriString: String,
    ): File? {
        val rootId = rootIdForUri(context, accountId, sourceFolderUri) ?: return null
        val root = runCatching {
            cloudFolderAppRootDirectory(context.filesDir, rootId).canonicalFile
        }.getOrNull() ?: return null
        val target = canonicalManagedFilePath(fileUriString) ?: return null
        val prefix = root.path + File.separator
        return target.takeIf { it.path.startsWith(prefix) }
    }

    private fun canonicalManagedFilePath(uriString: String): File? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        if (!uri.scheme.equals("file", ignoreCase = true) || !uri.authority.isNullOrBlank()) {
            return null
        }
        val path = uri.path?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { File(path).canonicalFile }.getOrNull()
    }

    private fun save(context: Context, accountId: String, entries: List<Entry>) {
        val key = keyFor(accountId) ?: return
        val array = JSONArray()
        entries
            .asSequence()
            .filter { it.rootId.isNotBlank() }
            .distinctBy { it.rootId }
            .sortedBy { it.rootId }
            .forEach { entry ->
                array.put(
                    JSONObject().apply {
                        put("rootId", entry.rootId)
                        put("name", entry.name)
                        put("lastScanTime", entry.lastScanTime.coerceAtLeast(0L))
                    }
                )
            }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            if (entries.isEmpty()) remove(key) else putString(key, array.toString())
        }
    }

    private fun keyFor(accountId: String): String? {
        val normalized = accountId.trim().takeIf { it.isNotBlank() } ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return KEY_PREFIX + digest.take(32)
    }
}
