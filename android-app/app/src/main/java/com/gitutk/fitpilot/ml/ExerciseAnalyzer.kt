package com.gitutk.fitpilot.ml

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.atan2

class ExerciseAnalyzer {

    // Squat state
    private var squatCounter = 0
    private var squatStage = "up"

    // Curl state
    private var leftCurlCounter = 0
    private var rightCurlCounter = 0
    private var leftCurlStage = "down"
    private var rightCurlStage = "down"

    fun reset() {
        squatCounter = 0
        squatStage = "up"
        leftCurlCounter = 0
        rightCurlCounter = 0
        leftCurlStage = "down"
        rightCurlStage = "down"
    }

    fun analyze(result: PoseLandmarkerResult, exerciseMode: String): ExerciseResult? {
        if (result.landmarks().isEmpty()) return null

        val landmarks = result.landmarks()[0] // First detected person
        if (landmarks.size < 33) return null

        return when (exerciseMode) {
            "squat" -> analyzeSquat(landmarks)
            "curl" -> analyzeCurl(landmarks)
            else -> null
        }
    }

    private fun analyzeSquat(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): ExerciseResult {
        val hip = landmarks[23]
        val knee = landmarks[25]
        val ankle = landmarks[27]
        val shoulder = landmarks[11]

        val kneeAngle = calculateAngle(
            hip.x(), hip.y(),
            knee.x(), knee.y(),
            ankle.x(), ankle.y()
        )

        val backAngle = calculateAngle(
            shoulder.x(), shoulder.y(),
            hip.x(), hip.y(),
            knee.x(), knee.y()
        )

        if (kneeAngle > 160.0) {
            squatStage = "up"
        } else if (kneeAngle < 90.0 && squatStage == "up") {
            squatStage = "down"
            squatCounter++
        }

        val feedback = mutableListOf<String>()
        var isFormCorrect = true

        if (squatStage == "down") {
            if (backAngle < 60.0) {
                feedback.add("⚠️ Lean back more — keep chest up")
                isFormCorrect = false
            } else {
                feedback.add("✅ Good depth! Back angle looks solid")
            }
        } else {
            feedback.add("Stage: ${squatStage.replaceFirstChar { it.uppercase() }}")
        }

        return ExerciseResult(
            reps = squatCounter,
            kneeAngle = kneeAngle.toInt(),
            backAngle = backAngle.toInt(),
            elbowAngle = -1,
            feedback = feedback,
            isFormCorrect = isFormCorrect
        )
    }

    private fun analyzeCurl(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): ExerciseResult {
        val leftShoulder = landmarks[11]
        val leftElbow = landmarks[13]
        val leftWrist = landmarks[15]

        val leftAngle = calculateAngle(
            leftShoulder.x(), leftShoulder.y(),
            leftElbow.x(), leftElbow.y(),
            leftWrist.x(), leftWrist.y()
        )

        if (leftAngle > 160.0) {
            leftCurlStage = "down"
        } else if (leftAngle < 35.0 && leftCurlStage == "down") {
            leftCurlStage = "up"
            leftCurlCounter++
        }

        val rightShoulder = landmarks[12]
        val rightElbow = landmarks[14]
        val rightWrist = landmarks[16]

        val rightAngle = calculateAngle(
            rightShoulder.x(), rightShoulder.y(),
            rightElbow.x(), rightElbow.y(),
            rightWrist.x(), rightWrist.y()
        )

        if (rightAngle > 160.0) {
            rightCurlStage = "down"
        } else if (rightAngle < 35.0 && rightCurlStage == "down") {
            rightCurlStage = "up"
            rightCurlCounter++
        }

        val totalReps = leftCurlCounter + rightCurlCounter
        val feedback = mutableListOf<String>()
        var isFormCorrect = true

        val leftShoulderHipDist = abs(leftShoulder.y() - landmarks[23].y())
        if (leftShoulderHipDist < 0.15f) {
            feedback.add("⚠️ Keep elbows pinned to sides")
            isFormCorrect = false
        }

        feedback.add("L: ${leftCurlStage} (${leftAngle.toInt()}°)")
        feedback.add("R: ${rightCurlStage} (${rightAngle.toInt()}°)")

        return ExerciseResult(
            reps = totalReps,
            kneeAngle = -1,
            backAngle = -1,
            elbowAngle = leftAngle.toInt(),
            feedback = feedback,
            isFormCorrect = isFormCorrect
        )
    }

    private fun calculateAngle(
        aX: Float, aY: Float,
        bX: Float, bY: Float,
        cX: Float, cY: Float
    ): Double {
        val radians = atan2((cY - bY).toDouble(), (cX - bX).toDouble()) -
                atan2((aY - bY).toDouble(), (aX - bX).toDouble())
        var angle = abs(radians * 180.0 / Math.PI)
        if (angle > 180.0) {
            angle = 360.0 - angle
        }
        return angle
    }

    data class ExerciseResult(
        val reps: Int,
        val kneeAngle: Int,
        val backAngle: Int,
        val elbowAngle: Int,
        val feedback: List<String>,
        val isFormCorrect: Boolean
    )
}
