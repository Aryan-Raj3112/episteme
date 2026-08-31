package com.aryan.reader.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Durable handoff requests are used when native callbacks arrive before the
 * Compose tree is ready (or while the process is being restored).  The
 * envelope intentionally contains only serializable data; file copying,
 * cleanup, and reader opening remain platform responsibilities.
 */
@Serializable
enum class MobileHandoffRequestKind {
    IMPORT_BATCH,
    EXTERNAL_FILE,
    TTS_TARGET,
}

@Serializable
enum class MobileHandoffOpenMode {
    LIBRARY_COPY,
    TEMPORARY,
}

@Serializable
enum class MobileHandoffCleanupPolicy {
    KEEP,
    DELETE_ON_CONSUME,
    DELETE_ON_FAILURE,
}

@Serializable
enum class MobileHandoffRequestState {
    PENDING,
    FAILED,
    CONSUMED,
}

@Serializable
data class MobileHandoffFileIdentity(
    val name: String,
    val path: String,
    val contentId: String = "",
    val size: Long = 0L,
    val lastModifiedTimestamp: Long = 0L,
    val relativePath: String = "",
)

/**
 * Playback source reported for TTS sessions started from the audiobook
 * ("listen with TTS") flow, as opposed to the reader or popup flows.
 */
const val TTS_PLAYBACK_SOURCE_AUDIOBOOK = "AUDIOBOOK_TTS"

/** The complete locator payload needed to reopen an Android TTS session. */
@Serializable
data class MobileHandoffTtsTarget(
    val bookId: String,
    val sourceCfi: String? = null,
    val startOffset: Int? = null,
    val chapterIndex: Int? = null,
    val pageIndex: Int? = null,
    val blockIndex: Int? = null,
    val charOffset: Int? = null,
    /** Which flow started the TTS session; used to pick the surface a tap opens. */
    val playbackSource: String? = null,
) {
    /** True when tapping this target should open the audiobook playback surface. */
    val isAudiobookListening: Boolean
        get() = playbackSource == TTS_PLAYBACK_SOURCE_AUDIOBOOK
}

@Serializable
data class MobileHandoffRequest(
    val requestId: String,
    val kind: MobileHandoffRequestKind,
    val files: List<MobileHandoffFileIdentity> = emptyList(),
    val openMode: MobileHandoffOpenMode = MobileHandoffOpenMode.LIBRARY_COPY,
    val cleanupPolicy: MobileHandoffCleanupPolicy = MobileHandoffCleanupPolicy.KEEP,
    val autoOpen: Boolean = true,
    val failedCount: Int = 0,
    val wasCancelled: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
    val ttsTarget: MobileHandoffTtsTarget? = null,
    val attemptCount: Int = 0,
    val state: MobileHandoffRequestState = MobileHandoffRequestState.PENDING,
    val createdAtMs: Long = 0L,
    val lastErrorAtMs: Long? = null,
)

@Serializable
data class MobileHandoffEnvelope(
    val schemaVersion: Int = CURRENT_MOBILE_HANDOFF_SCHEMA_VERSION,
    val requests: List<MobileHandoffRequest> = emptyList(),
)

enum class MobileHandoffAction {
    REPLAY,
    CONSUME,
}

/**
 * Small deterministic state machine shared by native bridges.  TTS targets
 * win over generic imports/external opens, while FIFO order is retained within
 * a kind.  Request ids are authoritative; the semantic key prevents repeated
 * delivery of the same native callback when a notification is tapped twice.
 */
object MobileHandoffReducer {
    fun enqueue(
        envelope: MobileHandoffEnvelope,
        request: MobileHandoffRequest,
    ): MobileHandoffEnvelope {
        val normalized = request.normalized()
        val duplicate = envelope.requests.firstOrNull { existing ->
            existing.state == MobileHandoffRequestState.PENDING &&
                (existing.requestId == normalized.requestId || existing.semanticKey() == normalized.semanticKey())
        }
        if (duplicate != null) {
            val upgraded = duplicate.copy(
                files = duplicate.files.ifEmpty { normalized.files },
                ttsTarget = duplicate.ttsTarget ?: normalized.ttsTarget,
                message = normalized.message ?: duplicate.message,
                messageIsError = duplicate.messageIsError || normalized.messageIsError,
                failedCount = maxOf(duplicate.failedCount, normalized.failedCount),
            )
            return envelope.copy(
                requests = envelope.requests.map { if (it === duplicate) upgraded else it }
            )
        }
        return envelope.copy(requests = envelope.requests + normalized)
    }

    fun replay(envelope: MobileHandoffEnvelope): MobileHandoffRequest? = envelope.requests
        .asSequence()
        .filter { it.state == MobileHandoffRequestState.PENDING }
        .sortedWith(compareByDescending<MobileHandoffRequest> { it.kind.priority() }.thenBy { it.createdAtMs })
        .firstOrNull()

    fun consume(
        envelope: MobileHandoffEnvelope,
        requestId: String,
    ): MobileHandoffEnvelope {
        if (envelope.requests.none { it.requestId == requestId }) return envelope
        return envelope.copy(
            requests = envelope.requests.filterNot { it.requestId == requestId }
        )
    }

