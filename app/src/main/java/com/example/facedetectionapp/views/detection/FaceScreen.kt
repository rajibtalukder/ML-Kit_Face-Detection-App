package com.example.facedetectionapp.views.detetion

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.facedetectionapp.views.detection.FaceAttendanceCameraScreen
import com.google.mlkit.vision.face.Face
import kotlin.math.sqrt

@Composable
fun FaceScreen(onOpenFaceAttendanceCameraScreen: () -> Unit) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Holds the latest frames detected dynamically by the camera stream
    var latestFaceData by remember { mutableStateOf<Pair<Face, Bitmap>?>(null) }

    // VARIABLE REQUESTED: Simulates your database storage for the registered Face ID vector array
    var registeredFaceId by remember { mutableStateOf<FloatArray?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {

            FaceAttendanceCameraScreen(
                onFaceProcessed = { face, fullFrameBitmap ->
                    latestFaceData = Pair(face, fullFrameBitmap)
                }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // REGISTER BUTTON
                Button(
                    onClick = {
                        latestFaceData?.let { (face, bitmap) ->
                            val embedding = registerFaceId(face, bitmap)
                            if (embedding != null) {
                                registeredFaceId = embedding // Saved into variable!
                                Log.d("🔒 Registration", "Successfully saved Face ID to variable (lastFaceId)! $registeredFaceId")
                            }
                        } ?: Log.e("Registration", "No face frame detected yet. Please look at the camera.")
                    },
                    modifier = Modifier.width(220.dp).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
                ) {
                    Text("Register Face", color = Color.White)
                }

                // VERIFY BUTTON
                Button(
                    onClick = {
                        latestFaceData?.let { (face, bitmap) ->
                            //verifyFaceId(face, bitmap, registeredFaceId)
                        } ?: Log.e("Verification", "No face frame detected yet.")
                    },
                    modifier = Modifier.width(220.dp).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                ) {
                    Text("Verify/Detect Face", color = Color.Black)
                }
            }
        }
    } else {
        Text(text = "Camera access is required for attendance tracking systems.")
    }
}


fun registerFaceId(face: Face, fullFrame: Bitmap): FloatArray? {
    val bounds = face.boundingBox
    val left = bounds.left.coerceAtLeast(0)
    val top = bounds.top.coerceAtLeast(0)
    val width = bounds.width().coerceAtMost(fullFrame.width - left)
    val height = bounds.height().coerceAtMost(fullFrame.height - top)

    return try {
        val croppedFaceBitmap = Bitmap.createBitmap(fullFrame, left, top, width, height)

        // Feed the cropped face image into your TFLite pipeline to generate the unique array
        val generatedEmbedding = passToTensorFlowLiteModel(croppedFaceBitmap)
        generatedEmbedding
    } catch (e: Exception) {
        Log.e("Registration", "Failed cropping face frame: ${e.localizedMessage}")
        null
    }
}


fun verifyFaceId(face: Face, fullFrame: Bitmap, registeredEmbedding: FloatArray?) {
    if (registeredEmbedding == null) {
        Log.e("❌ Verification Error", "Cannot verify! No user has been registered in the variable yet.")
        return
    }

    val bounds = face.boundingBox
    val left = bounds.left.coerceAtLeast(0)
    val top = bounds.top.coerceAtLeast(0)
    val width = bounds.width().coerceAtMost(fullFrame.width - left)
    val height = bounds.height().coerceAtMost(fullFrame.height - top)

    try {
        val croppedFaceBitmap = Bitmap.createBitmap(fullFrame, left, top, width, height)

        // Generate a fresh temporary embedding for the person standing at the camera right now
        val currentEmbedding = passToTensorFlowLiteModel(croppedFaceBitmap)

        // Calculate the mathematical difference between the registered vector and current vector
        val distance = calculateEuclideanDistance(currentEmbedding, registeredEmbedding)

        // Typically, a distance lower than 1.0 implies it's a match when using models like MobileFaceNet
        val threshold = 1.0
        val isMatch = distance < threshold

        Log.d("🔍 Verification Result", "---------------------------------------------$registeredEmbedding")
        Log.d("🔍 Verification Result", "Calculated Vector Distance: $distance")
        if (isMatch) {
            Log.d("🔍 Verification Result", "✅ MATCH SUCCESSFUL! Access/Attendance Granted.")
        } else {
            Log.d("🔍 Verification Result", "❌ MATCH FAILED! Face identity does not match the registered profile.")
        }
        Log.d("🔍 Verification Result", "---------------------------------------------")

    } catch (e: Exception) {
        Log.e("Verification", "Error processing verification frame: ${e.localizedMessage}")
    }
}

/**
 * Math Helper: Computes the distance between two vector coordinate point mappings
 */
fun calculateEuclideanDistance(vector1: FloatArray, vector2: FloatArray): Float {
    if (vector1.size != vector2.size) return Float.MAX_VALUE
    var sum = 0.0f
    for (i in vector1.indices) {
        val diff = vector1[i] - vector2[i]
        sum += diff * diff
    }
    return sqrt(sum)
}

/**
 * Mock Model Interpreter
 */
fun passToTensorFlowLiteModel(croppedFace: Bitmap): FloatArray {
    // Note: For testing verification logs locally, this currently outputs a predictable mock array.
    // Replace this array with your true TFLite model output processing in production.
    return FloatArray(128) { 0.5f }
}