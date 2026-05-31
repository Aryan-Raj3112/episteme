package com.aryan.reader.desktop

import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedLibrarySnapshotJson
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class DesktopLibraryDatabase(
    private val databaseFile: File = defaultDatabaseFile()
) {
    fun load(): SharedLibrarySnapshot {
        return loadFile(databaseFile)
            ?: loadFile(backupFile())
            ?: SharedLibrarySnapshot()
    }

    fun save(snapshot: SharedLibrarySnapshot) {
        val encoded = SharedLibrarySnapshotJson.encode(snapshot)
        databaseFile.writeTextAtomically(encoded)
        runCatching {
            backupFile().writeTextAtomically(encoded)
        }
    }

    private fun loadFile(file: File): SharedLibrarySnapshot? {
        if (!file.isFile) return null
        val raw = runCatching { file.readText() }.getOrNull() ?: return null
        val isJsonObject = runCatching {
            libraryDatabaseJson.parseToJsonElement(raw).jsonObject
        }.isSuccess
        if (!isJsonObject) return null
        return SharedLibrarySnapshotJson.decodeOrEmpty(raw)
    }

    private fun backupFile(): File {
        return File(databaseFile.parentFile ?: File("."), "${databaseFile.name}.bak")
    }

    companion object {
        fun defaultDatabaseFile(): File {
            return File(desktopUserDataRoot(), "library.json")
        }
    }
}

private val libraryDatabaseJson = Json { ignoreUnknownKeys = true }
