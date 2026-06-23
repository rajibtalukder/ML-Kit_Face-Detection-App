package com.example.facedetectionapp.views.detection


import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

    // State Tracking for the 3-step pipeline
    var currentStep by remember { mutableStateOf(RegistrationStep.LOOK_CENTER) }
    val capturedEmbeddings = remember { mutableMapOf<String, FloatArray>() }
    var isProcessingFrame by remember { mutableStateOf(false) }

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
            // Camera frame capture UI
            FaceAttendanceCameraScreen(
                onFaceProcessed = { face, croppedFaceBitmap ->
                    // Prevent frame overlapping during ML processing
                    if (!isProcessingFrame && currentStep != RegistrationStep.COMPLETED) {
                        isProcessingFrame = true

                        coroutineScope.launch(Dispatchers.Default) {
                            // 1. Verify face angle matches prompt using ML Kit's Euler angles
                            val isCorrectAngle = verifyFaceAngle(face, currentStep)

                            if (isCorrectAngle) {
                                // 2. Extract biometric embeddings vector
                                val embedding = faceNetEncoder.getFaceEmbedding(croppedFaceBitmap)
                                capturedEmbeddings[currentStep.key] = embedding

                                withContext(Dispatchers.Main) {
                                    // 3. Move state machine forward
                                    currentStep = when (currentStep) {
                                        RegistrationStep.LOOK_CENTER -> RegistrationStep.TURN_LEFT
                                        RegistrationStep.TURN_LEFT -> RegistrationStep.TURN_RIGHT
                                        RegistrationStep.TURN_RIGHT -> RegistrationStep.COMPLETED
                                        RegistrationStep.COMPLETED -> RegistrationStep.COMPLETED
                                    }
                                }
                            }

                            // Cool-down to let the user adjust positions
                            kotlinx.coroutines.delay(1200)
                            isProcessingFrame = false
                        }
                    }
                }
            )

            // Step Instruction / Overlay Panel
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
            // Save Action Layout when finished
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
                            // Transactionally insert User metadata and all 3 embeddings profiles
                            val userId = userDao.insertUser(UserEntity(name = userName))

                            capturedEmbeddings.forEach { (pose, vector) ->
                                userDao.insertEmbedding(
                                    FaceEmbeddingEntity(
                                        userId = userId.toInt(),
                                        poseType = pose,
                                        faceId = vector
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
        }
    }
}

/**
 * Validates whether the user's face matches the directed direction instruction
 */
private fun verifyFaceAngle(face: Face, step: RegistrationStep): Boolean {
    val headEulerY = face.headEulerAngleY // Left/Right turning angle

    return when (step) {
        RegistrationStep.LOOK_CENTER -> headEulerY in -12f..12f
        RegistrationStep.TURN_LEFT -> headEulerY > 18f    // Turned left significantly
        RegistrationStep.TURN_RIGHT -> headEulerY < -18f   // Turned right significantly
        else -> false
    }
}