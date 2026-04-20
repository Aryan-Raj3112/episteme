package com.aryan.reader.ml

import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class PanelResult(
    val rect: RectF,
    val confidence: Float
)

class ComicPanelDetector(modelFile: File) {

    private var interpreter: Interpreter? = null
    private val inputSize = 640

    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f))
        .build()

    init {
        try {
            val options = Interpreter.Options().apply {
                numThreads = 4
            }
            interpreter = Interpreter(modelFile, options)
            Timber.i("TFLite Model loaded successfully from ${modelFile.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Error loading TFLite model")
        }
    }

    fun detectPanels(bitmap: Bitmap, confidenceThreshold: Float = 0.25f, iouThreshold: Float = 0.45f): List<RectF> {
        val tflite = interpreter ?: run {
            Timber.e("Interpreter is null.")
            return emptyList()
        }

        // 1. Prepare Input Image
        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        // 2. Dynamically analyze Output Tensor Shape
        val outputTensor = tflite.getOutputTensor(0)
        val shape = outputTensor.shape()
        // shape is usually [1, 5, 8400] or[1, 8400, 5]
        Timber.d("Model Output Tensor Shape: ${shape.contentToString()}")

        val isTransposed = shape.size == 3 && shape[1] > shape[2] // true if[1, 8400, 5]
        val numBoxes = if (isTransposed) shape[1] else shape[2]
        val numElementsPerBox = if (isTransposed) shape[2] else shape[1]

        // Allocate a flat ByteBuffer to prevent multidimensional array mismatch crashes
        val outputBytes = numBoxes * numElementsPerBox * 4 // 4 bytes per float
        val outputBuffer = ByteBuffer.allocateDirect(outputBytes).order(ByteOrder.nativeOrder())

        // 3. Run Inference
        val startTime = System.currentTimeMillis()
        tflite.run(tensorImage.buffer, outputBuffer)
        Timber.d("Inference took ${System.currentTimeMillis() - startTime}ms")

        outputBuffer.rewind()
        val flatOutput = FloatArray(numBoxes * numElementsPerBox)
        outputBuffer.asFloatBuffer().get(flatOutput)

        // 4. Determine if coordinates are normalized (0.0-1.0) or absolute (0-640)
        var maxCoord = 0f
        for (i in 0 until min(100, numBoxes)) {
            val cx = if (isTransposed) flatOutput[i * numElementsPerBox + 0] else flatOutput[0 * numBoxes + i]
            if (cx > maxCoord) maxCoord = cx
        }
        val isNormalized = maxCoord <= 1.5f
        Timber.d("Are coordinates normalized? $isNormalized (Sample Max: $maxCoord)")

        val scaleX = if (isNormalized) bitmap.width.toFloat() else bitmap.width.toFloat() / inputSize
        val scaleY = if (isNormalized) bitmap.height.toFloat() else bitmap.height.toFloat() / inputSize

        // 5. Parse Boxes
        val parsedResults = mutableListOf<PanelResult>()

        for (i in 0 until numBoxes) {
            val confidence = if (isTransposed) flatOutput[i * numElementsPerBox + 4] else flatOutput[4 * numBoxes + i]

            if (confidence > confidenceThreshold) {
                val cx = if (isTransposed) flatOutput[i * numElementsPerBox + 0] else flatOutput[0 * numBoxes + i]
                val cy = if (isTransposed) flatOutput[i * numElementsPerBox + 1] else flatOutput[1 * numBoxes + i]
                val w = if (isTransposed) flatOutput[i * numElementsPerBox + 2] else flatOutput[2 * numBoxes + i]
                val h = if (isTransposed) flatOutput[i * numElementsPerBox + 3] else flatOutput[3 * numBoxes + i]

                val scaledCx = cx * scaleX
                val scaledCy = cy * scaleY
                val scaledW = w * scaleX
                val scaledH = h * scaleY

                val left = scaledCx - scaledW / 2
                val top = scaledCy - scaledH / 2
                val right = scaledCx + scaledW / 2
                val bottom = scaledCy + scaledH / 2

                parsedResults.add(
                    PanelResult(
                        rect = RectF(left, top, right, bottom),
                        confidence = confidence
                    )
                )
            }
        }

        // 6. Apply NMS
        val finalPanels = applyNMS(parsedResults, iouThreshold)

        // 7. Sort Top-to-Bottom, Right-to-Left
        return finalPanels.map { it.rect }.sortedWith { r1, r2 ->
            if (Math.abs(r1.top - r2.top) < (bitmap.height * 0.05f)) { // 5% height tolerance for horizontal rows
                r2.right.compareTo(r1.right)
            } else {
                r1.top.compareTo(r2.top)
            }
        }
    }

    private fun applyNMS(boxes: List<PanelResult>, iouThreshold: Float): List<PanelResult> {
        val sortedBoxes = boxes.sortedByDescending { it.confidence }.toMutableList()
        val selected = mutableListOf<PanelResult>()

        while (sortedBoxes.isNotEmpty()) {
            val current = sortedBoxes.removeAt(0)
            selected.add(current)
            sortedBoxes.removeAll { box ->
                calculateIoU(current.rect, box.rect) > iouThreshold
            }
        }
        return selected
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionLeft = max(box1.left, box2.left)
        val intersectionTop = max(box1.top, box2.top)
        val intersectionRight = min(box1.right, box2.right)
        val intersectionBottom = min(box1.bottom, box2.bottom)

        if (intersectionRight < intersectionLeft || intersectionBottom < intersectionTop) return 0f

        val intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)

        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}