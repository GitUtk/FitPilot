package com.gitutk.fitpilot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitutk.fitpilot.ui.theme.Slate200
import com.gitutk.fitpilot.ui.theme.Slate500
import com.gitutk.fitpilot.ui.theme.Slate900

sealed class TabScreen(val route: String, val title: String, val icon: String) {
    object Dashboard : TabScreen("dashboard", "Home", "🏠")
    object Pose : TabScreen("pose", "AI Coach", "📷")
    object Workouts : TabScreen("workouts", "Workouts", "🏋️")
    object Meals : TabScreen("meals", "Nutrition", "🍎")
}

@Composable
fun BottomNavigationBar(
    currentRoute: String,
    onTabSelected: (TabScreen) -> Unit
) {
    val tabs = listOf(
        TabScreen.Dashboard,
        TabScreen.Pose,
        TabScreen.Workouts,
        TabScreen.Meals
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.White)
            .border(1.dp, Slate200)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isSelected = currentRoute == tab.route
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTabSelected(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = tab.icon,
                    fontSize = 18.sp,
                    color = if (isSelected) Slate900 else Slate500
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = tab.title,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Slate900 else Slate500
                )
            }
        }
    }
}
