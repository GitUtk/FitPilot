package com.gitutk.fitpilot

import android.content.Context
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

    fun login(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        val json = JSONObject().apply {
            put("email", email)
            put("password", password)
        }
        val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = buildRequest("$BASE_URL/auth/login", "POST", body)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, e.localizedMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        val errorJson = response.body?.string()
                        val msg = try { JSONObject(errorJson).getString("detail") } catch(e: Exception) { "Login failed" }
                        callback(false, msg)
                        return
                    }

                    val data = JSONObject(response.body?.string() ?: "")
                    val accessToken = data.getString("access_token")
                    token = accessToken

                    // Fetch user info immediately after login to store user name
                    getMe { success, error ->
                        callback(true, null)
                    }
                }
            }
        })
    }

    fun signup(email: String, password: String, fullName: String, weightKg: Double, heightCm: Double, gender: String, callback: (Boolean, String?) -> Unit) {
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

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, e.localizedMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        val errorJson = response.body?.string()
                        val msg = try { JSONObject(errorJson).getString("detail") } catch(e: Exception) { "Signup failed" }
                        callback(false, msg)
                        return
                    }
                    callback(true, null)
                }
            }
        })
    }

    fun getMe(callback: (Boolean, String?) -> Unit) {
        val request = buildRequest("$BASE_URL/auth/me", "GET")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, e.localizedMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(false, "Failed to fetch profile")
                        return
                    }

                    val data = JSONObject(response.body?.string() ?: "")
                    userName = data.optString("full_name", "Pilot")
                    userEmail = data.optString("email", "")
                    userWeight = data.optDouble("weight_kg", 70.0).toFloat()
                    userHeight = data.optDouble("height_cm", 170.0).toFloat()
                    userGender = data.optString("gender", "")
                    callback(true, null)
                }
            }
        })
    }

    fun logWorkout(exercise: String, sets: Int, reps: Int, weight: Double, callback: (Boolean, JSONObject?, String?) -> Unit) {
        val json = JSONObject().apply {
            put("exercise", exercise)
            put("sets", sets)
            put("reps", reps)
            put("weight", weight)
        }
        val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = buildRequest("$BASE_URL/workouts/", "POST", body)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null, e.localizedMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(false, null, "Failed to log workout")
                        return
                    }
                    val data = JSONObject(response.body?.string() ?: "")
                    callback(true, data, null)
                }
            }
        })
    }

    fun getWorkouts(callback: (Boolean, JSONArray?, String?) -> Unit) {
        val request = buildRequest("$BASE_URL/workouts/", "GET")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null, e.localizedMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(false, null, "Failed to fetch workouts")
                        return
                    }
                    val data = JSONArray(response.body?.string() ?: "[]")
                    callback(true, data, null)
                }
            }
        })
    }

    fun getWorkoutStats(callback: (Boolean, JSONObject?, String?) -> Unit) {
        val request = buildRequest("$BASE_URL/workouts/stats", "GET")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null, e.localizedMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(false, null, "Failed to fetch workout stats")
                        return
                    }
                    val data = JSONObject(response.body?.string() ?: "")
                    callback(true, data, null)
                }
            }
        })
    }

    fun chatMeal(text: String, callback: (Boolean, JSONObject?, String?) -> Unit) {
        val json = JSONObject().apply {
            put("text", text)
        }
        val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = buildRequest("$BASE_URL/meals/chat", "POST", body)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null, e.localizedMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(false, null, "Failed to process chat")
                        return
                    }
                    val data = JSONObject(response.body?.string() ?: "")
                    callback(true, data, null)
                }
            }
        })
    }

    fun getChatHistory(callback: (Boolean, JSONArray?, String?) -> Unit) {
        val request = buildRequest("$BASE_URL/meals/chat", "GET")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null, e.localizedMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(false, null, "Failed to fetch chat history")
                        return
                    }
                    val data = JSONArray(response.body?.string() ?: "[]")
                    callback(true, data, null)
                }
            }
        })
    }

    fun clearChatHistory(callback: (Boolean, String?) -> Unit) {
        val request = buildRequest("$BASE_URL/meals/chat", "DELETE")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, e.localizedMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(false, "Failed to clear chat history")
                        return
                    }
                    callback(true, null)
                }
            }
        })
    }

    fun logMeal(description: String, calories: Double, protein: Double, carbs: Double, fat: Double, callback: (Boolean, JSONObject?, String?) -> Unit) {
        val json = JSONObject().apply {
            put("description", description)
            put("calories", calories)
            put("protein", protein)
            put("carbs", carbs)
            put("fat", fat)
        }
        val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = buildRequest("$BASE_URL/meals/", "POST", body)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null, e.localizedMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(false, null, "Failed to log meal")
                        return
                    }
                    val data = JSONObject(response.body?.string() ?: "")
                    callback(true, data, null)
                }
            }
        })
    }

    fun getMeals(callback: (Boolean, JSONArray?, String?) -> Unit) {
        val request = buildRequest("$BASE_URL/meals/", "GET")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null, e.localizedMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(false, null, "Failed to fetch meals")
                        return
                    }
                    val data = JSONArray(response.body?.string() ?: "[]")
                    callback(true, data, null)
                }
            }
        })
    }

    fun getMealsToday(callback: (Boolean, JSONArray?, String?) -> Unit) {
        val request = buildRequest("$BASE_URL/meals/today", "GET")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null, e.localizedMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(false, null, "Failed to fetch today's meals")
                        return
                    }
                    val data = JSONArray(response.body?.string() ?: "[]")
                    callback(true, data, null)
                }
            }
        })
    }

    fun getAdaptationAdvice(callback: (Boolean, JSONObject?, String?) -> Unit) {
        val request = buildRequest("$BASE_URL/meals/adaptation", "GET")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null, e.localizedMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(false, null, "Failed to fetch adaptation advice")
                        return
                    }
                    val data = JSONObject(response.body?.string() ?: "")
                    callback(true, data, null)
                }
            }
        })
    }
}
