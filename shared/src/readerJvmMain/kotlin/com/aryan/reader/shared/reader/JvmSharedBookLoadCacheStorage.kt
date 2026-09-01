package com.aryan.reader.shared.reader

import java.io.File

actual fun defaultSharedBookLoadCacheStorage(): SharedBookLoadCacheStorage {
    return JvmSharedBookLoadCacheStorage(defaultSharedBookLoadCacheRoot())
}

internal class JvmSharedBookLoadCacheStorage(
    private val cacheRoot: File
) : SharedBookLoadCacheStorage {

    override fun read(cacheId: String): ByteArray? {
        val file = cacheFile(cacheId)
        return if (file.isFile) file.readBytes() else null
    }

    override fun write(cacheId: String, bytes: ByteArray): Boolean {
        return runCatching {
            writeBookLoadCacheAtomically(cacheFile(cacheId), bytes)
            true
        }.getOrElse { false }
    }

    override fun cleanupOldEntries() {
        val files = cacheRoot.listFiles { file -> file.isFile && file.name.endsWith(".book.pb") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        files.drop(80).forEach { it.delete() }
    }

    override fun clear() {
        cacheRoot.deleteRecursively()
        cacheRoot.mkdirs()
    }

    private fun cacheFile(cacheId: String): File {
        return File(cacheRoot, "$cacheId.book.pb")
    }
}

private fun defaultSharedBookLoadCacheRoot(): File {
    val overridePath = System.getProperty("reader.book.load.cache.dir")
    if (!overridePath.isNullOrBlank()) return File(overridePath).apply { mkdirs() }
    return File(sharedJvmEpistemeCacheRoot(), "book_load_cache").apply { mkdirs() }
}

private fun writeBookLoadCacheAtomically(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val parent = file.parentFile ?: file.absoluteFile.parentFile ?: File(".")
    val temp = File(parent, "${file.name}.tmp")
    temp.writeBytes(bytes)
    if (file.exists() && !file.delete()) {
        temp.delete()
        return
    }
    if (!temp.renameTo(file)) {
        file.writeBytes(bytes)
        temp.delete()
    }
}