    fun fail(
        envelope: MobileHandoffEnvelope,
        requestId: String,
        message: String?,
        nowMs: Long = 0L,
    ): MobileHandoffEnvelope = envelope.copy(
        requests = envelope.requests.mapNotNull { request ->
            if (request.requestId != requestId) {
                request
            } else if (request.cleanupPolicy == MobileHandoffCleanupPolicy.DELETE_ON_FAILURE) {
                null
            } else {
                request.copy(
                    state = MobileHandoffRequestState.FAILED,
                    attemptCount = request.attemptCount + 1,
                    failedCount = request.failedCount + 1,
                    message = message ?: request.message,
                    messageIsError = true,
                    lastErrorAtMs = nowMs.takeIf { it > 0L },
                )
            }
        }
    )

    fun retryFailed(envelope: MobileHandoffEnvelope): MobileHandoffEnvelope = envelope.copy(
        requests = envelope.requests.map { request ->
            if (request.state == MobileHandoffRequestState.FAILED) {
                request.copy(state = MobileHandoffRequestState.PENDING)
            } else {
                request
            }
        }
    )

    fun clearFinished(envelope: MobileHandoffEnvelope): MobileHandoffEnvelope = envelope.copy(
        requests = envelope.requests.filter { it.state == MobileHandoffRequestState.PENDING }
    )

    private fun MobileHandoffRequest.normalized(): MobileHandoffRequest {
        val safeId = requestId.trim().ifBlank { semanticKey() }
        return copy(
            requestId = safeId,
            files = files.filter { it.path.isNotBlank() || it.contentId.isNotBlank() },
            failedCount = failedCount.coerceAtLeast(0),
            attemptCount = attemptCount.coerceAtLeast(0),
        )
    }

    private fun MobileHandoffRequest.semanticKey(): String {
        val fileKey = files.joinToString("|") { file ->
            file.contentId.ifBlank { file.path.ifBlank { file.name } }
        }
        val targetKey = ttsTarget?.let {
            listOf(it.bookId, it.sourceCfi.orEmpty(), it.startOffset, it.chapterIndex, it.pageIndex, it.blockIndex, it.charOffset)
                .joinToString(":")
        }.orEmpty()
        return listOf(kind.name, fileKey, openMode.name, targetKey).joinToString("#")
    }

    private fun MobileHandoffRequestKind.priority(): Int = when (this) {
        MobileHandoffRequestKind.TTS_TARGET -> 3
        MobileHandoffRequestKind.EXTERNAL_FILE -> 2
        MobileHandoffRequestKind.IMPORT_BATCH -> 1
    }
}

object MobileHandoffCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(envelope: MobileHandoffEnvelope): String = json.encodeToString(envelope)

    fun decodeOrEmpty(value: String?): MobileHandoffEnvelope = value
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { json.decodeFromString<MobileHandoffEnvelope>(it) }.getOrNull() }
        ?: MobileHandoffEnvelope()
}

object MobileHandoffMapper {
    fun importBatch(
        requestId: String,
        files: List<MobileHandoffFileIdentity>,
        failedCount: Int = 0,
        wasCancelled: Boolean = false,
        autoOpen: Boolean = true,
        message: String? = null,
        messageIsError: Boolean = false,
        createdAtMs: Long = 0L,
    ): MobileHandoffRequest = MobileHandoffRequest(
        requestId = requestId,
        kind = MobileHandoffRequestKind.IMPORT_BATCH,
        files = files,
        openMode = MobileHandoffOpenMode.LIBRARY_COPY,
        cleanupPolicy = MobileHandoffCleanupPolicy.DELETE_ON_CONSUME,
        autoOpen = autoOpen,
        failedCount = failedCount,
        wasCancelled = wasCancelled,
        message = message,
        messageIsError = messageIsError,
        createdAtMs = createdAtMs,
    )

    fun externalFile(
        requestId: String,
        file: MobileHandoffFileIdentity,
        openMode: MobileHandoffOpenMode,
        cleanupPolicy: MobileHandoffCleanupPolicy,
        autoOpen: Boolean = true,
        createdAtMs: Long = 0L,
    ): MobileHandoffRequest = MobileHandoffRequest(
        requestId = requestId,
        kind = MobileHandoffRequestKind.EXTERNAL_FILE,
        files = listOf(file),
        openMode = openMode,
        cleanupPolicy = cleanupPolicy,
        autoOpen = autoOpen,
        createdAtMs = createdAtMs,
    )

    fun ttsTarget(
        requestId: String,
        target: MobileHandoffTtsTarget,
        createdAtMs: Long = 0L,
    ): MobileHandoffRequest = MobileHandoffRequest(
        requestId = requestId,
        kind = MobileHandoffRequestKind.TTS_TARGET,
        ttsTarget = target,
        autoOpen = true,
        createdAtMs = createdAtMs,
    )

    fun stableTtsRequestId(target: MobileHandoffTtsTarget): String = buildString {
        append("tts:")
        append(target.bookId)
        append(':')
        append(target.sourceCfi.orEmpty())
        append(':')
        append(target.startOffset ?: -1)
        append(':')
        append(target.chapterIndex ?: -1)
        append(':')
        append(target.pageIndex ?: -1)
        append(':')
        append(target.blockIndex ?: -1)
        append(':')
        append(target.charOffset ?: -1)
    }
}

const val CURRENT_MOBILE_HANDOFF_SCHEMA_VERSION: Int = 1
