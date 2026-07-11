package com.gitutk.fitpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.gitutk.fitpilot.ui.FitPilotViewModel
import com.gitutk.fitpilot.ui.components.BottomNavigationBar
import com.gitutk.fitpilot.ui.components.TabScreen
import com.gitutk.fitpilot.ui.screens.*
import com.gitutk.fitpilot.ui.theme.FitPilotTheme

class MainActivity : ComponentActivity() {
    private val viewModel: FitPilotViewModel by viewModels()

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContent {
            FitPilotTheme {
                FitPilotApp(viewModel)
            }
        }
    }
}

@Composable
fun FitPilotApp(viewModel: FitPilotViewModel) {
    val navController = rememberNavController()
    val startDestination = if (viewModel.isLoggedIn) "main_tab_host" else "landing"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("landing") {
            LandingScreen(
                onNavigateToLogin = { navController.navigate("login") },
                onNavigateToSignup = { navController.navigate("signup") }
            )
        }

        composable("login") {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate("main_tab_host") {
                        popUpTo("landing") { inclusive = true }
                    }
                },
                onNavigateToSignup = { navController.navigate("signup") },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("signup") {
            SignupScreen(
                viewModel = viewModel,
                onSignupSuccess = {
                    navController.navigate("main_tab_host") {
                        popUpTo("landing") { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.navigate("login") },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("main_tab_host") {
            MainTabHost(viewModel, onNavigateToPose = { mode ->
                navController.navigate("pose/$mode")
            }, onLogoutSuccess = {
                navController.navigate("landing") {
                    popUpTo("main_tab_host") { inclusive = true }
                }
            })
        }

        composable(
            route = "pose/{mode}",
            arguments = listOf(navArgument("mode") { type = NavType.StringType })
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "squat"
            PoseScreen(
                initialMode = mode,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun MainTabHost(
    viewModel: FitPilotViewModel,
    onNavigateToPose: (String) -> Unit,
    onLogoutSuccess: () -> Unit
) {
    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: TabScreen.Dashboard.route

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                currentRoute = currentRoute,
                onTabSelected = { tab ->
                    if (tab == TabScreen.Pose) {
                        // Open the full-screen AI coach pose route
                        onNavigateToPose("squat")
                    } else {
                        tabNavController.navigate(tab.route) {
                            popUpTo(tabNavController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NavHost(
                navController = tabNavController,
                startDestination = TabScreen.Dashboard.route
            ) {
                composable(TabScreen.Dashboard.route) {
                    DashboardScreen(viewModel, onLogoutSuccess)
                }
                composable(TabScreen.Workouts.route) {
                    WorkoutLogScreen(viewModel, onNavigateToPose)
                }
                composable(TabScreen.Meals.route) {
                    MealLoggerScreen(viewModel)
                }
            }
        }
    }
}
