package com.gitutk.fitpilot.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.nio.ByteBuffer

class PoseLandmarkerHelper(
    private val context: Context,
    private val runningMode: RunningMode = RunningMode.LIVE_STREAM,
    private val listener: LandmarkerListener? = null,
    private val useGpu: Boolean = true
) {
    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    fun isReady(): Boolean = poseLandmarker != null

    fun setupPoseLandmarker() {
        val baseOptionBuilder = BaseOptions.builder()

        if (useGpu) {
            baseOptionBuilder.setDelegate(Delegate.GPU)
        } else {
            baseOptionBuilder.setDelegate(Delegate.CPU)
        }

        try {
            val modelBytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
            val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size)
            modelBuffer.put(modelBytes)
            baseOptionBuilder.setModelAssetBuffer(modelBuffer)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model file '$MODEL_FILE' from assets", e)
            listener?.onError("Failed to load pose model: ${e.message}")
            return
        }

        if (runningMode == RunningMode.LIVE_STREAM && listener == null) {
            throw IllegalStateException(
                "LandmarkerListener must be set when runningMode is LIVE_STREAM."
            )
        }

        try {
            val baseOptions = baseOptionBuilder.build()
            val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinPoseDetectionConfidence(DEFAULT_POSE_DETECTION_CONFIDENCE)
                .setMinTrackingConfidence(DEFAULT_POSE_TRACKING_CONFIDENCE)
                .setMinPosePresenceConfidence(DEFAULT_POSE_PRESENCE_CONFIDENCE)
                .setRunningMode(runningMode)
                .setNumPoses(DEFAULT_NUM_POSES)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::onLivestreamResult)
                    .setErrorListener(this::onLivestreamError)
            }

            poseLandmarker = PoseLandmarker.createFromOptions(context, optionsBuilder.build())
            Log.d(TAG, "PoseLandmarker initialized successfully (GPU=$useGpu)")
        } catch (e: Exception) {
            Log.e(TAG, "PoseLandmarker failed to initialize (GPU=$useGpu)", e)
            if (useGpu) {
                Log.w(TAG, "Retrying with CPU delegate...")
                try {
                    val cpuBaseOptions = BaseOptions.builder()
                        .setDelegate(Delegate.CPU)
                    val modelBytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
                    val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size)
                    modelBuffer.put(modelBytes)
                    cpuBaseOptions.setModelAssetBuffer(modelBuffer)

                    val cpuOptionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
                        .setBaseOptions(cpuBaseOptions.build())
                        .setMinPoseDetectionConfidence(DEFAULT_POSE_DETECTION_CONFIDENCE)
                        .setMinTrackingConfidence(DEFAULT_POSE_TRACKING_CONFIDENCE)
                        .setMinPosePresenceConfidence(DEFAULT_POSE_PRESENCE_CONFIDENCE)
                        .setRunningMode(runningMode)
                        .setNumPoses(DEFAULT_NUM_POSES)

                    if (runningMode == RunningMode.LIVE_STREAM) {
                        cpuOptionsBuilder
                            .setResultListener(this::onLivestreamResult)
                            .setErrorListener(this::onLivestreamError)
                    }

                    poseLandmarker = PoseLandmarker.createFromOptions(context, cpuOptionsBuilder.build())
                    Log.d(TAG, "PoseLandmarker initialized with CPU fallback")
                } catch (cpuError: Exception) {
                    Log.e(TAG, "PoseLandmarker failed even with CPU", cpuError)
                    listener?.onError("Pose detection initialization failed: ${cpuError.message}")
                }
            } else {
                listener?.onError("Pose detection failed to initialize: ${e.message}")
            }
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean = true) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            imageProxy.close()
            return
        }

        try {
            val frameTime = SystemClock.uptimeMillis()

            val imgWidth = imageProxy.width
            val imgHeight = imageProxy.height
            val rotation = imageProxy.imageInfo.rotationDegrees

            val bitmapBuffer = Bitmap.createBitmap(
                imgWidth, imgHeight, Bitmap.Config.ARGB_8888
            )

            imageProxy.use {
                bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
            }

            val matrix = Matrix().apply {
                postRotate(rotation.toFloat())
                if (isFrontCamera) {
                    postScale(
                        -1f, 1f,
                        imgWidth.toFloat(),
                        imgHeight.toFloat()
                    )
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )

            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            poseLandmarker?.detectAsync(mpImage, frameTime)
        } catch (e: Exception) {
            Log.e(TAG, "detectLiveStream frame processing failed", e)
            try { imageProxy.close() } catch (_: Exception) {}
        }
    }

    private fun onLivestreamResult(result: PoseLandmarkerResult, input: MPImage) {
        val inferenceTime = SystemClock.uptimeMillis() - result.timestampMs()
        listener?.onResults(
            ResultBundle(
                results = listOf(result),
                inferenceTime = inferenceTime,
                inputImageHeight = input.height,
                inputImageWidth = input.width
            )
        )
    }

    private fun onLivestreamError(error: RuntimeException) {
        listener?.onError(error.message ?: "Unknown pose detection error")
    }

    companion object {
        const val TAG = "PoseLandmarkerHelper"
        const val MODEL_FILE = "pose_landmarker_lite.task"
        const val DEFAULT_POSE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_POSES = 1
    }

    data class ResultBundle(
        val results: List<PoseLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int
    )

    interface LandmarkerListener {
        fun onError(error: String)
        fun onResults(resultBundle: ResultBundle)
    }
}
