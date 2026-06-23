//package com.example.facedetectionapp.views.detection
//
//import com.example.facedetectionapp.database.UserFaceDao
//import com.example.facedetectionapp.database.UserFaceEntity
//import com.example.facedetectionapp.utils.FaceMathUtils
//
//class FaceIdentifier(private val faceDao: UserFaceDao) {
//
//    // MobileFaceNet threshold: lower distance = closer match.
//    // 0.4f is standard, but you can adjust to 0.45f if it's too strict.
//    private val MATCHING_THRESHOLD = 0.40f
//
//    suspend fun identifyPerson(liveFaceVector: FloatArray): UserFaceEntity? {
//        // 1. Fetch all face records from your Room Database
//        val savedProfiles = faceDao.getAllRegisteredFaces()
//
//        var bestMatch: UserFaceEntity? = null
//        var minimumDistance = Float.MAX_VALUE
//
//        // 2. Loop through every saved face and compare distances
//        for (profile in savedProfiles) {
//            // Convert List<Float> from Room back into a FloatArray
//            val savedVector = profile.faceId.sortedArray()
//
//            // Calculate how similar they are
//            val distance = FaceMathUtils.calculateEuclideanDistance(liveFaceVector, savedVector)
//
//            // If it's the lowest distance seen so far and within our strict threshold
//            if (distance < MATCHING_THRESHOLD && distance < minimumDistance) {
//                minimumDistance = distance
//                bestMatch = profile
//            }
//        }
//
//        // 3. Returns the matched profile if found, or null if it's an unknown person
//        return bestMatch
//    }
//}