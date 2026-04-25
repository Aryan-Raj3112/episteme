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

class SpeechBubbleDetector(modelFile: File) : ISpeechBubbleDetector {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private val inputSize = 504

    init {
        try {
            env = OrtEnvironment.getEnvironment()

            // Configure for highly optimized CPU execution (4 threads)
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

    override fun detectBubbles(bitmap: Bitmap, confidenceThreshold: Float): List<RectF> {
        val currentEnv = env ?: return emptyList()
        val currentSession = session ?: return emptyList()

        try {
            // 1. Resize to 504x504
            val resized = bitmap.scale(inputSize, inputSize)

            // 2. Preprocess into NCHW array using a Direct ByteBuffer
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

            // 3. Create Tensor
            val inputTensor = OnnxTensor.createTensor(
                currentEnv,
                floatBuffer,
                longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
            )

            // 4. Run Inference
            val inputName = currentSession.inputNames.iterator().next()
            val results = currentSession.run(Collections.singletonMap(inputName, inputTensor))

            val parsedResults = mutableListOf<RectF>()

            var detsOutput: FloatArray? = null
            var labelsOutput: FloatArray? = null
            var numBoxes = 0
            var numClasses = 0

            // 5. Parse Specific Outputs (dets and labels)
            results.forEach { entry ->
                val value = entry.value as OnnxTensor
                val shape = value.info.shape

                if (entry.key == "dets") {
                    numBoxes = shape[1].toInt()
                    val flatOutput = FloatArray(shape.reduce { acc, l -> acc * l }.toInt())
                    value.floatBuffer.get(flatOutput)
                    detsOutput = flatOutput
                } else if (entry.key == "labels") {
                    numClasses = shape[2].toInt()
                    val flatOutput = FloatArray(shape.reduce { acc, l -> acc * l }.toInt())
                    value.floatBuffer.get(flatOutput)
                    labelsOutput = flatOutput
                }
            }

            if (detsOutput != null && labelsOutput != null) {
                // RT-DETR might output values normalized (0 to 1) or scaled to image size (504)
                var maxCoord = 0f
                for (i in 0 until min(100, detsOutput.size)) {
                    if (detsOutput[i] > maxCoord) maxCoord = detsOutput[i]
                }
                val isNormalized = maxCoord <= 1.5f

                val scaleX = if (isNormalized) bitmap.width.toFloat() else bitmap.width.toFloat() / inputSize
                val scaleY = if (isNormalized) bitmap.height.toFloat() else bitmap.height.toFloat() / inputSize

                for (i in 0 until numBoxes) {
                    var maxConf = 0f
                    // Iterate through class scores to find the highest confidence
                    for (c in 0 until numClasses) {
                        val conf = labelsOutput[i * numClasses + c]
                        if (conf > maxConf) maxConf = conf
                    }

                    if (maxConf > confidenceThreshold) {
                        val val0 = detsOutput[i * 4 + 0]
                        val val1 = detsOutput[i * 4 + 1]
                        val val2 = detsOutput[i * 4 + 2]
                        val val3 = detsOutput[i * 4 + 3]

                        Timber.d("Bubble Found! Score: $maxConf | Raw Coords:[$val0, $val1, $val2, $val3]")

                        // RT-DETR default output format is usually[cx, cy, w, h]
                        val left = (val0 - val2 / 2) * scaleX
                        val top = (val1 - val3 / 2) * scaleY
                        val right = (val0 + val2 / 2) * scaleX
                        val bottom = (val1 + val3 / 2) * scaleY

                        parsedResults.add(RectF(left, top, right, bottom))
                    }
                }
            }

            // Cleanup memory
            inputTensor.close()
            results.close()
            if (resized != bitmap) resized.recycle()

            return parsedResults

        } catch (t: Throwable) {
            Timber.e(t, "ONNX Inference failed")
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