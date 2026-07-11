package com.gitutk.fitpilot.data.api

import com.gitutk.fitpilot.data.session.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface FitPilotApi {

    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("auth/me")
    suspend fun getMe(): User

    // Workouts
    @POST("workouts/")
    suspend fun logWorkout(@Body request: WorkoutLogRequest): Workout

    @GET("workouts/")
    suspend fun getWorkouts(): List<Workout>

    @GET("workouts/stats")
    suspend fun getWorkoutStats(): WorkoutStats

    // Meals
    @POST("meals/")
    suspend fun logMeal(@Body request: MealLogRequest): Meal

    @GET("meals/")
    suspend fun getMeals(): List<Meal>

    @POST("meals/chat")
    suspend fun chatMeal(@Body request: MealChatRequest): ChatMessage

    @GET("meals/chat")
    suspend fun getChatHistory(): List<ChatMessage>

    @DELETE("meals/chat")
    suspend fun clearChatHistory(): Map<String, Any>

    @GET("meals/adaptation")
    suspend fun getAdaptationAdvice(): AdaptationResponse

    companion object {
        private const val BASE_URL = "https://fitpilot-dips.onrender.com/api/v1/"

        fun create(sessionManager: SessionManager): FitPilotApi {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val requestBuilder = original.newBuilder()
                    
                    sessionManager.getAuthToken()?.let { token ->
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                    }
                    
                    chain.proceed(requestBuilder.build())
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FitPilotApi::class.java)
        }
    }
}
