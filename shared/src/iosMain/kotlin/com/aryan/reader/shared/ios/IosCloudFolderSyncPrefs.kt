package com.aryan.reader.shared.ios

import com.aryan.reader.shared.CloudFolderSyncSelection
import com.aryan.reader.shared.localFolderSyncSha256Hex
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

/**
 * iOS account-scoped cloud-folder selection + incoming-prompt bookkeeping.
 *
 * Android is the absolute benchmark and is NOT changed. This mirrors
 * `CloudFolderSyncPrefs` (app/src/main/java/com/aryan/reader/CloudFolderSyncPrefs.kt)
 * exactly: per-account keys derived from SHA-256(accountId).take(32),
 * EXCLUDED default, normalized persistence, pending/discovered/dismissed
 * revision maps with the same "pending authoritative until dismissed" and
 * "snooze survives discovery" semantics.
 *
 * Storage is NSUserDefaults (device-local, like Android SharedPreferences).
 * Manifests/bindings themselves remain in the cloud-sync snapshot path; this
 * file only owns the selection + prompt durability that the settings surface
 * and the single incoming-prompt owner need.
 */
internal object IosCloudFolderSyncPrefs {
    private const val PREFS_PREFIX = "reader.ios.cloudFolderSync.v1."
    private const val KEY_SELECTION_PREFIX = "selection_v1_"
    private const val KEY_INCOMING_PENDING_PREFIX = "incoming_pending_v1_"
    private const val KEY_INCOMING_DISMISSED_PREFIX = "incoming_dismissed_v1_"
    private const val KEY_INCOMING_DISCOVERED_PREFIX = "incoming_discovered_v1_"

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private data class AccountKeys(
        val selection: String,
        val incomingPending: String,
        val incomingDismissed: String,
        val incomingDiscovered: String,
    )

    private fun accountKeys(accountId: String): AccountKeys? {
        val normalized = accountId.trim().takeIf { it.isNotBlank() } ?: return null
        val suffix = localFolderSyncSha256Hex(normalized).take(32)
        return AccountKeys(
            selection = PREFS_PREFIX + KEY_SELECTION_PREFIX + suffix,
            incomingPending = PREFS_PREFIX + KEY_INCOMING_PENDING_PREFIX + suffix,
            incomingDismissed = PREFS_PREFIX + KEY_INCOMING_DISMISSED_PREFIX + suffix,
            incomingDiscovered = PREFS_PREFIX + KEY_INCOMING_DISCOVERED_PREFIX + suffix,
        )
    }

    fun loadSelection(accountId: String?): CloudFolderSyncSelection {
        if (accountId.isNullOrBlank()) return CloudFolderSyncSelection.Default
        val key = accountKeys(accountId)?.selection ?: return CloudFolderSyncSelection.Default
        val raw = NSUserDefaults.standardUserDefaults.stringForKey(key) ?: return CloudFolderSyncSelection.Default
        return runCatching {
            json.decodeFromString(CloudFolderSyncSelection.serializer(), raw).normalized()
        }.getOrDefault(CloudFolderSyncSelection.Default)
    }

    fun saveSelection(accountId: String, selection: CloudFolderSyncSelection) {
        val key = accountKeys(accountId)?.selection ?: return
        val normalized = selection.normalized()
        NSUserDefaults.standardUserDefaults.setObject(
            json.encodeToString(CloudFolderSyncSelection.serializer(), normalized),
            forKey = key,
        )
    }

    fun clear(accountId: String) {
        val keys = accountKeys(accountId) ?: return
        val defaults = NSUserDefaults.standardUserDefaults
        defaults.removeObjectForKey(keys.selection)
        defaults.removeObjectForKey(keys.incomingPending)
        defaults.removeObjectForKey(keys.incomingDismissed)
        defaults.removeObjectForKey(keys.incomingDiscovered)
    }

    fun pendingIncomingRootIds(accountId: String?): Set<String> {
        if (accountId.isNullOrBlank()) return emptySet()
        return loadIncomingMap(accountId, pending = true).keys
    }

    fun discoveredIncomingRootIds(accountId: String?): Set<String> {
        if (accountId.isNullOrBlank()) return emptySet()
        return loadIncomingDiscovered(accountId).keys
    }

    fun markIncomingPromptPending(accountId: String, rootId: String, revision: Long) {
        val normalizedRootId = rootId.trim()
        if (accountId.isBlank() || normalizedRootId.isBlank()) return
        val pending = loadIncomingMap(accountId, pending = true).toMutableMap()
        val dismissed = loadIncomingMap(accountId, pending = false)
        if ((dismissed[normalizedRootId] ?: Long.MIN_VALUE) >= revision) return
        val discovered = loadIncomingDiscovered(accountId).toMutableMap()
        discovered[normalizedRootId] = maxOf(
            discovered[normalizedRootId] ?: Long.MIN_VALUE,
            revision.coerceAtLeast(0L),
        )
        saveIncomingDiscovered(accountId, discovered)
        pending[normalizedRootId] = maxOf(
            pending[normalizedRootId] ?: Long.MIN_VALUE,
            revision.coerceAtLeast(0L),
        )
        saveIncomingMap(accountId, pending = true, pending)
    }

