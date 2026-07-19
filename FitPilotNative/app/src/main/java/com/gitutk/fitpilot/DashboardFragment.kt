package com.gitutk.fitpilot

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DashboardFragment : Fragment() {

    private lateinit var apiService: ApiService
    private lateinit var tvGreeting: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvAdaptationText: TextView
    private lateinit var llAdaptationSkeleton: android.widget.LinearLayout
    private var adaptationAnimator: android.animation.ValueAnimator? = null
    
    private lateinit var tvWorkoutBurnVal: TextView
    private lateinit var tvWorkoutBurnSub: TextView
    private lateinit var tvFoodLoggedVal: TextView
    private lateinit var tvExertionRateVal: TextView
    private lateinit var tvProteinBalanceVal: TextView
    private lateinit var tvMacrosSub: TextView
    
    private lateinit var tvRecoveryScore: TextView
    private lateinit var tvRecoveryStatus: TextView
    private lateinit var tvRecoveryStatusSub: TextView
    
    private lateinit var btnSync: Button
    private lateinit var btnLogout: ImageButton
    private lateinit var llSuggestionsContainer: android.widget.LinearLayout

    // Shared metrics for dynamic recovery calculation
    private var todayBurn: Double = 0.0
    private var todayExertion: Double = 0.0
    private var todayProtein: Double = 0.0

    // IST timezone
    private val istTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)

        apiService = (activity as MainActivity).apiService

        // Bind Views
        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvDate = view.findViewById(R.id.tvDate)
        tvAdaptationText = view.findViewById(R.id.tvAdaptationText)
        llAdaptationSkeleton = view.findViewById(R.id.llAdaptationSkeleton)
        
        tvWorkoutBurnVal = view.findViewById(R.id.tvWorkoutBurnVal)
        tvWorkoutBurnSub = view.findViewById(R.id.tvWorkoutBurnSub)
        tvFoodLoggedVal = view.findViewById(R.id.tvFoodLoggedVal)
        tvExertionRateVal = view.findViewById(R.id.tvExertionRateVal)
        tvProteinBalanceVal = view.findViewById(R.id.tvProteinBalanceVal)
        tvMacrosSub = view.findViewById(R.id.tvMacrosSub)
        
        btnSync = view.findViewById(R.id.btnSync)
        btnLogout = view.findViewById(R.id.btnLogout)
        llSuggestionsContainer = view.findViewById(R.id.llSuggestionsContainer)

        tvRecoveryScore = view.findViewById(R.id.tvRecoveryScore)
        tvRecoveryStatus = view.findViewById(R.id.tvRecoveryStatus)
        tvRecoveryStatusSub = view.findViewById(R.id.tvRecoveryStatusSub)

        // Set Date in IST
        val sdf = SimpleDateFormat("EEEE, MMM dd", Locale.getDefault())
        sdf.timeZone = istTimeZone
        tvDate.text = sdf.format(Date())

        // Set Greeting Name using IST hour
        val istCalendar = Calendar.getInstance(istTimeZone)
        val hour = istCalendar.get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour in 5..11 -> "Good Morning"
            hour in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
        tvGreeting.text = "$greeting, ${apiService.userName}"

        // Button Listeners
        btnSync.setOnClickListener {
            refreshStats()
        }

        btnLogout.setOnClickListener {
            apiService.logout()
            (activity as MainActivity).showLogin()
        }

        refreshStats()
        populateFoodSuggestions()

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adaptationAnimator?.cancel()
        adaptationAnimator = null
    }

    private fun startAdaptationSkeletonAnimation() {
        llAdaptationSkeleton.visibility = View.VISIBLE
        tvAdaptationText.visibility = View.GONE
        
        adaptationAnimator?.cancel()
        adaptationAnimator = android.animation.ValueAnimator.ofFloat(0.4f, 1.0f).apply {
            duration = 800
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { animation ->
                val alphaVal = animation.animatedValue as Float
                llAdaptationSkeleton.alpha = alphaVal
            }
        }
        adaptationAnimator?.start()
    }

    private fun stopAdaptationSkeletonAnimation() {
        adaptationAnimator?.cancel()
        adaptationAnimator = null
        llAdaptationSkeleton.visibility = View.GONE
        tvAdaptationText.visibility = View.VISIBLE
    }

    private fun refreshStats() {
        startAdaptationSkeletonAnimation()

        // 1. Fetch today's workout stats (IST-filtered on backend)
        apiService.getWorkoutStats { success, data, error ->
            if (success && data != null) {
                activity?.runOnUiThread {
                    val burn = data.optDouble("total_calories", 0.0).toInt()
                    val mins = data.optInt("total_duration", 0)
                    val exertion = data.optDouble("average_intensity", 0.0)

                    tvWorkoutBurnVal.text = "$burn kcal"
                    tvWorkoutBurnSub.text = "$mins mins training"
                    tvExertionRateVal.text = String.format(Locale.getDefault(), "%.1f", exertion)

                    todayBurn = burn.toDouble()
                    todayExertion = exertion
                    calculateAndDisplayRecovery()
                }
            }
        }

        // 2. Fetch today's meals (IST-filtered on backend)
        apiService.getMealsToday { success, mealsArray, error ->
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

                    todayProtein = totalProtein
                    calculateAndDisplayRecovery()
                }
            }
        }

        // 3. Fetch AI adaptation advice (IST-filtered on backend)
        apiService.getAdaptationAdvice { success, data, error ->
            activity?.runOnUiThread {
                stopAdaptationSkeletonAnimation()
                if (success && data != null) {
                    val advice = data.optString("recommendation", "Continue your routine! Keep logging meals and workouts to get customized advice.")
                    
                    // Format advice tags to stand out with bold black typography (no colors or emojis)
                    var formattedAdvice = advice
                        .replace("[WORKOUT ADAPTATION]", "<b>WORKOUT ADAPTATION</b>")
                        .replace("[NUTRITION ADAPTATION]", "<br/><b>NUTRITION ADAPTATION</b>")
                        .replace("Energy Demand", "<b>Energy Demand</b>")
                        .replace("Enery Demand", "<b>Enery Demand</b>")
                        .replace("Protein Shortfall", "<b>Protein Shortfall</b>")
                        .replace("Lipids Level", "<b>Lipids Level</b>")
                        .replace("Carbohydrate Needs", "<b>Carbohydrate Needs</b>")
                        .replace("• ", "<br/>• ")
                    
                    formattedAdvice = formattedAdvice.replace("<br/><br/>", "<br/>").trim()
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        tvAdaptationText.text = android.text.Html.fromHtml(formattedAdvice, android.text.Html.FROM_HTML_MODE_LEGACY)
                    } else {
                        tvAdaptationText.text = android.text.Html.fromHtml(formattedAdvice)
                    }
                } else {
                    tvAdaptationText.text = "Add some workout and food logs to trigger AI adaptation engine insights."
                }
            }
        }
    }

    private fun calculateAndDisplayRecovery() {
        val userWeight = apiService.userWeight.toDouble()
        val targetProtein = 1.6 * userWeight
        val proteinDeficit = if (todayProtein < targetProtein) targetProtein - todayProtein else 0.0
        
        // Base sleep requirement is 7.5 hours
        // Physical exertion & calorie burn increases physical fatigue (adds up to 1.8 hours sleep need)
        val exertionFactor = todayExertion.coerceIn(0.0, 10.0)
        val workoutSleepBonus = (exertionFactor * 0.12) + (todayBurn / 450.0)
        
        // Slower muscle repair due to protein shortfall increases sleep recovery need (adds up to 0.7 hours sleep need)
        val proteinSleepBonus = if (proteinDeficit > 0) (proteinDeficit / targetProtein) * 0.7 else 0.0
        
        val totalSleepHours = (7.5 + workoutSleepBonus + proteinSleepBonus).coerceIn(7.0, 9.8)
        
        // Calculate dynamic recovery percentage
        val recoveryScoreVal = (95 - (exertionFactor * 4.2) - (todayBurn / 22.0) - (proteinSleepBonus * 8.0)).toInt().coerceIn(35, 98)
        
        activity?.runOnUiThread {
            tvRecoveryScore.text = "$recoveryScoreVal%"
            
            when {
                recoveryScoreVal >= 80 -> {
                    tvRecoveryStatus.text = "Optimal Recovery"
                    tvRecoveryStatusSub.text = String.format(Locale.getDefault(), "Ready for training • %.1fh sleep recommended", totalSleepHours)
                }
                recoveryScoreVal >= 60 -> {
                    tvRecoveryStatus.text = "Moderate Fatigue"
                    tvRecoveryStatusSub.text = String.format(Locale.getDefault(), "Active recovery recommended • %.1fh sleep needed", totalSleepHours)
                }
                else -> {
                    tvRecoveryStatus.text = "High Fatigue / Rest Day"
                    tvRecoveryStatusSub.text = String.format(Locale.getDefault(), "Muscles rebuilding • %.1fh sleep required", totalSleepHours)
                }
            }
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

        val istCalendar = Calendar.getInstance(istTimeZone)
        val hour = istCalendar.get(Calendar.HOUR_OF_DAY)
        val suggestions = when {
            hour in 5..11 -> listOf(
                FoodSuggestion("Moong Dal Chilla", "AI BREAKFAST PICK", "250 kcal", "P: 14g • C: 28g • F: 8g", R.drawable.moong_dal_chilla),
                FoodSuggestion("Eggs/Egg Scramble", "HIGH PROTEIN MORNING", "220 kcal", "P: 18g • C: 2g • F: 15g", R.drawable.egg_scramble),
                FoodSuggestion("Bread Peanut Butter", "BALANCED LIFESTYLE", "290 kcal", "P: 12g • C: 24g • F: 16g", R.drawable.bread_peanut_butter)
            )
            hour in 12..17 -> listOf(
                FoodSuggestion("Eggs/Egg Scramble", "AI LUNCH PICK", "220 kcal", "P: 18g • C: 2g • F: 15g", R.drawable.egg_scramble),
                FoodSuggestion("Moong Dal Chilla", "LIGHT & FIBER RICH", "250 kcal", "P: 14g • C: 28g • F: 8g", R.drawable.moong_dal_chilla),
                FoodSuggestion("Bread Peanut Butter", "LEAN PROTEIN BUILD", "290 kcal", "P: 12g • C: 24g • F: 16g", R.drawable.bread_peanut_butter)
            )
            else -> listOf(
                FoodSuggestion("Bread Peanut Butter", "AI DINNER PICK", "290 kcal", "P: 12g • C: 24g • F: 16g", R.drawable.bread_peanut_butter),
                FoodSuggestion("Eggs/Egg Scramble", "POST-WORKOUT RECOVERY", "220 kcal", "P: 18g • C: 2g • F: 15g", R.drawable.egg_scramble),
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
