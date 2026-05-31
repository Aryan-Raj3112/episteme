package com.aryan.reader.shared

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

data class ReaderTtsChapterCacheInfo(
    val chapterTitle: String,
    val chunkCount: Int,
    val totalChunks: Int?,
    val sizeBytes: Long,
    val directoryPath: String,
    val matchingFilePaths: List<String> = emptyList()
)

class ReaderTtsFileCacheManager(
    cacheRoot: File
) {
    private val baseDir = cacheRoot

    fun saveTotalChunks(bookTitle: String, chapterTitle: String?, totalChunks: Int) {
        val chapterDir = chapterDir(bookTitle, chapterTitle)
        if (!chapterDir.exists()) chapterDir.mkdirs()
        File(chapterDir, "total_chunks.txt").writeText(totalChunks.toString())
    }

    fun getCacheFile(
        bookTitle: String,
        chapterTitle: String?,
        text: String,
        speakerId: String
    ): File {
        val chapterDir = chapterDir(bookTitle, chapterTitle)
        if (!chapterDir.exists()) chapterDir.mkdirs()
        val hashParams = hash(text + speakerId + "CLOUD")
        val safeSpeaker = sanitizeFileToken(speakerId)
        return File(chapterDir, "cached_chunk_${safeSpeaker}_$hashParams.wav")
    }

    fun getBookCacheDir(bookTitle: String): File {
        return File(baseDir, safeCacheSegment(bookTitle, "book"))
    }

    fun getChapterCaches(bookTitle: String, speakerFilter: String? = null): List<ReaderTtsChapterCacheInfo> {
        val bookDir = getBookCacheDir(bookTitle)
        if (!bookDir.exists()) return emptyList()

        return bookDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { chapterDir ->
                val files = chapterDir.listFiles()
                    ?.filter { file -> file.isFile && file.name.endsWith(".wav") && file.matchesSpeaker(speakerFilter) }
                    .orEmpty()
                if (files.isEmpty()) return@mapNotNull null

                val metaFile = File(chapterDir, "total_chunks.txt")
                ReaderTtsChapterCacheInfo(
                    chapterTitle = chapterDir.name,
                    chunkCount = files.size,
                    totalChunks = metaFile.takeIf { it.exists() }?.readText()?.toIntOrNull(),
                    sizeBytes = files.sumOf { it.length() },
                    directoryPath = chapterDir.absolutePath,
                    matchingFilePaths = files.map { it.absolutePath }
                )
            }
            ?.sortedBy { it.chapterTitle }
            .orEmpty()
    }

    fun getCacheSummary(bookTitle: String, speakerId: String? = null): ReaderTtsCacheSummary {
        val allChapters = getChapterCaches(bookTitle, speakerFilter = null)
        val voiceChapters = speakerId
            ?.takeIf { it.isNotBlank() }
            ?.let { getChapterCaches(bookTitle, speakerFilter = it) }
            .orEmpty()
        return ReaderTtsCacheSummary(
            cachedChapterCount = allChapters.size,
            cachedChunkCount = allChapters.sumOf { it.chunkCount },
            currentVoiceChunkCount = voiceChapters.sumOf { it.chunkCount },
            totalSizeBytes = allChapters.sumOf { it.sizeBytes },
            currentVoiceSizeBytes = voiceChapters.sumOf { it.sizeBytes }
        )
    }

    fun cachedSpeakers(bookTitle: String): List<String> {
        val bookDir = getBookCacheDir(bookTitle)
        if (!bookDir.exists()) return emptyList()
        return bookDir.listFiles()
            ?.filter { it.isDirectory }
            ?.flatMap { chapterDir ->
                chapterDir.listFiles()
                    ?.mapNotNull { it.speakerFromCacheFileName() }
                    .orEmpty()
            }
            ?.distinct()
            ?.sorted()
            .orEmpty()
    }

    fun deleteSpecificFiles(filePaths: List<String>, chapterDirectoryPath: String) {
        val chapterDir = File(chapterDirectoryPath)
        if (!chapterDir.isInsideBaseDir()) return
        filePaths.forEach { path ->
            File(path).takeIf { it.isInside(chapterDir) }?.delete()
        }
        if (chapterDir.listFiles()?.isEmpty() == true && chapterDir.isInsideBaseDir()) {
            chapterDir.deleteRecursively()
        }
    }

    fun clearBookCache(bookTitle: String) {
        getBookCacheDir(bookTitle).takeIf { it.isInsideBaseDir() }?.deleteRecursively()
    }

    fun clearBookCacheForSpeaker(bookTitle: String, speakerId: String) {
        getChapterCaches(bookTitle, speakerFilter = speakerId).forEach { chapter ->
            deleteSpecificFiles(chapter.matchingFilePaths, chapter.directoryPath)
        }
    }

    private fun chapterDir(bookTitle: String, chapterTitle: String?): File {
        return File(getBookCacheDir(bookTitle), safeCacheSegment(chapterTitle ?: "Unknown_Chapter", "chapter"))
    }

    private fun File.matchesSpeaker(speakerFilter: String?): Boolean {
        if (speakerFilter.isNullOrBlank() || speakerFilter == "All") return true
        return speakerFromCacheFileName() == speakerFilter
    }

    private fun File.speakerFromCacheFileName(): String? {
        if (!name.startsWith("cached_chunk_") || !name.endsWith(".wav")) return null
        val withoutPrefix = name.removePrefix("cached_chunk_")
        return withoutPrefix.substringBeforeLast('_').takeIf { it.isNotBlank() }
    }

    private fun safeCacheSegment(name: String, fallback: String): String {
        val normalized = name.trim().takeIf { it.isNotBlank() } ?: fallback
        val slug = normalized
            .replace(Regex("[^a-zA-Z0-9_-]+"), "_")
            .trim('_', '-')
            .ifBlank { fallback }
            .take(48)
        return "${slug}_${hash(normalized).take(16)}"
    }

    private fun sanitizeFileToken(name: String): String {
        return name
            .replace(Regex("[^a-zA-Z0-9._-]+"), "_")
            .trim('.', '_', '-')
            .ifBlank { "default" }
    }

    private fun File.isInsideBaseDir(): Boolean {
        return isInside(baseDir)
    }

    private fun File.isInside(root: File): Boolean {
        val rootPath = runCatching { root.canonicalFile.path }.getOrNull() ?: return false
        val targetPath = runCatching { canonicalFile.path }.getOrNull() ?: return false
        return targetPath != rootPath && targetPath.startsWith(rootPath + File.separator)
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }
}

fun createReaderTtsWavHeaderUnknownLength(sampleRate: Int): ByteArray {
    val numChannels = 1
    val bitsPerSample = 16
    val byteRate = sampleRate * numChannels * bitsPerSample / 8
    val blockAlign = numChannels * bitsPerSample / 8

    val header = ByteBuffer.allocate(44)
    header.order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt(0x7FFFFFFF)
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16)
    header.putShort(1.toShort())
    header.putShort(numChannels.toShort())
    header.putInt(sampleRate)
    header.putInt(byteRate)
    header.putShort(blockAlign.toShort())
    header.putShort(bitsPerSample.toShort())
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(0x7FFFFFFF - 36)

    return header.array()
}

fun patchReaderTtsWavHeader(file: File, pcmDataLength: Int) {
    RandomAccessFile(file, "rw").use { raf ->
        raf.seek(4)
        raf.writeInt(Integer.reverseBytes(36 + pcmDataLength))
        raf.seek(40)
        raf.writeInt(Integer.reverseBytes(pcmDataLength))
    }
}
