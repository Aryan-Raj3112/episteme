package com.aryan.reader.shared.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual suspend fun decodeSharedMobileEpubImage(
    bytes: ByteArray,
    isSvg: Boolean
): ImageBitmap? {
    if (isSvg) {
        val context = sharedAndroidMobileApplicationContext() ?: return null
        return withContext(Dispatchers.IO) {
            val loader = ImageLoader.Builder(context)
                .components { add(SvgDecoder.Factory()) }
                .build()
            try {
                val result = loader.execute(
                    ImageRequest.Builder(context)
                        .data(bytes)
                        .allowHardware(false)
                        .build(),
                ) as? SuccessResult
                result?.drawable?.toBitmap()?.asImageBitmap()
            } finally {
                loader.shutdown()
            }
        }
    }
    return runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
