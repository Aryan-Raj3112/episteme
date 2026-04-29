package com.aryan.reader.ml

import android.graphics.Bitmap
import android.graphics.RectF

data class SpeechBubble(
    val bounds: RectF,
    val maskBitmap: Bitmap? = null
)

interface ISpeechBubbleDetector : AutoCloseable {
    fun detectBubbles(bitmap: Bitmap, confidenceThreshold: Float = 0.1f): List<SpeechBubble>
}