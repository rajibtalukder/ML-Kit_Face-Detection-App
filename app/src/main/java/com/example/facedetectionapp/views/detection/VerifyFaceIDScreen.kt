package com.example.facedetectionapp.views.detection

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.facedetectionapp.database.AppDatabase
import com.example.facedetectionapp.database.UserEntity
import com.example.facedetectionapp.database.UserWithEmbeddings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

@Composable
fun VerifyFaceIDScreen(onVerificationSuccess: (UserEntity) -> Unit, onBackPress: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val db = remember { AppDatabase.getDatabase(context) }
    val userDao = remember { db.userFaceDao() }
    val faceNetEncoder = remember { MobileFaceNetEncoder(context) }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    var registeredUsersList by remember { mutableStateOf<List<UserWithEmbeddings>>(emptyList()) }
    var isProcessingFrame by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Initializing scanner...") }
    var verificationStatus by remember { mutableStateOf<Boolean?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }

        statusText = "Loading biometric database..."
        withContext(Dispatchers.IO) {
            registeredUsersList = userDao.getAllUsersWithEmbeddings()
        }

        statusText = if (registeredUsersList.isEmpty()) {
            "⚠️ No registered faces found. Please register first!"
        } else {
            "Look straight at the camera to verify"
        }
    }

    if (hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
        ) {
            FaceAttendanceCameraScreen(
                onFaceProcessed = { face, croppedFaceBitmap ->
                    Log.d("FaceDB_Match", "Face Processed ----- 1 $face")
                    if (!isProcessingFrame && registeredUsersList.isNotEmpty() && verificationStatus == null) {
                        isProcessingFrame = true
                        Log.d("FaceDB_Match", "Face Processed ----- 2")

                        coroutineScope.launch(Dispatchers.Default) {
                            val currentLiveEmbedding = faceNetEncoder.getFaceEmbedding(croppedFaceBitmap)
                            Log.d("FaceDB_Match", "Face Processed ----- 3 $currentLiveEmbedding")

                            val matchedUser = findBestMatch(
                                liveEmbedding = currentLiveEmbedding,
                                liveEulerY = face.headEulerAngleY,  // ← Add this
                                databaseUsers = registeredUsersList,
                                maxThreshold = 0.60f  // ← Also lower threshold
                            )
                            Log.d("FaceDB_Match", "Face Processed ----- 4 ${matchedUser?.name}")
                            withContext(Dispatchers.Main) {
                                if (matchedUser != null) {
                                    Log.d("FaceDB_Match", "Face Processed ----- 10.0 Done ✅")
                                    verificationStatus = true
                                    statusText = "✅ Access Granted\nHey, ${matchedUser.name}! You're authorised"

                                    delay(2500)
                                    onVerificationSuccess(matchedUser)
                                    onBackPress()

                                } else {
                                    Log.d("FaceDB_Match", "Face Processed ----- 10.0 Failed ❌")
                                    verificationStatus = false
                                    statusText = "❌ Access Denied\nUnknown Face Structure"

                                    delay(2000)
                                    verificationStatus = null
                                    statusText = "Look straight at the camera to verify"
                                }
                                isProcessingFrame = false
                            }
                        }
                    }
                }
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 40.dp)
                    .align(Alignment.TopCenter),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (verificationStatus) {
                        true -> Color(0xFF1B5E20).copy(alpha = 0.9f)
                        false -> Color(0xFFB71C1C).copy(alpha = 0.9f)
                        null -> Color.Black.copy(alpha = 0.7f)
                    }
                )
            ) {
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Camera access permissions are strictly mandatory for biometric matching execution workflows.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
                color = Color.Gray
            )
        }
    }
}

