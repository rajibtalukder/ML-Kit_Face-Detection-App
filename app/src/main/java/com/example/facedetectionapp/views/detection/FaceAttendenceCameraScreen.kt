package com.example.facedetectionapp.views.detection

import android.graphics.*
import android.media.Image
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.android.gms.tasks.Tasks
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
@Composable
fun FaceAttendanceCameraScreen(
    onFaceProcessed: (Face, Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(300.dp)
                .height(500.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            ) { _ ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    Log.d("FaceDB_Match", "Face Processed ----- 100")
                    // 1. Configure high-speed, low-latency face detection options
                    val highAccuracyOpts = FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // Optimized for live video frames
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                        .build()
                    Log.d("FaceDB_Match", "Face Processed ----- 101")
                    val faceDetector = FaceDetection.getClient(highAccuracyOpts)

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    Log.d("FaceDB_Match", "Face Processed ----- 102")
                    // 3. Attach the analyzer to process your upright camera frames in background
                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        Log.d("FaceDB_Match", "Face Processed ----- 103")
                        val mediaImage: Image? = imageProxy.image
                        if (mediaImage != null) {
                            Log.d("FaceDB_Match", "Face Processed ----- 104")
                            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

                            try {
                                val faces = Tasks.await(faceDetector.process(inputImage))
                                val face = faces.firstOrNull()
                                if (face != null) {
                                    val fullFrameBitmap = imageProxy.toBitmap()
                                    val croppedFace = cropFaceSafely(fullFrameBitmap, face.boundingBox)

                                    if (croppedFace != null) {
                                        val uprightFaceBitmap = rotateBitmap(croppedFace, rotationDegrees.toFloat())
                                        onFaceProcessed(face, uprightFaceBitmap)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("CameraAnalyzer", "ML Kit Face detection failed", e)
                            } finally {
                                imageProxy.close()
                            }
                        } else {
                            imageProxy.close()
                        }
                    }

                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (exc: Exception) {
                        exc.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }
    }
}
fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return bitmap
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/**
 * Crops the face out of the full camera frame, adding strict validation boundaries
 * to guarantee it never crashes the app if the face hits the edge of the screen layout.
 */
fun cropFaceSafely(src: Bitmap, rect: Rect): Bitmap? {
    val left = rect.left.coerceAtLeast(0)
    val top = rect.top.coerceAtLeast(0)
    val right = rect.right.coerceAtMost(src.width)
    val bottom = rect.bottom.coerceAtMost(src.height)

    val width = right - left
    val height = bottom - top

    if (width <= 0 || height <= 0) return null
    return Bitmap.createBitmap(src, left, top, width, height)
}

fun InputImage.toBitmap(): Bitmap? {
    val mediaImage = this.mediaImage ?: return null

    val planes = mediaImage.planes
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, mediaImage.width, mediaImage.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)

    val imageBytes = out.toByteArray()
    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

    return bitmap?.let {
        val matrix = Matrix()
        matrix.postRotate(this.rotationDegrees.toFloat())
        matrix.postScale(-1f, 1f, it.width / 2f, it.height / 2f)
        Bitmap.createBitmap(it, 0, 0, it.width, it.height, matrix, true)
    }
}