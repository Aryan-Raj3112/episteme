package com.aryan.reader

import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

internal object SyncedFolderPrefs {
    const val KEY_SYNCED_FOLDERS_JSON = "synced_folders_list_json"
    const val KEY_LEGACY_SYNCED_FOLDER_URI = "synced_folder_uri"
    const val KEY_LEGACY_LAST_FOLDER_SCAN_TIME = "last_folder_scan_time"

    fun decodeSyncedFolders(
        jsonString: String?,
        legacyUri: String?,
        legacyLastScanTime: Long = 0L,
        legacyNameResolver: (String) -> String = { it },
        syncableTypes: Set<FileType> = ANDROID_SYNCABLE_FILE_TYPES
    ): List<SyncedFolder> {
        if (jsonString == null) {
            return legacyUri
                ?.takeIf { it.isNotBlank() }
                ?.let { uri ->
                    listOf(
                        SyncedFolder(
                            uriString = uri,
                            name = legacyNameResolver(uri),
                            lastScanTime = legacyLastScanTime,
                            allowedFileTypes = syncableTypes,
                            localSyncEnabled = true
                        )
                    )
                }
                .orEmpty()
        }

        return try {
            val array = JSONArray(jsonString)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val uri = obj.optString("uri").takeIf { it.isNotBlank() }
                    if (uri == null) continue
                    val name = obj.optString("name").takeIf { it.isNotBlank() } ?: legacyNameResolver(uri)
                    add(
                        SyncedFolder(
                            uriString = uri,
                            name = name,
                            lastScanTime = obj.optLong("lastScanTime", 0L),
                            allowedFileTypes = decodeAllowedFileTypes(obj, syncableTypes),
                            localSyncEnabled = obj.optBoolean("localSyncEnabled", true)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse synced folders JSON")
            emptyList()
        }
    }

    fun encodeSyncedFolders(
        folders: List<SyncedFolder>,
        syncableTypes: Set<FileType> = ANDROID_SYNCABLE_FILE_TYPES
    ): String {
        val jsonArray = JSONArray()
        folders.forEach { folder ->
            val obj = JSONObject()
            obj.put("uri", folder.uriString)
            obj.put("name", folder.name)
            obj.put("lastScanTime", folder.lastScanTime)
            obj.put("localSyncEnabled", folder.localSyncEnabled)
            val typesArray = JSONArray()
            folder.allowedFileTypes
                .filter { it in syncableTypes }
                .forEach { typesArray.put(it.name) }
            obj.put("allowedFileTypes", typesArray)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    fun isLocalSyncEnabled(
        jsonString: String?,
        legacyUri: String?,
        folderUriString: String,
        syncableTypes: Set<FileType> = ANDROID_SYNCABLE_FILE_TYPES
    ): Boolean {
        return decodeSyncedFolders(
            jsonString = jsonString,
            legacyUri = legacyUri,
            syncableTypes = syncableTypes
        ).firstOrNull { it.uriString == folderUriString }?.localSyncEnabled == true
    }

    private fun decodeAllowedFileTypes(
        obj: JSONObject,
        syncableTypes: Set<FileType>
    ): Set<FileType> {
        if (!obj.has("allowedFileTypes")) return syncableTypes
        val typesArray = obj.optJSONArray("allowedFileTypes") ?: return syncableTypes
        return buildSet {
            for (i in 0 until typesArray.length()) {
                val type = runCatching { FileType.valueOf(typesArray.getString(i)) }.getOrNull()
                if (type != null && type in syncableTypes) add(type)
            }
        }
    }
}
