package com.gitutk.fitpilot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment(), SensorEventListener {

    private lateinit var apiService: ApiService
    private lateinit var tvGreeting: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvStepCount: TextView
    private lateinit var tvStepCalories: TextView
    private lateinit var tvStepDistance: TextView
    private lateinit var tvStepTime: TextView
    private lateinit var stepProgressBar: ProgressBar
    private lateinit var tvAdaptationText: TextView
    private lateinit var pbAdaptation: ProgressBar
    
    private lateinit var tvWorkoutBurnVal: TextView
    private lateinit var tvWorkoutBurnSub: TextView
    private lateinit var tvFoodLoggedVal: TextView
    private lateinit var tvExertionRateVal: TextView
    private lateinit var tvProteinBalanceVal: TextView
    private lateinit var tvMacrosSub: TextView
    
    private lateinit var btnSync: Button
    private lateinit var btnLogout: ImageButton
    private lateinit var llSuggestionsContainer: android.widget.LinearLayout

    // Sensor state
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var initialStepCount = -1f
    private var currentSteps = 4120 // Fallback/Mock starting steps if no sensor

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)

        apiService = (activity as MainActivity).apiService

        // Bind Views
        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvDate = view.findViewById(R.id.tvDate)
        tvStepCount = view.findViewById(R.id.tvStepCount)
        tvStepCalories = view.findViewById(R.id.tvStepCalories)
        tvStepDistance = view.findViewById(R.id.tvStepDistance)
        tvStepTime = view.findViewById(R.id.tvStepTime)
        stepProgressBar = view.findViewById(R.id.stepProgressBar)
        tvAdaptationText = view.findViewById(R.id.tvAdaptationText)
        pbAdaptation = view.findViewById(R.id.pbAdaptation)
        
        tvWorkoutBurnVal = view.findViewById(R.id.tvWorkoutBurnVal)
        tvWorkoutBurnSub = view.findViewById(R.id.tvWorkoutBurnSub)
        tvFoodLoggedVal = view.findViewById(R.id.tvFoodLoggedVal)
        tvExertionRateVal = view.findViewById(R.id.tvExertionRateVal)
        tvProteinBalanceVal = view.findViewById(R.id.tvProteinBalanceVal)
        tvMacrosSub = view.findViewById(R.id.tvMacrosSub)
        
        btnSync = view.findViewById(R.id.btnSync)
        btnLogout = view.findViewById(R.id.btnLogout)
        llSuggestionsContainer = view.findViewById(R.id.llSuggestionsContainer)

        // Set Date
        val sdf = SimpleDateFormat("EEEE, MMM dd", Locale.getDefault())
        tvDate.text = sdf.format(Date())

        // Set Greeting Name
        tvGreeting.text = "Hello, ${apiService.userName}"

        // Button Listeners
        btnSync.setOnClickListener {
            refreshStats()
        }

        btnLogout.setOnClickListener {
            apiService.logout()
            (activity as MainActivity).showLogin()
        }

        setupSteps()
        refreshStats()
        populateFoodSuggestions()

        return view
    }

    private fun setupSteps() {
        // Initialize sensor
        sensorManager = activity?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepSensor != null) {
            // Check & request permission for Android Q+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACTIVITY_RECOGNITION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(
                        arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                        101
                    )
                } else {
                    registerSensor()
                }
            } else {
                registerSensor()
            }
        } else {
            // Fallback: update display with mock steps
            updateStepDisplay(currentSteps)
        }
    }

    private fun registerSensor() {
        stepSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun updateStepDisplay(steps: Int) {
        activity?.runOnUiThread {
            tvStepCount.text = String.format("%,d", steps)
            val pct = (steps * 100) / 8000
            stepProgressBar.progress = pct.coerceIn(0, 100)

            // Calculations
            val calories = (steps * 0.04).toInt()
            val distance = String.format(Locale.getDefault(), "%.1f km", (steps * 0.75) / 1000.0)
            val minutes = steps / 160

            tvStepCalories.text = "$calories kcal"
            tvStepDistance.text = distance
            tvStepTime.text = "$minutes mins"
        }
    }

    private fun refreshStats() {
        pbAdaptation.visibility = View.VISIBLE
        tvAdaptationText.text = "Syncing metabolic stats..."

        // 1. Fetch Workouts stats
        apiService.getWorkoutStats { success, data, error ->
            if (success && data != null) {
                activity?.runOnUiThread {
                    val burn = data.optDouble("total_calories", 0.0).toInt()
                    val mins = data.optDouble("total_duration", 0.0).toInt()
                    val exertion = data.optDouble("average_intensity", 0.0)

                    tvWorkoutBurnVal.text = "$burn kcal"
                    tvWorkoutBurnSub.text = "$mins mins training"
                    tvExertionRateVal.text = String.format(Locale.getDefault(), "%.1f", exertion)
                }
            }
        }

        // 2. Fetch Meals stats
        apiService.getMeals { success, mealsArray, error ->
            if (success && mealsArray != null) {
                activity?.runOnUiThread {
                    var totalCalories = 0.0
                    var totalProtein = 0.0
                    var totalCarbs = 0.0
                    var totalFat = 0.0

                    for (i in 0 until mealsArray.length()) {
                        val meal = mealsArray.getJSONObject(i)
                        totalCalories += meal.optDouble("calories", 0.0)
                        totalProtein += meal.optDouble("protein", 0.0)
                        totalCarbs += meal.optDouble("carbs", 0.0)
                        totalFat += meal.optDouble("fat", 0.0)
                    }

                    tvFoodLoggedVal.text = "${totalCalories.toInt()} kcal"
                    tvProteinBalanceVal.text = "${totalProtein.toInt()}g"
                    tvMacrosSub.text = "C: ${totalCarbs.toInt()}g • F: ${totalFat.toInt()}g"
                }
            }
        }

        // 3. Fetch AI adaptation advice
        apiService.getAdaptationAdvice { success, data, error ->
            activity?.runOnUiThread {
                pbAdaptation.visibility = View.GONE
                if (success && data != null) {
                    val advice = data.optString("recommendation", "Continue your routine! Keep logging meals and workouts to get customized advice.")
                    tvAdaptationText.text = advice
                } else {
                    tvAdaptationText.text = "Add some workout and food logs to trigger AI adaptation engine insights."
                }
            }
        }

        // Simulate step increase slightly on manual sync if no sensor
        if (stepSensor == null) {
            currentSteps += (120..350).random()
            updateStepDisplay(currentSteps)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceReboot = event.values[0]
            if (initialStepCount < 0) {
                initialStepCount = totalStepsSinceReboot
            }
            val stepsToday = (totalStepsSinceReboot - initialStepCount).toInt()
            updateStepDisplay(stepsToday)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        if (stepSensor != null) {
            registerSensor()
        }
    }

    override fun onPause() {
        super.onPause()
        if (stepSensor != null) {
            sensorManager?.unregisterListener(this)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            registerSensor()
        }
    }

    private data class FoodSuggestion(
        val name: String,
        val tag: String,
        val calories: String,
        val macros: String,
        val imageResId: Int
    )

    private fun populateFoodSuggestions() {
        val context = context ?: return
        llSuggestionsContainer.removeAllViews()

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val suggestions = when {
            hour in 5..11 -> listOf(
                FoodSuggestion("Moong Dal Chilla", "AI BREAKFAST PICK", "250 kcal", "P: 14g • C: 28g • F: 8g", R.drawable.moong_dal_chilla),
                FoodSuggestion("Paneer Bhurji", "HIGH PROTEIN MORNING", "320 kcal", "P: 18g • C: 6g • F: 22g", R.drawable.paneer_bhurji),
                FoodSuggestion("Egg Bhurji", "BALANCED LIFESTYLE", "290 kcal", "P: 20g • C: 4g • F: 21g", R.drawable.egg_bhurji)
            )
            hour in 12..17 -> listOf(
                FoodSuggestion("Paneer Bhurji", "AI LUNCH PICK", "320 kcal", "P: 18g • C: 6g • F: 22g", R.drawable.paneer_bhurji),
                FoodSuggestion("Moong Dal Chilla", "LIGHT & FIBER RICH", "250 kcal", "P: 14g • C: 28g • F: 8g", R.drawable.moong_dal_chilla),
                FoodSuggestion("Egg Bhurji", "LEAN PROTEIN BUILD", "290 kcal", "P: 20g • C: 4g • F: 21g", R.drawable.egg_bhurji)
            )
            else -> listOf(
                FoodSuggestion("Egg Bhurji", "AI DINNER PICK", "290 kcal", "P: 20g • C: 4g • F: 21g", R.drawable.egg_bhurji),
                FoodSuggestion("Paneer Bhurji", "POST-WORKOUT RECOVERY", "320 kcal", "P: 18g • C: 6g • F: 22g", R.drawable.paneer_bhurji),
                FoodSuggestion("Moong Dal Chilla", "EASY DIGESTION SNACK", "250 kcal", "P: 14g • C: 28g • F: 8g", R.drawable.moong_dal_chilla)
            )
        }

        val inflater = LayoutInflater.from(context)
        for (item in suggestions) {
            val cardView = inflater.inflate(R.layout.item_food_suggestion, llSuggestionsContainer, false)
            
            val ivFoodImage = cardView.findViewById<android.widget.ImageView>(R.id.ivFoodImage)
            val tvFoodTag = cardView.findViewById<TextView>(R.id.tvFoodTag)
            val tvFoodName = cardView.findViewById<TextView>(R.id.tvFoodName)
            val tvFoodCalories = cardView.findViewById<TextView>(R.id.tvFoodCalories)
            val tvFoodMacros = cardView.findViewById<TextView>(R.id.tvFoodMacros)

            ivFoodImage.setImageResource(item.imageResId)
            tvFoodTag.text = item.tag
            tvFoodName.text = item.name
            tvFoodCalories.text = item.calories
            tvFoodMacros.text = item.macros

            cardView.setOnClickListener {
                Toast.makeText(context, "Try logging '${item.name}' in the Food AI section!", Toast.LENGTH_LONG).show()
            }

            llSuggestionsContainer.addView(cardView)
        }
    }
}
