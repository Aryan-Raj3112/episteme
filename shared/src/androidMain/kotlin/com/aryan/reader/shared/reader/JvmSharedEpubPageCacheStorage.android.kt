package com.aryan.reader.shared.reader

import java.io.File
import java.security.MessageDigest
import java.util.Locale

private const val AndroidEpubPageCacheDir = "epub_page_cache"

class JvmSharedEpubPageCacheStorage(
    private val root: File
) : SharedEpubPageCacheStorage {

    override fun exists(path: String): Boolean = file(path).exists()

    override fun isDirectory(path: String): Boolean = file(path).isDirectory

    override fun readBytes(path: String): ByteArray? {
        val file = file(path)
        if (!file.isFile) return null
        return file.readBytes()
    }

    override fun writeBytesAtomically(path: String, bytes: ByteArray) {
        val file = file(path)
        file.parentFile?.mkdirs()
        val parent = file.parentFile ?: file.absoluteFile.parentFile ?: root
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

    override fun deleteFile(path: String) {
        file(path).delete()
    }

    override fun deleteDirectory(path: String) {
        file(path).deleteRecursively()
    }

    override fun deleteAll() {
        root.deleteRecursively()
        root.mkdirs()
    }

    override fun listFileNames(path: String): List<String> {
        return file(path).listFiles()?.map { it.name } ?: emptyList()
    }

    override fun lastModifiedMillis(path: String): Long {
        return file(path).lastModified()
    }

    override fun rootLabel(): String = root.absolutePath

    private fun file(path: String): File {
        if (path.isBlank()) return root
        return File(root, path)
    }

    companion object {
        fun default(): JvmSharedEpubPageCacheStorage {
            val overridePath = System.getProperty("reader.epub.pagination.cache.dir")
            if (!overridePath.isNullOrBlank()) {
                return JvmSharedEpubPageCacheStorage(File(overridePath).apply { mkdirs() })
            }
            val baseDir = System.getProperty("java.io.tmpdir")
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?: File(System.getProperty("user.home", "."))
            return JvmSharedEpubPageCacheStorage(File(baseDir, AndroidEpubPageCacheDir).apply { mkdirs() })
        }
    }
}

actual fun defaultSharedEpubPageCacheStorage(): SharedEpubPageCacheStorage {
    return JvmSharedEpubPageCacheStorage.default()
}

actual fun sharedSha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(Locale.US, byte.toInt() and 0xFF) }
}