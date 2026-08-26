package com.aryan.reader

import android.content.Context
import androidx.core.content.edit
import com.aryan.reader.shared.CloudFolderSyncSelection
import kotlinx.serialization.json.Json

/** Device-local account selection. New installs exclude folder roots by default. */
object CloudFolderSyncPrefs {
    private const val PREFS_NAME = "cloud_folder_sync"
    private const val KEY_SELECTION = "selection_v1"

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun load(context: Context): CloudFolderSyncSelection {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTION, null)
            ?: return CloudFolderSyncSelection.Default
        return runCatching {
            json.decodeFromString(CloudFolderSyncSelection.serializer(), raw).normalized()
        }.getOrDefault(CloudFolderSyncSelection.Default)
    }

    fun save(context: Context, selection: CloudFolderSyncSelection) {
        val normalized = selection.normalized()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            putString(KEY_SELECTION, json.encodeToString(CloudFolderSyncSelection.serializer(), normalized))
        }
    }
}
