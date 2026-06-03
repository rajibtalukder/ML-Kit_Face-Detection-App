package com.example.facedetectionapp.views.detetion

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun FaceScreen(modifier: Modifier) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

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
        Log.d("FaceScreen", "Camera permission granted. Displaying FaceAttendanceCameraScreen.")
        FaceAttendanceCameraScreen(
            modifier = Modifier.fillMaxSize(),
            onFaceProcessed = { face ->
                // Verification Trigger:
                // Log bounding box data, or trigger your TFLite feature extractor match.
                val bounds = face.boundingBox
                Log.d(
                    "✅Face detected at: ",
                    "L:${bounds.left}, T:${bounds.top}, R:${bounds.right}, B:${bounds.bottom}"
                )
            }
        )
    } else {
        Text(text = "Camera access is required for attendance tracking systems.")
    }
}