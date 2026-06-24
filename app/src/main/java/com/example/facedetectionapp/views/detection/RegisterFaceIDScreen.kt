package com.example.facedetectionapp.views.detection


import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.facedetectionapp.database.AppDatabase
import com.example.facedetectionapp.database.FaceEmbeddingEntity
import com.example.facedetectionapp.database.UserEntity
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


enum class RegistrationStep(val instruction: String, val key: String) {
    LOOK_CENTER("Look straight into the camera", "CENTER"),
    TURN_LEFT("Turn your head slightly LEFT", "LEFT"),
    TURN_RIGHT("Turn your head slightly RIGHT", "RIGHT"),
    COMPLETED("Registration Complete!", "DONE")
}

@Composable
fun RegisterFaceIDScreen(onRegistrationComplete: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val db = remember { AppDatabase.getDatabase(context) }
    val userDao = remember { db.userFaceDao() } // Assume your DB exposes this new Dao

    // Initialize ML Kit/MobileFaceNet components safely
    val faceNetEncoder = remember { MobileFaceNetEncoder(context) }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    var currentStep by remember { mutableStateOf(RegistrationStep.LOOK_CENTER) }
    val capturedEmbeddings = remember { mutableMapOf<String, FloatArray>() }
    var isProcessingFrame by remember { mutableStateOf(false) }
    var lastCroppedFace by remember { mutableStateOf<Bitmap?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
        ) {
            FaceAttendanceCameraScreen(
                onFaceProcessed = { face, croppedFaceBitmap ->
                    if (!isProcessingFrame && currentStep != RegistrationStep.COMPLETED) {
                        isProcessingFrame = true

                        coroutineScope.launch(Dispatchers.Default) {
                            val isCorrectAngle = verifyFaceAngle(face, currentStep)

                            if (isCorrectAngle) {
                                try {
                                    // The croppedFaceBitmap is already cropped to the face bounding box and rotated upright.
                                    val scaledFaceBitmap = Bitmap.createScaledBitmap(croppedFaceBitmap, 112, 112, true)
                                    lastCroppedFace = scaledFaceBitmap

                                    // Generate the face embedding vector safely
                                    val embedding = faceNetEncoder.getFaceEmbedding(scaledFaceBitmap)
                                    capturedEmbeddings[currentStep.key] = embedding

                                    withContext(Dispatchers.Main) {
                                        currentStep = when (currentStep) {
                                            RegistrationStep.LOOK_CENTER -> RegistrationStep.TURN_LEFT
                                            RegistrationStep.TURN_LEFT -> RegistrationStep.TURN_RIGHT
                                            RegistrationStep.TURN_RIGHT -> RegistrationStep.COMPLETED
                                            RegistrationStep.COMPLETED -> RegistrationStep.COMPLETED
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("RegisterFaceId", "Bitmap operation failed safely", e)
                                }
                            }

                            // Delay to allow the user time to adjust their face angle for the next step
                            kotlinx.coroutines.delay(1200)
                            isProcessingFrame = false
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
                    containerColor = if (currentStep == RegistrationStep.COMPLETED)
                        Color(0xFF1B5E20).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (currentStep != RegistrationStep.COMPLETED) "Step ${currentStep.ordinal + 1} of 3" else "Success",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentStep.instruction,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            var userName by remember { mutableStateOf("") }
            if (currentStep == RegistrationStep.COMPLETED) {
                TextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("Enter Your Name") },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
                        .fillMaxWidth(0.8f),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.White,unfocusedContainerColor = Color.White)
                )
            }
            if (currentStep == RegistrationStep.COMPLETED) {
                Button(
                    onClick = {
                        if(userName.isEmpty()){
                            Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch(Dispatchers.IO) {
                            val userId = userDao.insertUser(UserEntity(name = userName))

                            capturedEmbeddings.forEach { (pose, vector) ->
                                val normalizedVector = normalizeEmbedding(vector)

                                userDao.insertEmbedding(
                                    FaceEmbeddingEntity(
                                        userId = userId.toInt(),
                                        poseType = pose,
                                        faceId = normalizedVector  // Store normalized
                                    )
                                )
                            }

                            withContext(Dispatchers.Main) {
                                onRegistrationComplete()
                                Toast.makeText(context, "Successfully Registered Face", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                        .fillMaxWidth(0.8f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Save Biometric Profile", color = Color.White, fontSize = 16.sp)
                }


            }

            if (lastCroppedFace != null) {
                Card(
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 180.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Image(
                        bitmap = lastCroppedFace!!.asImageBitmap(),
                        contentDescription = "Cropped Face Preview",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
// Add this helper function
private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
    val norm = kotlin.math.sqrt(embedding.sumOf { (it * it).toDouble() }.toFloat())
    return if (norm > 0f) {
        embedding.map { it / norm }.toFloatArray()
    } else {
        embedding
    }
}
private fun verifyFaceAngle(face: Face, step: RegistrationStep): Boolean {
    val headEulerY = face.headEulerAngleY

    return when (step) {
        RegistrationStep.LOOK_CENTER -> headEulerY in -12f..12f
        RegistrationStep.TURN_LEFT -> headEulerY > 18f
        RegistrationStep.TURN_RIGHT -> headEulerY < -18f
        else -> false
    }
}