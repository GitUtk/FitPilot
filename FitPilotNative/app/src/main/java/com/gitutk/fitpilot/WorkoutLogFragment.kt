package com.gitutk.fitpilot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class WorkoutLogFragment : Fragment() {

    private lateinit var apiService: ApiService
    private lateinit var llHistoryList: LinearLayout
    private lateinit var pbHistory: ProgressBar
    private lateinit var tvEmptyHistory: TextView

    // Exercise details helper map
    private val exercises = listOf(
        ExerciseViewItem("squat", R.id.rlSquatHeader, R.id.llSquatDropdown, R.id.ivSquatChevron, R.id.etSquatSets, R.id.etSquatReps, R.id.etSquatWeight, R.id.btnSquatLog, R.id.btnSquatAI, "Squats"),
        ExerciseViewItem("curl", R.id.rlCurlHeader, R.id.llCurlDropdown, R.id.ivCurlChevron, R.id.etCurlSets, R.id.etCurlReps, R.id.etCurlWeight, R.id.btnCurlLog, R.id.btnCurlAI, "Bicep Curls"),
        ExerciseViewItem("pushup", R.id.rlPushupHeader, R.id.llPushupDropdown, R.id.ivPushupChevron, R.id.etPushupSets, R.id.etPushupReps, R.id.etPushupWeight, R.id.btnPushupLog, R.id.btnPushupAI, "Push-Ups"),
        ExerciseViewItem("lunge", R.id.rlLungeHeader, R.id.llLungeDropdown, R.id.ivLungeChevron, R.id.etLungeSets, R.id.etLungeReps, R.id.etLungeWeight, R.id.btnLungeLog, R.id.btnLungeAI, "Lunges"),
        ExerciseViewItem("press", R.id.rlPressHeader, R.id.llPressDropdown, R.id.ivPressChevron, R.id.etPressSets, R.id.etPressReps, R.id.etPressWeight, R.id.btnPressLog, R.id.btnPressAI, "Overhead Press")
    )

    data class ExerciseViewItem(
        val key: String,
        val headerId: Int,
        val dropdownId: Int,
        val chevronId: Int,
        val setsId: Int,
        val repsId: Int,
        val weightId: Int,
        val logBtnId: Int,
        val aiBtnId: Int?,
        val displayName: String
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_workout_log, container, false)

        apiService = (activity as MainActivity).apiService
        llHistoryList = view.findViewById(R.id.llHistoryList)
        pbHistory = view.findViewById(R.id.pbHistory)
        tvEmptyHistory = view.findViewById(R.id.tvEmptyHistory)

        setupExercises(view)
        fetchHistory()

        return view
    }

    private fun setupExercises(root: View) {
        for (item in exercises) {
            val header = root.findViewById<RelativeLayout>(item.headerId)
            val dropdown = root.findViewById<LinearLayout>(item.dropdownId)
            val chevron = root.findViewById<ImageView>(item.chevronId)
            val setsInput = root.findViewById<EditText>(item.setsId)
            val repsInput = root.findViewById<EditText>(item.repsId)
            val weightInput = root.findViewById<EditText>(item.weightId)
            val logBtn = root.findViewById<Button>(item.logBtnId)
            val aiBtn = item.aiBtnId?.let { root.findViewById<Button>(it) }

            // Click header to toggle dropdown panel
            header.setOnClickListener {
                if (dropdown.visibility == View.VISIBLE) {
                    dropdown.visibility = View.GONE
                    chevron.animate().rotation(0f).start()
                } else {
                    dropdown.visibility = View.VISIBLE
                    chevron.animate().rotation(180f).start()
                }
            }

            // Log set button
            logBtn.setOnClickListener {
                val setsStr = setsInput.text.toString()
                val repsStr = repsInput.text.toString()
                val weightStr = weightInput.text.toString()

                if (setsStr.isEmpty() || repsStr.isEmpty() || weightStr.isEmpty()) {
                    Toast.makeText(context, "Please fill in all set fields", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val sets = setsStr.toInt()
                val reps = repsStr.toInt()
                val weight = weightStr.toDouble()

                logBtn.isEnabled = false
                apiService.logWorkout(item.key, sets, reps, weight) { success, data, error ->
                    activity?.runOnUiThread {
                        logBtn.isEnabled = true
                        if (success) {
                            Toast.makeText(context, "${item.displayName} logged successfully!", Toast.LENGTH_SHORT).show()
                            fetchHistory()
                        } else {
                            Toast.makeText(context, error ?: "Failed to log workout", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

            // AI Camera Scan button click
            aiBtn?.setOnClickListener {
                // Navigate to real-time scanning
                val poseFragment = PoseFragment.newInstance(item.key)
                (activity as MainActivity).loadFragment(poseFragment, true)
            }
        }
    }

    private fun fetchHistory() {
        pbHistory.visibility = View.VISIBLE
        tvEmptyHistory.visibility = View.GONE
        llHistoryList.removeAllViews()

        apiService.getWorkouts { success, array, error ->
            activity?.runOnUiThread {
                pbHistory.visibility = View.GONE
                if (success && array != null && array.length() > 0) {
                    for (i in 0 until array.length()) {
                        val log = array.getJSONObject(i)
                        addHistoryRow(log)
                    }
                } else {
                    tvEmptyHistory.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun addHistoryRow(log: JSONObject) {
        val context = context ?: return
        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }
            cardElevation = 0f
            radius = 8f
            setStrokeColor(androidx.core.content.ContextCompat.getColorStateList(context, R.color.border))
            strokeWidth = 1
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
        }

        val exerciseKey = log.optString("exercise", "")
        val displayName = when (exerciseKey) {
            "squat" -> "Squats"
            "curl" -> "Bicep Curls"
            "pushup" -> "Push-Ups"
            "lunge" -> "Lunges"
            "press" -> "Overhead Press"
            else -> exerciseKey.replaceFirstChar { it.uppercase() }
        }

        val sets = log.optInt("sets", 0)
        val reps = log.optInt("reps", 0)
        val weight = log.optDouble("weight", 0.0)
        val dateStr = log.optString("created_at", "")

        val tvTitle = TextView(context).apply {
            text = displayName
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val tvDetail = TextView(context).apply {
            text = "$sets sets × $reps reps @ ${weight} kg"
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 4
            }
        }

        val tvDate = TextView(context).apply {
            text = formatDate(dateStr)
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 6
            }
        }

        row.addView(tvTitle)
        row.addView(tvDetail)
        row.addView(tvDate)
        card.addView(row)
        llHistoryList.addView(card, 0) // Prepend to see newest first
    }

    private fun formatDate(raw: String): String {
        return try {
            // Raw is like "2026-07-11T19:35:42.123456"
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
            val date = inputFormat.parse(raw)
            date?.let { outputFormat.format(it) } ?: raw
        } catch (e: Exception) {
            raw
        }
    }
}
