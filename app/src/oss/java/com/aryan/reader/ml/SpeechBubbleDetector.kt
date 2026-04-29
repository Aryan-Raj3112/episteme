package com.aryan.reader.ml

import android.graphics.Bitmap
import timber.log.Timber
import java.io.File

class SpeechBubbleDetector(modelFile: File) : ISpeechBubbleDetector {

    init {
        Timber.i("OSS flavor: SpeechBubbleDetector stub initialized. ONNX features are disabled.")
    }

    override fun detectBubbles(bitmap: Bitmap, confidenceThreshold: Float): List<SpeechBubble> {
        return emptyList()
    }

    override fun close() {
        // No-op
    }
}