    fun snoozeIncomingPrompt(accountId: String, rootId: String, revision: Long) {
        val normalizedRootId = rootId.trim()
        if (accountId.isBlank() || normalizedRootId.isBlank()) return
        val pending = loadIncomingMap(accountId, pending = true).toMutableMap()
        pending.remove(normalizedRootId)
        saveIncomingMap(accountId, pending = true, pending)
        val discovered = loadIncomingDiscovered(accountId).toMutableMap()
        discovered[normalizedRootId] = maxOf(
            discovered[normalizedRootId] ?: Long.MIN_VALUE,
            revision.coerceAtLeast(0L),
        )
        saveIncomingDiscovered(accountId, discovered)
        val snoozed = loadIncomingMap(accountId, pending = false).toMutableMap()
        snoozed[normalizedRootId] = maxOf(
            snoozed[normalizedRootId] ?: Long.MIN_VALUE,
            revision.coerceAtLeast(0L),
        )
        saveIncomingMap(accountId, pending = false, snoozed)
    }

    fun dismissIncomingPrompt(accountId: String, rootId: String, revision: Long) {
        val normalizedRootId = rootId.trim()
        if (accountId.isBlank() || normalizedRootId.isBlank()) return
        val pending = loadIncomingMap(accountId, pending = true).toMutableMap()
        pending.remove(normalizedRootId)
        saveIncomingMap(accountId, pending = true, pending)
        val discovered = loadIncomingDiscovered(accountId).toMutableMap()
        discovered.remove(normalizedRootId)
        saveIncomingDiscovered(accountId, discovered)
        val dismissed = loadIncomingMap(accountId, pending = false).toMutableMap()
        dismissed[normalizedRootId] = maxOf(
            dismissed[normalizedRootId] ?: Long.MIN_VALUE,
            revision.coerceAtLeast(0L),
        )
        saveIncomingMap(accountId, pending = false, dismissed)
    }

    fun forgetIncomingPrompt(accountId: String, rootId: String) {
        val normalizedRootId = rootId.trim()
        if (accountId.isBlank() || normalizedRootId.isBlank()) return
        val pending = loadIncomingMap(accountId, pending = true).toMutableMap()
        pending.remove(normalizedRootId)
        saveIncomingMap(accountId, pending = true, pending)
        val dismissed = loadIncomingMap(accountId, pending = false).toMutableMap()
        dismissed.remove(normalizedRootId)
        saveIncomingMap(accountId, pending = false, dismissed)
        val discovered = loadIncomingDiscovered(accountId).toMutableMap()
        discovered.remove(normalizedRootId)
        saveIncomingDiscovered(accountId, discovered)
    }

    private fun loadIncomingDiscovered(accountId: String): Map<String, Long> {
        val keys = accountKeys(accountId) ?: return emptyMap()
        return loadIncomingMapRaw(keys.incomingDiscovered)
    }

    private fun saveIncomingDiscovered(accountId: String, revisions: Map<String, Long>) {
        val keys = accountKeys(accountId) ?: return
        saveIncomingMapRaw(keys.incomingDiscovered, revisions)
    }

    private fun loadIncomingMap(accountId: String, pending: Boolean): Map<String, Long> {
        val keys = accountKeys(accountId) ?: return emptyMap()
        val key = if (pending) keys.incomingPending else keys.incomingDismissed
        return loadIncomingMapRaw(key)
    }

    private fun saveIncomingMap(accountId: String, pending: Boolean, revisions: Map<String, Long>) {
        val keys = accountKeys(accountId) ?: return
        val key = if (pending) keys.incomingPending else keys.incomingDismissed
        saveIncomingMapRaw(key, revisions)
    }

    private fun loadIncomingMapRaw(key: String): Map<String, Long> {
        val raw = NSUserDefaults.standardUserDefaults.stringForKey(key) ?: return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, Long>>(raw)
                .mapNotNull { (rootId, revision) ->
                    rootId.trim().takeIf { it.isNotBlank() }?.let { it to revision.coerceAtLeast(0L) }
                }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun saveIncomingMapRaw(key: String, revisions: Map<String, Long>) {
        val defaults = NSUserDefaults.standardUserDefaults
        if (revisions.isEmpty()) {
            defaults.removeObjectForKey(key)
            return
        }
        val sanitized = revisions
            .filter { it.key.isNotBlank() }
            .mapValues { it.value.coerceAtLeast(0L) }
        defaults.setObject(json.encodeToString(sanitized), forKey = key)
    }
}
