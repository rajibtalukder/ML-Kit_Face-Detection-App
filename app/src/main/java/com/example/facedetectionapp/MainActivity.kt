package com.example.facedetectionapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.facedetectionapp.ui.theme.FaceDetectionAppTheme
import com.example.facedetectionapp.views.EntryScreen
import com.example.facedetectionapp.views.detetion.FaceScreen
import com.example.facedetectionapp.views.navigation.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FaceDetectionAppTheme {
                AppNavHost()
            }
        }
    }
}