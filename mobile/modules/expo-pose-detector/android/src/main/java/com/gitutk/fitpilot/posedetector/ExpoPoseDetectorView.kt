/*
 * ExpoPoseDetectorView - The Expo native view that hosts the CameraX preview,
 * MediaPipe pose landmarker, overlay rendering, and exercise analysis.
 *
 * Architecture follows the PoseDetector reference repo:
 * - CameraX Preview → PreviewView (FILL_CENTER)
 * - CameraX ImageAnalysis → PoseLandmarkerHelper (RGBA_8888 → Bitmap → MPImage)
 * - Results → PoseOverlayView (normalized coords → screen coords)
 * - Results → ExerciseAnalyzer → onPoseUpdate event → React Native
 *
 * Key fix over previous implementation: uses MediaPipe Tasks Vision instead
 * of ML Kit, with proper RGBA_8888 image format and Bitmap rotation/mirroring.
 */
package com.gitutk.fitpilot.posedetector

import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.tasks.vision.core.RunningMode
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ExpoPoseDetectorView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {

    private val onPoseUpdate by EventDispatcher()

    // Camera preview fills the entire view
    private val previewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    // Skeleton overlay drawn on top of camera
    private val overlayView = PoseOverlayView(context)

    // MediaPipe helper (initialized when camera starts)
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null

    // Exercise analysis
    private val exerciseAnalyzer = ExerciseAnalyzer()

    // Camera state
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null

    // Props from React Native
    private var exerciseMode = "squat"
    private var isActive = false

    // Throttle event dispatching to avoid overwhelming JS bridge
    private var lastEventTime = 0L
    private var lastReps = -1

    private val landmarkerListener = object : PoseLandmarkerHelper.LandmarkerListener {
        override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
            post {
                // Update overlay
                if (resultBundle.results.isNotEmpty()) {
                    overlayView.setResults(
                        resultBundle.results.first(),
                        resultBundle.inputImageHeight,
                        resultBundle.inputImageWidth,
                        RunningMode.LIVE_STREAM
                    )
                } else {
                    overlayView.clear()
                }

                // Run exercise analysis and dispatch to RN
                if (isActive && resultBundle.results.isNotEmpty()) {
                    val result = exerciseAnalyzer.analyze(resultBundle.results.first(), exerciseMode)
                    result?.let { dispatchExerciseResult(it) }
                }
            }
        }

        override fun onError(error: String) {
            Log.e(TAG, "PoseLandmarker error: $error")
        }
    }

    init {
        addView(previewView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        addView(overlayView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = right - left
        val h = bottom - top
        // Explicitly size child views to fill parent (critical in React Native)
        previewView.layout(0, 0, w, h)
        overlayView.layout(0, 0, w, h)
    }

    fun setExerciseMode(mode: String) {
        if (this.exerciseMode != mode) {
            this.exerciseMode = mode
            exerciseAnalyzer.reset()
            lastReps = -1
            overlayView.clear()
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
    }

    private fun getLifecycleOwner(): LifecycleOwner {
        val activity = appContext.currentActivity
            ?: throw IllegalStateException("Current activity is null")
        return activity as? LifecycleOwner
            ?: throw IllegalStateException("Activity is not a LifecycleOwner")
    }

    private fun startCamera() {
        // Create a dedicated single-thread executor for image analysis
        if (cameraExecutor == null || cameraExecutor!!.isShutdown) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }

        // Initialize PoseLandmarkerHelper if needed
        if (poseLandmarkerHelper == null) {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = context.applicationContext,
                runningMode = RunningMode.LIVE_STREAM,
                listener = landmarkerListener,
                useGpu = true
            )
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                bindCameraUseCases(provider)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get camera provider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(provider: ProcessCameraProvider) {
        val lifecycleOwner = try {
            getLifecycleOwner()
        } catch (e: Exception) {
            Log.e(TAG, "LifecycleOwner not found", e)
            return
        }

        provider.unbindAll()

        // Front camera for self-facing exercise tracking
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        // Camera preview use case
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        // Image analysis use case - critical: use RGBA_8888 format for MediaPipe
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor!!) { imageProxy ->
                    poseLandmarkerHelper?.detectLiveStream(imageProxy, isFrontCamera = true)
                }
            }

        try {
            // Bind using UseCaseGroup (following reference repo pattern)
            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageAnalysis)
                .build()

            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                useCaseGroup
            )
            Log.d(TAG, "Camera bound with Preview + ImageAnalysis")
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
        }
    }

    private fun stopCamera() {
        post {
            cameraProvider?.unbindAll()
            overlayView.clear()
            poseLandmarkerHelper?.clearPoseLandmarker()
            poseLandmarkerHelper = null
            cameraExecutor?.shutdown()
            cameraExecutor = null
            exerciseAnalyzer.reset()
            lastReps = -1
        }
    }

    private fun dispatchExerciseResult(result: ExerciseAnalyzer.ExerciseResult) {
        // Throttle: send at most every 100ms OR when reps change
        val now = System.currentTimeMillis()
        if (now - lastEventTime < 100 && result.reps == lastReps) return

        lastEventTime = now
        lastReps = result.reps

        val eventData = mapOf(
            "reps" to result.reps,
            "kneeAngle" to if (result.kneeAngle >= 0) "${result.kneeAngle}" else "--",
            "backAngle" to if (result.backAngle >= 0) "${result.backAngle}" else "--",
            "elbowAngle" to if (result.elbowAngle >= 0) "${result.elbowAngle}" else "--",
            "feedback" to result.feedback,
            "isFormCorrect" to result.isFormCorrect
        )
        onPoseUpdate(eventData)
    }

    companion object {
        private const val TAG = "ExpoPoseDetectorView"
    }
}
