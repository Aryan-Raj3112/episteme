package com.aryan.reader.ml

import android.graphics.Bitmap
import android.graphics.RectF

interface IPanelDetector {
    fun detectPanels(bitmap: Bitmap, confidenceThreshold: Float = 0.25f, iouThreshold: Float = 0.45f): List<RectF>
    fun close()
}