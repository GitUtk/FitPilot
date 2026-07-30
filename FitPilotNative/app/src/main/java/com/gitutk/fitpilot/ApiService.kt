package com.gitutk.fitpilot

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ApiService(context: Context) {
    private val client = OkHttpClient()
    private val prefs = context.getSharedPreferences("fitpilot_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val BASE_URL = "https://fit-pilot-backend.vercel.app/api/v1"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    var token: String?
        get() = prefs.getString("token", null)
        set(value) {
            prefs.edit().putString("token", value).apply()
        }

    var userName: String?
        get() = prefs.getString("user_name", "Pilot")
        set(value) {
            prefs.edit().putString("user_name", value).apply()
        }

    var userEmail: String?
        get() = prefs.getString("user_email", "")
        set(value) {
            prefs.edit().putString("user_email", value).apply()
        }

    var userWeight: Float
        get() = prefs.getFloat("user_weight", 70f)
        set(value) {
            prefs.edit().putFloat("user_weight", value).apply()
        }

    var userHeight: Float
        get() = prefs.getFloat("user_height", 170f)
        set(value) {
            prefs.edit().putFloat("user_height", value).apply()
        }

    var userGender: String?
        get() = prefs.getString("user_gender", "")
        set(value) {
            prefs.edit().putString("user_gender", value).apply()
        }

    fun logout() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean {
        return token != null
    }

    private fun buildRequest(url: String, method: String, body: RequestBody? = null): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
        
        token?.let {
            builder.header("Authorization", "Bearer $it")
        }

        when (method) {
            "GET" -> builder.get()
            "POST" -> body?.let { builder.post(it) }
            "DELETE" -> builder.delete()
        }

        return builder.build()
    }

    suspend fun login(email: String, password: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("email", email)
            put("password", password)
        }
        val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = buildRequest("$BASE_URL/auth/login", "POST", body)

        try {
            val response = client.newCall(request).execute()
            response.use {
                if (!response.isSuccessful) {
                    val errorJson = response.body?.string()
                    val msg = try { JSONObject(errorJson).getString("detail") } catch(e: Exception) { "Login failed" }
                    return@withContext Pair(false, msg)
                }

                val data = JSONObject(response.body?.string() ?: "")
                val accessToken = data.getString("access_token")
                token = accessToken

                // Fetch user info immediately after login to store user name
                getMe()
                Pair(true, null)
            }
        } catch (e: IOException) {
            Pair(false, e.localizedMessage)
        }
    }

    suspend fun signup(email: String, password: String, fullName: String, weightKg: Double, heightCm: Double, gender: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("email", email)
            put("password", password)
            put("full_name", fullName.ifEmpty { JSONObject.NULL })
            put("weight_kg", weightKg)
            put("height_cm", heightCm)
            put("gender", gender)
        }
        val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = buildRequest("$BASE_URL/auth/signup", "POST", body)

        try {
            val response = client.newCall(request).execute()
            response.use {
                if (!response.isSuccessful) {
                    val errorJson = response.body?.string()
                    val msg = try { JSONObject(errorJson).getString("detail") } catch(e: Exception) { "Signup failed" }
                    return@withContext Pair(false, msg)
                }
                Pair(true, null)
            }
        } catch (e: IOException) {
            Pair(false, e.localizedMessage)
        }
    }

    suspend fun getMe(): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val request = buildRequest("$BASE_URL/auth/me", "GET")

        try {
            val response = client.newCall(request).execute()
            response.use {
                if (!response.isSuccessful) {
                    return@withContext Pair(false, "Failed to fetch profile")
                }

                val data = JSONObject(response.body?.string() ?: "")
                userName = data.optString("full_name", "Pilot")
                userEmail = data.optString("email", "")
                userWeight = data.optDouble("weight_kg", 70.0).toFloat()
                userHeight = data.optDouble("height_cm", 170.0).toFloat()
                userGender = data.optString("gender", "")
                Pair(true, null)
            }
        } catch (e: IOException) {
            Pair(false, e.localizedMessage)
        }
    }

    suspend fun logWorkout(exercise: String, sets: Int, reps: Int, weight: Double): Triple<Boolean, JSONObject?, String?> = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("exercise", exercise)
            put("sets", sets)
            put("reps", reps)
            put("weight", weight)
        }
        val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = buildRequest("$BASE_URL/workouts/", "POST", body)

        try {
            val response = client.newCall(request).execute()
            response.use {
                if (!response.isSuccessful) {
                    return@withContext Triple(false, null, "Failed to log workout")
                }
                val data = JSONObject(response.body?.string() ?: "")
                Triple(true, data, null)
            }
        } catch (e: IOException) {
            Triple(false, null, e.localizedMessage)
        }
    }

    suspend fun getWorkouts(): Triple<Boolean, JSONArray?, String?> = withContext(Dispatchers.IO) {
        val request = buildRequest("$BASE_URL/workouts/", "GET")

        try {
            val response = client.newCall(request).execute()
            response.use {
                if (!response.isSuccessful) {
                    return@withContext Triple(false, null, "Failed to fetch workouts")
                }
                val data = JSONArray(response.body?.string() ?: "[]")
                Triple(true, data, null)
            }
        } catch (e: IOException) {
            Triple(false, null, e.localizedMessage)
        }
    }

    suspend fun getWorkoutStats(): Triple<Boolean, JSONObject?, String?> = withContext(Dispatchers.IO) {
        val request = buildRequest("$BASE_URL/workouts/stats", "GET")

        try {
            val response = client.newCall(request).execute()
            response.use {
                if (!response.isSuccessful) {
                    return@withContext Triple(false, null, "Failed to fetch workout stats")
                }
                val data = JSONObject(response.body?.string() ?: "")
                Triple(true, data, null)
            }
        } catch (e: IOException) {
            Triple(false, null, e.localizedMessage)
        }
    }

    suspend fun chatMeal(text: String): Triple<Boolean, JSONObject?, String?> = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("text", text)
        }
        val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = buildRequest("$BASE_URL/meals/chat", "POST", body)

        try {
            val response = client.newCall(request).execute()
            response.use {
                if (!response.isSuccessful) {
                    return@withContext Triple(false, null, "Failed to process chat")
                }
                val data = JSONObject(response.body?.string() ?: "")
                Triple(true, data, null)
            }
        } catch (e: IOException) {
            Triple(false, null, e.localizedMessage)
        }
    }

    suspend fun getChatHistory(): Triple<Boolean, JSONArray?, String?> = withContext(Dispatchers.IO) {
        val request = buildRequest("$BASE_URL/meals/chat", "GET")

        try {
            val response = client.newCall(request).execute()
            response.use {
                if (!response.isSuccessful) {
                    return@withContext Triple(false, null, "Failed to fetch chat history")
                }
                val data = JSONArray(response.body?.string() ?: "[]")
                Triple(true, data, null)
            }
        } catch (e: IOException) {
            Triple(false, null, e.localizedMessage)
        }
    }

    suspend fun clearChatHistory(): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val request = buildRequest("$BASE_URL/meals/chat", "DELETE")

        try {
            val response = client.newCall(request).execute()
            response.use {
                if (!response.isSuccessful) {
                    return@withContext Pair(false, "Failed to clear chat history")
                }
                Pair(true, null)
            }
        } catch (e: IOException) {
            Pair(false, e.localizedMessage)
        }
    }

    suspend fun logMeal(description: String, calories: Double, protein: Double, carbs: Double, fat: Double): Triple<Boolean, JSONObject?, String?> = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("description", description)
            put("calories", calories)
            put("protein", protein)
            put("carbs", carbs)
            put("fat", fat)
        }
        val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = buildRequest("$BASE_URL/meals/", "POST", body)

        try {
            val response = client.newCall(request).execute()
            response.use {
                if (!response.isSuccessful) {
                    return@withContext Triple(false, null, "Failed to log meal")
                }
                val data = JSONObject(response.body?.string() ?: "")
                Triple(true, data, null)
            }
        } catch (e: IOException) {
            Triple(false, null, e.localizedMessage)
        }
    }

    suspend fun getMeals(): Triple<Boolean, JSONArray?, String?> = withContext(Dispatchers.IO) {
        val request = buildRequest("$BASE_URL/meals/", "GET")

        try {
            val response = client.newCall(request).execute()
            response.use {
                if (!response.isSuccessful) {
                    return@withContext Triple(false, null, "Failed to fetch meals")
                }
                val data = JSONArray(response.body?.string() ?: "[]")
                Triple(true, data, null)
            }
        } catch (e: IOException) {
            Triple(false, null, e.localizedMessage)
        }
    }

    suspend fun getMealsToday(): Triple<Boolean, JSONArray?, String?> = withContext(Dispatchers.IO) {
        val request = buildRequest("$BASE_URL/meals/today", "GET")

        try {
            val response = client.newCall(request).execute()
            response.use {
                if (!response.isSuccessful) {
                    return@withContext Triple(false, null, "Failed to fetch today's meals")
                }
                val data = JSONArray(response.body?.string() ?: "[]")
                Triple(true, data, null)
            }
        } catch (e: IOException) {
            Triple(false, null, e.localizedMessage)
        }
    }

    suspend fun getAdaptationAdvice(): Triple<Boolean, JSONObject?, String?> = withContext(Dispatchers.IO) {
        val request = buildRequest("$BASE_URL/meals/adaptation", "GET")

        try {
            val response = client.newCall(request).execute()
            response.use {
                if (!response.isSuccessful) {
                    return@withContext Triple(false, null, "Failed to fetch adaptation advice")
                }
                val data = JSONObject(response.body?.string() ?: "")
                Triple(true, data, null)
            }
        } catch (e: IOException) {
            Triple(false, null, e.localizedMessage)
        }
    }
}
