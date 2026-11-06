package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

internal val LocalHapticFeedbackEnabled = compositionLocalOf { false }

@Composable
internal fun rememberHapticPulse(
  effect: Int = HapticFeedbackConstants.VIRTUAL_KEY
): () -> Unit {
  val view = LocalView.current
  val enabled = LocalHapticFeedbackEnabled.current
  return remember(view, enabled, effect) {
    { if (enabled) view.performHapticFeedback(effect) }
  }
}

@Composable
internal fun hapticClick(onClick: () -> Unit): () -> Unit {
  val pulse = rememberHapticPulse()
  return {
    pulse()
    onClick()
  }
}
