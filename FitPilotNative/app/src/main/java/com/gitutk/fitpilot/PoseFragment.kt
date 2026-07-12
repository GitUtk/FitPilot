package com.gitutk.fitpilot

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.gitutk.fitpilot.data.BodyPart
import com.gitutk.fitpilot.data.Device
import com.gitutk.fitpilot.data.Person
import com.gitutk.fitpilot.ml.ModelType
import com.gitutk.fitpilot.ml.MoveNet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2

class PoseFragment : Fragment() {

    companion object {
        private const val TAG = "FitPilotPose"
        private const val ARG_EXERCISE_MODE = "exercise_mode"

        fun newInstance(exerciseMode: String): PoseFragment {
            return PoseFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_EXERCISE_MODE, exerciseMode)
                }
            }
        }
    }

    private lateinit var apiService: ApiService
    private lateinit var cameraPreview: PreviewView
    private lateinit var poseOverlay: PoseOverlayView
    private lateinit var tvExerciseName: TextView
    private lateinit var tvHudReps: TextView
    private lateinit var tvHudAngle: TextView
    private lateinit var tvHudForm: TextView
    private lateinit var tvHudFeedback: TextView
    private lateinit var btnEndSession: Button
    private lateinit var btnRotateCamera: ImageButton

    private var exerciseMode = "squat" // default
    private var isFrontCamera = true
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null

    // MoveNet model
    private var moveNet: MoveNet? = null
    private lateinit var yuvConverter: YuvToRgbConverter
    private lateinit var imageBitmap: Bitmap

    // Rep counter state
    private var repsCount = 0
    private var squatStage = "up"
    private var leftStage = "down"
    private var rightStage = "down"
    private var leftCounter = 0
    private var rightCounter = 0
    private var pushupStage = "up"
    private var lungeStage = "up"
    private var pressStage = "down"
    private var lastPlankSecond = 0L
    private var situpStage = "down"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(context, "Camera permission is required for AI scan", Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exerciseMode = arguments?.getString(ARG_EXERCISE_MODE) ?: "squat"
        apiService = (activity as MainActivity).apiService
        yuvConverter = YuvToRgbConverter(requireContext())
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Create MoveNet SinglePose Lightning model on CPU (works best on SDK 30/older devices)
        try {
            moveNet = MoveNet.create(requireContext(), Device.CPU, ModelType.Lightning)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating MoveNet model", e)
            Toast.makeText(context, "Failed to load AI model", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_pose, container, false)

        cameraPreview = view.findViewById(R.id.cameraPreview)
        poseOverlay = view.findViewById(R.id.poseOverlay)
        tvExerciseName = view.findViewById(R.id.tvExerciseName)
        tvHudReps = view.findViewById(R.id.tvHudReps)
        tvHudAngle = view.findViewById(R.id.tvHudAngle)
        tvHudForm = view.findViewById(R.id.tvHudForm)
        tvHudFeedback = view.findViewById(R.id.tvHudFeedback)
        btnEndSession = view.findViewById(R.id.btnEndSession)
        btnRotateCamera = view.findViewById(R.id.btnRotateCamera)

        tvExerciseName.text = when (exerciseMode) {
            "squat" -> "Squats AI Session"
            "curl" -> "Curls AI Session"
            "pushup" -> "Push-Ups AI Session"
            "lunge" -> "Lunges AI Session"
            "press" -> "Press AI Session"
            "plank" -> "Plank AI Session"
            "situp" -> "Sit-ups AI Session"
            else -> "${exerciseMode.uppercase()} AI Session"
        }

        btnEndSession.setOnClickListener {
            endSessionAndSave()
        }

        btnRotateCamera.setOnClickListener {
            isFrontCamera = !isFrontCamera
            bindCameraUseCases()
        }

        checkPermissionAndStart()

        return view
    }

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        // Choose between front and back camera lens
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(if (isFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK)
            .build()

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(cameraPreview.surfaceProvider)

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor!!) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null && moveNet != null) {
                if (!::imageBitmap.isInitialized) {
                    imageBitmap = Bitmap.createBitmap(
                        imageProxy.width,
                        imageProxy.height,
                        Bitmap.Config.ARGB_8888
                    )
                }
                
                // Convert YUV to RGB bitmap
                yuvConverter.yuvToRgb(mediaImage, imageBitmap)

                // Rotate, and horizontally mirror only if it is the front camera
                val matrix = Matrix().apply {
                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                    if (isFrontCamera) {
                        postScale(-1f, 1f)
                    }
                }

                val rotatedBitmap = Bitmap.createBitmap(
                    imageBitmap, 0, 0, imageBitmap.width, imageBitmap.height,
                    matrix, false
                )

                // Run MoveNet inference
                val persons = moveNet!!.estimatePoses(rotatedBitmap)
                
                if (persons.isNotEmpty()) {
                    val person = persons[0]
                    
                    // Run rep counting logic
                    activity?.runOnUiThread {
                        processPersonPose(person)
                        // Update skeletal overlay overlay
                        poseOverlay.setResults(person, rotatedBitmap.width, rotatedBitmap.height)
                    }
                }

                imageProxy.close()
            } else {
                imageProxy.close()
            }
        }

        try {
            provider.bindToLifecycle(
                viewLifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
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

    private fun processPersonPose(person: Person) {
        if (person.score < 0.2f) return

        when (exerciseMode) {
            "squat" -> {
                val hip = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_HIP }
                val knee = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_KNEE }
                val ankle = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_ANKLE }

                if (hip != null && knee != null && ankle != null &&
                    hip.score > 0.2f && knee.score > 0.2f && ankle.score > 0.2f
                ) {
                    val angle = calculateAngle(
                        hip.coordinate.x, hip.coordinate.y,
                        knee.coordinate.x, knee.coordinate.y,
                        ankle.coordinate.x, ankle.coordinate.y
                    )

                    tvHudAngle.text = "${angle.toInt()}°"

                    if (angle > 155.0) {
                        squatStage = "up"
                        tvHudForm.text = "UP"
                        tvHudFeedback.text = "Squat down until thighs are parallel!"
                    } else if (angle < 95.0 && squatStage == "up") {
                        squatStage = "down"
                        repsCount++
                        tvHudReps.text = repsCount.toString()
                        tvHudForm.text = "DOWN"
                        tvHudFeedback.text = "Excellent depth! Now stand up."
                    } else if (angle < 95.0) {
                        tvHudForm.text = "DOWN"
                        tvHudFeedback.text = "Good job! Now push back up."
                    }
                }
            }
            "curl" -> {
                val leftShoulder = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_SHOULDER }
                val leftElbow = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_ELBOW }
                val leftWrist = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_WRIST }

                val rightShoulder = person.keyPoints.find { it.bodyPart == BodyPart.RIGHT_SHOULDER }
                val rightElbow = person.keyPoints.find { it.bodyPart == BodyPart.RIGHT_ELBOW }
                val rightWrist = person.keyPoints.find { it.bodyPart == BodyPart.RIGHT_WRIST }

                var leftAngle = 0.0
                var rightAngle = 0.0

                if (leftShoulder != null && leftElbow != null && leftWrist != null &&
                    leftShoulder.score > 0.2f && leftElbow.score > 0.2f && leftWrist.score > 0.2f
                ) {
                    leftAngle = calculateAngle(
                        leftShoulder.coordinate.x, leftShoulder.coordinate.y,
                        leftElbow.coordinate.x, leftElbow.coordinate.y,
                        leftWrist.coordinate.x, leftWrist.coordinate.y
                    )

                    if (leftAngle > 150.0) {
                        leftStage = "down"
                    } else if (leftAngle < 35.0 && leftStage == "down") {
                        leftStage = "up"
                        leftCounter++
                    }
                }

                if (rightShoulder != null && rightElbow != null && rightWrist != null &&
                    rightShoulder.score > 0.2f && rightElbow.score > 0.2f && rightWrist.score > 0.2f
                ) {
                    rightAngle = calculateAngle(
                        rightShoulder.coordinate.x, rightShoulder.coordinate.y,
                        rightElbow.coordinate.x, rightElbow.coordinate.y,
                        rightWrist.coordinate.x, rightWrist.coordinate.y
                    )

                    if (rightAngle > 150.0) {
                        rightStage = "down"
                    } else if (rightAngle < 35.0 && rightStage == "down") {
                        rightStage = "up"
                        rightCounter++
                    }
                }

                repsCount = leftCounter + rightCounter
                tvHudReps.text = repsCount.toString()

                val activeAngle = if (leftAngle > 0) leftAngle else rightAngle
                tvHudAngle.text = "${activeAngle.toInt()}°"
                
                tvHudForm.text = "CURL"
                tvHudFeedback.text = "L: ${leftStage.uppercase()} | R: ${rightStage.uppercase()}"
            }
            "pushup" -> {
                val shoulder = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_SHOULDER }
                val elbow = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_ELBOW }
                val wrist = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_WRIST }

                if (shoulder != null && elbow != null && wrist != null &&
                    shoulder.score > 0.2f && elbow.score > 0.2f && wrist.score > 0.2f
                ) {
                    val angle = calculateAngle(
                        shoulder.coordinate.x, shoulder.coordinate.y,
                        elbow.coordinate.x, elbow.coordinate.y,
                        wrist.coordinate.x, wrist.coordinate.y
                    )

                    tvHudAngle.text = "${angle.toInt()}°"

                    if (angle > 150.0) {
                        pushupStage = "up"
                        tvHudForm.text = "UP"
                        tvHudFeedback.text = "Lower your chest to the floor!"
                    } else if (angle < 95.0 && pushupStage == "up") {
                        pushupStage = "down"
                        repsCount++
                        tvHudReps.text = repsCount.toString()
                        tvHudForm.text = "DOWN"
                        tvHudFeedback.text = "Perfect depth! Push back up."
                    } else if (angle < 95.0) {
                        tvHudForm.text = "DOWN"
                        tvHudFeedback.text = "Good push-up form! Push back up."
                    }
                }
            }
            "lunge" -> {
                val hip = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_HIP }
                val knee = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_KNEE }
                val ankle = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_ANKLE }

                if (hip != null && knee != null && ankle != null &&
                    hip.score > 0.2f && knee.score > 0.2f && ankle.score > 0.2f
                ) {
                    val angle = calculateAngle(
                        hip.coordinate.x, hip.coordinate.y,
                        knee.coordinate.x, knee.coordinate.y,
                        ankle.coordinate.x, ankle.coordinate.y
                    )

                    tvHudAngle.text = "${angle.toInt()}°"

                    if (angle > 155.0) {
                        lungeStage = "up"
                        tvHudForm.text = "UP"
                        tvHudFeedback.text = "Step forward and lower your hips!"
                    } else if (angle < 100.0 && lungeStage == "up") {
                        lungeStage = "down"
                        repsCount++
                        tvHudReps.text = repsCount.toString()
                        tvHudForm.text = "DOWN"
                        tvHudFeedback.text = "Great depth! Push back to start position."
                    } else if (angle < 100.0) {
                        tvHudForm.text = "DOWN"
                        tvHudFeedback.text = "Excellent lunge posture!"
                    }
                }
            }
            "press" -> {
                val shoulder = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_SHOULDER }
                val elbow = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_ELBOW }
                val wrist = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_WRIST }

                if (shoulder != null && elbow != null && wrist != null &&
                    shoulder.score > 0.2f && elbow.score > 0.2f && wrist.score > 0.2f
                ) {
                    val angle = calculateAngle(
                        shoulder.coordinate.x, shoulder.coordinate.y,
                        elbow.coordinate.x, elbow.coordinate.y,
                        wrist.coordinate.x, wrist.coordinate.y
                    )

                    tvHudAngle.text = "${angle.toInt()}°"

                    if (angle < 95.0) {
                        pressStage = "down"
                        tvHudForm.text = "DOWN"
                        tvHudFeedback.text = "Drive the weight overhead!"
                    } else if (angle > 160.0 && pressStage == "down") {
                        pressStage = "up"
                        repsCount++
                        tvHudReps.text = repsCount.toString()
                        tvHudForm.text = "UP"
                        tvHudFeedback.text = "Locked out! Now lower slowly."
                    } else if (angle > 160.0) {
                        tvHudForm.text = "UP"
                        tvHudFeedback.text = "Good lock out! Return to chest."
                    }
                }
            }
            "plank" -> {
                val shoulder = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_SHOULDER }
                val hip = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_HIP }
                val knee = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_KNEE }

                if (shoulder != null && hip != null && knee != null &&
                    shoulder.score > 0.2f && hip.score > 0.2f && knee.score > 0.2f
                ) {
                    val angle = calculateAngle(
                        shoulder.coordinate.x, shoulder.coordinate.y,
                        hip.coordinate.x, hip.coordinate.y,
                        knee.coordinate.x, knee.coordinate.y
                    )

                    tvHudAngle.text = "${angle.toInt()}°"

                    if (angle > 160.0) {
                        tvHudForm.text = "ACTIVE"
                        tvHudFeedback.text = "Plank active! Keep holding."
                        val now = System.currentTimeMillis()
                        if (lastPlankSecond == 0L) {
                            lastPlankSecond = now
                        } else if (now - lastPlankSecond >= 1000L) {
                            repsCount++
                            tvHudReps.text = repsCount.toString()
                            lastPlankSecond = now
                        }
                    } else {
                        tvHudForm.text = "ALIGN"
                        tvHudFeedback.text = "Keep hips level with shoulders!"
                        lastPlankSecond = 0L
                    }
                }
            }
            "situp" -> {
                val shoulder = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_SHOULDER }
                val hip = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_HIP }
                val knee = person.keyPoints.find { it.bodyPart == BodyPart.LEFT_KNEE }

                if (shoulder != null && hip != null && knee != null &&
                    shoulder.score > 0.2f && hip.score > 0.2f && knee.score > 0.2f
                ) {
                    val angle = calculateAngle(
                        shoulder.coordinate.x, shoulder.coordinate.y,
                        hip.coordinate.x, hip.coordinate.y,
                        knee.coordinate.x, knee.coordinate.y
                    )

                    tvHudAngle.text = "${angle.toInt()}°"

                    if (angle > 140.0) {
                        situpStage = "down"
                        tvHudForm.text = "DOWN"
                        tvHudFeedback.text = "Sit all the way up!"
                    } else if (angle < 65.0 && situpStage == "down") {
                        situpStage = "up"
                        repsCount++
                        tvHudReps.text = repsCount.toString()
                        tvHudForm.text = "UP"
                        tvHudFeedback.text = "Great contraction! Lower slowly."
                    } else if (angle < 65.0) {
                        tvHudForm.text = "UP"
                        tvHudFeedback.text = "Control the descent."
                    }
                }
            }
        }
    }

    private fun endSessionAndSave() {
        if (repsCount > 0) {
            Toast.makeText(context, "Saving session: $repsCount reps...", Toast.LENGTH_SHORT).show()
            val defaultWeight = when (exerciseMode) {
                "squat" -> 40.0
                "curl" -> 15.0
                "pushup" -> 0.0
                "lunge" -> 20.0
                "press" -> 30.0
                "plank" -> 0.0
                "situp" -> 0.0
                else -> 10.0
            }

            btnEndSession.isEnabled = false
            apiService.logWorkout(exerciseMode, sets = 1, reps = repsCount, weight = defaultWeight) { success, data, error ->
                activity?.runOnUiThread {
                    if (success) {
                        Toast.makeText(context, "Saved to metabolic log!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, error ?: "Failed to save online", Toast.LENGTH_LONG).show()
                    }
                    parentFragmentManager.popBackStack()
                }
            }
        } else {
            Toast.makeText(context, "Session discarded (0 reps)", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor?.shutdown()
        cameraExecutor = null
    }

    override fun onDestroy() {
        super.onDestroy()
        moveNet?.close()
        moveNet = null
    }
}
