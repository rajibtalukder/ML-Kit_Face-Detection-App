package com.example.facedetectionapp.views.navigation

sealed class AppScreen(val route : String) {
    data object EntryScreen : AppScreen("entryScreen")
    data object FaceScreen : AppScreen("faceScreen")
    data object FaceAttendanceCameraScreen : AppScreen("faceAttendanceCameraScreen")
}