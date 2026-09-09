package com.example.detection

import kotlin.math.sqrt

data class SensorData(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val magnitude: Float
)

class SnatchDetector(
    private val onSnatchDetected: (Int, Float, Float) -> Unit,
    private val onScoreUpdated: (Int, Float, Float) -> Unit
) {

    // Thresholds
    private val ACCEL_EXTREME = 32.0f
    private val ACCEL_HIGH = 20.0f
    
    private val GYRO_EXTREME = 12.0f
    private val GYRO_HIGH = 7.0f
    
    private val MAX_SCORE = 100
    private val DECAY_RATE = 12 // Aggressive score decay per update to reduce false positives

    private var currentScore = 0
    private var lastAccelMag = 9.8f
    private var lastGyroMag = 0f

    // Gravity filter
    private val alpha = 0.8f
    private var gravity = floatArrayOf(0f, 0f, 0f)

    fun processAccelerometer(x: Float, y: Float, z: Float) {
        // Apply low-pass filter to isolate gravity
        gravity[0] = alpha * gravity[0] + (1 - alpha) * x
        gravity[1] = alpha * gravity[1] + (1 - alpha) * y
        gravity[2] = alpha * gravity[2] + (1 - alpha) * z

        // Remove gravity to get linear acceleration
        val linearX = x - gravity[0]
        val linearY = y - gravity[1]
        val linearZ = z - gravity[2]

        lastAccelMag = sqrt((linearX * linearX + linearY * linearY + linearZ * linearZ).toDouble()).toFloat()
        evaluateMotion()
    }

    fun processGyroscope(x: Float, y: Float, z: Float) {
        lastGyroMag = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        evaluateMotion()
    }

    private fun evaluateMotion() {
        var eventScore = 0

        // 1. Evaluate pure acceleration
        if (lastAccelMag > ACCEL_EXTREME) {
            eventScore += 60
        } else if (lastAccelMag > ACCEL_HIGH) {
            eventScore += 25
        }

        // 2. Evaluate rotation (requires some linear force to count, avoiding pure wrist twists)
        if (lastGyroMag > GYRO_EXTREME) {
            eventScore += 40
        } else if (lastGyroMag > GYRO_HIGH && lastAccelMag > 12.0f) {
            eventScore += 20
        }

        // 3. The Snatch Combo: High acceleration AND High rotation simultaneously
        if (lastAccelMag > ACCEL_HIGH && lastGyroMag > GYRO_HIGH) {
            eventScore += 40 
        }

        if (eventScore > 0) {
            currentScore += eventScore
        } else {
            currentScore -= DECAY_RATE
        }

        // Clamp score
        currentScore = currentScore.coerceIn(0, MAX_SCORE)

        onScoreUpdated(currentScore, lastAccelMag, lastGyroMag)

        if (currentScore >= MAX_SCORE) {
            onSnatchDetected(currentScore, lastAccelMag, lastGyroMag)
            currentScore = 0 // Reset after detection
        }
    }
    
    fun reset() {
        currentScore = 0
        lastAccelMag = 0f
        lastGyroMag = 0f
        gravity = floatArrayOf(0f, 0f, 0f)
        onScoreUpdated(0, 0f, 0f)
    }
}
