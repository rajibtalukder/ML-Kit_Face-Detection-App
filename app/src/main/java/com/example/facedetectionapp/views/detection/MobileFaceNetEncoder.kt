package com.example.facedetectionapp.views.detection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt
import androidx.core.graphics.scale

class MobileFaceNetEncoder(context: Context) {
    private var interpreter: Interpreter
    private var gpuDelegate: GpuDelegate? = null
    companion object {
        private const val INPUT_SIZE = 112
        private const val EMBEDDING_SIZE = 192
        // Pre-calculated normalization constants
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 128.0f
    }

    init {
        val mappedByteBuffer = loadModelFile(context, "mobilefacenet.tflite")

        val options = Interpreter.Options().apply {
            // 1. Hardware Acceleration (GPU Fallback to NNAPI/CPU)
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                gpuDelegate = GpuDelegate(delegateOptions)
                addDelegate(gpuDelegate)
            } else {
                // Highly performant fallback for CPU execution
                setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
            }
            // 2. Performance hint optimization
            setCancellable(false)
        }

        interpreter = Interpreter(mappedByteBuffer, options)
    }

    /**
     * Extracts a high-accuracy face embedding.
     * Expects a tightly cropped image containing *only* the face.
     */
    fun getFaceEmbedding(bitmap: Bitmap): FloatArray {
        // High-quality bilinear scaling if the input isn't exactly 112x112
        val resizedBitmap = if (bitmap.width != INPUT_SIZE || bitmap.height != INPUT_SIZE) {
            bitmap.scale(INPUT_SIZE, INPUT_SIZE)
        } else {
            bitmap
        }

        // Allocate direct native memory (1 * 112 * 112 * 3 channels * 4 bytes for Float)
        val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        resizedBitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // Ultra-fast pixel normalization loop
        inputBuffer.rewind()
        for (pixelValue in intValues) {
            val r = (pixelValue shr 16) and 0xFF
            val g = (pixelValue shr 8) and 0xFF
            val b = pixelValue and 0xFF

            // MobileFaceNet standard normalization
            inputBuffer.putFloat((r - IMAGE_MEAN) / IMAGE_STD)
            inputBuffer.putFloat((g - IMAGE_MEAN) / IMAGE_STD)
            inputBuffer.putFloat((b - IMAGE_MEAN) / IMAGE_STD)
        }

        // Output buffer allocation
        val outputArray = Array(1) { FloatArray(EMBEDDING_SIZE) }
        // Inference step
        interpreter.run(inputBuffer, outputArray)
        val embedding = outputArray[0]
        val normalized = normalizeEmbedding(embedding)

        // Memory cleanup to prevent OOM errors during high-frequency detection
        if (resizedBitmap != bitmap) {
            resizedBitmap.recycle()
        }

        return normalized
    }

    /**
     * Highly optimized L2 Normalization (Euclidean norm)
     */
    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        var sum = 0.0f
        for (i in embedding.indices) {
            sum += embedding[i] * embedding[i]
        }
        val norm = sqrt(sum)

        if (norm > 0f) {
            for (i in embedding.indices) {
                embedding[i] /= norm
            }
        }
        return embedding
    }

    private fun loadModelFile(context: Context, modelName: String): java.nio.MappedByteBuffer {
        val modelFile = context.assets.openFd(modelName)
        val stream = FileInputStream(modelFile.fileDescriptor)
        val fileChannel = stream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, modelFile.startOffset, modelFile.declaredLength)
    }

    fun close() {
        interpreter.close()
        gpuDelegate?.close()
    }
}