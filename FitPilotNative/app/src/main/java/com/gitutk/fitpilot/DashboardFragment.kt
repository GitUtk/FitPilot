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

    private fun refreshStats() {
        pbAdaptation.visibility = View.VISIBLE
        tvAdaptationText.text = "Syncing metabolic stats..."

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
                }
            }
        }

        // 3. Fetch AI adaptation advice (IST-filtered on backend)
        apiService.getAdaptationAdvice { success, data, error ->
            activity?.runOnUiThread {
                pbAdaptation.visibility = View.GONE
                if (success && data != null) {
                    val advice = data.optString("recommendation", "Continue your routine! Keep logging meals and workouts to get customized advice.")
                    
                    // Format advice tags to stand out with bold and primary/accent colors
                    var formattedAdvice = advice
                        .replace("[WORKOUT ADAPTATION]", "<b><font color='#3B82F6'>🏋️ WORKOUT ADAPTATION</font></b>")
                        .replace("[NUTRITION ADAPTATION]", "<br/><b><font color='#10B981'>🥗 NUTRITION ADAPTATION</font></b>")
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
