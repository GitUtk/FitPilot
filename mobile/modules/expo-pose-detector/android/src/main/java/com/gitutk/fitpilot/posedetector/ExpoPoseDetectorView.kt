package com.gitutk.fitpilot.posedetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import kotlin.math.abs
import kotlin.math.atan2

class ExpoPoseDetectorView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {

    private val onPoseUpdate by EventDispatcher()

    private val previewView = PreviewView(context)
    private val overlayView = PoseOverlayView(context)

    private val options = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        .build()
    private val poseDetector = PoseDetection.getClient(options)

    private var cameraProvider: ProcessCameraProvider? = null

    // State variables
    private var exerciseMode = "squat"
    private var isActive = false

    // Squat state
    private var squatCounter = 0
    private var squatStage = "up"

    // Curl state
    private var leftCounter = 0
    private var rightCounter = 0
    private var leftStage = "down"
    private var rightStage = "down"

    // Track previous values to prevent spamming updates
    private var lastReps = -1
    private var lastFeedback = listOf<String>()

    init {
        // Setup scaling and configuration for PreviewView to fill the screen
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        
        addView(previewView)
        addView(overlayView)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = right - left
        val h = bottom - top
        
        // Explicitly size child views to fill the parent.
        // This is critical in React Native to prevent children from defaulting to 0x0 size.
        previewView.layout(0, 0, w, h)
        overlayView.layout(0, 0, w, h)
    }

    fun setExerciseMode(mode: String) {
        if (this.exerciseMode != mode) {
            this.exerciseMode = mode
            resetCounters()
        }
    }

