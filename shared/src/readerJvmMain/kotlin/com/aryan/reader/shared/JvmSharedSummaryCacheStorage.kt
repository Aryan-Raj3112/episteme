package com.aryan.reader.shared

import com.aryan.reader.shared.reader.sharedJvmEpistemeCacheRoot
import java.io.File

actual fun defaultSharedSummaryCacheStorage(): SharedSummaryCacheStorage {
    return JvmSharedSummaryCacheStorage(defaultSharedSummaryCacheRoot())
}

internal class JvmSharedSummaryCacheStorage(
    private val cacheRoot: File
) : SharedSummaryCacheStorage {

    override fun read(fileName: String): String? {
        val file = File(cacheRoot, fileName)
        return if (file.isFile) runCatching { file.readText() }.getOrNull() else null
    }

    override fun write(fileName: String, content: String): Boolean {
        return runCatching {
            cacheRoot.mkdirs()
            val file = File(cacheRoot, fileName)
            val temp = File(cacheRoot, "$fileName.tmp")
            temp.writeText(content)
            if (file.exists() && !file.delete()) {
                temp.delete()
                return false
            }
            if (!temp.renameTo(file)) {
                file.writeText(content)
                temp.delete()
            }
            true
        }.getOrElse { false }
    }

    override fun delete(fileName: String): Boolean {
        val file = File(cacheRoot, fileName)
        return if (file.exists()) file.delete() else true
    }

    override fun listFileNames(): List<String> {
        return cacheRoot.listFiles { file -> file.isFile && file.name.endsWith(".txt") }
            ?.map { it.name }
            .orEmpty()
    }
}

private fun defaultSharedSummaryCacheRoot(): File {
    val overridePath = System.getProperty("reader.summary.cache.dir")
    if (!overridePath.isNullOrBlank()) return File(overridePath).apply { mkdirs() }
    return File(sharedJvmEpistemeCacheRoot(), "chapter_summaries").apply { mkdirs() }
}
