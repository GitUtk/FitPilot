package com.gitutk.fitpilot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class WorkoutLogFragment : Fragment() {

    private lateinit var apiService: ApiService
    private var selectedExerciseKey = "squat"

    // Selection layout bindings
    private lateinit var llItemSquat: LinearLayout
    private lateinit var llItemCurl: LinearLayout
    private lateinit var llItemPushup: LinearLayout
    private lateinit var llItemLunge: LinearLayout
    private lateinit var llItemPress: LinearLayout

    private lateinit var tvItemSquat: TextView
    private lateinit var tvItemCurl: TextView
    private lateinit var tvItemPushup: TextView
    private lateinit var tvItemLunge: TextView
    private lateinit var tvItemPress: TextView

    // Control panel bindings
    private lateinit var tvSelectedExerciseTitle: TextView
    private lateinit var tvRepsLabel: TextView
    private lateinit var tvWeightLabel: TextView
    private lateinit var etSets: EditText
    private lateinit var etReps: EditText
    private lateinit var etWeight: EditText
    private lateinit var llWeightContainer: LinearLayout
    private lateinit var btnLogSet: Button
    private lateinit var btnStartAI: Button

    // Infographics bindings
    private lateinit var tvFocusLegs: TextView
    private lateinit var tvFocusArms: TextView
    private lateinit var tvFocusChest: TextView

    private lateinit var pbLegsFocus: ProgressBar
    private lateinit var pbArmsFocus: ProgressBar
    private lateinit var pbChestFocus: ProgressBar

    // Today's Exercise List
    private lateinit var llTodayExercisesContainer: LinearLayout

    // Today's Workout Stats
    private lateinit var tvTodayCalories: TextView
    private lateinit var tvTodayDuration: TextView
    private lateinit var tvTodaySetsReps: TextView
    private lateinit var tvTodayIntensity: TextView

    private lateinit var pbHistory: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_workout_log, container, false)

        apiService = (activity as MainActivity).apiService

        // Bind layouts
        llItemSquat = view.findViewById(R.id.llItemSquat)
        llItemCurl = view.findViewById(R.id.llItemCurl)
        llItemPushup = view.findViewById(R.id.llItemPushup)
        llItemLunge = view.findViewById(R.id.llItemLunge)
        llItemPress = view.findViewById(R.id.llItemPress)

        tvItemSquat = view.findViewById(R.id.tvItemSquat)
        tvItemCurl = view.findViewById(R.id.tvItemCurl)
        tvItemPushup = view.findViewById(R.id.tvItemPushup)
        tvItemLunge = view.findViewById(R.id.tvItemLunge)
        tvItemPress = view.findViewById(R.id.tvItemPress)

        tvSelectedExerciseTitle = view.findViewById(R.id.tvSelectedExerciseTitle)
        tvRepsLabel = view.findViewById(R.id.tvRepsLabel)
        tvWeightLabel = view.findViewById(R.id.tvWeightLabel)
        etSets = view.findViewById(R.id.etSets)
        etReps = view.findViewById(R.id.etReps)
        etWeight = view.findViewById(R.id.etWeight)
        llWeightContainer = view.findViewById(R.id.llWeightContainer)
        btnLogSet = view.findViewById(R.id.btnLogSet)
        btnStartAI = view.findViewById(R.id.btnStartAI)

        tvFocusLegs = view.findViewById(R.id.tvFocusLegs)
        tvFocusArms = view.findViewById(R.id.tvFocusArms)
        tvFocusChest = view.findViewById(R.id.tvFocusChest)

        llTodayExercisesContainer = view.findViewById(R.id.llTodayExercisesContainer)

        // Dynamically find muscle progress bars in visual hierarchy
        pbLegsFocus = view.findViewById(R.id.pbLegsFocus)
        pbArmsFocus = view.findViewById(R.id.pbArmsFocus)
        pbChestFocus = view.findViewById(R.id.pbChestFocus)

        // Today's Workout Stats
        tvTodayCalories = view.findViewById(R.id.tvTodayCalories)
        tvTodayDuration = view.findViewById(R.id.tvTodayDuration)
        tvTodaySetsReps = view.findViewById(R.id.tvTodaySetsReps)
        tvTodayIntensity = view.findViewById(R.id.tvTodayIntensity)

        pbHistory = view.findViewById(R.id.pbHistory)

        // Setup horizontal items listeners
        setupHorizontalSelection()

        // Default selection
        selectExercise("squat")

        // Load today's workout stats from API
        fetchTodayStats()

        // Bind actions
        btnLogSet.setOnClickListener { logCurrentSet() }
        btnStartAI.setOnClickListener { startAICamera() }

        return view
    }

    private fun setupHorizontalSelection() {
        val clickListener = View.OnClickListener { v ->
            val key = when (v.id) {
                R.id.llItemSquat -> "squat"
                R.id.llItemCurl -> "curl"
                R.id.llItemPushup -> "pushup"
                R.id.llItemLunge -> "lunge"
                R.id.llItemPress -> "press"
                else -> "squat"
            }
            selectExercise(key)
        }

        llItemSquat.setOnClickListener(clickListener)
        llItemCurl.setOnClickListener(clickListener)
        llItemPushup.setOnClickListener(clickListener)
        llItemLunge.setOnClickListener(clickListener)
        llItemPress.setOnClickListener(clickListener)
    }

    private fun selectExercise(key: String) {
        selectedExerciseKey = key

        // Reset all item text states
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        val activeColor = ContextCompat.getColor(requireContext(), R.color.text_primary)

        tvItemSquat.setTextColor(inactiveColor)
        tvItemSquat.setTypeface(null, android.graphics.Typeface.NORMAL)
        tvItemCurl.setTextColor(inactiveColor)
        tvItemCurl.setTypeface(null, android.graphics.Typeface.NORMAL)
        tvItemPushup.setTextColor(inactiveColor)
        tvItemPushup.setTypeface(null, android.graphics.Typeface.NORMAL)
        tvItemLunge.setTextColor(inactiveColor)
        tvItemLunge.setTypeface(null, android.graphics.Typeface.NORMAL)
        tvItemPress.setTextColor(inactiveColor)
        tvItemPress.setTypeface(null, android.graphics.Typeface.NORMAL)

        // Highlight selected
        val (selectedText, displayName) = when (key) {
            "squat" -> Pair(tvItemSquat, "Squats")
            "curl" -> Pair(tvItemCurl, "Bicep Curls")
            "pushup" -> Pair(tvItemPushup, "Push-Ups")
            "lunge" -> Pair(tvItemLunge, "Lunges")
            "press" -> Pair(tvItemPress, "Overhead Press")
            else -> Pair(tvItemSquat, "Squats")
        }

        selectedText.setTextColor(activeColor)
        selectedText.setTypeface(null, android.graphics.Typeface.BOLD)

        tvSelectedExerciseTitle.text = "$displayName Configuration"

        // Set inputs default configuration
        when (key) {
            "squat" -> {
                etSets.setText("3")
                etReps.setText("10")
                etWeight.setText("40")
                llWeightContainer.visibility = View.VISIBLE
                etWeight.isEnabled = true
                tvRepsLabel.text = "Reps"
                tvWeightLabel.text = "Weight (kg)"
            }
            "curl" -> {
                etSets.setText("3")
                etReps.setText("12")
                etWeight.setText("15")
                llWeightContainer.visibility = View.VISIBLE
                etWeight.isEnabled = true
                tvRepsLabel.text = "Reps"
                tvWeightLabel.text = "Weight (kg)"
            }
            "pushup" -> {
                etSets.setText("3")
                etReps.setText("15")
                etWeight.setText("0")
                llWeightContainer.visibility = View.GONE
                etWeight.isEnabled = false
                tvRepsLabel.text = "Reps"
                tvWeightLabel.text = ""
            }
            "lunge" -> {
                etSets.setText("3")
                etReps.setText("12")
                etWeight.setText("20")
                llWeightContainer.visibility = View.VISIBLE
                etWeight.isEnabled = true
                tvRepsLabel.text = "Reps"
                tvWeightLabel.text = "Weight (kg)"
            }
            "press" -> {
                etSets.setText("3")
                etReps.setText("10")
                etWeight.setText("30")
                llWeightContainer.visibility = View.VISIBLE
                etWeight.isEnabled = true
                tvRepsLabel.text = "Reps"
                tvWeightLabel.text = "Weight (kg)"
            }
        }
    }

    private fun logCurrentSet() {
        val setsStr = etSets.text.toString()
        val repsStr = etReps.text.toString()
        val weightStr = etWeight.text.toString()

        if (setsStr.isEmpty() || repsStr.isEmpty() || (selectedExerciseKey != "pushup" && weightStr.isEmpty())) {
            Toast.makeText(context, "Please configure all set values", Toast.LENGTH_SHORT).show()
            return
        }

        val sets = setsStr.toInt()
        val reps = repsStr.toInt()
        val weight = if (selectedExerciseKey == "pushup") 0.0 else weightStr.toDouble()

        btnLogSet.isEnabled = false
        apiService.logWorkout(selectedExerciseKey, sets, reps, weight) { success, _, error ->
            activity?.runOnUiThread {
                btnLogSet.isEnabled = true
                if (success) {
                    val displayName = selectedExerciseKey.replaceFirstChar { it.uppercase() }
                    Toast.makeText(context, "$displayName set logged!", Toast.LENGTH_SHORT).show()
                    fetchTodayStats()
                } else {
                    Toast.makeText(context, error ?: "Failed to log set", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startAICamera() {
        val poseFragment = PoseFragment.newInstance(selectedExerciseKey)
        (activity as MainActivity).loadFragment(poseFragment, true)
    }

    private fun fetchTodayStats() {
        pbHistory.visibility = View.VISIBLE

        apiService.getWorkoutStats { success, data, _ ->
            activity?.runOnUiThread {
                pbHistory.visibility = View.GONE

                // Clear and recreate today's exercises container header
                llTodayExercisesContainer.removeAllViews()
                val headerTv = TextView(context).apply {
                    text = "Logged Exercises Today"
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    textSize = 11f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (12 * resources.displayMetrics.density).toInt()
                    }
                    layoutParams = params
                }
                llTodayExercisesContainer.addView(headerTv)

                if (success && data != null) {
                    val calories = data.optDouble("total_calories", 0.0)
                    val duration = data.optInt("total_duration", 0)
                    val sets = data.optInt("total_sets", 0)
                    val reps = data.optInt("total_reps", 0)
                    val intensity = data.optDouble("average_intensity", 0.0)

                    // Today's Workout card
                    tvTodayCalories.text = "${calories.toInt()} kcal"
                    tvTodayDuration.text = "$duration min"
                    tvTodaySetsReps.text = "$sets / $reps"
                    tvTodayIntensity.text = String.format(Locale.getDefault(), "%.1f", intensity)

                    // Muscle Focus from exercise breakdown
                    val breakdown = data.optJSONObject("exercise_breakdown")
                    var legsSets = 0
                    var armsSets = 0
                    var chestSets = 0

                    if (breakdown != null && breakdown.length() > 0) {
                        val keys = breakdown.keys()
                        while (keys.hasNext()) {
                            val ex = keys.next()
                            val item = breakdown.optJSONObject(ex)
                            if (item != null) {
                                val exSets = item.optInt("sets", 0)
                                val exReps = item.optInt("reps", 0)
                                when (ex) {
                                    "squat", "lunge" -> legsSets += exSets
                                    "curl", "press" -> armsSets += exSets
                                    "pushup" -> chestSets += exSets
                                }

                                // Create dynamic row layout for each exercise
                                val rowLayout = LinearLayout(context).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        bottomMargin = (8 * resources.displayMetrics.density).toInt()
                                    }
                                }

                                val displayName = when (ex.lowercase(Locale.getDefault())) {
                                    "squat" -> "Squats"
                                    "curl" -> "Bicep Curls"
                                    "pushup" -> "Push-Ups"
                                    "lunge" -> "Lunges"
                                    "press" -> "Overhead Press"
                                    else -> ex.replaceFirstChar { it.uppercase() }
                                }

                                val tvName = TextView(context).apply {
                                    text = displayName
                                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                                    textSize = 13f
                                    layoutParams = LinearLayout.LayoutParams(
                                        0,
                                        LinearLayout.LayoutParams.WRAP_CONTENT,
                                        1f
                                    )
                                }

                                val tvDetail = TextView(context).apply {
                                    text = "$exSets Sets / $exReps Reps"
                                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                                    textSize = 13f
                                    setTypeface(null, android.graphics.Typeface.BOLD)
                                }

                                rowLayout.addView(tvName)
                                rowLayout.addView(tvDetail)
                                llTodayExercisesContainer.addView(rowLayout)
                            }
                        }
                    } else {
                        val tvEmpty = TextView(context).apply {
                            text = "No exercises logged today"
                            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                            textSize = 13f
                            setTypeface(null, android.graphics.Typeface.ITALIC)
                        }
                        llTodayExercisesContainer.addView(tvEmpty)
                    }

                    val totalMuscleSets = legsSets + armsSets + chestSets
                    if (totalMuscleSets > 0) {
                        val legsPct = (legsSets * 100) / totalMuscleSets
                        val armsPct = (armsSets * 100) / totalMuscleSets
                        val chestPct = (chestSets * 100) / totalMuscleSets

                        tvFocusLegs.text = "$legsPct%"
                        tvFocusArms.text = "$armsPct%"
                        tvFocusChest.text = "$chestPct%"

                        pbLegsFocus.progress = legsPct
                        pbArmsFocus.progress = armsPct
                        pbChestFocus.progress = chestPct
                    } else {
                        tvFocusLegs.text = "0%"
                        tvFocusArms.text = "0%"
                        tvFocusChest.text = "0%"

                        pbLegsFocus.progress = 0
                        pbArmsFocus.progress = 0
                        pbChestFocus.progress = 0
                    }
                } else {
                    // No data / error — reset everything
                    tvTodayCalories.text = "0 kcal"
                    tvTodayDuration.text = "0 min"
                    tvTodaySetsReps.text = "0 / 0"
                    tvTodayIntensity.text = "0.0"

                    tvFocusLegs.text = "0%"
                    tvFocusArms.text = "0%"
                    tvFocusChest.text = "0%"
                    pbLegsFocus.progress = 0
                    pbArmsFocus.progress = 0
                    pbChestFocus.progress = 0

                    val tvEmpty = TextView(context).apply {
                        text = "No exercises logged today"
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        textSize = 13f
                        setTypeface(null, android.graphics.Typeface.ITALIC)
                    }
                    llTodayExercisesContainer.addView(tvEmpty)
                }
            }
        }
    }
}
