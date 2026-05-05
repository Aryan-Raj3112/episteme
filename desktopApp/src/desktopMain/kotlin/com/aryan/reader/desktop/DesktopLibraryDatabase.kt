package com.aryan.reader.desktop

import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedLibrarySnapshotJson
import java.io.File

class DesktopLibraryDatabase(
    private val databaseFile: File = defaultDatabaseFile()
) {
    fun load(): SharedLibrarySnapshot {
        if (!databaseFile.exists()) return SharedLibrarySnapshot()
        return SharedLibrarySnapshotJson.decodeOrEmpty(databaseFile.readText())
    }

    fun save(snapshot: SharedLibrarySnapshot) {
        databaseFile.parentFile?.mkdirs()
        databaseFile.writeText(SharedLibrarySnapshotJson.encode(snapshot))
    }

    companion object {
        fun defaultDatabaseFile(): File {
            val baseDir = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                ?: File(System.getProperty("user.home"), "AppData/Roaming").absolutePath
            return File(baseDir, "Episteme/library.json")
        }
    }
}
