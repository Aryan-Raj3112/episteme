package com.aryan.reader.desktop

import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties

internal data class DesktopCachedSummaryItem(
    val index: Int,
    val title: String,
    val summary: String
)

internal class DesktopSummaryCacheStore(
    private val root: File = File(desktopUserCacheRoot(), "summary-cache")
) {
    fun getSummary(bookKey: String, index: Int): String? {
        val file = summaryFile(bookKey, index)
        if (!file.isFile) return null
        return load(file).getProperty("summary", "").takeIf { it.isNotBlank() }
    }

    fun saveSummary(bookKey: String, index: Int, title: String, summary: String) {
        if (summary.isBlank()) return
        val file = summaryFile(bookKey, index)
        file.parentFile?.mkdirs()
        Properties().apply {
            setProperty("index", index.toString())
            setProperty("title", title)
            setProperty("summary", summary)
        }.also { properties ->
            file.outputStream().use { output ->
                properties.store(output, "Episteme desktop summary cache")
            }
        }
    }

    fun getAllSummaries(bookKey: String): List<DesktopCachedSummaryItem> {
        val directory = bookDirectory(bookKey)
        return directory.listFiles { file -> file.isFile && file.extension == "properties" }
            .orEmpty()
            .mapNotNull { file ->
                val properties = load(file)
                val index = properties.getProperty("index")?.toIntOrNull()
                    ?: file.nameWithoutExtension.toIntOrNull()
                    ?: return@mapNotNull null
                val summary = properties.getProperty("summary", "").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                DesktopCachedSummaryItem(
                    index = index,
                    title = properties.getProperty("title", "Chapter ${index + 1}"),
                    summary = summary
                )
            }
            .sortedBy { it.index }
    }

    fun deleteSummary(bookKey: String, index: Int) {
        summaryFile(bookKey, index).delete()
    }

    fun clearBookCache(bookKey: String) {
        val directory = bookDirectory(bookKey)
        directory.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
    }

    private fun summaryFile(bookKey: String, index: Int): File {
        return bookDirectory(bookKey).resolve("$index.properties")
    }

    private fun bookDirectory(bookKey: String): File {
        return root.resolve(bookKey.cacheKey())
    }

    private fun load(file: File): Properties {
        return Properties().apply {
            runCatching { file.inputStream().use { input -> load(input) } }
        }
    }
}

private fun String.cacheKey(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(trim().ifBlank { "untitled" }.toByteArray(Charsets.UTF_8))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).take(32)
}
