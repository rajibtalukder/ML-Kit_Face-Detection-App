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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.facedetectionapp.database.UserFaceEntity
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RegisterFaceIDScreen(onOpenFaceAttendanceCameraScreen: () -> Unit) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Initialize Room Database and Decoder components securely
    val db = remember { AppDatabase.getDatabase(context) }
    val userDao = remember { db.userFaceDao() }
    val faceNetEncoder = remember { MobileFaceNetEncoder(context) }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    // Receives the cropped 112x112 px bitmap frame directly from the analyzer interface
    var latestFaceData by remember { mutableStateOf<Pair<Face, Bitmap>?>(null) }
    var statusText by remember { mutableStateOf("Align face inside the frame scanner") }
    var isVerifiedStatus by remember { mutableStateOf<Boolean?>(null) }

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
        latestFaceData?.let { (_, croppedBitmap) ->
            statusText = "Extracting features..."
            coroutineScope.launch(Dispatchers.Default) {
                val embedding = faceNetEncoder.getFaceEmbedding(croppedBitmap)

                withContext(Dispatchers.IO) {
                    val generatedIdName = "User_${System.currentTimeMillis()}"
                    val newProfile = UserFaceEntity(
                        name = generatedIdName,
                        faceId = embedding
                    )
                    userDao.insertFace(newProfile)

                    withContext(Dispatchers.Main) {
                        isVerifiedStatus = true
                        statusText = "💾 Successfully Registered:\n$generatedIdName"
                    }
                }
            }
        } ?: run {
            statusText = "⚠️ Center your face inside the frame"
            isVerifiedStatus = null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212)) // Dark sleek theme
        ) {
            // Render the full screen Camera system
            FaceAttendanceCameraScreen(
                onFaceProcessed = { face, croppedFaceBitmap ->
                    latestFaceData = Pair(face, croppedFaceBitmap)
                }
            )

            // Top Status Panel Display overlay
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 40.dp)
                    .align(Alignment.TopCenter),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (isVerifiedStatus) {
                        true -> Color(0xFF1B5E20).copy(alpha = 0.9f) // Deep Green
                        false -> Color(0xFFB71C1C).copy(alpha = 0.9f) // Deep Red
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

            // Bottom Actions Control Panel
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Camera hardware access permissions are required to run bio-metric attendance verification.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
                color = Color.Gray
            )
        }
    }
}