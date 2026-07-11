package com.gitutk.fitpilot.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.gitutk.fitpilot.ml.ExerciseAnalyzer
import com.gitutk.fitpilot.ml.PoseLandmarkerHelper
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.Executors

@Composable
fun PoseScreen(
    initialMode: String = "squat",
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var selectedMode by remember { mutableStateOf(initialMode) }
    
    // Exercise analyzer
    val analyzer = remember { ExerciseAnalyzer() }
    
    // States updated from ML thread
    var repsCount by remember { mutableStateOf(0) }
    var kneeAngle by remember { mutableStateOf(-1) }
    var backAngle by remember { mutableStateOf(-1) }
    var elbowAngle by remember { mutableStateOf(-1) }
    var feedbackList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFormCorrect by remember { mutableStateOf(true) }
    
    // Joint coordinates for Canvas overlay
    var currentLandmarks by remember { mutableStateOf<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>(emptyList()) }

    // Reset counts on mode switcher
    LaunchedEffect(selectedMode) {
        analyzer.reset()
        repsCount = 0
        kneeAngle = -1
        backAngle = -1
        elbowAngle = -1
        feedbackList = emptyList()
        isFormCorrect = true
        currentLandmarks = emptyList()
    }

    // Camera executor
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // Pose landmarker helper
    var landmarkerHelper by remember { mutableStateOf<PoseLandmarkerHelper?>(null) }

    // Initialize landmarker
    DisposableEffect(Unit) {
        val helper = PoseLandmarkerHelper(
            context = context,
            listener = object : PoseLandmarkerHelper.LandmarkerListener {
                override fun onError(error: String) {
                    Log.e("PoseScreen", "Landmarker error: $error")
                }

                override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
                    val result = resultBundle.results.firstOrNull() ?: return
                    
                    // Update overlay landmarks coordinates
                    if (result.landmarks().isNotEmpty()) {
                        currentLandmarks = result.landmarks()[0]
                    } else {
                        currentLandmarks = emptyList()
                    }

                    // Feed results to the analyzer
                    val analysis = analyzer.analyze(result, selectedMode)
                    if (analysis != null) {
                        repsCount = analysis.reps
                        kneeAngle = analysis.kneeAngle
                        backAngle = analysis.backAngle
                        elbowAngle = analysis.elbowAngle
                        feedbackList = analysis.feedback
                        isFormCorrect = analysis.isFormCorrect
                    }
                }
            }
        )
        landmarkerHelper = helper

        onDispose {
            helper.clearPoseLandmarker()
            cameraExecutor.shutdown()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        if (!hasCameraPermission) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Camera permission required for AI pose coaching.",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                
                // CameraX Preview
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            // Preview config
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            // Analysis config
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .build()
                                .also {
                                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                                        landmarkerHelper?.detectLiveStream(
                                            imageProxy = imageProxy,
                                            isFrontCamera = true
                                        )
                                    }
                                }

                            // Front camera selector
                            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (exc: Exception) {
                                Log.e("PoseScreen", "Use case binding failed", exc)
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Skeleton Canvas Overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (currentLandmarks.isNotEmpty()) {
                        val width = size.width
                        val height = size.height

                        // Coordinates mapping helper
                        fun getPos(index: Int): Offset? {
                            if (index >= currentLandmarks.size) return null
                            val lm = currentLandmarks[index]
                            return Offset(lm.x() * width, lm.y() * height)
                        }

                        // Drawing connections
                        val connections = listOf(
                            Pair(11, 12), // Shoulders
                            Pair(11, 13), Pair(13, 15), // Left Arm
                            Pair(12, 14), Pair(14, 16), // Right Arm
                            Pair(23, 24), // Hips
                            Pair(11, 23), Pair(12, 24), // Torso sides
                            Pair(23, 25), Pair(25, 27), // Left Leg
                            Pair(24, 26), Pair(26, 28)  // Right Leg
                        )

                        connections.forEach { (startIdx, endIdx) ->
                            val startPos = getPos(startIdx)
                            val endPos = getPos(endIdx)
                            if (startPos != null && endPos != null) {
                                drawLine(
                                    color = if (isFormCorrect) Color(0xFF10B981) else Color(0xFFEF4444),
                                    start = startPos,
                                    end = endPos,
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        // Drawing joint nodes
                        currentLandmarks.forEachIndexed { idx, lm ->
                            if (idx in 11..16 || idx in 23..28) {
                                val pos = Offset(lm.x() * width, lm.y() * height)
                                drawCircle(
                                    color = Color.White,
                                    radius = 5.dp.toPx(),
                                    center = pos
                                )
                                drawCircle(
                                    color = if (isFormCorrect) Color(0xFF10B981) else Color(0xFFEF4444),
                                    radius = 3.dp.toPx(),
                                    center = pos
                                )
                            }
                        }
                    }
                }

                // Header Back Button
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .padding(top = 40.dp, start = 20.dp)
                        .size(36.dp)
                        .background(Color(0x99000000), CircleShape)
                        .align(Alignment.TopStart)
                ) {
                    Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                // Floating Exercise Selector Tabs
                Row(
                    modifier = Modifier
                        .padding(top = 40.dp)
                        .background(Color(0x99000000), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(20.dp))
                        .padding(4.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("squat", "curl").forEach { mode ->
                        Button(
                            onClick = { selectedMode = mode },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedMode == mode) Color.White else Color.Transparent,
                                contentColor = if (selectedMode == mode) Color.Black else Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = mode.replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Stats Dashboard Panel (Bottom)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .align(Alignment.BottomCenter)
                        .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xDD09090B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("REPS", color = Color(0xFFA1A1AA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = repsCount.toString(),
                                    color = Color.White,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("ANGLES", color = Color(0xFFA1A1AA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (selectedMode == "squat") {
                                        AngleBadge("Knee", kneeAngle)
                                        AngleBadge("Back", backAngle)
                                    } else {
                                        AngleBadge("Elbow", elbowAngle)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Feedback Messages
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (feedbackList.isEmpty()) {
                                Text(
                                    text = "Position yourself fully in front of the camera.",
                                    color = Color(0xFFA1A1AA),
                                    fontSize = 12.sp
                                )
                            } else {
                                feedbackList.forEach { msg ->
                                    Text(
                                        text = msg,
                                        color = if (msg.contains("⚠️")) Color(0xFFEF4444) else Color(0xFF10B981),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AngleBadge(label: String, angle: Int) {
    Box(
        modifier = Modifier
            .background(Color(0xFF27272A), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$label: ${if (angle == -1) "--" else "$angle°"}",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
