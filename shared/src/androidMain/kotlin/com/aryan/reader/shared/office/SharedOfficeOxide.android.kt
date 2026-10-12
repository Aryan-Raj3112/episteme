package com.aryan.reader.shared.office

import android.util.Log

/**
 * Android actual: `office_oxide_bridge` reached through the app's `native-lib`.
 *
 * Mirrors `Md4cJni`'s approach exactly — the JNI class lives in this source set
 * and loads `native-lib` itself, so `:shared` carries no dependency on the app
 * module. The `.so` is built from `rust/office-oxide-bridge` by
 * `./gradlew :app:buildOfficeOxideLibraries`.
 *
 * Loading failure is non-fatal: callers fall back to the existing parsers.
 */
actual object SharedOfficeOxide {
    private const val TAG = "OfficeOxide"

    actual val isAvailable: Boolean = SharedOfficeOxideJni.available

    actual fun supports(extension: String): Boolean =
        SharedOfficeOxideJni.available && SharedOfficeOxideJni.supports(extension)

    actual fun renderHtml(bytes: ByteArray, extension: String, embedImages: Boolean): String? {
        val mode = if (embedImages) MODE_HTML_EMBEDDED_IMAGES else MODE_HTML
        return render(bytes, extension, mode, "html")
    }

    actual fun renderPlainText(bytes: ByteArray, extension: String): String? =
        render(bytes, extension, MODE_TEXT, "text")

    private fun render(bytes: ByteArray, extension: String, mode: Int, label: String): String? {
        if (!SharedOfficeOxideJni.available) {
            Log.w(TAG, "bridge unavailable; skipping $label render of .$extension")
            return null
        }
        return try {
            val started = System.currentTimeMillis()
            // The JNI layer transports raw bytes; decode here so malformed
            // sequences become U+FFFD instead of failing the whole call the way
            // NewStringUTF would.
            val result = SharedOfficeOxideJni.render(bytes, extension, mode)?.decodeToString()
            Log.i(
                TAG,
                "$label .$extension | in=${bytes.size}B out=${result?.length ?: -1} chars " +
                    "in ${System.currentTimeMillis() - started}ms",
            )
            result
        } catch (error: UnsatisfiedLinkError) {
            Log.e(TAG, "JNI call failed for $label .$extension", error)
            null
        }
    }

    // Kept in sync with rust/office-oxide-bridge/include/office_oxide_bridge.h.
    private const val MODE_TEXT = 0
    private const val MODE_HTML = 2
    private const val MODE_HTML_EMBEDDED_IMAGES = 3
}

/**
 * JNI surface; the native implementation is
 * `app/src/main/cpp/office_oxide_bridge_jni.cpp`. R8 must keep these method
 * names — see `app/proguard-rules.pro`.
 */
object SharedOfficeOxideJni {
    val available: Boolean = try {
        System.loadLibrary("native-lib")
        true
    } catch (error: UnsatisfiedLinkError) {
        Log.e("OfficeOxide", "native-lib FAILED to load", error)
        false
    }

    @JvmStatic
    external fun render(data: ByteArray, extension: String, mode: Int): ByteArray?

    @JvmStatic
    external fun supports(extension: String): Boolean
}
