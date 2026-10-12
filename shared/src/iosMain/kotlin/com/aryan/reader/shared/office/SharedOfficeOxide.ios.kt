package com.aryan.reader.shared.office

import com.aryan.reader.shared.office.cinterop.office_bridge_free
import com.aryan.reader.shared.office.cinterop.office_bridge_render
import com.aryan.reader.shared.office.cinterop.office_bridge_supports
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value

/**
 * iOS actual: `office_oxide_bridge` through the C ABI, bound by
 * `shared/src/nativeInterop/cinterop/office_oxide.def`. The Rust `staticlib`
 * (`liboffice_oxide_bridge.a`) is linked into the static `ReaderShared`
 * framework, the same way `libmobi.a` and `libreaderarchive.a` already are.
 *
 * The bridge is statically linked, so a load failure is not possible — the only
 * failure mode is the library rejecting a document, which `render` maps to null.
 */
@OptIn(ExperimentalForeignApi::class)
actual object SharedOfficeOxide {

    actual val isAvailable: Boolean = true

    actual fun supports(extension: String): Boolean =
        office_bridge_supports(extension) != 0

    actual fun renderHtml(bytes: ByteArray, extension: String, embedImages: Boolean): String? {
        val mode = if (embedImages) MODE_HTML_EMBEDDED_IMAGES else MODE_HTML
        return render(bytes, extension, mode)
    }

    actual fun renderPlainText(bytes: ByteArray, extension: String): String? =
        render(bytes, extension, MODE_TEXT)

    private fun render(bytes: ByteArray, extension: String, mode: Int): String? {
        if (bytes.isEmpty()) return null
        return bytes.usePinned { pinned ->
            memScoped {
                val outLength = alloc<ULongVar>()
                val pointer: CPointer<ByteVar>? = office_bridge_render(
                    pinned.addressOf(0).reinterpret(),
                    bytes.size.toULong(),
                    extension,
                    mode,
                    outLength.ptr,
                )
                if (pointer == null) {
                    null
                } else {
                    try {
                        pointer.readBytes(outLength.value.toInt()).decodeToString()
                    } finally {
                        office_bridge_free(pointer)
                    }
                }
            }
        }
    }

    // Kept in sync with rust/office-oxide-bridge/include/office_oxide_bridge.h.
    private const val MODE_TEXT = 0
    private const val MODE_HTML = 2
    private const val MODE_HTML_EMBEDDED_IMAGES = 3
}
