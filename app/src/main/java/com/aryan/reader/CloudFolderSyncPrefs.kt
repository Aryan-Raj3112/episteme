package com.aryan.reader

import android.content.Context
import androidx.core.content.edit
import com.aryan.reader.shared.CloudFolderSyncSelection
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import org.json.JSONObject

/** Device-local account selection. New installs exclude folder roots by default. */
object CloudFolderSyncPrefs {
    private const val PREFS_NAME = "cloud_folder_sync"
    private const val KEY_SELECTION_PREFIX = "selection_v1_"
    private const val KEY_INCOMING_PENDING_PREFIX = "incoming_pending_v1_"
    private const val KEY_INCOMING_DISMISSED_PREFIX = "incoming_dismissed_v1_"
    private const val KEY_INCOMING_DISCOVERED_PREFIX = "incoming_discovered_v1_"

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun load(context: Context, accountId: String): CloudFolderSyncSelection {
        val key = selectionKey(accountId) ?: return CloudFolderSyncSelection.Default
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key, null)
            ?: return CloudFolderSyncSelection.Default
        return runCatching {
            json.decodeFromString(CloudFolderSyncSelection.serializer(), raw).normalized()
        }.getOrDefault(CloudFolderSyncSelection.Default)
    }

    fun save(context: Context, accountId: String, selection: CloudFolderSyncSelection) {
        val key = requireNotNull(selectionKey(accountId)) { "Cloud-folder selection requires an account ID" }
        val normalized = selection.normalized()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            putString(key, json.encodeToString(CloudFolderSyncSelection.serializer(), normalized))
        }
    }

    fun clear(context: Context, accountId: String) {
        val keys = accountKeys(accountId) ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            remove(keys.selection)
            remove(keys.incomingPending)
            remove(keys.incomingDismissed)
            remove(keys.incomingDiscovered)
        }
    }

    /**
     * Roots are persisted as soon as a manifest is discovered, but the user
     * prompt itself also needs durable state so process death cannot cause the
     * same incoming root to be offered repeatedly.  A dismissed revision may
     * be shown again only when that root publishes a newer revision.
     */
    fun pendingIncomingRootIds(context: Context, accountId: String): Set<String> =
        loadIncomingRevisions(context, accountId, incomingPendingKey = true).keys

    /**
     * Roots discovered from another device remain visible in Folders even
     * after the user taps "Not now". They are placeholders only: no local
     * files or books are exposed until a materialization choice completes.
     */
    fun discoveredIncomingRootIds(context: Context, accountId: String): Set<String> =
        loadIncomingDiscovered(context, accountId).keys

    fun markIncomingPromptPending(
        context: Context,
        accountId: String,
        rootId: String,
        revision: Long,
    ) {
        val normalizedRootId = rootId.trim()
        if (normalizedRootId.isBlank()) return
        val pending = loadIncomingRevisions(context, accountId, incomingPendingKey = true).toMutableMap()
        val dismissed = loadIncomingRevisions(context, accountId, incomingPendingKey = false)
        val discovered = loadIncomingDiscovered(context, accountId).toMutableMap()
        if ((dismissed[normalizedRootId] ?: Long.MIN_VALUE) >= revision) {
            return
        }
        discovered[normalizedRootId] = maxOf(discovered[normalizedRootId] ?: Long.MIN_VALUE, revision.coerceAtLeast(0L))
        saveIncomingDiscovered(context, accountId, discovered)
        pending[normalizedRootId] = maxOf(pending[normalizedRootId] ?: Long.MIN_VALUE, revision.coerceAtLeast(0L))
        saveIncomingRevisions(context, accountId, incomingPendingKey = true, pending)
    }

    /** Hide the global prompt for now while retaining a Folders placeholder. */
    fun snoozeIncomingPrompt(
        context: Context,
        accountId: String,
        rootId: String,
        revision: Long,
    ) {
        val normalizedRootId = rootId.trim()
        if (normalizedRootId.isBlank()) return
        val pending = loadIncomingRevisions(context, accountId, incomingPendingKey = true).toMutableMap()
        pending.remove(normalizedRootId)
        saveIncomingRevisions(context, accountId, incomingPendingKey = true, pending)
        val discovered = loadIncomingDiscovered(context, accountId).toMutableMap()
        discovered[normalizedRootId] = maxOf(discovered[normalizedRootId] ?: Long.MIN_VALUE, revision.coerceAtLeast(0L))
        saveIncomingDiscovered(context, accountId, discovered)
        // "Not now" must survive the next discovery pass. Without a snoozed
        // marker the same revision is re-offered on every sync, which is why
        // the prompt kept returning after the user dismissed it.
        val snoozed = loadIncomingRevisions(context, accountId, incomingPendingKey = false).toMutableMap()
        snoozed[normalizedRootId] = maxOf(snoozed[normalizedRootId] ?: Long.MIN_VALUE, revision.coerceAtLeast(0L))
        saveIncomingRevisions(context, accountId, incomingPendingKey = false, snoozed)
    }

    fun dismissIncomingPrompt(
        context: Context,
        accountId: String,
        rootId: String,
        revision: Long,
    ) {
        val normalizedRootId = rootId.trim()
        if (normalizedRootId.isBlank()) return
        val pending = loadIncomingRevisions(context, accountId, incomingPendingKey = true).toMutableMap()
        pending.remove(normalizedRootId)
        saveIncomingRevisions(context, accountId, incomingPendingKey = true, pending)

        val discovered = loadIncomingDiscovered(context, accountId).toMutableMap()
        discovered.remove(normalizedRootId)
        saveIncomingDiscovered(context, accountId, discovered)

        val dismissed = loadIncomingRevisions(context, accountId, incomingPendingKey = false).toMutableMap()
        dismissed[normalizedRootId] = maxOf(dismissed[normalizedRootId] ?: Long.MIN_VALUE, revision.coerceAtLeast(0L))
        saveIncomingRevisions(context, accountId, incomingPendingKey = false, dismissed)
    }

    fun clearIncomingPromptState(context: Context, accountId: String) {
        val keys = accountKeys(accountId) ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            remove(keys.incomingPending)
            remove(keys.incomingDismissed)
            remove(keys.incomingDiscovered)
        }
    }

    /**
     * Fully erase one root from incoming-prompt bookkeeping so it can be
     * discovered and offered again from scratch (used when the user removes a
     * local binding and wants the folder re-mergeable later).
     */
    fun forgetIncomingPrompt(context: Context, accountId: String, rootId: String) {
        val normalizedRootId = rootId.trim()
        if (normalizedRootId.isBlank()) return
        val pending = loadIncomingRevisions(context, accountId, incomingPendingKey = true).toMutableMap()
        pending.remove(normalizedRootId)
        saveIncomingRevisions(context, accountId, incomingPendingKey = true, pending)
        val dismissed = loadIncomingRevisions(context, accountId, incomingPendingKey = false).toMutableMap()
        dismissed.remove(normalizedRootId)
        saveIncomingRevisions(context, accountId, incomingPendingKey = false, dismissed)
        val discovered = loadIncomingDiscovered(context, accountId).toMutableMap()
        discovered.remove(normalizedRootId)
        saveIncomingDiscovered(context, accountId, discovered)
    }

    private fun selectionKey(accountId: String): String? {
        return accountKeys(accountId)?.selection
    }

    private data class AccountKeys(
        val selection: String,
        val incomingPending: String,
        val incomingDismissed: String,
        val incomingDiscovered: String,
    )

    private fun accountKeys(accountId: String): AccountKeys? {
        val normalized = accountId.trim().takeIf { it.isNotBlank() } ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        val suffix = digest.take(32)
        return AccountKeys(
            selection = KEY_SELECTION_PREFIX + suffix,
            incomingPending = KEY_INCOMING_PENDING_PREFIX + suffix,
            incomingDismissed = KEY_INCOMING_DISMISSED_PREFIX + suffix,
            incomingDiscovered = KEY_INCOMING_DISCOVERED_PREFIX + suffix,
        )
    }

    private fun loadIncomingDiscovered(context: Context, accountId: String): Map<String, Long> {
        val keys = accountKeys(accountId) ?: return emptyMap()
        return loadIncomingMap(
            context = context,
            key = keys.incomingDiscovered,
        )
    }

    private fun saveIncomingDiscovered(
        context: Context,
        accountId: String,
        revisions: Map<String, Long>,
    ) {
        val keys = accountKeys(accountId) ?: return
        saveIncomingMap(context, keys.incomingDiscovered, revisions)
    }

    private fun loadIncomingRevisions(
        context: Context,
        accountId: String,
        incomingPendingKey: Boolean,
    ): Map<String, Long> {
        val keys = accountKeys(accountId) ?: return emptyMap()
        val key = if (incomingPendingKey) keys.incomingPending else keys.incomingDismissed
        return loadIncomingMap(context, key)
    }

    private fun loadIncomingMap(context: Context, key: String): Map<String, Long> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key, null)
            ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().mapNotNull { rootId ->
                val revision = json.optLong(rootId, Long.MIN_VALUE)
                rootId.trim().takeIf { it.isNotBlank() }?.let { it to revision.coerceAtLeast(0L) }
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun saveIncomingRevisions(
        context: Context,
        accountId: String,
        incomingPendingKey: Boolean,
        revisions: Map<String, Long>,
    ) {
        val keys = accountKeys(accountId) ?: return
        val key = if (incomingPendingKey) keys.incomingPending else keys.incomingDismissed
        saveIncomingMap(context, key, revisions)
    }

    private fun saveIncomingMap(
        context: Context,
        key: String,
        revisions: Map<String, Long>,
    ) {
        val json = JSONObject()
        revisions
            .asSequence()
            .filter { it.key.isNotBlank() }
            .sortedBy { it.key }
            .forEach { (rootId, revision) -> json.put(rootId, revision.coerceAtLeast(0L)) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            if (revisions.isEmpty()) remove(key) else putString(key, json.toString())
        }
    }
}
