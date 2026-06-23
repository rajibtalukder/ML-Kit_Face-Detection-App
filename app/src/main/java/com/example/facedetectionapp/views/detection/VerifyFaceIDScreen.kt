package com.example.facedetectionapp.views.detection

import android.Manifest
import android.content.pm.PackageManager
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
fun VerifyFaceIDScreen(onVerificationSuccess: (UserEntity) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Initialize components
    val db = remember { AppDatabase.getDatabase(context) }
    val userDao = remember { db.userFaceDao() }
    val faceNetEncoder = remember { MobileFaceNetEncoder(context) }

    // 2. State Management
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    // Holds the complete snapshot of local DB users & vectors
    var registeredUsersList by remember { mutableStateOf<List<UserWithEmbeddings>>(emptyList()) }
    var isProcessingFrame by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Initializing scanner...") }
    var verificationStatus by remember { mutableStateOf<Boolean?>(null) } // true = green, false = red, null = black

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    // 3. Pre-load registered biometrics cleanly from Room
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
            // Live Camera processing system
            FaceAttendanceCameraScreen(
                onFaceProcessed = { face, croppedFaceBitmap ->
                    // Guard conditions: don't look up if empty or already calculating a heavy TFLite frame
                    if (!isProcessingFrame && registeredUsersList.isNotEmpty() && verificationStatus == null) {
                        isProcessingFrame = true

                        coroutineScope.launch(Dispatchers.Default) {
                            // Step A: Extract live face embedding features matrix vector
                            val currentLiveEmbedding = faceNetEncoder.getFaceEmbedding(croppedFaceBitmap)

                            // Step B: Calculate nearest match across multi-angle database entries
                            // Standard recognition threshold for MobileFaceNet is typically between 0.4f and 0.5f
                            val matchedUser = findBestMatch(
                                liveEmbedding = currentLiveEmbedding,
                                databaseUsers = registeredUsersList,
                                maxThreshold = 0.45f
                            )

                            withContext(Dispatchers.Main) {
                                if (matchedUser != null) {
                                    verificationStatus = true
                                    statusText = "✅ Access Granted\nWelcome back, ${matchedUser.name}!"

                                    // Cool down then fire navigate navigation callback actions
                                    delay(2500)
                                    onVerificationSuccess(matchedUser)
                                } else {
                                    verificationStatus = false
                                    statusText = "❌ Access Denied\nUnknown Face Structure"

                                    // Hold failure display then reset frame listening loops automatically
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

            // Status Display Card Overlay
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 40.dp)
                    .align(Alignment.TopCenter),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (verificationStatus) {
                        true -> Color(0xFF1B5E20).copy(alpha = 0.9f)  // Deep Forest Emerald
                        false -> Color(0xFFB71C1C).copy(alpha = 0.9f) // Deep Crimson Red
                        null -> Color.Black.copy(alpha = 0.7f)        // Translucent Neutral Grey
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

// =======================================================
// CORE MATCHING ENGINE (Math Euclidean Metric Evaluators)
// =======================================================

/**
 * Loops through all users and their stored multi-angle signatures
 * to find the absolute minimum structural variance.
 */
private fun findBestMatch(
    liveEmbedding: FloatArray,
    databaseUsers: List<UserWithEmbeddings>,
    maxThreshold: Float
): UserEntity? {
    var bestMatchUser: UserEntity? = null
    var minimumDistanceFound = Float.MAX_VALUE

    for (userContainer in databaseUsers) {
        // Evaluate the live scan frame against every available angle profile (Center, Left, Right)
        for (savedEmbeddingEntity in userContainer.embeddings) {
            val distance = calculateEuclideanDistance(liveEmbedding, savedEmbeddingEntity.faceId)

            // If the calculated distance is closer than anything we've checked so far, track it
            if (distance < minimumDistanceFound) {
                minimumDistanceFound = distance
                bestMatchUser = userContainer.user
            }
        }
    }

    // Ensure our closest structural match is within the secure match verification threshold limit
    return if (minimumDistanceFound < maxThreshold) bestMatchUser else null
}

/**
 * Computes the straight-line vector distance between two spatial feature matrices.
 * Lower means identical profiles; Higher means distinct structural variations.
 */
private fun calculateEuclideanDistance(vectorA: FloatArray, vectorB: FloatArray): Float {
    if (vectorA.size != vectorB.size) return Float.MAX_VALUE
    var sumOfSquares = 0.0f
    for (i in vectorA.indices) {
        val delta = vectorA[i] - vectorB[i]
        sumOfSquares += delta * delta
    }
    return sqrt(sumOfSquares)
}