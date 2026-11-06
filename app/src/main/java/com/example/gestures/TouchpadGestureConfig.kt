package com.example.gestures

enum class ThreeFingerShortcutAction(val label: String) {
  NONE("Disabled"),
  TASK_SWITCHER("Task Switcher"),
  SHOW_DESKTOP("Show Desktop"),
  PLAY_PAUSE("Media Play / Pause"),
  SCREENSHOT("Take Screenshot")
}

data class TouchpadGestureConfig(
  val sensitivity: Float = 1.4f,
  val pointerAcceleration: Float = 0.35f,
  val scrollSpeed: Float = 1.0f,
  val naturalScrolling: Boolean = true,
  val tapToClick: Boolean = true,
  val twoFingerRightClick: Boolean = true,
  val doubleTapAndHoldDrag: Boolean = true,
  val threeFingerSwipeUp: ThreeFingerShortcutAction = ThreeFingerShortcutAction.TASK_SWITCHER,
  val threeFingerSwipeDown: ThreeFingerShortcutAction = ThreeFingerShortcutAction.SHOW_DESKTOP
)
