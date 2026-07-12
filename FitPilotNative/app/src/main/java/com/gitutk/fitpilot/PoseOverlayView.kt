package com.gitutk.fitpilot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.gitutk.fitpilot.data.BodyPart
import com.gitutk.fitpilot.data.Person

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var person: Person? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    // Red paint configurations
    private val paintCircle = Paint().apply {
        color = Color.parseColor("#FF0000")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val paintLine = Paint().apply {
        color = Color.parseColor("#FF0000")
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val paintText = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        style = Paint.Style.FILL
        isAntiAlias = true
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    fun setResults(person: Person?, imageWidth: Int, imageHeight: Int) {
        this.person = person
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    fun clear() {
        person = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val person = person ?: return
        if (imageWidth == 0 || imageHeight == 0) return

        // Scale factors to map model outputs to overlay size
        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight

        // Draw skeleton lines
        val bodyConnections = listOf(
            Pair(BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER),
            Pair(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_ELBOW),
            Pair(BodyPart.LEFT_ELBOW, BodyPart.LEFT_WRIST),
            Pair(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_ELBOW),
            Pair(BodyPart.RIGHT_ELBOW, BodyPart.RIGHT_WRIST),
            Pair(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_HIP),
            Pair(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_HIP),
            Pair(BodyPart.LEFT_HIP, BodyPart.RIGHT_HIP),
            Pair(BodyPart.LEFT_HIP, BodyPart.LEFT_KNEE),
            Pair(BodyPart.LEFT_KNEE, BodyPart.LEFT_ANKLE),
            Pair(BodyPart.RIGHT_HIP, BodyPart.RIGHT_KNEE),
            Pair(BodyPart.RIGHT_KNEE, BodyPart.RIGHT_ANKLE)
        )

        for (connection in bodyConnections) {
            val fromNode = person.keyPoints.find { it.bodyPart == connection.first }
            val toNode = person.keyPoints.find { it.bodyPart == connection.second }

            if (fromNode != null && toNode != null && fromNode.score > 0.2f && toNode.score > 0.2f) {
                val startX = fromNode.coordinate.x * scaleX
                val startY = fromNode.coordinate.y * scaleY
                val endX = toNode.coordinate.x * scaleX
                val endY = toNode.coordinate.y * scaleY
                canvas.drawLine(startX, startY, endX, endY, paintLine)
            }
        }

        // Draw joint points (excluding face joints)
        val faceParts = setOf(
            BodyPart.NOSE,
            BodyPart.LEFT_EYE,
            BodyPart.RIGHT_EYE,
            BodyPart.LEFT_EAR,
            BodyPart.RIGHT_EAR
        )
        for (keypoint in person.keyPoints) {
            if (keypoint.score > 0.2f && keypoint.bodyPart !in faceParts) {
                val x = keypoint.coordinate.x * scaleX
                val y = keypoint.coordinate.y * scaleY
                canvas.drawCircle(x, y, 16f, paintCircle)
            }
        }
    }
}
