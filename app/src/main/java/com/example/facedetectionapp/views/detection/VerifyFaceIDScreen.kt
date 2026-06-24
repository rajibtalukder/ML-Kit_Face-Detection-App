package com.example.facedetectionapp.views.detection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
                            var embedding: FloatArray? = null

                            try {
                                // The croppedFaceBitmap is already cropped to the face bounding box and rotated upright.
                                val scaledFaceBitmap = Bitmap.createScaledBitmap(croppedFaceBitmap, 112, 112, true)

                                // Generate the embedding vector
                                embedding = faceNetEncoder.getFaceEmbedding(scaledFaceBitmap)
                            } catch (e: Exception) {
                                Log.e("FaceDetection", "Bitmap operations failed", e)
                            }

                            Log.d("FaceDB_Match", "Face Processed ----- 3 $embedding")

                            // Only proceed if we successfully generated a valid embedding array
                            if (embedding != null) {
                                val matchedUser = findBestMatch(
                                    liveEmbedding = embedding,
                                    databaseUsers = registeredUsersList,
                                    minSimilarityThreshold = 0.80f // Reasonable cosine similarity threshold
                                )

                                withContext(Dispatchers.Main) {
                                    if (matchedUser != null) {
                                        Log.d("FaceDB_Match", "🎯 Verified Successfully: ${matchedUser.name}")
                                        verificationStatus = true
                                        statusText = "✅ Access Granted\nWelcome, ${matchedUser.name}!"

                                        delay(2000)
                                        onVerificationSuccess(matchedUser)
                                    } else {
                                        Log.d("FaceDB_Match", "❌ Face matches no local profiles.")
                                        verificationStatus = false
                                        statusText = "❌ Access Denied\nUnknown Face Structure"

                                        delay(1500)
                                        verificationStatus = null // Reset status so it can try scanning again
                                    }
                                    isProcessingFrame = false
                                }
                            } else {
                                // Fallback reset if calculation failed entirely
                                withContext(Dispatchers.Main) {
                                    isProcessingFrame = false
                                }
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
    databaseUsers: List<UserWithEmbeddings>,
    minSimilarityThreshold: Float
): UserEntity? {
    var bestMatchUser: UserEntity? = null
    var highestSimilarityFound = -1.0f

    Log.d("FaceDB_Match", "⚡ Starting Cosine Similarity Scan across ${databaseUsers.size} profiles...")

    for (userContainer in databaseUsers) {
        for (savedEmbeddingEntity in userContainer.embeddings) {
            val similarity = calculateCosineSimilarity(liveEmbedding, savedEmbeddingEntity.faceId)
            Log.d("FaceDB_Match", "👤 User: ${userContainer.user.name} [${savedEmbeddingEntity.poseType}] -> Match Score: ${(similarity * 100).toInt()}% ($similarity)")

            if (similarity > highestSimilarityFound) {
                highestSimilarityFound = similarity
                bestMatchUser = userContainer.user
            }
        }
    }

    Log.d("FaceDB_Match", "📊 Scan Done. Highest similarity found: ${(highestSimilarityFound * 100).toInt()}% (Required: ${(minSimilarityThreshold * 100).toInt()}%)")

    return if (highestSimilarityFound >= minSimilarityThreshold) bestMatchUser else null
}

/**
 * Computes the angular similarity between two feature arrays.
 * Handles internal vector normalization on-the-fly to guarantee reliable output scales.
 */
private fun calculateCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
    if (vectorA.size != vectorB.size) return 0.0f

    var dotProduct = 0.0f
    var normA = 0.0f
    var normB = 0.0f

    for (i in vectorA.indices) {
        dotProduct += vectorA[i] * vectorB[i]
        normA += vectorA[i] * vectorA[i]
        normB += vectorB[i] * vectorB[i]
    }

    val denominator = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
    return if (denominator.toDouble() == 0.0) 0.0f else (dotProduct / denominator)
}