package com.example.gestures

import com.example.input.MouseButton
import com.example.input.RemoteCommand
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure, deterministic touchpad gesture interpreter.
 * Translates raw multi-touch pointer events into transport-independent [RemoteCommand]s.
 *
 * Designed for:
 * - Zero gesture conflicts between tap, drag, scroll, pinch, and 3-finger swipes
 * - Sub-pixel residual accumulation so slow precision motion is smooth
 * - Guaranteed button release on cancellation or disconnect
 */
class TouchpadGestureEngine(
  var config: TouchpadGestureConfig = TouchpadGestureConfig(),
  private val doubleTapWindowMs: Long = 260L,
  private val longPressDragMs: Long = 380L,
  private val tapSlopPx: Float = 14f,
  private val threeFingerSwipeThresholdPx: Float = 75f
) {

  sealed interface GestureResult {
    val commands: List<RemoteCommand>
    val triggerHaptic: Boolean
  }

  data class Output(
    override val commands: List<RemoteCommand> = emptyList(),
    override val triggerHaptic: Boolean = false,
    val isDragging: Boolean = false,
    val activeGestureLabel: String? = null
  ) : GestureResult

  private enum class ActiveMode {
    IDLE,
    ONE_FINGER_DOWN,
    MOVING,
    DRAGGING,
    TWO_FINGER_DOWN,
    SCROLLING_OR_PINCH,
    THREE_FINGER_SWIPE
  }

  private var mode: ActiveMode = ActiveMode.IDLE
  private var primaryStartTimeMs: Long = 0L
  private var primaryStartX: Float = 0f
  private var primaryStartY: Float = 0f
  private var lastPrimaryX: Float = 0f
  private var lastPrimaryY: Float = 0f

  private var twoFingerStartTimeMs: Long = 0L
  private var maxPointerCountInSequence: Int = 0
  private var lastTwoFingerCenterX: Float = 0f
  private var lastTwoFingerCenterY: Float = 0f
  private var lastTwoFingerDistance: Float = 0f

  private var threeFingerStartY: Float = 0f
  private var threeFingerTriggered: Boolean = false

  // Double-tap tracking
  private var lastSingleTapReleaseMs: Long = 0L
  private var awaitingSecondTap: Boolean = false

  // Sub-pixel residual accumulators
  private var residualX: Float = 0f
  private var residualY: Float = 0f
  private var residualScrollX: Float = 0f
  private var residualScrollY: Float = 0f

  val isDragging: Boolean
    get() = mode == ActiveMode.DRAGGING

  fun onTouchStart(
    pointerCount: Int,
    x: Float,
    y: Float,
    secondX: Float = 0f,
    secondY: Float = 0f,
    timeMs: Long
  ): Output {
    maxPointerCountInSequence = maxOf(maxPointerCountInSequence, pointerCount)
    val commands = mutableListOf<RemoteCommand>()
    var haptic = false

    when (pointerCount) {
      1 -> {
        residualX = 0f
        residualY = 0f
        primaryStartTimeMs = timeMs
        primaryStartX = x
        primaryStartY = y
        lastPrimaryX = x
        lastPrimaryY = y

        val withinDoubleTap = awaitingSecondTap && (timeMs - lastSingleTapReleaseMs) <= doubleTapWindowMs
        if (withinDoubleTap && config.doubleTapAndHoldDrag) {
          mode = ActiveMode.ONE_FINGER_DOWN
        } else {
          awaitingSecondTap = false
          mode = ActiveMode.ONE_FINGER_DOWN
        }
      }
      2 -> {
        residualScrollX = 0f
        residualScrollY = 0f
        if (mode == ActiveMode.DRAGGING) {
          commands += RemoteCommand.PointerUp(MouseButton.LEFT, timestampMs = timeMs)
        }
        mode = ActiveMode.TWO_FINGER_DOWN
        twoFingerStartTimeMs = timeMs
        lastTwoFingerCenterX = (x + secondX) / 2f
        lastTwoFingerCenterY = (y + secondY) / 2f
        lastTwoFingerDistance = hypot(x - secondX, y - secondY)
      }
      else -> {
        if (mode == ActiveMode.DRAGGING) {
          commands += RemoteCommand.PointerUp(MouseButton.LEFT, timestampMs = timeMs)
        }
        mode = ActiveMode.THREE_FINGER_SWIPE
        threeFingerStartY = y
        threeFingerTriggered = false
      }
    }

    return Output(
      commands = commands,
      triggerHaptic = haptic,
      isDragging = mode == ActiveMode.DRAGGING
    )
  }

  fun onTouchMove(
    pointerCount: Int,
    x: Float,
    y: Float,
    secondX: Float = 0f,
    secondY: Float = 0f,
    timeMs: Long,
    forceScrollMode: Boolean = false
  ): Output {
    maxPointerCountInSequence = maxOf(maxPointerCountInSequence, pointerCount)
    val commands = mutableListOf<RemoteCommand>()
    var haptic = false
    var label: String? = null

    when {
      pointerCount >= 3 -> {
        if (mode != ActiveMode.THREE_FINGER_SWIPE) {
          mode = ActiveMode.THREE_FINGER_SWIPE
          threeFingerStartY = y
          threeFingerTriggered = false
        }
        if (!threeFingerTriggered) {
          val dy = y - threeFingerStartY
          if (abs(dy) >= threeFingerSwipeThresholdPx) {
            threeFingerTriggered = true
            val action = if (dy < 0) config.threeFingerSwipeUp else config.threeFingerSwipeDown
            val shortcutCmds = mapThreeFingerAction(action, timeMs)
            if (shortcutCmds.isNotEmpty()) {
              commands += shortcutCmds
              haptic = true
              label = action.label
            }
          }
        }
      }

      pointerCount == 2 -> {
        val centerX = (x + secondX) / 2f
        val centerY = (y + secondY) / 2f
        val distance = hypot(x - secondX, y - secondY)

        if (mode != ActiveMode.TWO_FINGER_DOWN && mode != ActiveMode.SCROLLING_OR_PINCH) {
          mode = ActiveMode.TWO_FINGER_DOWN
          twoFingerStartTimeMs = timeMs
          lastTwoFingerCenterX = centerX
          lastTwoFingerCenterY = centerY
          lastTwoFingerDistance = distance
          return Output()
        }

        val dx = centerX - lastTwoFingerCenterX
        val dy = centerY - lastTwoFingerCenterY
        val distDelta = distance - lastTwoFingerDistance

        if (mode == ActiveMode.TWO_FINGER_DOWN &&
          hypot(dx, dy) < tapSlopPx * 0.6f &&
          abs(distDelta) < tapSlopPx
        ) {
          return Output()
        }

        mode = ActiveMode.SCROLLING_OR_PINCH

        if (lastTwoFingerDistance > 40f && abs(distDelta) > 14f && abs(distDelta) > hypot(dx, dy) * 1.4f) {
          val scale = (distance / lastTwoFingerDistance).coerceIn(0.5f, 2.0f)
          commands += RemoteCommand.PinchZoom(scaleDelta = scale, timestampMs = timeMs)
          lastTwoFingerDistance = distance
          label = "Zoom"
        } else {
          val directionMultiplier = if (config.naturalScrolling) 1f else -1f
          residualScrollX += dx * 0.28f * config.scrollSpeed * directionMultiplier
          residualScrollY += dy * 0.28f * config.scrollSpeed * directionMultiplier
          val scrollX = residualScrollX.roundToInt().coerceIn(-127, 127)
          val scrollY = residualScrollY.roundToInt().coerceIn(-127, 127)
          residualScrollX -= scrollX
          residualScrollY -= scrollY
          if (scrollX != 0 || scrollY != 0) {
            commands += RemoteCommand.PointerScroll(scrollX, scrollY, timestampMs = timeMs)
            label = "Scroll"
          }
        }

        lastTwoFingerCenterX = centerX
        lastTwoFingerCenterY = centerY
        lastTwoFingerDistance = distance
      }

      pointerCount == 1 -> {
        if (forceScrollMode) {
          val dx = x - lastPrimaryX
          val dy = y - lastPrimaryY
          lastPrimaryX = x
          lastPrimaryY = y
          if (hypot(x - primaryStartX, y - primaryStartY) >= tapSlopPx * 0.45f) {
            mode = ActiveMode.MOVING
            val directionMultiplier = if (config.naturalScrolling) 1f else -1f
            residualScrollX += dx * 0.32f * config.scrollSpeed * directionMultiplier
            residualScrollY += dy * 0.32f * config.scrollSpeed * directionMultiplier
            val sx = residualScrollX.roundToInt().coerceIn(-127, 127)
            val sy = residualScrollY.roundToInt().coerceIn(-127, 127)
            residualScrollX -= sx
            residualScrollY -= sy
            if (sx != 0 || sy != 0) {
              commands += RemoteCommand.PointerScroll(sx, sy, timestampMs = timeMs)
              label = "Scroll Mode"
            }
          }
          return Output(commands = commands, activeGestureLabel = label)
        }

        val totalDist = hypot(x - primaryStartX, y - primaryStartY)
        val elapsed = timeMs - primaryStartTimeMs

        if (mode == ActiveMode.ONE_FINGER_DOWN) {
          val isDoubleTapHold = awaitingSecondTap &&
            (primaryStartTimeMs - lastSingleTapReleaseMs) <= doubleTapWindowMs &&
            config.doubleTapAndHoldDrag
          val isLongPress = elapsed >= longPressDragMs && totalDist < tapSlopPx

          if (isLongPress || (isDoubleTapHold && totalDist >= tapSlopPx * 0.4f)) {
            mode = ActiveMode.DRAGGING
            awaitingSecondTap = false
            commands += RemoteCommand.PointerDown(MouseButton.LEFT, timestampMs = timeMs)
            haptic = true
            label = "Dragging"
          } else if (totalDist >= tapSlopPx) {
            mode = ActiveMode.MOVING
            awaitingSecondTap = false
          }
        }

        if (mode == ActiveMode.MOVING || mode == ActiveMode.DRAGGING) {
          val rawDx = x - lastPrimaryX
          val rawDy = y - lastPrimaryY
          val speed = hypot(rawDx, rawDy)
          val accelFactor = 1f + (speed * 0.03f * config.pointerAcceleration).coerceIn(0f, 1.8f)
          val gain = config.sensitivity * accelFactor.pow(1.05f)

          residualX += rawDx * gain
          residualY += rawDy * gain

          val emitDx = residualX.roundToInt().coerceIn(-127, 127)
          val emitDy = residualY.roundToInt().coerceIn(-127, 127)
          residualX -= emitDx
          residualY -= emitDy

          if (emitDx != 0 || emitDy != 0) {
            commands += RemoteCommand.PointerMove(emitDx, emitDy, timestampMs = timeMs)
          }
          if (mode == ActiveMode.DRAGGING) {
            label = "Dragging"
          }
        }

        lastPrimaryX = x
        lastPrimaryY = y
      }
    }

    return Output(
      commands = commands,
      triggerHaptic = haptic,
      isDragging = mode == ActiveMode.DRAGGING,
      activeGestureLabel = label
    )
  }

  fun onTouchEnd(remainingPointerCount: Int, timeMs: Long): Output {
    val commands = mutableListOf<RemoteCommand>()
    var haptic = false
    var label: String? = null

    if (remainingPointerCount == 0) {
      when (mode) {
        ActiveMode.DRAGGING -> {
          commands += RemoteCommand.PointerUp(MouseButton.LEFT, timestampMs = timeMs)
          haptic = true
        }

        ActiveMode.ONE_FINGER_DOWN -> {
          if (maxPointerCountInSequence == 1 && config.tapToClick) {
            val isDoubleTap = awaitingSecondTap &&
              (primaryStartTimeMs - lastSingleTapReleaseMs) <= doubleTapWindowMs
            if (isDoubleTap) {
              commands += RemoteCommand.PointerClick(MouseButton.LEFT, count = 2, timestampMs = timeMs)
              awaitingSecondTap = false
              haptic = true
              label = "Double Click"
            } else {
              commands += RemoteCommand.PointerClick(MouseButton.LEFT, count = 1, timestampMs = timeMs)
              awaitingSecondTap = true
              lastSingleTapReleaseMs = timeMs
              haptic = true
              label = "Left Click"
            }
          }
        }

        ActiveMode.TWO_FINGER_DOWN -> {
          if (config.twoFingerRightClick && (timeMs - twoFingerStartTimeMs) <= 320L) {
            commands += RemoteCommand.PointerClick(MouseButton.RIGHT, count = 1, timestampMs = timeMs)
            haptic = true
            label = "Right Click"
          }
        }

        else -> Unit
      }

      mode = ActiveMode.IDLE
      maxPointerCountInSequence = 0
      residualX = 0f
      residualY = 0f
      residualScrollX = 0f
      residualScrollY = 0f
    }

    return Output(
      commands = commands,
      triggerHaptic = haptic,
      isDragging = false,
      activeGestureLabel = label
    )
  }

  fun cancelActiveTouches(timeMs: Long = System.currentTimeMillis()): Output {
    val commands = mutableListOf<RemoteCommand>()
    if (mode == ActiveMode.DRAGGING) {
      commands += RemoteCommand.PointerUp(MouseButton.LEFT, timestampMs = timeMs)
    }
    mode = ActiveMode.IDLE
    maxPointerCountInSequence = 0
    awaitingSecondTap = false
    residualX = 0f
    residualY = 0f
    residualScrollX = 0f
    residualScrollY = 0f
    return Output(commands = commands, isDragging = false)
  }

  private fun mapThreeFingerAction(
    action: ThreeFingerShortcutAction,
    timeMs: Long
  ): List<RemoteCommand> = when (action) {
    ThreeFingerShortcutAction.NONE -> emptyList()
    ThreeFingerShortcutAction.TASK_SWITCHER -> listOf(
      RemoteCommand.KeyTap(
        com.example.input.HidKeyCode.TAB,
        com.example.input.HidModifiers.LEFT_ALT,
        timestampMs = timeMs
      )
    )
    ThreeFingerShortcutAction.SHOW_DESKTOP -> listOf(
      RemoteCommand.KeyTap(
        com.example.input.HidKeyCode.D,
        com.example.input.HidModifiers.LEFT_GUI,
        timestampMs = timeMs
      )
    )
    ThreeFingerShortcutAction.PLAY_PAUSE -> listOf(
      RemoteCommand.MediaControl(com.example.input.MediaAction.PLAY_PAUSE, timestampMs = timeMs)
    )
    ThreeFingerShortcutAction.SCREENSHOT -> listOf(
      RemoteCommand.KeyTap(
        com.example.input.HidKeyCode.PRINT_SCREEN,
        com.example.input.HidModifiers.NONE,
        timestampMs = timeMs
      )
    )
  }
}
