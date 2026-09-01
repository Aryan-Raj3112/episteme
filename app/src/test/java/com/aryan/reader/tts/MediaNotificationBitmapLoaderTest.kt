package com.aryan.reader.tts

import android.content.Context
import android.graphics.Bitmap
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.MediaNotificationBitmapLoader
import com.google.common.util.concurrent.Futures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaNotificationBitmapLoaderTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `supports image mime types only`() {
        val loader = MediaNotificationBitmapLoader(context)
        assertTrue(loader.supportsMimeType("image/png"))
        assertFalse(loader.supportsMimeType("audio/wav"))
    }

    @Test
    fun `decode resolves synchronously and downsamples to max size`() {
        val loader = MediaNotificationBitmapLoader(context, maxBitmapSize = 128)
        val pngBytes = encodePng(Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888))

        val future = loader.decodeBitmap(pngBytes)

        assertTrue(future.isDone)
        assertEquals(Futures.immediateFuture(1L).isDone, future.isDone)
        val bitmap = future.get()
        assertTrue(bitmap.width <= 128)
        assertTrue(bitmap.height <= 128)
    }

    @Test
    fun `metadata without artwork resolves to null`() {
        val loader = MediaNotificationBitmapLoader(context)
        val metadata = MediaMetadata.Builder().setTitle("book").build()

        assertNull(loader.loadBitmapFromMetadata(metadata))
    }

    @Test
    fun `metadata with artwork data resolves synchronously`() {
        val loader = MediaNotificationBitmapLoader(context)
        val artwork = encodePng(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))
        val metadata = MediaMetadata.Builder().setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()

        val future = loader.loadBitmapFromMetadata(metadata)

        assertTrue(future!!.isDone)
        assertEquals(64, future.get().width)
    }

    private fun encodePng(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }
}
