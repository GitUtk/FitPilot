package com.gitutk.fitpilot

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.text.style.LeadingMarginSpan

class MealLoggerFragment : Fragment() {

    private lateinit var apiService: ApiService
    
    // Chat views
    private lateinit var llChatMessages: LinearLayout
    private lateinit var etChatInput: EditText
    private lateinit var btnSendChat: ImageButton
    private lateinit var btnClearChat: ImageButton
    private lateinit var svChat: ScrollView

    // Thinking bubble variables
    private var thinkingBubbleView: View? = null
    private var thinkingRunnable: Runnable? = null
    private val thinkingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    
    // Skeleton loading variables
    private val skeletonAnimators = mutableListOf<android.animation.ValueAnimator>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_meal_logger, container, false)

        apiService = (activity as MainActivity).apiService

        // Bind Chat
        llChatMessages = view.findViewById(R.id.llChatMessages)
        etChatInput = view.findViewById(R.id.etChatInput)
        btnSendChat = view.findViewById(R.id.btnSendChat)
        btnClearChat = view.findViewById(R.id.btnClearChat)
        svChat = view.findViewById(R.id.svChat)

        setupChat()

        // Fetch chat history on start
        fetchChatHistory()

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        thinkingRunnable?.let {
            thinkingHandler.removeCallbacks(it)
        }
        clearSkeletonAnimators()
    }

    private fun showThinkingBubble() {
        val context = context ?: return
        val density = context.resources.displayMetrics.density
        
        val marginTopBottom = (8 * density).toInt()
        val marginOpposite = (64 * density).toInt()
        val marginSide = (8 * density).toInt()
        val cardRadius = 18 * density
        val strokePx = (1.5f * density).toInt()
        val padHorizontal = (16 * density).toInt()
        val padVertical = (12 * density).toInt()

        val bubble = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = marginTopBottom
                bottomMargin = marginTopBottom
            }
            gravity = Gravity.START
        }

        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = marginSide
                rightMargin = marginOpposite
            }
            cardElevation = 0f
            radius = cardRadius
            setContentPadding(padHorizontal, padVertical, padHorizontal, padVertical)
            setCardBackgroundColor(ContextCompat.getColorStateList(context, R.color.card_background))
            setStrokeColor(ContextCompat.getColorStateList(context, R.color.border))
            strokeWidth = strokePx
        }

        val tv = TextView(context).apply {
            text = "Thinking"
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.ITALIC)
        }

        card.addView(tv)
        bubble.addView(card)
        llChatMessages.addView(bubble)
        thinkingBubbleView = bubble
        
        // Animate dots
        var dotCount = 0
        thinkingRunnable = object : Runnable {
            override fun run() {
                dotCount = (dotCount + 1) % 4
                val dots = when (dotCount) {
                    1 -> "."
                    2 -> ".."
                    3 -> "..."
                    else -> ""
                }
                tv.text = "Thinking$dots"
                thinkingHandler.postDelayed(this, 500)
            }
        }
        thinkingHandler.post(thinkingRunnable!!)
    }

    private fun removeThinkingBubble() {
        thinkingRunnable?.let {
            thinkingHandler.removeCallbacks(it)
        }
        thinkingRunnable = null
        thinkingBubbleView?.let {
            llChatMessages.removeView(it)
        }
        thinkingBubbleView = null
    }

    private fun setupChat() {
        btnSendChat.setOnClickListener {
            val text = etChatInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            etChatInput.setText("")
            addMessageBubble(text, true)
            scrollToBottom()

            btnSendChat.isEnabled = false
            showThinkingBubble()
            scrollToBottom()

            viewLifecycleOwner.lifecycleScope.launch {
                val (success, data, error) = apiService.chatMeal(text)
                btnSendChat.isEnabled = true
                removeThinkingBubble()
                if (success && data != null) {
                    val reply = data.optString("text", "I've processed your request.")
                    addMessageBubble(reply, false)
                    
                    val foodItems = data.optJSONArray("logged_items")
                    if (foodItems != null && foodItems.length() > 0) {
                        Toast.makeText(context, "Logged ${foodItems.length()} items to metabolic log!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    addMessageBubble(error ?: "Failed to get advice from coach. Please try again.", false)
                }
                scrollToBottom()
            }
        }

        btnClearChat.setOnClickListener {
            btnClearChat.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val (success, error) = apiService.clearChatHistory()
                btnClearChat.isEnabled = true
                if (success) {
                    llChatMessages.removeAllViews()
                    showWelcomeMessage()
                    Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, error ?: "Failed to clear chat", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showWelcomeMessage() {
        val name = apiService.userName ?: "Pilot"
        val weight = apiService.userWeight
        val height = apiService.userHeight
        val gender = apiService.userGender ?: "Not specified"

        val heightM = height / 100.0
        val bmi = if (heightM > 0) weight / (heightM * heightM) else 0.0
        val bmiStr = if (bmi > 0) String.format(java.util.Locale.getDefault(), "%.1f", bmi) else "--"

        val welcomeText = "Hi $name! I'm your AI Coach. 🏋️‍♂️🥗\n\n" +
                "You can chat with me to get exercise tips, or to **automatically log meals**. For example, try saying:\n" +
                "* *\"I had a bowl of oats and 2 boiled eggs\"*\n" +
                "* *\"Suggest a quick leg workout\"*\n\n" +
                "Here are your current profile stats:\n" +
                "* **Gender**: $gender\n" +
                "* **Weight**: ${weight} kg\n" +
                "* **Height**: ${height} cm\n" +
                "* **BMI**: $bmiStr"

        addMessageBubble(welcomeText, false)
    }

    private fun showSkeletalLoading() {
        val context = context ?: return
        val density = context.resources.displayMetrics.density
        
        val marginTopBottom = (8 * density).toInt()
        val marginOpposite = (96 * density).toInt()
        val marginSide = (8 * density).toInt()
        val cardRadius = 18 * density
        val strokePx = (1.5f * density).toInt()
        val padHorizontal = (16 * density).toInt()
        val padVertical = (16 * density).toInt()

        val widths = listOf(
            listOf(0.7f, 0.4f),        // AI 1
            listOf(0.5f),              // User 1
            listOf(0.85f, 0.6f, 0.3f), // AI 2
            listOf(0.75f),             // User 2
            listOf(0.6f, 0.5f),        // AI 3
            listOf(0.4f)               // User 3
        )

        for (i in 0 until 6) {
            val isUserBubble = i % 2 != 0
            val bubble = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = marginTopBottom
                    bottomMargin = marginTopBottom
                }
                gravity = if (isUserBubble) Gravity.END else Gravity.START
            }

            val card = MaterialCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = if (isUserBubble) marginOpposite else marginSide
                    rightMargin = if (isUserBubble) marginSide else marginOpposite
                }
                cardElevation = 0f
                radius = cardRadius
                setContentPadding(padHorizontal, padVertical, padHorizontal, padVertical)
                
                if (isUserBubble) {
                    setCardBackgroundColor(ContextCompat.getColorStateList(context, R.color.primary))
                } else {
                    setCardBackgroundColor(ContextCompat.getColorStateList(context, R.color.card_background))
                    setStrokeColor(ContextCompat.getColorStateList(context, R.color.border))
                    strokeWidth = strokePx
                }
            }

            val barContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val bubbleWidths = widths[i]
            for (j in bubbleWidths.indices) {
                val relativeWidth = bubbleWidths[j]
                val bar = View(context).apply {
                    val shape = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 6 * density
                        val barColor = if (isUserBubble) "#334155" else "#E2E8F0"
                        setColor(android.graphics.Color.parseColor(barColor))
                    }
                    background = shape
                    
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        (14 * density).toInt()
                    ).apply {
                        weight = relativeWidth
                        topMargin = if (j > 0) (8 * density).toInt() else 0
                    }
                }
                
                val rowContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    weightSum = 1f
                }
                rowContainer.addView(bar)
                barContainer.addView(rowContainer)
            }

            card.addView(barContainer)
            bubble.addView(card)
            llChatMessages.addView(bubble)
            
            val animator = android.animation.ValueAnimator.ofFloat(0.4f, 1.0f).apply {
                duration = 800
                repeatMode = android.animation.ValueAnimator.REVERSE
                repeatCount = android.animation.ValueAnimator.INFINITE
                addUpdateListener { animation ->
                    val alphaVal = animation.animatedValue as Float
                    card.alpha = alphaVal
                }
                startDelay = (i * 150).toLong()
            }
            skeletonAnimators.add(animator)
            animator.start()
        }
    }

    private fun clearSkeletonAnimators() {
        for (animator in skeletonAnimators) {
            animator.cancel()
        }
        skeletonAnimators.clear()
    }

    private fun fetchChatHistory() {
        llChatMessages.removeAllViews()
        clearSkeletonAnimators()
        showSkeletalLoading()
        scrollToBottom()
        
        viewLifecycleOwner.lifecycleScope.launch {
            val (success, array, _) = apiService.getChatHistory()
            clearSkeletonAnimators()
            llChatMessages.removeAllViews()
            if (success && array != null && array.length() > 0) {
                for (i in 0 until array.length()) {
                    val msg = array.getJSONObject(i)
                    val role = msg.optString("role", "")
                    val text = msg.optString("text", "")
                    addMessageBubble(text, role == "user")
                }
            } else {
                showWelcomeMessage()
            }
            scrollToBottom()
        }
    }

    private fun addMessageBubble(text: String, isUser: Boolean) {
        val context = context ?: return
        val density = context.resources.displayMetrics.density
        
        val marginTopBottom = (8 * density).toInt()
        val marginOpposite = (64 * density).toInt()
        val marginSide = (8 * density).toInt()
        val cardRadius = 18 * density
        val strokePx = (1.5f * density).toInt()
        val padHorizontal = (16 * density).toInt()
        val padVertical = (12 * density).toInt()

        val bubble = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = marginTopBottom
                bottomMargin = marginTopBottom
            }
            gravity = if (isUser) Gravity.END else Gravity.START
        }

        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = if (isUser) marginOpposite else marginSide
                rightMargin = if (isUser) marginSide else marginOpposite
            }
            cardElevation = 0f
            radius = cardRadius
            setContentPadding(padHorizontal, padVertical, padHorizontal, padVertical)

            if (isUser) {
                setCardBackgroundColor(ContextCompat.getColorStateList(context, R.color.primary))
            } else {
                setCardBackgroundColor(ContextCompat.getColorStateList(context, R.color.card_background))
                setStrokeColor(ContextCompat.getColorStateList(context, R.color.border))
                strokeWidth = strokePx
            }
        }

        val tv = TextView(context).apply {
            this.text = if (isUser) text else parseMarkdown(text)
            setTextColor(ContextCompat.getColor(context, if (isUser) R.color.white else R.color.text_primary))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(4f, 1.2f)
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

    private fun parseMarkdown(text: String): SpannableStringBuilder {
        val lines = text.split("\n")
        val formattedText = StringBuilder()
        val bulletIntervals = mutableListOf<Pair<Int, Int>>()
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
                val bulletContent = trimmed.substring(2)
                val start = formattedText.length
                formattedText.append("•  ").append(bulletContent).append("\n")
                val end = formattedText.length - 1
                bulletIntervals.add(Pair(start, end))
            } else {
                formattedText.append(line).append("\n")
            }
        }
        if (formattedText.isNotEmpty()) {
            formattedText.setLength(formattedText.length - 1)
        }

        val ssb = SpannableStringBuilder(formattedText.toString())

        for (interval in bulletIntervals) {
            ssb.setSpan(LeadingMarginSpan.Standard(32, 32), interval.first, interval.second, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        var boldIndex = ssb.indexOf("**")
        while (boldIndex != -1) {
            val nextBoldIndex = ssb.indexOf("**", boldIndex + 2)
            if (nextBoldIndex != -1) {
                ssb.delete(nextBoldIndex, nextBoldIndex + 2)
                ssb.delete(boldIndex, boldIndex + 2)
                ssb.setSpan(
                    StyleSpan(Typeface.BOLD),
                    boldIndex,
                    nextBoldIndex - 2,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                boldIndex = ssb.indexOf("**", nextBoldIndex - 2)
            } else {
                break
            }
        }

        var italicIndex = ssb.indexOf("*")
        while (italicIndex != -1) {
            if (italicIndex + 1 < ssb.length && ssb[italicIndex + 1] == '*') {
                italicIndex = ssb.indexOf("*", italicIndex + 2)
                continue
            }
            val nextItalicIndex = ssb.indexOf("*", italicIndex + 1)
            if (nextItalicIndex != -1) {
                ssb.delete(nextItalicIndex, nextItalicIndex + 1)
                ssb.delete(italicIndex, italicIndex + 1)
                ssb.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    italicIndex,
                    nextItalicIndex - 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                italicIndex = ssb.indexOf("*", nextItalicIndex - 1)
            } else {
                break
            }
        }

        return ssb
    }
}
