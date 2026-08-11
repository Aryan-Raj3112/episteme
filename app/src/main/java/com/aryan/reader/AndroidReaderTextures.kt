@file:OptIn(ExperimentalMaterial3Api::class) @file:Suppress("KotlinConstantConditions")

package com.aryan.reader

import com.aryan.reader.shared.ReaderTexture

import androidx.compose.material3.ExperimentalMaterial3Api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aryan.reader.shared.BuiltInReaderThemes
import com.aryan.reader.shared.ReaderTextureFilePrefix
import com.aryan.reader.shared.normalizeReaderTextureExtension
import com.aryan.reader.shared.readerTextureDisplayName as sharedReaderTextureDisplayName
import com.aryan.reader.shared.readerTextureMimeTypeForExtension
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

fun readerTextureDisplayName(textureId: String?): String {
    return sharedReaderTextureDisplayName(textureId)
}

fun importReaderTexture(context: Context, uri: Uri): String? {
    return try {
        val extension = normalizeReaderTextureExtension(
            context.contentResolver.getType(uri)?.substringAfterLast('/')
        ) ?: normalizeReaderTextureExtension(
            uri.lastPathSegment?.substringAfterLast('.', "")
        )
            ?: "img"
        val dir = File(context.filesDir, READER_TEXTURE_DIR).apply { mkdirs() }
        val output = File(dir, "texture_${System.currentTimeMillis()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            output.outputStream().use { out -> input.copyTo(out) }
        } ?: return null
        ReaderTextureFilePrefix + output.absolutePath
    } catch (e: Exception) {
        Timber.e(e, "Failed to import reader texture")
        null
    }
}

internal const val DEFAULT_CANVAS_SAFE_BITMAP_BYTES = 64L * 1024L * 1024L
internal const val DEFAULT_CANVAS_SAFE_BITMAP_DIMENSION = 4096
internal const val MAX_READER_TEXTURE_DIMENSION_PX = 1024

fun Bitmap.safeAllocationByteCount(): Long {
    return try {
        allocationByteCount.toLong()
    } catch (_: Exception) {
        width.toLong() * height.toLong() * 4L
    }
}

fun Bitmap.isCanvasSafeBitmap(
    maxBytes: Long = DEFAULT_CANVAS_SAFE_BITMAP_BYTES,
    maxDimension: Int = DEFAULT_CANVAS_SAFE_BITMAP_DIMENSION
): Boolean {
    return !isRecycled &&
        width > 0 &&
        height > 0 &&
        width <= maxDimension &&
        height <= maxDimension &&
        safeAllocationByteCount() <= maxBytes
}

fun Bitmap.scaledToCanvasLimit(
    maxBytes: Long = DEFAULT_CANVAS_SAFE_BITMAP_BYTES,
    maxDimension: Int = DEFAULT_CANVAS_SAFE_BITMAP_DIMENSION
): Bitmap {
    if (isCanvasSafeBitmap(maxBytes, maxDimension)) return this

    val byteScale = sqrt(maxBytes.toDouble() / safeAllocationByteCount().coerceAtLeast(1L).toDouble())
    val dimensionScale = maxDimension.toDouble() / max(width, height).coerceAtLeast(1).toDouble()
    val scale = min(1.0, min(byteScale, dimensionScale)).coerceAtLeast(0.01)
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

internal fun decodeSampledBitmapFile(path: String, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateBitmapSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    }
    return BitmapFactory.decodeFile(path, options)
}

internal fun calculateBitmapSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sampleSize = 1
    var sampledWidth = width
    var sampledHeight = height
    while (sampledWidth / 2 >= maxDimension || sampledHeight / 2 >= maxDimension) {
        sampleSize *= 2
        sampledWidth /= 2
        sampledHeight /= 2
    }
    return sampleSize.coerceAtLeast(1)
}

fun loadReaderTextureBitmap(context: Context, textureId: String?): ImageBitmap? {
    if (textureId == null) return null
    return try {
        val bitmap = if (textureId.startsWith(ReaderTextureFilePrefix)) {
            decodeSampledBitmapFile(
                path = textureId.removePrefix(ReaderTextureFilePrefix),
                maxDimension = MAX_READER_TEXTURE_DIMENSION_PX
            )
        } else {
            val texture = ReaderTexture.entries.find { it.id == textureId } ?: return null
            val resourceId = texture.androidTextureResourceId()
            when {
                resourceId != null -> BitmapFactory.decodeResource(context.resources, resourceId)
                else -> context.assets.open(texture.assetPath).use(BitmapFactory::decodeStream)
            }
        }
        val safeBitmap = bitmap?.scaledToCanvasLimit(
            maxBytes = DEFAULT_CANVAS_SAFE_BITMAP_BYTES,
            maxDimension = MAX_READER_TEXTURE_DIMENSION_PX
        )
        if (bitmap != null && safeBitmap !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        safeBitmap?.asImageBitmap()
    } catch (e: Exception) {
        Timber.e(e, "Failed to load reader texture bitmap: $textureId")
        null
    }
}

fun getReaderTextureDataUri(context: Context, textureId: String?): String? {
    if (textureId == null) return null
    return try {
        var mimeType = "image/png"
        val bytes = if (textureId.startsWith(ReaderTextureFilePrefix)) {
            val file = File(textureId.removePrefix(ReaderTextureFilePrefix))
            mimeType = "image/png"
            val decodedBitmap = decodeSampledBitmapFile(file.absolutePath, MAX_READER_TEXTURE_DIMENSION_PX)
                ?: return null
            val bitmap = decodedBitmap.scaledToCanvasLimit(
                maxBytes = DEFAULT_CANVAS_SAFE_BITMAP_BYTES,
                maxDimension = MAX_READER_TEXTURE_DIMENSION_PX
            )
            try {
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
                    out.toByteArray()
                }
            } finally {
                if (bitmap !== decodedBitmap && !decodedBitmap.isRecycled) {
                    decodedBitmap.recycle()
                }
                bitmap.recycle()
            }
        } else {
            val texture = ReaderTexture.entries.find { it.id == textureId } ?: return null
            val resourceId = texture.androidTextureResourceId()
            when {
                resourceId != null -> {
                    val bitmap = BitmapFactory.decodeResource(context.resources, resourceId)
                    ByteArrayOutputStream().use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        out.toByteArray()
                    }
                }
                else -> {
                    mimeType = readerTextureMimeTypeForExtension(texture.assetPath.substringAfterLast('.', "png"))
                    context.assets.open(texture.assetPath).use { it.readBytes() }
                }
            }
        } ?: return null
        "data:$mimeType;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        Timber.e(e, "Failed to encode reader texture: $textureId")
        null
    }
}

internal fun ReaderTexture.androidTextureResourceId(): Int? {
    return when (this) {
        ReaderTexture.PAPER -> R.drawable.texture_paper
        ReaderTexture.CANVAS -> R.drawable.texture_canvas
        ReaderTexture.EINK -> R.drawable.texture_eink
        ReaderTexture.SLATE -> R.drawable.texture_slate
        else -> null
    }
}

val BuiltInThemes = BuiltInReaderThemes
