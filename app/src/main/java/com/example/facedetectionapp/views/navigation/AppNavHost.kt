package com.example.facedetectionapp.views.navigation
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.facedetectionapp.views.EntryScreen
import com.example.facedetectionapp.views.detection.FaceAttendanceCameraScreen
import com.example.facedetectionapp.views.detection.RegisterFaceIDScreen
import com.example.facedetectionapp.views.detection.VerifyFaceIDScreen


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
                    navController.navigate(AppScreen.RegisterFaceIDScreen.route)
                },
                onOpenVerifyScreen = {
                    navController.navigate(AppScreen.VerifyFaceIDScreen.route)
                }
            )
        }
        composable(AppScreen.RegisterFaceIDScreen.route) {
            RegisterFaceIDScreen(
                onRegistrationComplete = {
                    navController.navigate(AppScreen.FaceAttendanceCameraScreen.route)
                }
            )
        }
        composable(AppScreen.VerifyFaceIDScreen.route) {
            VerifyFaceIDScreen(
                onVerificationSuccess = {
                    navController.navigate(AppScreen.FaceAttendanceCameraScreen.route)
                },
                onBackPress = { navController.popBackStack() }
            )
        }
        composable(AppScreen.FaceAttendanceCameraScreen.route) {
            FaceAttendanceCameraScreen(
                onFaceProcessed = { face, fullFrameBitmap -> Unit}
            )
        }
    }
}