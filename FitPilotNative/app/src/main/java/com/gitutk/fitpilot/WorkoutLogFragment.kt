package com.gitutk.fitpilot

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
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
    private lateinit var btnLogSet: Button
    private lateinit var btnStartAI: Button

    // Infographics bindings
    private lateinit var tvFormPercentage: TextView
    private lateinit var tvFocusLegs: TextView
    private lateinit var tvFocusArms: TextView
    private lateinit var tvFocusChest: TextView

    private lateinit var pbLegsFocus: ProgressBar
    private lateinit var pbArmsFocus: ProgressBar
    private lateinit var pbChestFocus: ProgressBar

    // Weekly bars
    private lateinit var pbMon: ProgressBar
    private lateinit var pbTue: ProgressBar
    private lateinit var pbWed: ProgressBar
    private lateinit var pbThu: ProgressBar
    private lateinit var pbFri: ProgressBar
    private lateinit var pbSat: ProgressBar
    private lateinit var pbSun: ProgressBar



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
        btnLogSet = view.findViewById(R.id.btnLogSet)
        btnStartAI = view.findViewById(R.id.btnStartAI)

        tvFormPercentage = view.findViewById(R.id.tvFormPercentage)
        tvFocusLegs = view.findViewById(R.id.tvFocusLegs)
        tvFocusArms = view.findViewById(R.id.tvFocusArms)
        tvFocusChest = view.findViewById(R.id.tvFocusChest)

        pbMon = view.findViewById(R.id.pbMon)
        pbTue = view.findViewById(R.id.pbTue)
        pbWed = view.findViewById(R.id.pbWed)
        pbThu = view.findViewById(R.id.pbThu)
        pbFri = view.findViewById(R.id.pbFri)
        pbSat = view.findViewById(R.id.pbSat)
        pbSun = view.findViewById(R.id.pbSun)

        // Dynamically find muscle progress bars in visual hierarchy
        pbLegsFocus = view.findViewById(R.id.pbLegsFocus)
        pbArmsFocus = view.findViewById(R.id.pbArmsFocus)
        pbChestFocus = view.findViewById(R.id.pbChestFocus)
        


        pbHistory = view.findViewById(R.id.pbHistory)

        // Setup horizontal items listeners
        setupHorizontalSelection()

        // Default selection
        selectExercise("squat")

        // Load dynamic infographics from recent history
        fetchHistoryAndCalculateStats()

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
                etWeight.isEnabled = true
                tvRepsLabel.text = "Reps"
                tvWeightLabel.text = "Weight (kg)"
            }
            "curl" -> {
                etSets.setText("3")
                etReps.setText("12")
                etWeight.setText("15")
                etWeight.isEnabled = true
                tvRepsLabel.text = "Reps"
                tvWeightLabel.text = "Weight (kg)"
            }
            "pushup" -> {
                etSets.setText("3")
                etReps.setText("15")
                etWeight.setText("0")
                etWeight.isEnabled = false
                tvRepsLabel.text = "Reps"
                tvWeightLabel.text = "Weight (Bodyweight)"
            }
            "lunge" -> {
                etSets.setText("3")
                etReps.setText("12")
                etWeight.setText("20")
                etWeight.isEnabled = true
                tvRepsLabel.text = "Reps"
                tvWeightLabel.text = "Weight (kg)"
            }
            "press" -> {
                etSets.setText("3")
                etReps.setText("10")
                etWeight.setText("30")
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

        if (setsStr.isEmpty() || repsStr.isEmpty() || weightStr.isEmpty()) {
            Toast.makeText(context, "Please configure all set values", Toast.LENGTH_SHORT).show()
            return
        }

        val sets = setsStr.toInt()
        val reps = repsStr.toInt()
        val weight = weightStr.toDouble()

        btnLogSet.isEnabled = false
        apiService.logWorkout(selectedExerciseKey, sets, reps, weight) { success, _, error ->
            activity?.runOnUiThread {
                btnLogSet.isEnabled = true
                if (success) {
                    val displayName = selectedExerciseKey.replaceFirstChar { it.uppercase() }
                    Toast.makeText(context, "$displayName set logged!", Toast.LENGTH_SHORT).show()
                    fetchHistoryAndCalculateStats()
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

    private fun fetchHistoryAndCalculateStats() {
        pbHistory.visibility = View.VISIBLE

        apiService.getWorkouts { success, array, _ ->
            activity?.runOnUiThread {
                pbHistory.visibility = View.GONE
                if (success && array != null) {
                    calculateAndPopulateStats(array)
                } else {
                    // Populate default/empty stats representation
                    calculateAndPopulateStats(JSONArray())
                }
            }
        }
    }

    private fun calculateAndPopulateStats(array: JSONArray) {
        var totalVolume = 0.0
        var totalSets = 0
        var totalReps = 0
        val totalSessions = array.length()

        // Muscle distribution sets counts
        var legsSets = 0
        var armsSets = 0
        var chestSets = 0

        // Weekly activity volume distribution (Mon-Sun)
        val weeklyVolume = DoubleArray(7)

        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val cal = Calendar.getInstance()

        for (i in 0 until array.length()) {
            val log = array.getJSONObject(i)
            val exercise = log.optString("exercise", "")
            val sets = log.optInt("sets", 0)
            val reps = log.optInt("reps", 0)
            val weight = log.optDouble("weight", 0.0)
            val dateStr = log.optString("created_at", "")

            // Sum standard metrics
            totalSets += sets
            totalReps += reps
            totalVolume += (sets * reps * weight)

            // Categorize muscle focus by sets count
            when (exercise) {
                "squat", "lunge" -> legsSets += sets
                "curl", "press" -> armsSets += sets
                "pushup" -> chestSets += sets
            }

            // Calculate weekly day distribution
            try {
                val date = parser.parse(dateStr)
                if (date != null) {
                    cal.time = date
                    // Calendar.DAY_OF_WEEK: Sunday = 1, Monday = 2, ..., Saturday = 7
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                    // Map to Mon=0, Tue=1, Wed=2, Thu=3, Fri=4, Sat=5, Sun=6
                    val dayIndex = when (dayOfWeek) {
                        Calendar.MONDAY -> 0
                        Calendar.TUESDAY -> 1
                        Calendar.WEDNESDAY -> 2
                        Calendar.THURSDAY -> 3
                        Calendar.FRIDAY -> 4
                        Calendar.SATURDAY -> 5
                        Calendar.SUNDAY -> 6
                        else -> 0
                    }
                    weeklyVolume[dayIndex] += (sets * reps * weight)
                }
            } catch (e: Exception) {
                // Ignore parsing issues
            }
        }



        // 2. Update Muscle Focus Percentages
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
            // Default split if no logs exist yet
            tvFocusLegs.text = "45%"
            tvFocusArms.text = "35%"
            tvFocusChest.text = "20%"

            pbLegsFocus.progress = 45
            pbArmsFocus.progress = 35
            pbChestFocus.progress = 20
        }

        // 3. Update Form Quality rating
        if (totalSessions > 0) {
            tvFormPercentage.text = "94%"
        } else {
            tvFormPercentage.text = "--"
        }

        // 4. Update Weekly load distribution bars
        var maxVol = 0.0
        for (vol in weeklyVolume) {
            if (vol > maxVol) maxVol = vol
        }

        val bars = arrayOf(pbMon, pbTue, pbWed, pbThu, pbFri, pbSat, pbSun)
        for (day in 0..6) {
            val vol = weeklyVolume[day]
            val pct = if (maxVol > 0) ((vol * 100) / maxVol).toInt() else 0
            bars[day].progress = if (pct > 5) pct else 5 // keep a minimal visual dot if 0
        }
    }
}
