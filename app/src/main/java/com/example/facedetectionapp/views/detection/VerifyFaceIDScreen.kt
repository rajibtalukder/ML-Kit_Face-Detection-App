package com.example.facedetectionapp.views.detection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
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

// Number of frames to buffer and average before making a match decision
private const val FRAMES_TO_COLLECT = 5

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
    var statusText by remember { mutableStateOf("Initializing scanner...") }
    var verificationStatus by remember { mutableStateOf<Boolean?>(null) }
    var lastCroppedFace by remember { mutableStateOf<Bitmap?>(null) }

    // Multi-frame buffer: collect several embeddings and average them for stable matching
    val embeddingBuffer = remember { mutableListOf<FloatArray>() }
    var isMatchingActive by remember { mutableStateOf(false) } // True = actively matching (don't collect new frames)
    var framesCollected by remember { mutableStateOf(0) }

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
            "Position your face in the frame"
        }
    }

    if (hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
        ) {
            FaceAttendanceCameraScreen(
                onFaceProcessed = { _, croppedFaceBitmap ->
                    // Don't collect frames while a match decision is being computed
                    if (isMatchingActive || registeredUsersList.isEmpty() || verificationStatus != null) return@FaceAttendanceCameraScreen

                    coroutineScope.launch(Dispatchers.Default) {
                        try {
                            val scaledFaceBitmap = Bitmap.createScaledBitmap(croppedFaceBitmap, 112, 112, true)
                            lastCroppedFace = scaledFaceBitmap

                            val embedding = faceNetEncoder.getFaceEmbedding(scaledFaceBitmap)

                            synchronized(embeddingBuffer) {
                                embeddingBuffer.add(embedding)
                            }

                            val currentCount = synchronized(embeddingBuffer) { embeddingBuffer.size }
                            withContext(Dispatchers.Main) {
                                framesCollected = currentCount
                                statusText = "Scanning... ($currentCount/$FRAMES_TO_COLLECT frames)"
                            }

                            // Once we have collected enough frames, average them and match
                            if (currentCount >= FRAMES_TO_COLLECT) {
                                isMatchingActive = true

                                val bufferedEmbeddings = synchronized(embeddingBuffer) {
                                    embeddingBuffer.toList().also { embeddingBuffer.clear() }
                                }

                                // Average all collected embeddings into one stable embedding vector
                                val averagedEmbedding = averageEmbeddings(bufferedEmbeddings)

                                val (matchedUser, highestSimilarity) = findBestMatch(
                                    liveEmbedding = averagedEmbedding,
                                    databaseUsers = registeredUsersList,
                                    minSimilarityThreshold = 0.65f // Threshold for averaged/stable embeddings
                                )

                                withContext(Dispatchers.Main) {
                                    framesCollected = 0
                                    if (matchedUser != null) {
                                        Log.d("FaceDB_Match", "🎯 Verified: ${matchedUser.name} | Score: $highestSimilarity")
                                        verificationStatus = true
                                        statusText = "✅ Welcome, ${matchedUser.name}!\n(Match: ${(highestSimilarity * 100).toInt()}%)"

                                        delay(1500)
                                        onVerificationSuccess(matchedUser)
                                        isMatchingActive = false
                                    } else {
                                        Log.d("FaceDB_Match", "❌ No match. Best: $highestSimilarity")
                                        verificationStatus = null
                                        statusText = "❌ Not Recognized (${(highestSimilarity * 100).toInt()}%)\nTry again — look straight at the camera"

                                        delay(1200)
                                        statusText = "Position your face in the frame"
                                        isMatchingActive = false
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("FaceDetection", "Embedding extraction failed", e)
                            synchronized(embeddingBuffer) { embeddingBuffer.clear() }
                            withContext(Dispatchers.Main) {
                                framesCollected = 0
                                isMatchingActive = false
                            }
                        }
                    }
                }
            )

            // Status card at top
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
                        null -> Color.Black.copy(alpha = 0.75f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = statusText,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Show frame collection progress bar only while actively collecting frames
                    if (verificationStatus == null && registeredUsersList.isNotEmpty() && framesCollected > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { framesCollected.toFloat() / FRAMES_TO_COLLECT.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = Color(0xFF00E676),
                            trackColor = Color.White.copy(alpha = 0.2f),
                            strokeCap = StrokeCap.Round
                        )
                        Text(
                            text = "Capturing frames to improve accuracy...",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Cropped face thumbnail at the bottom
            if (lastCroppedFace != null) {
                Card(
                    modifier = Modifier
                        .size(110.dp)
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 60.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(2.dp, if (verificationStatus == true) Color(0xFF00E676) else Color.White.copy(alpha = 0.3f))
                ) {
                    Image(
                        bitmap = lastCroppedFace!!.asImageBitmap(),
                        contentDescription = "Cropped Face Preview",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Camera permission is required for face verification.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
                color = Color.Gray
            )
        }
    }
}

/**
 * Averages multiple embedding vectors element-wise into a single stable embedding,
 * then re-normalizes the result to unit length.
 */
private fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray {
    if (embeddings.isEmpty()) return FloatArray(192)
    val size = embeddings[0].size
    val averaged = FloatArray(size)
    for (emb in embeddings) {
        for (i in emb.indices) {
            averaged[i] += emb[i]
        }
    }
    for (i in averaged.indices) {
        averaged[i] /= embeddings.size.toFloat()
    }
    // Re-normalize the averaged embedding to unit length
    val norm = kotlin.math.sqrt(averaged.sumOf { (it * it).toDouble() }.toFloat())
    return if (norm > 0f) averaged.map { it / norm }.toFloatArray() else averaged
}

private fun findBestMatch(
    liveEmbedding: FloatArray,
    databaseUsers: List<UserWithEmbeddings>,
    minSimilarityThreshold: Float
): Pair<UserEntity?, Float> {
    var bestMatchUser: UserEntity? = null
    var highestSimilarityFound = -1.0f

    Log.d("FaceDB_Match", "⚡ Scanning ${databaseUsers.size} profiles with averaged embedding...")

    for (userContainer in databaseUsers) {
        for (savedEmbeddingEntity in userContainer.embeddings) {
            val similarity = calculateCosineSimilarity(liveEmbedding, savedEmbeddingEntity.faceId)
            Log.d("FaceDB_Match", "👤 ${userContainer.user.name} [${savedEmbeddingEntity.poseType}] -> ${(similarity * 100).toInt()}%")

            if (similarity > highestSimilarityFound) {
                highestSimilarityFound = similarity
                bestMatchUser = userContainer.user
            }
        }
    }

    Log.d("FaceDB_Match", "📊 Best: ${(highestSimilarityFound * 100).toInt()}% (threshold: ${(minSimilarityThreshold * 100).toInt()}%)")
    val matched = if (highestSimilarityFound >= minSimilarityThreshold) bestMatchUser else null
    return Pair(matched, highestSimilarityFound)
}

/**
 * Computes cosine similarity between two L2-normalized embedding vectors.
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