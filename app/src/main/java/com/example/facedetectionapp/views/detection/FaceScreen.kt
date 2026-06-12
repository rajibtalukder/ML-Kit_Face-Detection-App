package com.example.facedetectionapp.views.detection

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
import com.example.facedetectionapp.database.AppDatabase
import com.example.facedetectionapp.database.UserFaceEntity
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sqrt

@Composable
fun FaceScreen(onOpenFaceAttendanceCameraScreen: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Initialize Room Database dependencies
    val db = remember { AppDatabase.getDatabase(context) }
    val userDao = remember { db.userFaceDao() }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    var latestFaceData by remember { mutableStateOf<Pair<Face, Bitmap>?>(null) }

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

                // REGISTER USER BUTTON
                Button(
                    onClick = {
                        latestFaceData?.let { (face, bitmap) ->
                            val embedding = registerFaceId(face, bitmap)
                            Log.d("Registration", "Face embedding: $embedding")
                            if (embedding != null) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    // Mocking Name Input: Replace "User_${System.currentTimeMillis()}" with a real input field text
                                    val newProfile = UserFaceEntity(
                                        name = "User_${System.currentTimeMillis()}",
                                        faceId = embedding
                                    )
                                    userDao.insertFace(newProfile)
                                    Log.d("RoomDB", "💾 Successfully saved ${newProfile.name} profile into Room Database!")
                                }
                            }
                        } ?: Log.e("Registration", "No face aligned in viewport.")
                    },
                    modifier = Modifier.width(220.dp).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
                ) {
                    Text("Register Face", color = Color.White)
                }

                // IDENTIFY / VERIFY PERSON BUTTON
                Button(
                    onClick = {
                        latestFaceData?.let { (face, bitmap) ->
                            coroutineScope.launch(Dispatchers.IO) {
                                // 1. Pull all saved biometric vectors from Room DB
                                val savedProfiles = userDao.getAllRegisteredFaces()

                                // 2. Pass to verification engine
                                identifyPersonFromDatabase(face, bitmap, savedProfiles)
                            }
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

/**
 * Iterates over Room database profiles to locate the matching user vector
 */
fun identifyPersonFromDatabase(face: Face, fullFrame: Bitmap, databaseProfiles: List<UserFaceEntity>) {
    if (databaseProfiles.isEmpty()) {
        Log.e("❌ Database Empty", "No registered users found in Room DB. Please register a face first.")
        return
    }

    val bounds = face.boundingBox
    val left = bounds.left.coerceAtLeast(0)
    val top = bounds.top.coerceAtLeast(0)
    val width = bounds.width().coerceAtMost(fullFrame.width - left)
    val height = bounds.height().coerceAtMost(fullFrame.height - top)

    try {
        val croppedFaceBitmap = Bitmap.createBitmap(fullFrame, left, top, width, height)
        val currentEmbedding = passToTensorFlowLiteModel(croppedFaceBitmap)

        var matchedUserName = "Unknown Person"
        var lowestDistance = 1.0f // Threshold limit (values below this match the user profile)

        // Loop through profiles pulled dynamically out of Room DB
        for (profile in databaseProfiles) {
            val distance = calculateEuclideanDistance(currentEmbedding, profile.faceId)
            if (distance < lowestDistance) {
                lowestDistance = distance
                matchedUserName = profile.name
            }
        }

        Log.d("🔍 Identity Result", "========================================")
        if (matchedUserName != "Unknown Person") {
            Log.d("🔍 Identity Result", "✅ Verified: $matchedUserName (Confidence Distance: $lowestDistance)")
        } else {
            Log.d("🔍 Identity Result", "❌ Access Denied: User matches no registered database logs.")
        }
        Log.d("🔍 Identity Result", "========================================")

    } catch (e: Exception) {
        Log.e("Verification", "Error extracting validation metrics: ${e.localizedMessage}")
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
        passToTensorFlowLiteModel(croppedFaceBitmap)
    } catch (e: Exception) { null }
}

fun calculateEuclideanDistance(v1: FloatArray, v2: FloatArray): Float {
    if (v1.size != v2.size) return Float.MAX_VALUE
    var sum = 0.0f
    for (i in v1.indices) {
        val diff = v1[i] - v2[i]
        sum += diff * diff
    }
    return sqrt(sum)
}

fun passToTensorFlowLiteModel(bitmap: Bitmap): FloatArray {
    // Simulated placeholder vector fingerprint array
    return FloatArray(128) { 0.5f }
}