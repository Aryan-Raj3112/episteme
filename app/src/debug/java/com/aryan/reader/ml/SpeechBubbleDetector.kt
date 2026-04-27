package com.aryan.reader.ml

import android.graphics.Bitmap
import android.graphics.RectF
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import kotlin.math.min
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap

data class SpeechBubble(
    val bounds: RectF,
    val maskBitmap: Bitmap? = null
)

interface ISpeechBubbleDetector : AutoCloseable {
    fun detectBubbles(bitmap: Bitmap, confidenceThreshold: Float = 0.4f): List<SpeechBubble>
}

class SpeechBubbleDetector(modelFile: File) : ISpeechBubbleDetector {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private val inputSize = 504

    init {
        try {
            env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            session = env?.createSession(modelFile.absolutePath, options)
            Timber.i("ONNX Model loaded successfully on CPU (4 threads) from ${modelFile.absolutePath}")
        } catch (t: Throwable) {
            Timber.e(t, "Fatal error initializing ONNX model")
        }
    }

    override fun detectBubbles(bitmap: Bitmap, confidenceThreshold: Float): List<SpeechBubble> {
        Timber.tag("BubbleZoom").d("Detector: detectBubbles started. Bitmap: ${bitmap.width}x${bitmap.height}, threshold: $confidenceThreshold")
        val currentEnv = env ?: run {
            Timber.tag("BubbleZoom").w("Detector: OrtEnvironment is null")
            return emptyList()
        }
        val currentSession = session ?: run {
            Timber.tag("BubbleZoom").w("Detector: OrtSession is null")
            return emptyList()
        }

        try {
            val resized = bitmap.scale(inputSize, inputSize)
            val byteBuffer = ByteBuffer.allocateDirect(3 * inputSize * inputSize * 4).order(ByteOrder.nativeOrder())
            val floatBuffer = byteBuffer.asFloatBuffer()
            val pixels = IntArray(inputSize * inputSize)
            resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

            for (i in 0 until inputSize * inputSize) {
                val pixel = pixels[i]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                floatBuffer.put(i, r)
                floatBuffer.put(i + inputSize * inputSize, g)
                floatBuffer.put(i + 2 * inputSize * inputSize, b)
            }
            floatBuffer.rewind()

            val inputTensor = OnnxTensor.createTensor(currentEnv, floatBuffer, longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()))
            val inputName = currentSession.inputNames.iterator().next()

            Timber.tag("BubbleZoom").d("Detector: Running ONNX inference...")
            val results = currentSession.run(Collections.singletonMap(inputName, inputTensor))
            Timber.tag("BubbleZoom").d("Detector: ONNX inference finished.")

            val parsedResults = mutableListOf<SpeechBubble>()

            var detsOutput: FloatArray? = null
            var labelsOutput: FloatArray? = null
            var masksOutput: FloatArray? = null

            var numBoxes = 0
            var numClasses = 0
            var maskH = 0
            var maskW = 0

            Timber.tag("ONNX_SEG").d("--- ONNX OUTPUT TENSORS ---")
            results.forEach { entry ->
                val value = entry.value as OnnxTensor
                val shape = value.info.shape
                Timber.tag("ONNX_SEG").d("Name: ${entry.key}, Shape: ${shape.contentToString()}")

                if (entry.key.contains("dets") || entry.key.contains("boxes") || (shape.size == 3 && shape[2] == 4L)) {
                    numBoxes = shape[1].toInt()
                    val flatOutput = FloatArray(shape.reduce { acc, l -> acc * l }.toInt())
                    value.floatBuffer.get(flatOutput)
                    detsOutput = flatOutput
                } else if (entry.key.contains("labels") || entry.key.contains("scores") || (shape.size == 3 && shape[2] != 4L && shape[1] == numBoxes.toLong())) {
                    numClasses = shape[2].toInt()
                    val flatOutput = FloatArray(shape.reduce { acc, l -> acc * l }.toInt())
                    value.floatBuffer.get(flatOutput)
                    labelsOutput = flatOutput
                } else if (entry.key.contains("masks") || shape.size == 4) {
                    maskH = shape[2].toInt()
                    maskW = shape[3].toInt()
                    val flatOutput = FloatArray(shape.reduce { acc, l -> acc * l }.toInt())
                    value.floatBuffer.get(flatOutput)
                    masksOutput = flatOutput
                }
            }

            Timber.tag("BubbleZoom").d("Detector: Outputs mapped. Boxes: $numBoxes, Classes: $numClasses, Masks: ${maskW}x${maskH}")

            if (detsOutput != null && labelsOutput != null) {
                var maxCoord = 0f
                for (i in 0 until min(100, detsOutput.size)) {
                    if (detsOutput[i] > maxCoord) maxCoord = detsOutput[i]
                }
                val isNormalized = maxCoord <= 1.5f
                val scaleX = if (isNormalized) bitmap.width.toFloat() else bitmap.width.toFloat() / inputSize
                val scaleY = if (isNormalized) bitmap.height.toFloat() else bitmap.height.toFloat() / inputSize

                for (i in 0 until numBoxes) {
                    var maxConf = 0f
                    for (c in 0 until numClasses) {
                        val conf = labelsOutput[i * numClasses + c]
                        if (conf > maxConf) maxConf = conf
                    }

                    if (maxConf > confidenceThreshold) {
                        val val0 = detsOutput[i * 4 + 0]
                        val val1 = detsOutput[i * 4 + 1]
                        val val2 = detsOutput[i * 4 + 2]
                        val val3 = detsOutput[i * 4 + 3]

                        // YOLOv8 NMS export returns [x1, y1, x2, y2]
                        val cx = val0

                        val rawLeft = cx - val2 / 2
                        val rawTop = val1 - val3 / 2
                        val rawRight = cx + val2 / 2
                        val rawBottom = val1 + val3 / 2

                        val left = rawLeft * scaleX
                        val top = rawTop * scaleY
                        val right = rawRight * scaleX
                        val bottom = rawBottom * scaleY

                        Timber.tag("BubbleZoom").v("Detector: Bubble $i: raw coords[$rawLeft, $rawTop, $rawRight, $rawBottom], conf=$maxConf")

                        var maskBitmap: Bitmap? = null
                        if (masksOutput != null && maskH > 0 && maskW > 0) {
                            try {
                                // 126x126 is the mask for the ENTIRE image. We need to crop it to the bounding box.
                                val maskScaleX = if (isNormalized) maskW.toFloat() else maskW.toFloat() / inputSize
                                val maskScaleY = if (isNormalized) maskH.toFloat() else maskH.toFloat() / inputSize

                                val mLeft = (rawLeft * maskScaleX).toInt().coerceIn(0, maskW - 1)
                                val mTop = (rawTop * maskScaleY).toInt().coerceIn(0, maskH - 1)
                                val mRight = (rawRight * maskScaleX).toInt().coerceIn(0, maskW - 1)
                                val mBottom = (rawBottom * maskScaleY).toInt().coerceIn(0, maskH - 1)

                                val cropW = mRight - mLeft
                                val cropH = mBottom - mTop

                                if (cropW > 0 && cropH > 0) {
                                    val maskBmp = createBitmap(cropW, cropH)
                                    val maskPixels = IntArray(cropW * cropH)
                                    val offset = i * maskW * maskH

                                    for (y in 0 until cropH) {
                                        for (x in 0 until cropW) {
                                            val maskX = mLeft + x
                                            val maskY = mTop + y
                                            val p = maskY * maskW + maskX

                                            if (offset + p < masksOutput.size) {
                                                maskPixels[y * cropW + x] = if (masksOutput[offset + p] > 0.0f) android.graphics.Color.WHITE else android.graphics.Color.TRANSPARENT
                                            }
                                        }
                                    }
                                    maskBmp.setPixels(maskPixels, 0, cropW, 0, 0, cropW, cropH)
                                    maskBitmap = maskBmp
                                }
                            } catch (e: Exception) {
                                Timber.tag("ONNX_SEG").e(e, "Failed to parse mask for bubble $i")
                            }
                        }

                        // Prevent adding invalid boxes
                        if (right > left && bottom > top) {
                            parsedResults.add(SpeechBubble(RectF(left, top, right, bottom), maskBitmap))
                        }
                    }
                }
            }

            Timber.tag("BubbleZoom").d("Detector: Parsed ${parsedResults.size} valid bubbles above threshold.")

            inputTensor.close()
            results.close()
            if (resized != bitmap) resized.recycle()

            return parsedResults

        } catch (t: Throwable) {
            Timber.tag("BubbleZoom").e(t, "Detector: ONNX Inference failed completely")
        }
        return emptyList()
    }

    override fun close() {
        session?.close()
        session = null
        env?.close()
        env = null
    }
}