package com.aryan.reader.ml

import android.graphics.Bitmap
import android.graphics.RectF

interface ISpeechBubbleDetector {
    fun detectBubbles(bitmap: Bitmap, confidenceThreshold: Float = 0.4f): List<RectF>
    fun close()
}