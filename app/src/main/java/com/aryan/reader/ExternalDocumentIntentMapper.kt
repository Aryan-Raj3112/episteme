package com.aryan.reader

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.aryan.reader.shared.ExternalDocumentAction
import com.aryan.reader.shared.ExternalDocumentCandidate
import com.aryan.reader.shared.ExternalDocumentGrantCapabilities
import com.aryan.reader.shared.ExternalDocumentIntakeResult
import com.aryan.reader.shared.ExternalDocumentSource
import com.aryan.reader.shared.SharedExternalDocumentIntake

/**
 * Extracts document URIs from Android's VIEW/SEND handoff shapes.  Only URI
 * carriers are considered: EXTRA_TEXT and ClipData text are intentionally not
 * treated as files, even when an ACTION_SEND intent declares text/plain.
 */
object ExternalDocumentIntentMapper {
    fun map(intent: Intent, context: Context? = null): ExternalDocumentIntakeResult? {
        val action = intent.externalDocumentAction() ?: return null
        val resolver = context?.contentResolver
        val candidates = buildList {
            intent.data?.let { uri ->
                add(uri.candidate(ExternalDocumentSource.DATA, intent.type, resolver))
            }
            intent.streamUris().forEach { uri ->
                add(uri.candidate(ExternalDocumentSource.EXTRA_STREAM, intent.type, resolver))
            }
            intent.clipData?.let { clipData ->
                for (index in 0 until clipData.itemCount) {
                    clipData.getItemAt(index).uri?.let { uri ->
                        add(uri.candidate(ExternalDocumentSource.CLIP_DATA, intent.type, resolver))
                    }
                }
            }
        }

        val grants = ExternalDocumentGrantCapabilities(
            read = intent.hasFlag(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            write = intent.hasFlag(Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
            persistable = intent.hasFlag(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),
            prefix = intent.hasFlag(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION),
        )
        return SharedExternalDocumentIntake.normalize(
            action = action,
            candidates = candidates,
            grantCapabilities = grants,
        )
    }

    private fun Intent.externalDocumentAction(): ExternalDocumentAction? = when (action) {
        Intent.ACTION_VIEW -> ExternalDocumentAction.VIEW
        Intent.ACTION_SEND -> ExternalDocumentAction.SEND
        Intent.ACTION_SEND_MULTIPLE -> ExternalDocumentAction.SEND_MULTIPLE
        else -> null
    }

    private fun Intent.streamUris(): List<Uri> {
        val stream = runCatching { extras?.get(Intent.EXTRA_STREAM) }.getOrNull() ?: return emptyList()
        return when (stream) {
            is Uri -> listOf(stream)
            is List<*> -> stream.filterIsInstance<Uri>()
            is Array<*> -> stream.filterIsInstance<Uri>()
            else -> emptyList()
        }
    }

    private fun Uri.candidate(
        source: ExternalDocumentSource,
        intentMimeType: String?,
        resolver: ContentResolver?,
    ): ExternalDocumentCandidate {
        val displayName = resolver?.displayNameFor(this) ?: lastPathSegment
        val mimeType = resolver?.typeFor(this) ?: intentMimeType
        return ExternalDocumentCandidate(
            uri = toString(),
            displayName = displayName,
            mimeType = mimeType,
            source = source,
        )
    }

    private fun Intent.hasFlag(flag: Int): Boolean = flags and flag != 0

    private fun ContentResolver.typeFor(uri: Uri): String? = runCatching { getType(uri) }
        .getOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    private fun ContentResolver.displayNameFor(uri: Uri): String? {
        if (uri.scheme != "content") return null
        return runCatching {
            query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor: Cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index)
                    else null
                } else {
                    null
                }
            }
        }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}
