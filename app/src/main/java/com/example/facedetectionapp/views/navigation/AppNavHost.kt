package com.example.facedetectionapp.views.navigation
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.facedetectionapp.views.EntryScreen
import com.example.facedetectionapp.views.detection.FaceAttendanceCameraScreen
import com.example.facedetectionapp.views.detetion.FaceScreen
import com.google.mlkit.vision.face.Face


@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = AppScreen.EntryScreen.route
    ) {
        composable(AppScreen.EntryScreen.route) {
            EntryScreen(
                onOpenFaceScreen = {
                    navController.navigate(AppScreen.FaceScreen.route)
                }
            )
        }
        composable(AppScreen.FaceScreen.route) {
            FaceScreen(
                onOpenFaceAttendanceCameraScreen = {
                    navController.navigate(AppScreen.FaceAttendanceCameraScreen.route)
                }
            )
        }
        composable(AppScreen.FaceAttendanceCameraScreen.route) {
            FaceAttendanceCameraScreen(
                onFaceProcessed = { face, fullFrameBitmap -> Unit}
            )
        }
    }
}