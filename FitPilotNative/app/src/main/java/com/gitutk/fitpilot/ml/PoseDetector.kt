package com.gitutk.fitpilot.ml

import android.graphics.Bitmap
import com.gitutk.fitpilot.data.Person

interface PoseDetector : AutoCloseable {
    fun estimatePoses(bitmap: Bitmap): List<Person>
    fun lastInferenceTimeNanos(): Long
}
