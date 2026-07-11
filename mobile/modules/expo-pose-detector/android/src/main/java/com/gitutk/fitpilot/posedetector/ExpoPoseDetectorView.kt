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
import android.graphics.Color
import android.util.Log
import android.widget.FrameLayout
import android.view.Gravity
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
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

    // Camera preview fills the entire view.
    // COMPATIBLE mode uses TextureView (not SurfaceView) which is required
    // for React Native — SurfaceView has Z-ordering issues in RN's view hierarchy
    // and renders behind other views, causing a black screen.
    private val previewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    // Skeleton overlay drawn on top of camera
    // Skeleton overlay must be explicitly transparent so it doesn't occlude the preview
    private val overlayView = PoseOverlayView(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
    }

    // Diagnostic status text overlay to show initialization/error states directly on screen
    private val statusTextView = TextView(context).apply {
        setTextColor(Color.YELLOW)
        textSize = 14f
        gravity = Gravity.START or Gravity.TOP
        setBackgroundColor(Color.parseColor("#CC000000")) // semi-transparent black
        setPadding(30, 30, 30, 30)
        text = "Status: Initializing..."
    }

    private fun setStatus(message: String) {
        post {
            statusTextView.text = "Status: $message"
            Log.d(TAG, "Status updated: $message")
        }
    }

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
        addView(statusTextView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)

        val exactWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val exactHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)

        previewView.measure(exactWidthSpec, exactHeightSpec)
        overlayView.measure(exactWidthSpec, exactHeightSpec)
        statusTextView.measure(
            exactWidthSpec,
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = right - left
        val h = bottom - top

        // Force child measurement to match parent dimensions exactly.
        // Without this, React Native's layout engine can leave children measured at 0x0.
        val exactWidthSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val exactHeightSpec = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        
        previewView.measure(exactWidthSpec, exactHeightSpec)
        previewView.layout(0, 0, w, h)

        overlayView.measure(exactWidthSpec, exactHeightSpec)
        overlayView.layout(0, 0, w, h)

        statusTextView.measure(exactWidthSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST))
        statusTextView.layout(0, 0, w, statusTextView.measuredHeight)
    }

    private var isLayoutScheduled = false

    override fun requestLayout() {
        super.requestLayout()
        // Force layout pass because React Native's custom layout hierarchy frequently
        // bypasses/ignores standard layout requests from nested native views.
        if (!isLayoutScheduled) {
            isLayoutScheduled = true
            post(measureAndLayoutRunnable)
        }
    }

    private val measureAndLayoutRunnable = Runnable {
        isLayoutScheduled = false
        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        layout(left, top, right, bottom)
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
        setStatus("Starting camera...")
        // Create a dedicated single-thread executor for image analysis
        if (cameraExecutor == null || cameraExecutor!!.isShutdown) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }

        // Initialize PoseLandmarkerHelper if needed
        if (poseLandmarkerHelper == null) {
            try {
                setStatus("Loading pose model...")
                poseLandmarkerHelper = PoseLandmarkerHelper(
                    context = context.applicationContext,
                    runningMode = RunningMode.LIVE_STREAM,
                    listener = landmarkerListener,
                    useGpu = true
                )
                setStatus("Pose model loaded.")
            } catch (e: Exception) {
                setStatus("Error initializing model: ${e.message}")
                Log.e(TAG, "PoseLandmarkerHelper init failed", e)
                return
            }
        }

        setStatus("Getting camera provider...")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                bindCameraUseCases(provider)
            } catch (e: Exception) {
                setStatus("Error: Failed to get camera provider: ${e.message}")
                Log.e(TAG, "Failed to get camera provider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(provider: ProcessCameraProvider) {
        setStatus("Binding use cases...")
        val lifecycleOwner = try {
            getLifecycleOwner()
        } catch (e: Exception) {
            setStatus("Error: LifecycleOwner not found: ${e.message}")
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
            // Direct binding (more compatible than UseCaseGroup in Expo/RN context)
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            setStatus("Camera bound and active.")
            Log.d(TAG, "Camera bound with Preview + ImageAnalysis")
        } catch (e: Exception) {
            setStatus("Error binding camera: ${e.message}")
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
