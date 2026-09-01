package com.aryan.reader.shared.ios

/**
 * The iOS Pencil interaction is a discrete system gesture, while Android
 * reports a barrel button as part of each stylus pointer event. Keeping this
 * state in the iOS source set lets both platforms continue through the same
 * shared ink renderer without making touch input an eraser accidentally.
 */
private var iosPencilEraserOverride = false

/**
 * Toggles the "switch to eraser" state used by the shared PDF ink pipeline.
 * UIKit delivers Pencil interaction callbacks on the main thread, just like
 * Compose pointer input.
 */
fun toggleIosPencilEraserOverride(): Boolean {
    iosPencilEraserOverride = !iosPencilEraserOverride
    return iosPencilEraserOverride
}

/** Resets the shortcut when the reader host is recreated. */
fun resetIosPencilEraserOverride() {
    iosPencilEraserOverride = false
}

internal fun isIosPencilEraserOverrideEnabled(): Boolean = iosPencilEraserOverride
