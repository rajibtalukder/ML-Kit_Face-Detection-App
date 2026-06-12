package com.example.facedetectionapp.utils

import kotlin.math.pow
import kotlin.math.sqrt

object FaceMathUtils {
    fun calculateEuclideanDistance(vector1: FloatArray, vector2: FloatArray): Float {
        if (vector1.size != vector2.size) {
            throw IllegalArgumentException("Vectors must be of the same size (${vector1.size} vs ${vector2.size})")
        }

        var sum = 0.0f
        for (i in vector1.indices) {
            sum += (vector1[i] - vector2[i]).pow(2)
        }

        return sqrt(sum)
    }
}