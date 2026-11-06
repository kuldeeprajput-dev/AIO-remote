package com.example.domain

import com.example.input.HidKeyCode
import com.example.input.HidModifiers
import com.example.input.MediaAction
import com.example.input.PresentationAction
import com.example.models.OperatingSystem

enum class ShortcutIconKey(val label: String) {
  LOCK("Lock"),
  APPS("Task Switcher"),
  DESKTOP("Desktop"),
  SCREENSHOT("Screenshot"),
  BROWSER("Browser"),
  FILES("Files"),
  PLAY("Media"),
  VOLUME("Volume"),
  SLIDES("Slides"),
  MACRO("Macro")
}

data class MacroStep(
  val keyCode: HidKeyCode,
  val modifiers: Int = HidModifiers.NONE,
  val delayAfterMs: Long = 80L
)

sealed interface ShortcutAction {
  data class KeyCombination(
    val keyCode: HidKeyCode,
    val modifiers: Int = HidModifiers.NONE,
    val macKeyCode: HidKeyCode? = null,
    val macModifiers: Int? = null
  ) : ShortcutAction

  data class Media(val action: MediaAction) : ShortcutAction
  data class Presentation(val action: PresentationAction) : ShortcutAction
  data class Macro(val steps: List<MacroStep>) : ShortcutAction
}

data class ShortcutItem(
  val id: String,
  val name: String,
  val icon: ShortcutIconKey,
  val targetOs: OperatingSystem? = null,
  val action: ShortcutAction,
  val badgeLabel: String
)

object DefaultShortcuts {
  fun defaults(): List<ShortcutItem> = listOf(
    ShortcutItem(
      id = "lock_screen",
      name = "Lock Screen",
      icon = ShortcutIconKey.LOCK,
      action = ShortcutAction.KeyCombination(
        keyCode = HidKeyCode.L,
        modifiers = HidModifiers.LEFT_GUI,
        macKeyCode = HidKeyCode.Q,
        macModifiers = HidModifiers.LEFT_CTRL or HidModifiers.LEFT_GUI
      ),
      badgeLabel = "Win+L"
    ),
    ShortcutItem(
      id = "show_desktop",
      name = "Show Desktop",
      icon = ShortcutIconKey.DESKTOP,
      action = ShortcutAction.KeyCombination(
        keyCode = HidKeyCode.D,
        modifiers = HidModifiers.LEFT_GUI,
        macKeyCode = HidKeyCode.F11,
        macModifiers = HidModifiers.NONE
      ),
      badgeLabel = "Win+D"
    ),
    ShortcutItem(
      id = "task_switcher",
      name = "Task Switcher",
      icon = ShortcutIconKey.APPS,
      action = ShortcutAction.KeyCombination(
        keyCode = HidKeyCode.TAB,
        modifiers = HidModifiers.LEFT_ALT,
        macKeyCode = HidKeyCode.TAB,
        macModifiers = HidModifiers.LEFT_GUI
      ),
      badgeLabel = "Alt+Tab"
    ),
    ShortcutItem(
      id = "screenshot",
      name = "Screenshot",
      icon = ShortcutIconKey.SCREENSHOT,
      action = ShortcutAction.KeyCombination(
        keyCode = HidKeyCode.S,
        modifiers = HidModifiers.LEFT_GUI or HidModifiers.LEFT_SHIFT,
        macKeyCode = HidKeyCode.NUM_4,
        macModifiers = HidModifiers.LEFT_GUI or HidModifiers.LEFT_SHIFT
      ),
      badgeLabel = "Capture"
    ),
    ShortcutItem(
      id = "open_browser",
      name = "Open Browser",
      icon = ShortcutIconKey.BROWSER,
      action = ShortcutAction.KeyCombination(
        keyCode = HidKeyCode.T,
        modifiers = HidModifiers.LEFT_CTRL,
        macKeyCode = HidKeyCode.T,
        macModifiers = HidModifiers.LEFT_GUI
      ),
      badgeLabel = "Ctrl+T"
    ),
    ShortcutItem(
      id = "file_manager",
      name = "File Manager",
      icon = ShortcutIconKey.FILES,
      action = ShortcutAction.KeyCombination(
        keyCode = HidKeyCode.E,
        modifiers = HidModifiers.LEFT_GUI,
        macKeyCode = HidKeyCode.SPACE,
        macModifiers = HidModifiers.LEFT_GUI
      ),
      badgeLabel = "Win+E"
    )
  )
}
