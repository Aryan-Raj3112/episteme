package com.aryan.reader.ml

import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PanelResult(
    val rect: RectF,
    val confidence: Float
)

class ComicPanelDetector(modelFile: File) : IPanelDetector {

    private var interpreter: Interpreter? = null
    private val inputSize = 640
    private var gpuDelegate: GpuDelegate? = null

    private var isTransposed: Boolean = false
    private var numBoxes: Int = 0
    private var numElementsPerBox: Int = 0
    private var outputBuffer: ByteBuffer? = null
    private var floatOutputBuffer: java.nio.FloatBuffer? = null
    private var flatOutput: FloatArray? = null

    private val preAllocatedTensorImage = TensorImage(DataType.FLOAT32)

    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f))
        .build()

    init {
        try {
            val compatList = CompatibilityList()
            val options = Interpreter.Options().apply {
                numThreads = 4

                if (compatList.isDelegateSupportedOnThisDevice) {
                    val delegateOptions = compatList.bestOptionsForThisDevice.apply {
                        isPrecisionLossAllowed = true

                        val cacheDir = File(modelFile.parentFile, "gpu_cache")
                        if (!cacheDir.exists()) cacheDir.mkdirs()
                        setSerializationParams(cacheDir.absolutePath, "${modelFile.name}_${modelFile.length()}")
                    }
                    gpuDelegate = GpuDelegate(delegateOptions)
                    addDelegate(gpuDelegate)
                    Timber.i("GPU Delegate added successfully with serialization caching.")
                } else {
                    Timber.i("GPU not supported on this device. Falling back to 4 CPU threads.")
                }
            }
            interpreter = Interpreter(modelFile, options)

            val outputTensor = interpreter!!.getOutputTensor(0)
            val shape = outputTensor.shape()
            Timber.d("Model Output Tensor Shape: ${shape.contentToString()}")

            isTransposed = shape.size == 3 && shape[1] > shape[2]
            numBoxes = if (isTransposed) shape[1] else shape[2]
            numElementsPerBox = if (isTransposed) shape[2] else shape[1]

            val outputBytes = numBoxes * numElementsPerBox * 4
            outputBuffer = ByteBuffer.allocateDirect(outputBytes).order(ByteOrder.nativeOrder())
            floatOutputBuffer = outputBuffer!!.asFloatBuffer()
            flatOutput = FloatArray(numBoxes * numElementsPerBox)

            Timber.i("TFLite Model loaded and buffers allocated successfully from ${modelFile.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Error loading TFLite model or allocating buffers")
        }
    }

    override fun detectPanels(bitmap: Bitmap, confidenceThreshold: Float, iouThreshold: Float): List<RectF> {
        val tflite = interpreter ?: return emptyList()
        val buffer = outputBuffer ?: return emptyList()
        val floatBuf = floatOutputBuffer ?: return emptyList()
        val flatOut = flatOutput ?: return emptyList()

        if (numBoxes <= 0) {
            Timber.w("Detector not initialized correctly: numBoxes is 0")
            return emptyList()
        }

        preAllocatedTensorImage.load(bitmap)
        val processedImage = imageProcessor.process(preAllocatedTensorImage)

        buffer.rewind()
        val startTime = System.currentTimeMillis()
        tflite.run(processedImage.buffer, buffer)
        Timber.d("Inference took ${System.currentTimeMillis() - startTime}ms")

        floatBuf.rewind()
        floatBuf.get(flatOut)

        var maxCoord = 0f
        for (i in 0 until min(100, numBoxes)) {
            val cx = if (isTransposed) flatOutput!![i * numElementsPerBox + 0] else flatOutput!![0 * numBoxes + i]
            if (cx > maxCoord) maxCoord = cx
        }
        val isNormalized = maxCoord <= 1.5f
        Timber.d("Are coordinates normalized? $isNormalized (Sample Max: $maxCoord)")

        val scaleX = if (isNormalized) bitmap.width.toFloat() else bitmap.width.toFloat() / inputSize
        val scaleY = if (isNormalized) bitmap.height.toFloat() else bitmap.height.toFloat() / inputSize

        val parsedResults = mutableListOf<PanelResult>()

        for (i in 0 until numBoxes) {
            val confidence = if (isTransposed) flatOutput!![i * numElementsPerBox + 4] else flatOutput!![4 * numBoxes + i]

            if (confidence > confidenceThreshold) {
                val cx = if (isTransposed) flatOutput!![i * numElementsPerBox + 0] else flatOutput!![0 * numBoxes + i]
                val cy = if (isTransposed) flatOutput!![i * numElementsPerBox + 1] else flatOutput!![1 * numBoxes + i]
                val w = if (isTransposed) flatOutput!![i * numElementsPerBox + 2] else flatOutput!![2 * numBoxes + i]
                val h = if (isTransposed) flatOutput!![i * numElementsPerBox + 3] else flatOutput!![3 * numBoxes + i]

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

        val finalPanels = applyNMS(parsedResults, iouThreshold)

        return finalPanels.map { it.rect }.sortedWith { r1, r2 ->
            if (abs(r1.top - r2.top) < (bitmap.height * 0.05f)) {
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

    override fun close() {
        interpreter?.close()
        interpreter = null

        gpuDelegate?.close()
        gpuDelegate = null

        outputBuffer = null
        floatOutputBuffer = null
        flatOutput = null
    }
}