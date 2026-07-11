package com.gitutk.fitpilot.data

import android.graphics.RectF

data class Person(
    var id: Int = -1, // default id for single pose
    val keyPoints: List<KeyPoint>,
    val boundingBox: RectF? = null, // bounding box of person
    val score: Float
)
