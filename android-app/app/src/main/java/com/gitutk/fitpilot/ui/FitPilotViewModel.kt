package com.gitutk.fitpilot.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitutk.fitpilot.data.api.*
import com.gitutk.fitpilot.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FitPilotViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager(application)
    private val api = FitPilotApi.create(sessionManager)

    var token by mutableStateOf<String?>(sessionManager.getAuthToken())
        private set

    var userEmail by mutableStateOf<String?>(sessionManager.getUserEmail())
        private set

    var userName by mutableStateOf<String?>(sessionManager.getUserName())
        private set

    val isLoggedIn: Boolean
        get() = token != null

    // API UI states
    var isLoading by mutableStateOf(false)
        private set

    var authError by mutableStateOf<String?>(null)
        private set

    var workoutError by mutableStateOf<String?>(null)
        private set

    var mealError by mutableStateOf<String?>(null)
        private set

    // Data States
    var workoutStats by mutableStateOf<WorkoutStats?>(null)
        private set

    var workoutsList by mutableStateOf<List<Workout>>(emptyList())
        private set

    var mealsList by mutableStateOf<List<Meal>>(emptyList())
        private set

    var adaptationText by mutableStateOf("No recommendations active. Log meals to generate advice.")
        private set

    var isAdaptationLoading by mutableStateOf(false)
        private set

    // Chat Message state
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    var isChatSending by mutableStateOf(false)
        private set

    init {
        if (isLoggedIn) {
            fetchDashboardData()
            fetchWorkoutsHistory()
            fetchChatHistory()
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            authError = null
            try {
                val res = api.login(LoginRequest(email, password))
                sessionManager.saveAuthToken(res.accessToken)
                sessionManager.saveUserDetails(res.user.fullName, res.user.email)
                token = res.accessToken
                userName = res.user.fullName
                userEmail = res.user.email
                fetchDashboardData()
                fetchWorkoutsHistory()
                fetchChatHistory()
                onSuccess()
            } catch (e: Exception) {
                authError = e.message ?: "Authentication failed"
            } finally {
                isLoading = false
            }
        }
    }

    fun signup(email: String, password: String, fullName: String?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            authError = null
            try {
                val res = api.signup(SignupRequest(email, password, fullName))
                sessionManager.saveAuthToken(res.accessToken)
                sessionManager.saveUserDetails(res.user.fullName, res.user.email)
                token = res.accessToken
                userName = res.user.fullName
                userEmail = res.user.email
                fetchDashboardData()
                fetchWorkoutsHistory()
                fetchChatHistory()
                onSuccess()
            } catch (e: Exception) {
                authError = e.message ?: "Registration failed"
            } finally {
                isLoading = false
            }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        sessionManager.clear()
        token = null
        userName = null
        userEmail = null
        workoutStats = null
        workoutsList = emptyList()
        mealsList = emptyList()
        _chatMessages.value = emptyList()
        onSuccess()
    }

    fun fetchDashboardData() {
        viewModelScope.launch {
            isAdaptationLoading = true
            try {
                val stats = api.getWorkoutStats()
                workoutStats = stats
            } catch (e: Exception) {
                // handle silently or set error
            }

            try {
                val meals = api.getMeals()
                mealsList = meals
            } catch (e: Exception) {
                // handle
            }

            try {
                val advice = api.getAdaptationAdvice()
                adaptationText = advice.recommendation
            } catch (e: Exception) {
                adaptationText = "Could not fetch adaptation advice at this time."
            } finally {
                isAdaptationLoading = false
            }
        }
    }

    fun fetchWorkoutsHistory() {
        viewModelScope.launch {
            workoutError = null
            try {
                val history = api.getWorkouts()
                workoutsList = history
            } catch (e: Exception) {
                workoutError = e.message ?: "Failed to load workouts history"
            }
        }
    }

    fun logWorkout(exercise: String, sets: Int, reps: Int, weight: Float, onSuccess: () -> Unit) {
        viewModelScope.launch {
            workoutError = null
            isLoading = true
            try {
                val newWorkout = api.logWorkout(WorkoutLogRequest(exercise, sets, reps, weight))
                workoutsList = listOf(newWorkout) + workoutsList
                fetchDashboardData() // refresh stats
                onSuccess()
            } catch (e: Exception) {
                workoutError = e.message ?: "Failed to log workout"
            } finally {
                isLoading = false
            }
        }
    }

    fun logMeal(description: String, calories: Float, protein: Float, carbs: Float, fat: Float, onSuccess: () -> Unit) {
        viewModelScope.launch {
            mealError = null
            isLoading = true
            try {
                val newMeal = api.logMeal(MealLogRequest(description, calories, protein, carbs, fat))
                mealsList = listOf(newMeal) + mealsList
                fetchDashboardData() // refresh stats
                onSuccess()
            } catch (e: Exception) {
                mealError = e.message ?: "Failed to log meal"
            } finally {
                isLoading = false
            }
        }
    }

    fun fetchChatHistory() {
        viewModelScope.launch {
            try {
                val history = api.getChatHistory()
                if (history.isNotEmpty()) {
                    _chatMessages.value = history
                } else {
                    _chatMessages.value = listOf(
                        ChatMessage(
                            id = "welcome",
                            role = "model",
                            text = "Hello! I am your nutrition assistant. Tell me what you ate in plain language (e.g. 'I had 2 medium rotis and a bowl of yellow dal for lunch') and I'll calculate your macros using ICMR standards.",
                            timestamp = null
                        )
                    )
                }
            } catch (e: Exception) {
                // Keep empty or add error msg
            }
        }
    }

    fun sendChatMessage(text: String) {
        if (text.trim().isEmpty()) return
        val userMsg = ChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            role = "user",
            text = text,
            timestamp = null
        )
        _chatMessages.value = _chatMessages.value + userMsg
        isChatSending = true

        viewModelScope.launch {
            try {
                val res = api.chatMeal(MealChatRequest(text))
                _chatMessages.value = _chatMessages.value + res
                fetchDashboardData() // refresh stats because chat might log a meal
            } catch (e: Exception) {
                val errorMsg = ChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    role = "model",
                    text = e.message ?: "Error processing message. Please try again.",
                    timestamp = null
                )
                _chatMessages.value = _chatMessages.value + errorMsg
            } finally {
                isChatSending = false
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            try {
                api.clearChatHistory()
                _chatMessages.value = listOf(
                    ChatMessage(
                        id = "welcome",
                        role = "model",
                        text = "Hello! I am your nutrition assistant. Tell me what you ate in plain language (e.g. 'I had 2 medium rotis and a bowl of yellow dal for lunch') and I'll calculate your macros using ICMR standards.",
                        timestamp = null
                    )
                )
            } catch (e: Exception) {
                // handle
            }
        }
    }
}