    fun setIsActive(active: Boolean) {
        if (this.isActive != active) {
            this.isActive = active
            if (isAttachedToWindow) {
                if (active) {
                    startCamera()
                } else {
                    stopCamera()
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isActive) {
            startCamera()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopCamera()
        poseDetector.close()
    }

    private fun resetCounters() {
        squatCounter = 0
        squatStage = "up"
        leftCounter = 0
        rightCounter = 0
        leftStage = "down"
        rightStage = "down"
        lastReps = -1
        lastFeedback = emptyList()
        post {
            overlayView.clear()
        }
    }

    private fun getLifecycleOwner(): LifecycleOwner {
        val activity = appContext.currentActivity ?: throw IllegalStateException("Current activity is null")
        return activity as? LifecycleOwner ?: throw IllegalStateException("Activity is not a LifecycleOwner")
    }

    private fun startCamera() {
        val context = context
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                bindCameraUseCases(provider)
            } catch (e: Exception) {
                android.util.Log.e("ExpoPoseDetector", "Failed to get camera provider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(provider: ProcessCameraProvider) {
        val lifecycleOwner = try {
            getLifecycleOwner()
        } catch (e: Exception) {
            android.util.Log.e("ExpoPoseDetector", "LifecycleOwner not found", e)
            return
        }

        provider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(
            ContextCompat.getMainExecutor(context),
            PoseAnalyzer(poseDetector) { pose, imageProxy ->
                val rotation = imageProxy.imageInfo.rotationDegrees
                val width = imageProxy.width
                val height = imageProxy.height

                post {
                    overlayView.setFrameInfo(width, height, rotation)
                    overlayView.setPose(pose)
                    processPose(pose)
                }
            }
        )

        try {
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            android.util.Log.e("ExpoPoseDetector", "Use case binding failed", e)
        }
    }

    private fun stopCamera() {
        post {
            cameraProvider?.unbindAll()
            overlayView.clear()
        }
    }

    private fun calculateAngle(
        aX: Float, aY: Float,
        bX: Float, bY: Float,
        cX: Float, cY: Float
    ): Double {
        val radians = atan2(cY - bY, cX - bX) - atan2(aY - bY, aX - bX)
        var angle = abs(radians * 180.0 / Math.PI)
        if (angle > 180.0) {
            angle = 360.0 - angle
        }
        return angle
    }

    private fun processPose(pose: Pose) {
        if (!isActive) return

        val landmarks = pose.allPoseLandmarks
        if (landmarks.isEmpty()) return

        if (exerciseMode == "squat") {
            val hip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
            val knee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
            val ankle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)

            if (hip != null && knee != null && ankle != null) {
                val angle = calculateAngle(
                    hip.position.x, hip.position.y,
                    knee.position.x, knee.position.y,
                    ankle.position.x, ankle.position.y
                )

                if (angle > 160.0) {
                    squatStage = "up"
                } else if (angle < 90.0 && squatStage == "up") {
                    squatStage = "down"
                    squatCounter++
                }

                val feedback = listOf("Stage: $squatStage")
                val reps = squatCounter

                if (reps != lastReps || feedback != lastFeedback) {
                    lastReps = reps
                    lastFeedback = feedback
                    sendPoseUpdate(
                        reps = reps,
                        kneeAngle = angle,
                        elbowAngleLeft = 0.0,
                        elbowAngleRight = 0.0,
                        feedback = feedback,
                        isFormCorrect = true
                    )
                }
            }
        } else {
            // curl logic
            val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
            val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
            val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)

            val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
            val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
            val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

            var leftAngle = 0.0
            var rightAngle = 0.0

            if (leftShoulder != null && leftElbow != null && leftWrist != null) {
                leftAngle = calculateAngle(
                    leftShoulder.position.x, leftShoulder.position.y,
                    leftElbow.position.x, leftElbow.position.y,
                    leftWrist.position.x, leftWrist.position.y
                )

                if (leftAngle > 160.0) {
                    leftStage = "down"
                } else if (leftAngle < 30.0 && leftStage == "down") {
                    leftStage = "up"
                    leftCounter++
                }
            }

            if (rightShoulder != null && rightElbow != null && rightWrist != null) {
                rightAngle = calculateAngle(
                    rightShoulder.position.x, rightShoulder.position.y,
                    rightElbow.position.x, rightElbow.position.y,
                    rightWrist.position.x, rightWrist.position.y
                )

                if (rightAngle > 160.0) {
                    rightStage = "down"
                } else if (rightAngle < 30.0 && rightStage == "down") {
                    rightStage = "up"
                    rightCounter++
                }
            }

            val reps = leftCounter + rightCounter
            val feedback = listOf("Left: $leftStage", "Right: $rightStage")

            if (reps != lastReps || feedback != lastFeedback) {
                lastReps = reps
                lastFeedback = feedback
                sendPoseUpdate(
                    reps = reps,
                    kneeAngle = 0.0,
                    elbowAngleLeft = leftAngle,
                    elbowAngleRight = rightAngle,
                    feedback = feedback,
                    isFormCorrect = true
                )
            }
        }
    }

    private fun sendPoseUpdate(
        reps: Int,
        kneeAngle: Double,
        elbowAngleLeft: Double,
        elbowAngleRight: Double,
        feedback: List<String>,
        isFormCorrect: Boolean
    ) {
        val eventData = mapOf(
            "reps" to reps,
            "kneeAngle" to if (exerciseMode == "squat") "${kneeAngle.toInt()}°" else "--",
            "backAngle" to "--",
            "elbowAngle" to if (exerciseMode == "curl") "L: ${elbowAngleLeft.toInt()}° | R: ${elbowAngleRight.toInt()}°" else "--",
            "feedback" to feedback,
            "isFormCorrect" to isFormCorrect
        )
        onPoseUpdate(eventData)
    }

    private class PoseAnalyzer(
        private val poseDetector: PoseDetector,
        private val onPoseDetected: (Pose, ImageProxy) -> Unit
    ) : ImageAnalysis.Analyzer {

        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                poseDetector.process(image)
                    .addOnSuccessListener { pose ->
                        onPoseDetected(pose, imageProxy)
                    }
                    .addOnFailureListener {
                        imageProxy.close()
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    private class PoseOverlayView(context: Context) : View(context) {
        private var imageWidth = 0
        private var imageHeight = 0
        private var rotationDegrees = 0
        private var pose: Pose? = null

        private val linePaint = Paint().apply {
            color = Color.rgb(16, 185, 129) // neon green
            strokeWidth = 8f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        private val pointPaint = Paint().apply {
            color = Color.rgb(239, 68, 68) // red dot
            strokeWidth = 14f
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        fun setFrameInfo(width: Int, height: Int, rotation: Int) {
            this.imageWidth = width
            this.imageHeight = height
            this.rotationDegrees = rotation
        }

        fun setPose(pose: Pose) {
            this.pose = pose
            postInvalidate()
        }

        fun clear() {
            this.pose = null
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val currentPose = pose ?: return
            val landmarks = currentPose.allPoseLandmarks
            if (landmarks.isEmpty()) return

            val isLandscape = rotationDegrees == 90 || rotationDegrees == 270
            val targetWidth = if (isLandscape) imageHeight else imageWidth
            val targetHeight = if (isLandscape) imageWidth else imageHeight

            if (targetWidth == 0 || targetHeight == 0) return

            val scaleX = width.toFloat() / targetWidth
            val scaleY = height.toFloat() / targetHeight

            fun getScreenX(landmark: PoseLandmark): Float {
                // Mirrored horizontally for front camera (flip the X)
                val x = targetWidth - landmark.position.x
                return x * scaleX
            }

            fun getScreenY(landmark: PoseLandmark): Float {
                return landmark.position.y * scaleY
            }

            fun drawLine(startType: Int, endType: Int) {
                val startLandmark = currentPose.getPoseLandmark(startType)
                val endLandmark = currentPose.getPoseLandmark(endType)
                if (startLandmark != null && endLandmark != null) {
                    canvas.drawLine(
                        getScreenX(startLandmark),
                        getScreenY(startLandmark),
                        getScreenX(endLandmark),
                        getScreenY(endLandmark),
                        linePaint
                    )
                }
            }

            // Torso/Shoulders/Hips
            drawLine(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
            drawLine(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
            drawLine(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP)
            drawLine(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP)

            // Left Arm
            drawLine(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW)
            drawLine(PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)

            // Right Arm
            drawLine(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW)
            drawLine(PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)

            // Left Leg
            drawLine(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE)
            drawLine(PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)

            // Right Leg
            drawLine(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE)
            drawLine(PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)

            // Draw joints
            for (landmark in landmarks) {
                canvas.drawCircle(
                    getScreenX(landmark),
                    getScreenY(landmark),
                    10f,
                    pointPaint
                )
            }
        }
    }
}
