package com.aryan.reader

import android.content.Context
import com.aryan.reader.data.CloudFolderMetadataOutboxEntity
import com.aryan.reader.data.CloudFolderSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Bridges the existing folder-sidecar writers to the durable cloud-folder
 * protocol.  A sidecar is the source of truth; this class only records a
 * coalesced wake-up after the writer has validated the committed file.
 */
internal object CloudFolderMetadataSyncScheduler {
    suspend fun onSidecarCommitted(
        context: Context,
        sourceFolderUri: String,
        bookId: String,
        kind: String,
    ) = withContext(Dispatchers.IO) {
        if (!BuildConfig.IS_PRO) return@withContext
        val accountId = AuthRepository(context.applicationContext).getSignedInUser()?.uid
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext
        val normalizedUri = sourceFolderUri.trim().takeIf { it.isNotBlank() }
            ?: return@withContext
        val repository = CloudFolderSyncRepository(context.applicationContext, accountId)
        val rootId = CloudFolderAppStoragePrefs.rootIdForUri(
            context = context.applicationContext,
            accountId = accountId,
            uriString = normalizedUri,
        ) ?: repository.findBindingForLocalUri(normalizedUri)?.rootId
        if (rootId.isNullOrBlank()) {
            Timber.tag("EpistemeCloudFolderSync").d(
                "event=metadata_outbox_skip reason=unbound_folder book=${cloudFolderSafeId(bookId)}"
            )
            return@withContext
        }
        val binding = repository.getBinding(rootId)
        if (binding == null || binding.materializationMode == com.aryan.reader.shared.CloudFolderMaterializationMode.CLOUD_ONLY) {
            Timber.tag("EpistemeCloudFolderSync").d(
                "event=metadata_outbox_skip reason=no_local_materialization " +
                    "root=${cloudFolderSafeId(rootId)} book=${cloudFolderSafeId(bookId)}"
            )
            return@withContext
        }
        val pending = repository.markMetadataOutboxPending(
            rootId = rootId,
            bookId = bookId,
            dirtyKinds = kind,
        ) ?: return@withContext
        Timber.tag("EpistemeCloudFolderSync").d(
            "event=metadata_outbox_enqueue root=${cloudFolderSafeId(rootId)} " +
                "book=${cloudFolderSafeId(bookId)} generation=${pending.generation} kinds=${pending.dirtyKinds}"
        )
        if (isCloudFolderSyncEnabled(context.applicationContext)) {
            CloudFolderSyncWorker.enqueue(
                context = context.applicationContext,
                accountId = accountId,
                rootId = rootId,
                direction = com.aryan.reader.shared.CloudFolderSyncDirection.NONE,
                replace = false,
                metadataOnly = true,
            )
        }
    }

    const val METADATA_KIND: String = CloudFolderMetadataOutboxEntity.KIND_METADATA
    const val ANNOTATIONS_KIND: String = CloudFolderMetadataOutboxEntity.KIND_ANNOTATIONS
}
