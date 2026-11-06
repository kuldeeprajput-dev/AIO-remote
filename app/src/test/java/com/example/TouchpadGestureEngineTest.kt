package com.example

import com.example.gestures.ThreeFingerShortcutAction
import com.example.gestures.TouchpadGestureConfig
import com.example.gestures.TouchpadGestureEngine
import com.example.input.HidKeyCode
import com.example.input.HidModifiers
import com.example.input.MouseButton
import com.example.input.RemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchpadGestureEngineTest {

  @Test
  fun `one finger movement emits pointer move deltas`() {
    val engine = TouchpadGestureEngine(TouchpadGestureConfig(sensitivity = 1.0f, pointerAcceleration = 0f))
    engine.onTouchStart(pointerCount = 1, x = 100f, y = 100f, timeMs = 1000L)

    // Move beyond tap slop
    val out = engine.onTouchMove(pointerCount = 1, x = 140f, y = 120f, timeMs = 1016L)
    val moves = out.commands.filterIsInstance<RemoteCommand.PointerMove>()
    assertTrue(moves.isNotEmpty())
    assertEquals(40, moves.first().dx)
    assertEquals(20, moves.first().dy)
  }

  @Test
  fun `single tap emits left click and double tap emits double click`() {
    val engine = TouchpadGestureEngine(TouchpadGestureConfig(tapToClick = true))

    engine.onTouchStart(1, 200f, 200f, timeMs = 1000L)
    val tap1 = engine.onTouchEnd(remainingPointerCount = 0, timeMs = 1060L)
    assertEquals(listOf(RemoteCommand.PointerClick(MouseButton.LEFT, 1, timestampMs = 1060L)), tap1.commands)
    assertTrue(tap1.triggerHaptic)

    engine.onTouchStart(1, 202f, 201f, timeMs = 1150L)
    val tap2 = engine.onTouchEnd(remainingPointerCount = 0, timeMs = 1200L)
    assertEquals(listOf(RemoteCommand.PointerClick(MouseButton.LEFT, 2, timestampMs = 1200L)), tap2.commands)
  }

  @Test
  fun `two finger tap emits right click`() {
    val engine = TouchpadGestureEngine(TouchpadGestureConfig(twoFingerRightClick = true))
    engine.onTouchStart(2, 100f, 100f, 180f, 100f, timeMs = 500L)
    val end = engine.onTouchEnd(remainingPointerCount = 0, timeMs = 580L)
    assertEquals(listOf(RemoteCommand.PointerClick(MouseButton.RIGHT, 1, timestampMs = 580L)), end.commands)
  }

  @Test
  fun `two finger vertical swipe respects natural scrolling`() {
    val naturalEngine = TouchpadGestureEngine(TouchpadGestureConfig(naturalScrolling = true, scrollSpeed = 1.0f))
    naturalEngine.onTouchStart(2, 100f, 200f, 180f, 200f, timeMs = 100L)
    val naturalMove = naturalEngine.onTouchMove(2, 100f, 260f, 180f, 260f, timeMs = 120L)
    val scrollNat = naturalMove.commands.filterIsInstance<RemoteCommand.PointerScroll>().first()
    assertTrue(scrollNat.dy > 0)

    val invertedEngine = TouchpadGestureEngine(TouchpadGestureConfig(naturalScrolling = false, scrollSpeed = 1.0f))
    invertedEngine.onTouchStart(2, 100f, 200f, 180f, 200f, timeMs = 100L)
    val invMove = invertedEngine.onTouchMove(2, 100f, 260f, 180f, 260f, timeMs = 120L)
    val scrollInv = invMove.commands.filterIsInstance<RemoteCommand.PointerScroll>().first()
    assertTrue(scrollInv.dy < 0)
  }

  @Test
  fun `long press starts drag and cancel releases mouse button`() {
    val engine = TouchpadGestureEngine()
    engine.onTouchStart(1, 300f, 300f, timeMs = 1000L)
    val dragStart = engine.onTouchMove(1, 302f, 302f, timeMs = 1450L)

    assertTrue(dragStart.isDragging)
    assertTrue(dragStart.commands.any { it is RemoteCommand.PointerDown && it.button == MouseButton.LEFT })

    val cancel = engine.cancelActiveTouches(timeMs = 1500L)
    assertFalse(cancel.isDragging)
    assertTrue(cancel.commands.any { it is RemoteCommand.PointerUp && it.button == MouseButton.LEFT })
  }

  @Test
  fun `three finger swipe up dispatches configured shortcut`() {
    val engine = TouchpadGestureEngine(
      TouchpadGestureConfig(threeFingerSwipeUp = ThreeFingerShortcutAction.TASK_SWITCHER)
    )
    engine.onTouchStart(3, 200f, 400f, timeMs = 1000L)
    val out = engine.onTouchMove(3, 200f, 280f, timeMs = 1080L)
    val tap = out.commands.filterIsInstance<RemoteCommand.KeyTap>().first()
    assertEquals(HidKeyCode.TAB, tap.keyCode)
    assertEquals(HidModifiers.LEFT_ALT, tap.modifiers)
  }
}
