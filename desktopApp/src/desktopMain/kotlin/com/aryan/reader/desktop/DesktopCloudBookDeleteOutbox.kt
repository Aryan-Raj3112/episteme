package com.aryan.reader.desktop

import com.aryan.reader.shared.CloudBookTombstone
import com.aryan.reader.shared.mergeCloudBookTombstones
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val DELETE_OUTBOX_FORMAT_VERSION = 2
private const val LEGACY_UNSCOPED_ACCOUNT = "__legacy_unscoped__"

/**
 * JSON codec kept separate so the durable outbox can be tested without disk IO.
 * The original array format is still readable as an unscoped legacy payload,
 * but it is never replayed under a newly signed-in account.
 */
internal object DesktopCloudBookDeleteOutboxCodec {

    fun merge(tombstones: Collection<CloudBookTombstone>): List<CloudBookTombstone> =
        mergeCloudBookTombstones(tombstones)

    fun encode(tombstones: Collection<CloudBookTombstone>): String =
        DesktopCloudBookDeleteOutboxJson.encodeToString(
            JsonArray.serializer(),
            encodeTombstoneArray(tombstones),
        )

    fun decode(raw: String?): List<CloudBookTombstone> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            decodeTombstoneArray(
                DesktopCloudBookDeleteOutboxJson.parseToJsonElement(raw).jsonArray,
            )
        }.getOrDefault(emptyList())
    }

    /** Encodes one account-scoped durable store. */
    fun encodeAccounts(
        tombstonesByAccount: Map<String, Collection<CloudBookTombstone>>,
    ): String {
        val accounts = buildJsonObject {
            tombstonesByAccount.toSortedMap().forEach { (accountId, tombstones) ->
                put(accountId, encodeTombstoneArray(tombstones))
            }
        }
        return DesktopCloudBookDeleteOutboxJson.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("version", JsonPrimitive(DELETE_OUTBOX_FORMAT_VERSION))
                put("accounts", accounts)
            },
        )
    }

    /**
     * Decodes the v2 account map. A legacy top-level array is retained under a
     * sentinel key so a migration never silently overwrites an old intent,
     * while callers can refuse to execute it without an account identity.
     */
    fun decodeAccounts(raw: String?): Map<String, List<CloudBookTombstone>> {
        if (raw.isNullOrBlank()) return emptyMap()
        val root = DesktopCloudBookDeleteOutboxJson.parseToJsonElement(raw)
        if (root is JsonArray) {
            return mapOf(LEGACY_UNSCOPED_ACCOUNT to decodeTombstoneArray(root))
        }
        val accounts = root.jsonObject["accounts"]?.jsonObject ?: return emptyMap()
        return accounts.mapNotNull { (accountId, value) ->
            val accountTombstones = value as? JsonArray ?: return@mapNotNull null
            accountId to decodeTombstoneArray(accountTombstones)
        }.toMap()
    }

    private fun encodeTombstoneArray(
        tombstones: Collection<CloudBookTombstone>,
    ): JsonArray = buildJsonArray {
        tombstones
            .filter { it.bookId.isNotBlank() }
            .forEach { tombstone ->
                add(
                    buildJsonObject {
                        put("bookId", JsonPrimitive(tombstone.bookId))
                        tombstone.type?.let { put("type", JsonPrimitive(it)) }
                        put("deletedAt", JsonPrimitive(tombstone.deletedAt))
                    },
                )
            }
    }

    private fun decodeTombstoneArray(array: JsonArray): List<CloudBookTombstone> =
        array.mapNotNull { element ->
            val document = element as? JsonObject ?: return@mapNotNull null
            val bookId = document.stringField("bookId")?.trim().orEmpty()
            if (bookId.isBlank()) return@mapNotNull null
            CloudBookTombstone(
                bookId = bookId,
                type = document.stringField("type"),
                deletedAt = document.longField("deletedAt"),
            )
        }

    private fun JsonObject.stringField(key: String): String? =
        (this[key] as? JsonPrimitive)?.content

    private fun JsonObject.longField(key: String): Long =
        (this[key] as? JsonPrimitive)?.longOrNull ?: 0L
}

private val DesktopCloudBookDeleteOutboxJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

/**
 * Durable desktop retry state for cloud book deletion. Each account has an
 * independent queue; pending work from another signed-in account is never
 * replayed. Writes use a temp file and atomic replacement where supported.
 */
internal class DesktopCloudBookDeleteOutbox(
    private val storeFile: File = File(desktopUserConfigRoot(), "cloud-book-delete-outbox.json"),
) {
    @Synchronized
    fun pending(accountId: String): List<CloudBookTombstone> {
        val account = normalizeAccountId(accountId)
        return readAccountsSafely()[account].orEmpty()
    }

    @Synchronized
    fun enqueue(accountId: String, tombstones: Collection<CloudBookTombstone>): Boolean {
        if (tombstones.isEmpty()) return true
        val account = normalizeAccountId(accountId)
        return runCatching {
            val accounts = readAccountsStrict().toMutableMap()
            accounts[account] = DesktopCloudBookDeleteOutboxCodec.merge(
                accounts[account].orEmpty() + tombstones,
            )
            persist(accounts)
        }.onFailure(::logPersistFailure).getOrDefault(false)
    }

    @Synchronized
    fun remove(accountId: String, bookIds: Collection<String>): Boolean {
        if (bookIds.isEmpty()) return true
        val account = normalizeAccountId(accountId)
        val ids = bookIds.toSet()
        return runCatching {
            val accounts = readAccountsStrict().toMutableMap()
            val remaining = accounts[account].orEmpty().filterNot { it.bookId in ids }
            if (remaining.isEmpty()) accounts.remove(account) else accounts[account] = remaining
            persistOrDeleteWhenEmpty(accounts)
        }.onFailure(::logPersistFailure).getOrDefault(false)
    }

    @Synchronized
    fun clear(accountId: String): Boolean {
        val account = normalizeAccountId(accountId)
        return runCatching {
            val accounts = readAccountsStrict().toMutableMap()
            accounts.remove(account)
            persistOrDeleteWhenEmpty(accounts)
        }.onFailure(::logPersistFailure).getOrDefault(false)
    }

    private fun normalizeAccountId(accountId: String): String = accountId.trim().also {
        require(it.isNotEmpty()) { "Cloud delete outbox requires an account id" }
    }

    private fun readAccountsSafely(): Map<String, List<CloudBookTombstone>> =
        runCatching { readAccountsStrict() }
            .onFailure { error ->
                logDesktopCloudSync {
                    "desktop.delete_outbox.read_failed error=\"${error.message.orEmpty().logPreview(240)}\""
                }
            }
            .getOrDefault(emptyMap())

    private fun readAccountsStrict(): Map<String, List<CloudBookTombstone>> {
        if (!storeFile.isFile) return emptyMap()
        val raw = storeFile.readText()
        return DesktopCloudBookDeleteOutboxCodec.decodeAccounts(raw)
    }

    private fun persistOrDeleteWhenEmpty(
        accounts: Map<String, List<CloudBookTombstone>>,
    ): Boolean {
        return if (accounts.isEmpty()) {
            Files.deleteIfExists(storeFile.toPath())
        } else {
            persist(accounts)
        }
    }

    private fun persist(
        accounts: Map<String, Collection<CloudBookTombstone>>,
    ): Boolean {
        val parent = storeFile.parentFile ?: File(".")
        if (!parent.exists() && !parent.mkdirs()) {
            error("Could not create delete outbox directory: ${parent.absolutePath}")
        }
        val tempPath = Files.createTempFile(parent.toPath(), "${storeFile.name}.", ".tmp")
        val tempFile = tempPath.toFile()
        return try {
            tempFile.writeText(DesktopCloudBookDeleteOutboxCodec.encodeAccounts(accounts))
            try {
                Files.move(
                    tempPath,
                    storeFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempPath,
                    storeFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            true
        } finally {
            Files.deleteIfExists(tempPath)
        }
    }

    private fun logPersistFailure(error: Throwable) {
        logDesktopCloudSync {
            "desktop.delete_outbox.persist_failed error=\"${error.message.orEmpty().logPreview(240)}\""
        }
    }
}
