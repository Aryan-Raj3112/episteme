package com.aryan.reader.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.aryan.reader.shared.ReaderTexture
import com.aryan.reader.shared.ReaderTextureFilePrefix
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Base64
import java.util.Locale
import javax.imageio.ImageIO

internal object DesktopReaderTextures {
    private val bytesCache = mutableMapOf<String, ByteArray?>()
    private val dataUriCache = mutableMapOf<String, String?>()
    private val imageCache = mutableMapOf<String, ImageBitmap?>()
    private val importExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    fun importedTextureIds(): List<String> {
        return readerTextureDirectory()
            .listFiles { file -> file.isFile && file.extension.lowercase(Locale.ROOT) in importExtensions }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            ?.map { ReaderTextureFilePrefix + it.absolutePath }
            .orEmpty()
    }

    fun importTexture(source: File): String? {
        if (!source.isFile) return null
        val extension = source.extension.lowercase(Locale.ROOT)
            .takeIf { it in importExtensions }
            ?: return null
        val safeName = source.nameWithoutExtension
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "texture" }
        val directory = readerTextureDirectory().apply { mkdirs() }
        val target = File(directory, "texture_${System.currentTimeMillis()}_$safeName.$extension")
        return runCatching {
            source.copyTo(target, overwrite = false)
            val textureId = ReaderTextureFilePrefix + target.absolutePath
            bytesCache.remove(textureId)
            dataUriCache.remove(textureId)
            imageCache.remove(textureId)
            textureId
        }.getOrNull()
    }

    fun dataUriFor(textureId: String): String? {
        return dataUriCache.getOrPut(textureId) {
            val bytes = bytesFor(textureId) ?: return@getOrPut null
            val extension = textureExtension(textureId)
            "data:${imageMimeTypeForExtension(extension)};base64," +
                Base64.getEncoder().encodeToString(bytes)
        }
    }

    fun imageBitmapFor(textureId: String?): ImageBitmap? {
        val id = textureId ?: return null
        return imageCache.getOrPut(id) {
            val bytes = bytesFor(id) ?: return@getOrPut null
            runCatching {
                ImageIO.read(ByteArrayInputStream(bytes))?.toComposeImageBitmap()
            }.getOrNull()
        }
    }

    private fun bytesFor(textureId: String): ByteArray? {
        return bytesCache.getOrPut(textureId) {
            if (textureId.startsWith(ReaderTextureFilePrefix)) {
                File(textureId.removePrefix(ReaderTextureFilePrefix)).takeIf { it.isFile }?.readBytes()
            } else {
                val texture = ReaderTexture.entries.firstOrNull { it.id == textureId } ?: return@getOrPut null
                val classLoader = Thread.currentThread().contextClassLoader ?: DesktopReaderTextures::class.java.classLoader
                classLoader
                    ?.getResourceAsStream(texture.assetPath)
                    ?.use { it.readBytes() }
                    ?: DesktopReaderTextures::class.java.classLoader
                        ?.getResourceAsStream(texture.assetPath)
                        ?.use { it.readBytes() }
            }
        }
    }

    private fun textureExtension(textureId: String): String {
        if (textureId.startsWith(ReaderTextureFilePrefix)) {
            return File(textureId.removePrefix(ReaderTextureFilePrefix)).extension
        }
        return ReaderTexture.entries.firstOrNull { it.id == textureId }
            ?.assetPath
            ?.substringAfterLast('.', "png")
            ?: "png"
    }

    private fun readerTextureDirectory(): File {
        return File(desktopUserDataRoot(), "reader_textures")
    }
}

private fun imageMimeTypeForExtension(extension: String): String {
    return when (extension.lowercase(Locale.ROOT)) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> "image/png"
    }
}
