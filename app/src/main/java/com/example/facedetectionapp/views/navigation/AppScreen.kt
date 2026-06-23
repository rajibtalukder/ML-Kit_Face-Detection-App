package com.example.facedetectionapp.views.navigation

sealed class AppScreen(val route : String) {
    data object EntryScreen : AppScreen("entryScreen")
    data object RegisterFaceIDScreen : AppScreen("registerFaceIDScreen")
    data object VerifyFaceIDScreen : AppScreen("verifyFaceIDScreen")
    data object FaceAttendanceCameraScreen : AppScreen("faceAttendanceCameraScreen")
}