package com.gitutk.fitpilot.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitutk.fitpilot.R
import com.gitutk.fitpilot.ui.FitPilotViewModel
import com.gitutk.fitpilot.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    viewModel: FitPilotViewModel,
    onLogoutSuccess: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.fetchDashboardData()
    }

    val stats = viewModel.workoutStats
    val todayMeals = viewModel.mealsList

    // Calculate today's meal stats
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val filteredMeals = todayMeals.filter { 
        it.timestamp.startsWith(todayStr) 
    }
    
    val totalCalories = filteredMeals.sumOf { it.calories.toDouble() }.toInt()
    val totalProtein = filteredMeals.sumOf { it.protein.toDouble() }.toInt()
    val totalCarbs = filteredMeals.sumOf { it.carbs.toDouble() }.toInt()
    val totalFat = filteredMeals.sumOf { it.fat.toDouble() }.toInt()

    val formattedDate = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
    
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .border(1.dp, Slate200)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "Logo",
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FitPilot",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        letterSpacing = (-0.4).sp
                    )
                }
                
                IconButton(
                    onClick = { viewModel.logout(onLogoutSuccess) },
                    modifier = Modifier
                        .size(34.dp)
                        .background(Slate100, CircleShape)
                        .border(1.dp, Slate200, CircleShape)
                ) {
                    Text("⎋", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
                }
            }

            // Dashboard Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp)
            ) {
                // Greeting Section
                item {
                    Column {
                        Text(
                            text = formattedDate.uppercase(Locale.getDefault()),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate500,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "$greeting, ${viewModel.userName ?: "Pilot"}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                    }
                }

                // AI Adaptation Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Slate200, RoundedCornerShape(8.dp)),
                        colors = CardDefaults.cardColors(containerColor = Slate50),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertizontally,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Slate100, CircleShape)
                                        .border(1.dp, Slate200, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✨", fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AI Adaptation Engine",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            if (viewModel.isAdaptationLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.CenterHorizontally),
                                    color = Slate900,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = viewModel.adaptationText,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = Slate900
                                )
                            }
                        }
                    }
                }

                // Section Title
                item {
                    Text(
                        text = "Daily Performance",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        letterSpacing = 0.5.sp
                    )
                }

                // Performance Grid (2x2)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MetricCard(
                                title = "Workout Burn",
                                value = "${stats?.totalCalories ?: 0f} kcal",
                                subText = "${(stats?.totalSets ?: 0) * 2} mins training",
                                icon = "🔥",
                                iconBg = Color(0xFFFFECE0),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Food Logged",
                                value = "$totalCalories kcal",
                                subText = "Budget: 2,000 kcal",
                                icon = "🍔",
                                iconBg = Color(0xFFE8F5E9),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MetricCard(
                                title = "Exertion Rate",
                                value = "${stats?.averageIntensity ?: 0f}",
                                subText = "Score intensity",
                                icon = "⚡",
                                iconBg = Color(0xFFF1F5F9),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Protein Balance",
                                value = "${totalProtein}g",
                                subText = "Carbs: ${totalCarbs}g • Fat: ${totalFat}g",
                                icon = "🌱",
                                iconBg = Color(0xFFE8EAF6),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Sync button
                item {
                    Button(
                        onClick = { viewModel.fetchDashboardData() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .border(1.dp, Slate200, RoundedCornerShape(6.dp)),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Slate900
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertizontally) {
                            Text("🔄", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sync Metabolic Stats",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subText: String,
    icon: String,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, Slate200, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Slate50),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertizontally,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(iconBg, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(icon, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900,
                modifier = Modifier.padding(bottom = 2.dp)
            )

            Text(
                text = subText,
                fontSize = 10.sp,
                color = Slate500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
