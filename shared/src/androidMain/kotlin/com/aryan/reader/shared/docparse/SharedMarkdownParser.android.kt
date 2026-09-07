package com.aryan.reader.shared.docparse

/**
 * Android actual backed by md4c through the app's `native-lib` JNI bridge.
 * md4c emits `$...$`/`$$...$$` as `<x-equation>` spans; callers should pass
 * the result through [Md4cHtmlAdapter] to normalize them into math
 * placeholders consumable by the reader's HTML pipeline.
 *
 * JVM host environments (unit tests) cannot load the Android `.so`; those
 * (and any unexpected link failure) transparently fall back to the
 * hand-written [SharedMarkdownConverter] via the null path below, matching
 * desktop/iOS behavior until their native bindings land.
 */
actual object SharedMarkdownParser {
    actual fun isNative(): Boolean = Md4cJni.available

    actual fun toHtml(markdown: String, flags: Int): String? {
        val tag = "MdMathDiag"
        if (Md4cJni.available) {
            val start = System.currentTimeMillis()
            val html = try {
                Md4cJni.markdownToHtml(markdown, flags)
            } catch (error: UnsatisfiedLinkError) {
                android.util.Log.e(tag, "MD4C: JNI call threw UnsatisfiedLinkError", error)
                null
            }
            android.util.Log.i(
                tag,
                "MD4C: JNI done | inputChars=${markdown.length} | outputChars=${html?.length ?: -1} | " +
                    "elapsed=${System.currentTimeMillis() - start}ms"
            )
            if (html != null) {
                val normalized = Md4cHtmlAdapter.normalize(html)
                android.util.Log.i(tag, "MD4C: adapter normalized | chars=${normalized.length}")
                return normalized
            }
        } else {
            android.util.Log.w(tag, "MD4C: native lib unavailable -> using SharedMarkdownConverter fallback (no math spans)")
        }
        return SharedMarkdownConverter.convert(markdown).joinToString(separator = "\n") { it.html }
    }
}

/** JNI surface; native implementation lives in app/src/main/cpp/md4c_jni.c. */
internal object Md4cJni {
    /** True when the app's native library (containing md4c) is loadable. */
    val available: Boolean = try {
        System.loadLibrary("native-lib")
        android.util.Log.i("MdMathDiag", "MD4C: native-lib loaded OK")
        true
    } catch (error: UnsatisfiedLinkError) {
        android.util.Log.e("MdMathDiag", "MD4C: native-lib FAILED to load", error)
        false
    }

    @JvmStatic
    external fun markdownToHtml(markdown: String, flags: Int): String?
}
