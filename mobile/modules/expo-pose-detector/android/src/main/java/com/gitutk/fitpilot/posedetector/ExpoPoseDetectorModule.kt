package com.gitutk.fitpilot.posedetector

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ExpoPoseDetectorModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ExpoPoseDetector")

    View(ExpoPoseDetectorView::class) {
      Events("onPoseUpdate")
      Prop("exerciseMode") { view: ExpoPoseDetectorView, mode: String ->
        view.setExerciseMode(mode)
      }
      Prop("isActive") { view: ExpoPoseDetectorView, active: Boolean ->
        view.setIsActive(active)
      }
    }
  }
}
