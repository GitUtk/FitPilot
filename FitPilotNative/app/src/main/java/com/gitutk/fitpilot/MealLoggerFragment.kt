package com.gitutk.fitpilot

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject

class MealLoggerFragment : Fragment() {

    private lateinit var apiService: ApiService
    
    // Tab toggles
    private lateinit var btnTabChat: Button
    private lateinit var btnTabQuickLog: Button
    private lateinit var rlChatContainer: RelativeLayout
    private lateinit var svQuickLog: ScrollView

    // Chat views
    private lateinit var llChatMessages: LinearLayout
    private lateinit var etChatInput: EditText
    private lateinit var btnSendChat: ImageButton
    private lateinit var btnClearChat: ImageButton
    private lateinit var svChat: ScrollView

    // Quick Log views
    private lateinit var etMealDesc: EditText
    private lateinit var etMealCalories: EditText
    private lateinit var etMealProtein: EditText
    private lateinit var etMealCarbs: EditText
    private lateinit var etMealFat: EditText
    private lateinit var btnQuickLogSubmit: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_meal_logger, container, false)

        apiService = (activity as MainActivity).apiService

        // Bind layouts
        btnTabChat = view.findViewById(R.id.btnTabChat)
        btnTabQuickLog = view.findViewById(R.id.btnTabQuickLog)
        rlChatContainer = view.findViewById(R.id.rlChatContainer)
        svQuickLog = view.findViewById(R.id.svQuickLog)

        // Bind Chat
        llChatMessages = view.findViewById(R.id.llChatMessages)
        etChatInput = view.findViewById(R.id.etChatInput)
        btnSendChat = view.findViewById(R.id.btnSendChat)
        btnClearChat = view.findViewById(R.id.btnClearChat)
        svChat = view.findViewById(R.id.svChat)

        // Bind Quick Log
        etMealDesc = view.findViewById(R.id.etMealDesc)
        etMealCalories = view.findViewById(R.id.etMealCalories)
        etMealProtein = view.findViewById(R.id.etMealProtein)
        etMealCarbs = view.findViewById(R.id.etMealCarbs)
        etMealFat = view.findViewById(R.id.etMealFat)
        btnQuickLogSubmit = view.findViewById(R.id.btnQuickLogSubmit)

        setupTabs()
        setupChat()
        setupQuickLog()

        // Fetch chat history on start
        fetchChatHistory()

        return view
    }

    private fun setupTabs() {
        btnTabChat.setOnClickListener {
            // Activate Chat tab
            btnTabChat.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            btnTabQuickLog.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            rlChatContainer.visibility = View.VISIBLE
            svQuickLog.visibility = View.GONE
        }

        btnTabQuickLog.setOnClickListener {
            // Activate Quick Log tab
            btnTabQuickLog.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            btnTabChat.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            svQuickLog.visibility = View.VISIBLE
            rlChatContainer.visibility = View.GONE
        }
    }

    private fun setupChat() {
        btnSendChat.setOnClickListener {
            val text = etChatInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            etChatInput.setText("")
            addMessageBubble(text, true)
            scrollToBottom()

            btnSendChat.isEnabled = false
            apiService.chatMeal(text) { success, data, error ->
                activity?.runOnUiThread {
                    btnSendChat.isEnabled = true
                    if (success && data != null) {
                        val reply = data.optString("text", "I've logged that for you.")
                        addMessageBubble(reply, false)
                        
                        // If food items were parsed, let the user know by a toast
                        val foodItems = data.optJSONArray("logged_items")
                        if (foodItems != null && foodItems.length() > 0) {
                            Toast.makeText(context, "Logged ${foodItems.length()} items to metabolic log!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        addMessageBubble(error ?: "Failed to log meal via chat. Please try again.", false)
                    }
                    scrollToBottom()
                }
            }
        }

        btnClearChat.setOnClickListener {
            btnClearChat.isEnabled = false
            apiService.clearChatHistory { success, error ->
                activity?.runOnUiThread {
                    btnClearChat.isEnabled = true
                    if (success) {
                        llChatMessages.removeAllViews()
                        Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, error ?: "Failed to clear chat", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun fetchChatHistory() {
        llChatMessages.removeAllViews()
        apiService.getChatHistory { success, array, error ->
            if (success && array != null) {
                activity?.runOnUiThread {
                    for (i in 0 until array.length()) {
                        val msg = array.getJSONObject(i)
                        val role = msg.optString("role", "")
                        val text = msg.optString("text", "")
                        addMessageBubble(text, role == "user")
                    }
                    scrollToBottom()
                }
            }
        }
    }

    private fun addMessageBubble(text: String, isUser: Boolean) {
        val context = context ?: return
        val bubble = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 6
                bottomMargin = 6
            }
            gravity = if (isUser) Gravity.END else Gravity.START
        }

        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = if (isUser) 56 else 0
                rightMargin = if (isUser) 0 else 56
            }
            cardElevation = 0f
            radius = 16f
            setContentPadding(16, 12, 16, 12)

            if (isUser) {
                setCardBackgroundColor(ContextCompat.getColorStateList(context, R.color.primary))
            } else {
                setCardBackgroundColor(ContextCompat.getColorStateList(context, R.color.card_background))
                setStrokeColor(ContextCompat.getColorStateList(context, R.color.border))
                strokeWidth = 2
            }
        }

        val tv = TextView(context).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(context, if (isUser) R.color.white else R.color.text_primary))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(2f, 1.1f)
        }

        card.addView(tv)
        bubble.addView(card)
        llChatMessages.addView(bubble)
    }

    private fun scrollToBottom() {
        svChat.post {
            svChat.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun setupQuickLog() {
        btnQuickLogSubmit.setOnClickListener {
            val desc = etMealDesc.text.toString().trim()
            val calStr = etMealCalories.text.toString().trim()
            val protStr = etMealProtein.text.toString().trim()
            val carbStr = etMealCarbs.text.toString().trim()
            val fatStr = etMealFat.text.toString().trim()

            if (desc.isEmpty() || calStr.isEmpty()) {
                Toast.makeText(context, "Please fill in description and calories", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val calories = calStr.toDouble()
            val protein = if (protStr.isEmpty()) 0.0 else protStr.toDouble()
            val carbs = if (carbStr.isEmpty()) 0.0 else carbStr.toDouble()
            val fat = if (fatStr.isEmpty()) 0.0 else fatStr.toDouble()

            btnQuickLogSubmit.isEnabled = false
            apiService.logMeal(desc, calories, protein, carbs, fat) { success, data, error ->
                activity?.runOnUiThread {
                    btnQuickLogSubmit.isEnabled = true
                    if (success) {
                        Toast.makeText(context, "Meal logged successfully!", Toast.LENGTH_SHORT).show()
                        
                        // Reset forms
                        etMealDesc.setText("")
                        etMealCalories.setText("")
                        etMealProtein.setText("")
                        etMealCarbs.setText("")
                        etMealFat.setText("")
                        
                        // Switch to Chat tab to see dashboard updates
                        btnTabChat.performClick()
                    } else {
                        Toast.makeText(context, error ?: "Failed to log meal", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
