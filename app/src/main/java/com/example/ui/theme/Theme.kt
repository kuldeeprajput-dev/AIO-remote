package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.components.LocalHapticFeedbackEnabled

@Immutable
data class SlateExtendedColors(
  val border: Color,
  val success: Color,
  val warning: Color,
  val elevatedSurface: Color,
  val touchpadSurface: Color,
  val textSecondary: Color,
  val accentSoft: Color
)

val LocalSlateColors = staticCompositionLocalOf {
  SlateExtendedColors(
    border = SlatePalette.LightBorder,
    success = SlatePalette.LightSuccess,
    warning = SlatePalette.Warning,
    elevatedSurface = SlatePalette.LightElevatedSurface,
    touchpadSurface = SlatePalette.LightTouchpadSurface,
    textSecondary = SlatePalette.LightTextSecondary,
    accentSoft = SlatePalette.LightAccentSoft
  )
}

private val LightColorScheme = lightColorScheme(
  primary = SlatePalette.LightAccent,
  onPrimary = SlatePalette.LightOnAccent,
  primaryContainer = SlatePalette.LightAccentSoft,
  onPrimaryContainer = SlatePalette.LightTextPrimary,
  secondary = SlatePalette.LightTextSecondary,
  onSecondary = Color.White,
  secondaryContainer = SlatePalette.LightElevatedSurface,
  onSecondaryContainer = SlatePalette.LightTextPrimary,
  background = SlatePalette.LightBackground,
  onBackground = SlatePalette.LightTextPrimary,
  surface = SlatePalette.LightSurface,
  onSurface = SlatePalette.LightTextPrimary,
  surfaceVariant = SlatePalette.LightElevatedSurface,
  onSurfaceVariant = SlatePalette.LightTextSecondary,
  outline = SlatePalette.LightBorder,
  error = SlatePalette.ErrorLight,
  onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
  primary = SlatePalette.DarkAccent,
  onPrimary = SlatePalette.DarkOnAccent,
  primaryContainer = SlatePalette.DarkAccentSoft,
  onPrimaryContainer = SlatePalette.DarkTextPrimary,
  secondary = SlatePalette.DarkTextSecondary,
  onSecondary = SlatePalette.DarkBackground,
  secondaryContainer = SlatePalette.DarkElevatedSurface,
  onSecondaryContainer = SlatePalette.DarkTextPrimary,
  background = SlatePalette.DarkBackground,
  onBackground = SlatePalette.DarkTextPrimary,
  surface = SlatePalette.DarkSurface,
  onSurface = SlatePalette.DarkTextPrimary,
  surfaceVariant = SlatePalette.DarkElevatedSurface,
  onSurfaceVariant = SlatePalette.DarkTextSecondary,
  outline = SlatePalette.DarkBorder,
  error = SlatePalette.ErrorDark,
  onError = SlatePalette.DarkBackground
)

private val SlateShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(20.dp),
  extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun SlateRemoteTheme(
  darkTheme: Boolean = false,
  hapticFeedbackEnabled: Boolean = false,
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val extended = if (darkTheme) {
    SlateExtendedColors(
      border = SlatePalette.DarkBorder,
      success = SlatePalette.DarkSuccess,
      warning = SlatePalette.Warning,
      elevatedSurface = SlatePalette.DarkElevatedSurface,
      touchpadSurface = SlatePalette.DarkTouchpadSurface,
      textSecondary = SlatePalette.DarkTextSecondary,
      accentSoft = SlatePalette.DarkAccentSoft
    )
  } else {
    SlateExtendedColors(
      border = SlatePalette.LightBorder,
      success = SlatePalette.LightSuccess,
      warning = SlatePalette.Warning,
      elevatedSurface = SlatePalette.LightElevatedSurface,
      touchpadSurface = SlatePalette.LightTouchpadSurface,
      textSecondary = SlatePalette.LightTextSecondary,
      accentSoft = SlatePalette.LightAccentSoft
    )
  }

  CompositionLocalProvider(
    LocalSlateColors provides extended,
    LocalHapticFeedbackEnabled provides hapticFeedbackEnabled
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = SlateShapes,
      content = content
    )
  }
}

object SlateTheme {
  val colors: SlateExtendedColors
    @Composable get() = LocalSlateColors.current
}