private fun findBestMatch(
    liveEmbedding: FloatArray,
    liveEulerY: Float,  // ← Add this parameter
    databaseUsers: List<UserWithEmbeddings>,
    maxThreshold: Float
): UserEntity? {
    Log.d("FaceDB_Match", "Face Processed ----- 6")
    Log.d("FaceDB_Match", "Live Face Angle: eulerY = $liveEulerY")

    // Determine which poses to compare based on live face angle
    val posesToCompare = determinePosesToCompare(liveEulerY)
    Log.d("FaceDB_Match", "Will compare against poses: $posesToCompare")

    var bestMatchUser: UserEntity? = null
    var minimumDistanceFound = Float.MAX_VALUE
    var secondMinimumDistanceFound = Float.MAX_VALUE

    Log.d("FaceDB_Match", "Face Processed ----- 6.1")

    for (userContainer in databaseUsers) {
        Log.d("FaceDB_Match", "Face Processed ----- 6.2 ${userContainer.user.name}")

        for (savedEmbeddingEntity in userContainer.embeddings) {
            // ✅ Only compare against similar poses
            if (savedEmbeddingEntity.poseType !in posesToCompare) {
                Log.d("FaceDB_Matching", "Skipping ${userContainer.user.name} [${savedEmbeddingEntity.poseType}] - angle mismatch")
                continue
            }

            Log.d("FaceDB_Match", "Face Processed ----- 6.3 ---userId = ${savedEmbeddingEntity.userId} ---faceId = ${savedEmbeddingEntity.faceId}---poseType = ${savedEmbeddingEntity.poseType}")

            val distance = calculateEuclideanDistance(liveEmbedding, savedEmbeddingEntity.faceId)
            Log.d("FaceDB_Matching", "Comparing with ${userContainer.user.name} [${savedEmbeddingEntity.poseType}]. Calculated Distance: $distance")

            // Track top 2 distances
            if (distance < minimumDistanceFound) {
                secondMinimumDistanceFound = minimumDistanceFound
                minimumDistanceFound = distance
                bestMatchUser = userContainer.user
            } else if (distance < secondMinimumDistanceFound) {
                secondMinimumDistanceFound = distance
            }
        }
    }

    Log.d("FaceDB_Matching", "Absolute Best Match Distance: $minimumDistanceFound (Allowed Limit: $maxThreshold)")
    Log.d("FaceDB_Matching", "Second Best Match Distance: $secondMinimumDistanceFound")

    val confidenceMargin = secondMinimumDistanceFound - minimumDistanceFound
    Log.d("FaceDB_Matching", "Margin (Gap between top-1 and top-2): $confidenceMargin")

    val marginThreshold = 0.05f

    return if (minimumDistanceFound < maxThreshold && confidenceMargin >= marginThreshold) {
        Log.d("FaceDB_Matching", "✅ CONFIDENT MATCH: ${bestMatchUser?.name}")
        bestMatchUser
    } else if (minimumDistanceFound >= maxThreshold) {
        Log.d("FaceDB_Matching", "❌ REJECTED: Best match ($minimumDistanceFound) exceeds threshold ($maxThreshold)")
        null
    } else {
        Log.d("FaceDB_Matching", "⚠️ UNCERTAIN: Margin too small ($confidenceMargin < $marginThreshold)")
        null
    }
}

// Determine which stored poses to compare based on live face angle
private fun determinePosesToCompare(eulerY: Float): List<String> {
    return when {
        kotlin.math.abs(eulerY) <= 15f -> listOf("CENTER")  // Near frontal
        eulerY in -35f..-10f -> listOf("LEFT")               // Looking left
        eulerY in 10f..35f -> listOf("RIGHT")                // Looking right
        else -> {
            Log.w("FaceDB_Match", "Face angle $eulerY is too extreme, will not match")
            emptyList()  // Reject extreme angles
        }
    }
}

private fun calculateEuclideanDistance(vectorA: FloatArray, vectorB: FloatArray): Float {
    Log.d("FaceDB_Match", "Face Processed ----- 6.4")
    if (vectorA.size != vectorB.size) return Float.MAX_VALUE
    var sumOfSquares = 0.0f
    for (i in vectorA.indices) {
        val delta = vectorA[i] - vectorB[i]
        sumOfSquares += delta * delta
    }
    Log.d("FaceDB_Match", "Face Processed ----- 6.6 ${sqrt(sumOfSquares)}")
    return sqrt(sumOfSquares)
}