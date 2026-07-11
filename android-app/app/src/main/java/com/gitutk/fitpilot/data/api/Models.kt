package com.gitutk.fitpilot.data.api

import com.google.gson.annotations.SerializedName

// Auth
data class SignupRequest(
    val email: String,
    val password: String,
    @SerializedName("full_name") val fullName: String?
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class User(
    val id: Int,
    val email: String,
    @SerializedName("full_name") val fullName: String?
)

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    val user: User
)

// Workouts
data class WorkoutLogRequest(
    val exercise: String,
    val sets: Int,
    val reps: Int,
    val weight: Float
)

data class Workout(
    val id: Int,
    val exercise: String,
    val sets: Int,
    val reps: Int,
    val weight: Float,
    @SerializedName("duration_minutes") val durationMinutes: Int,
    @SerializedName("calories_burned") val caloriesBurned: Float,
    @SerializedName("intensity_score") val intensityScore: Float,
    val timestamp: String
)

data class WorkoutStats(
    @SerializedName("total_workouts") val totalWorkouts: Int,
    @SerializedName("total_calories") val totalCalories: Float,
    @SerializedName("total_sets") val totalSets: Int,
    @SerializedName("total_reps") val totalReps: Int,
    @SerializedName("average_intensity") val averageIntensity: Float
)

// Meals
data class MealLogRequest(
    val description: String,
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float
)

data class Meal(
    val id: Int,
    val description: String,
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val timestamp: String
)

data class MealChatRequest(
    val text: String
)

data class ChatMessage(
    val id: String?,
    val role: String, // "user" or "model"
    val text: String,
    val timestamp: String?
)

data class AdaptationResponse(
    val recommendation: String
)
