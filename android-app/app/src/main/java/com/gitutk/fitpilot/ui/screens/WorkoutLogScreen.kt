package com.gitutk.fitpilot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitutk.fitpilot.R
import com.gitutk.fitpilot.ui.FitPilotViewModel
import com.gitutk.fitpilot.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

data class ExerciseConfig(
    val key: String,
    val name: String,
    val imageRes: Int,
    val aiAvailable: Boolean,
    val defaultWeight: String,
    val defaultReps: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLogScreen(
    viewModel: FitPilotViewModel,
    onNavigateToPose: (String) -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.fetchWorkoutsHistory()
    }

    val exercises = remember {
        listOf(
            ExerciseConfig("Squat", "Squats", R.drawable.squat, true, "40", "10"),
            ExerciseConfig("Curl", "Bicep Curls", R.drawable.curl, true, "15", "12"),
            ExerciseConfig("Pushup", "Push-Ups", R.drawable.pushup, false, "0", "15"),
            ExerciseConfig("Lunge", "Lunges", R.drawable.lunge, false, "20", "12"),
            ExerciseConfig("Press", "Overhead Press", R.drawable.press, false, "30", "10")
        )
    }

    var activeExKey by remember { mutableStateOf<String?>(null) }
    var setsInput by remember { mutableStateOf("3") }
    var repsInput by remember { mutableStateOf("10") }
    var weightInput by remember { mutableStateOf("40") }
    var localError by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .border(1.dp, Slate200)
                    .padding(horizontal = 20.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Workout Log",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900,
                    letterSpacing = (-0.5).sp
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp)
            ) {
                // Exercise List
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        exercises.forEach { ex ->
                            val isSelected = activeExKey == ex.key
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, if (isSelected) Slate900 else Slate200, RoundedCornerShape(8.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Row click header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (isSelected) {
                                                    activeExKey = null
                                                } else {
                                                    activeExKey = ex.key
                                                    setsInput = "3"
                                                    repsInput = ex.defaultReps
                                                    weightInput = ex.defaultWeight
                                                    localError = null
                                                }
                                            }
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .background(Slate100, RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Image(
                                                    painter = painterResource(id = ex.imageRes),
                                                    contentDescription = ex.name,
                                                    modifier = Modifier.size(40.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                text = ex.name,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Slate900
                                            )
                                        }

                                        Text(
                                            text = if (isSelected) "▲" else "▼",
                                            fontSize = 12.sp,
                                            color = Slate500
                                        )
                                    }

                                    // Dropdown Panel
                                    AnimatedVisibility(visible = isSelected) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp, vertical = 10.dp)
                                        ) {
                                            val errorMsg = localError ?: viewModel.workoutError
                                            if (errorMsg != null) {
                                                Text(
                                                    text = errorMsg,
                                                    color = Red500,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(bottom = 12.dp)
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = setsInput,
                                                    onValueChange = { setsInput = it; localError = null },
                                                    label = { Text("Sets", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                OutlinedTextField(
                                                    value = repsInput,
                                                    onValueChange = { repsInput = it; localError = null },
                                                    label = { Text("Reps", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                OutlinedTextField(
                                                    value = weightInput,
                                                    onValueChange = { weightInput = it; localError = null },
                                                    label = { Text("Weight", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Button(
                                                onClick = {
                                                    val s = setsInput.toIntOrNull()
                                                    val r = repsInput.toIntOrNull()
                                                    val w = weightInput.toFloatOrNull()

                                                    if (s == null || s <= 0 || r == null || r <= 0 || w == null || w < 0) {
                                                        localError = "Please enter valid positive numbers"
                                                        return@Button
                                                    }
                                                    viewModel.logWorkout(ex.name, s, r, w) {
                                                        activeExKey = null
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(44.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                                            ) {
                                                Text("Log Set", color = Color.White, fontWeight = FontWeight.SemiBold)
                                            }

                                            if (ex.aiAvailable) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                OutlinedButton(
                                                    onClick = {
                                                        onNavigateToPose(if (ex.key == "Squat") "squat" else "curl")
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(44.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(1.dp, Slate200)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("📷", fontSize = 12.sp)
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "Start Real-Time AI Scan",
                                                            color = Slate900,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 12.sp
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
                }

                // History Section
                item {
                    Text(
                        text = "History",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                if (viewModel.isLoading && viewModel.workoutsList.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(size = 24.dp, color = Slate900)
                        }
                    }
                } else if (viewModel.workoutsList.isEmpty()) {
                    item {
                        Text(
                            text = "No logs found",
                            fontSize = 13.sp,
                            color = Slate500,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(viewModel.workoutsList) { workout ->
                        // Format date
                        val dateFormatted = try {
                            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                            parser.timeZone = TimeZone.getTimeZone("UTC")
                            val date = parser.parse(workout.timestamp)
                            val formatter = SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault())
                            formatter.format(date ?: Date())
                        } catch (e: Exception) {
                            workout.timestamp
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Slate200, RoundedCornerShape(6.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = workout.exercise,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate900
                                    )
                                    Text(
                                        text = "${workout.sets} sets × ${workout.reps} reps @ ${workout.weight.toInt()}kg",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Slate900
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = dateFormatted,
                                    fontSize = 10.sp,
                                    color = Slate500
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
