/*
 * Overlay view that draws detected pose landmarks and skeleton connections
 * on top of the camera preview. Adapted from the PoseDetector reference repo.
 *
 * Uses MediaPipe's normalized landmark coordinates and proper scaling
 * to align the overlay with the FILL_CENTER camera preview.
 */
package com.gitutk.fitpilot.posedetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max

class PoseOverlayView(context: Context) : View(context) {

    private var results: PoseLandmarkerResult? = null
    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    private val pointPaint = Paint().apply {
        color = Color.rgb(239, 68, 68) // Red joints
        strokeWidth = POINT_STROKE_WIDTH
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.rgb(16, 185, 129) // Emerald green skeleton
        strokeWidth = LINE_STROKE_WIDTH
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    fun setResults(
        poseLandmarkerResult: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.LIVE_STREAM
    ) {
        results = poseLandmarkerResult
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        // For LIVE_STREAM with PreviewView in FILL_CENTER mode,
        // use max to match the fill scaling behavior (same as reference repo)
        scaleFactor = when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
            else -> {
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
        }
        invalidate()
    }

    fun clear() {
        results = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val poseLandmarkerResult = results ?: return
        if (poseLandmarkerResult.landmarks().isEmpty()) return

        // Calculate the scaled image dimensions and centering offset
        // This matches PreviewView.ScaleType.FILL_CENTER behavior
        val scaledImageWidth = imageWidth * scaleFactor
        val scaledImageHeight = imageHeight * scaleFactor
        val offsetX = (width - scaledImageWidth) / 2f
        val offsetY = (height - scaledImageHeight) / 2f

        for (landmarkList in poseLandmarkerResult.landmarks()) {
            // Draw skeleton connections
            PoseLandmarker.POSE_LANDMARKS.forEach { connection ->
                if (landmarkList.size > connection.start() && landmarkList.size > connection.end()) {
                    val startLm = landmarkList[connection.start()]
                    val endLm = landmarkList[connection.end()]
                    canvas.drawLine(
                        offsetX + startLm.x() * scaledImageWidth,
                        offsetY + startLm.y() * scaledImageHeight,
                        offsetX + endLm.x() * scaledImageWidth,
                        offsetY + endLm.y() * scaledImageHeight,
                        linePaint
                    )
                }
            }

            // Draw joint points on top of the skeleton lines
            for (normalizedLandmark in landmarkList) {
                canvas.drawCircle(
                    offsetX + normalizedLandmark.x() * scaledImageWidth,
                    offsetY + normalizedLandmark.y() * scaledImageHeight,
                    POINT_RADIUS,
                    pointPaint
                )
            }
        }
    }

    companion object {
        private const val LINE_STROKE_WIDTH = 8f
        private const val POINT_STROKE_WIDTH = 14f
        private const val POINT_RADIUS = 8f
    }
}
