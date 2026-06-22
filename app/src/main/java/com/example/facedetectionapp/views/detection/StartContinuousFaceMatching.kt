package com.example.facedetectionapp.views.detection

import android.graphics.Bitmap
import android.util.Log
import com.example.facedetectionapp.database.UserFaceDao
import com.example.facedetectionapp.utils.FaceMathUtils.calculateEuclideanDistance
import kotlinx.coroutines.delay


suspend fun startContinuousFaceMatching(
    faceNetEncoder: MobileFaceNetEncoder,
    userDao: UserFaceDao,
    getLatestBitmap: () -> Bitmap?,
    onResult: (String, Float) -> Unit,
    onStatusUpdate: (String) -> Unit
) {
    var isIdentified = false

    while (!isIdentified) {
        val currentBitmap = getLatestBitmap()

        if (currentBitmap == null) {
            onStatusUpdate("⚠️ Hold still... aligning viewport matrix")
            delay(300)
            continue
        }

        val savedProfiles = userDao.getAllRegisteredFaces()
        if (savedProfiles.isEmpty()) {
            onResult("Database Empty", 1.0f)
            return
        }

        val currentEmbedding = faceNetEncoder.getFaceEmbedding(currentBitmap)

        var matchedUserName = "Unknown Person"
        var lowestDistance = 0.40f // Strict MobileFaceNet Euclidean ceiling limit

        // 3. Matrix comparison loop
        for (profile in savedProfiles) {
            val distance = calculateEuclideanDistance(currentEmbedding, profile.faceId)
            if (distance < lowestDistance) {
                lowestDistance = distance
                matchedUserName = profile.name
            }
        }

        if (matchedUserName != "Unknown Person") {
            // Success! Break out of the infinite retry mechanism loop
            isIdentified = true
            onResult(matchedUserName, lowestDistance)
        } else {
            // Mismatch event tracker. Log it and drop loop step down to let frame buffer refresh
            Log.d("FaceAuthContinuous", "Identity mismatch. Retrying execution pass...")
            onStatusUpdate("🔄 Retrying scan... keep looking at the camera")

            // CRITICAL: Delay prevents thread starvation and lets the front camera deliver a new frame
            delay(100)
        }
    }
}