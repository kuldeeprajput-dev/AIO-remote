package com.example.ui.screens.remote

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.hapticClick
import com.example.ui.components.rememberHapticPulse
import com.example.R
import com.example.gestures.TouchpadGestureEngine
import com.example.input.MouseButton
import com.example.input.RemoteCommand
import com.example.models.ConnectionState
import com.example.models.DeviceCapabilities
import com.example.preferences.TouchpadSurfaceStyle
import com.example.preferences.UserSettings
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.theme.SlateTheme
import kotlinx.coroutines.delay

@Composable
fun TouchpadPane(
  connectionState: ConnectionState,
  capabilities: DeviceCapabilities,
  settings: UserSettings,
  isScrollMode: Boolean,
  isFullscreen: Boolean,
  gestureEngine: TouchpadGestureEngine,
  onSendCommands: (List<RemoteCommand>) -> Unit,
  onMouseButton: (MouseButton, Boolean) -> Unit,
  onMouseClick: (MouseButton) -> Unit,
  onToggleScrollMode: () -> Unit,
  onSetFullscreen: (Boolean) -> Unit,
  onOpenKeyboard: () -> Unit,
  onOpenMedia: () -> Unit,
  onSwitchDevice: () -> Unit,
  onConnectPrompt: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showMoreMenu by remember { mutableStateOf(false) }

  LaunchedEffect(settings) {
    gestureEngine.config = settings.toGestureConfig()
  }

  val primaryButton = if (settings.leftHandedLayout) MouseButton.RIGHT else MouseButton.LEFT
  val secondaryButton = if (settings.leftHandedLayout) MouseButton.LEFT else MouseButton.RIGHT
  val primaryLabel = if (settings.leftHandedLayout) stringResource(R.string.right_click) else stringResource(R.string.left_click)
  val secondaryLabel = if (settings.leftHandedLayout) stringResource(R.string.left_click) else stringResource(R.string.right_click)

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isLandscape = maxWidth > maxHeight && maxHeight < 460.dp

    if (isLandscape) {
      Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        PrecisionTouchpadSurface(
          connectionState = connectionState,
          capabilities = capabilities,
          surfaceStyle = settings.touchpadStyle,
          isScrollMode = isScrollMode,
          gestureEngine = gestureEngine,
          onSendCommands = onSendCommands,
          onConnectPrompt = onConnectPrompt,
          modifier = Modifier
            .weight(1.65f)
            .fillMaxHeight()
            .testTag("touchpad_surface")
        )

        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            MouseClickPadButton(
              label = primaryLabel,
              highlightLeft = !settings.leftHandedLayout,
              onPressChange = { pressed -> onMouseButton(primaryButton, pressed) },
              modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .testTag("left_click_button")
            )

            MouseClickPadButton(
              label = secondaryLabel,
              highlightLeft = settings.leftHandedLayout,
              onPressChange = { pressed -> onMouseButton(secondaryButton, pressed) },
              modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .testTag("right_click_button")
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            QuickActionToolItem(
              icon = Icons.Outlined.Keyboard,
              label = "Keyboard",
              active = false,
              onClick = onOpenKeyboard,
              modifier = Modifier.weight(1f)
            )
            QuickActionToolItem(
              icon = Icons.Outlined.SwapVert,
              label = "Scroll",
              active = isScrollMode,
              onClick = onToggleScrollMode,
              modifier = Modifier.weight(1f)
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            QuickActionToolItem(
              icon = Icons.Outlined.Fullscreen,
              label = "Fullscreen",
              active = isFullscreen,
              onClick = { onSetFullscreen(true) },
              modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.weight(1f)) {
              QuickActionToolItem(
                icon = Icons.Outlined.MoreHoriz,
                label = "More",
                active = false,
                onClick = { showMoreMenu = true },
                modifier = Modifier.fillMaxWidth()
              )
              DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false }
              ) {
                DropdownMenuItem(
                  text = { Text("Middle Click") },
                  onClick = hapticClick {
                    showMoreMenu = false
                    onMouseClick(MouseButton.MIDDLE)
                  }
                )
                DropdownMenuItem(
                  text = { Text("Media Controls") },
                  onClick = hapticClick {
                    showMoreMenu = false
                    onOpenMedia()
                  }
                )
                DropdownMenuItem(
                  text = { Text("Switch Device") },
                  onClick = hapticClick {
                    showMoreMenu = false
                    onSwitchDevice()
                  }
                )
              }
            }
          }
        }
      }
    } else {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // 1. Main Precision Trackpad Surface with corner brackets and center hand icon
        PrecisionTouchpadSurface(
          connectionState = connectionState,
          capabilities = capabilities,
          surfaceStyle = settings.touchpadStyle,
          isScrollMode = isScrollMode,
          gestureEngine = gestureEngine,
          onSendCommands = onSendCommands,
          onConnectPrompt = onConnectPrompt,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .testTag("touchpad_surface")
        )

        // 2. Left Click & Right Click Buttons matching reference screen 8
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(68.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          MouseClickPadButton(
            label = primaryLabel,
            highlightLeft = !settings.leftHandedLayout,
            onPressChange = { pressed -> onMouseButton(primaryButton, pressed) },
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
              .testTag("left_click_button")
          )

          MouseClickPadButton(
            label = secondaryLabel,
            highlightLeft = settings.leftHandedLayout,
            onPressChange = { pressed -> onMouseButton(secondaryButton, pressed) },
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
              .testTag("right_click_button")
          )
        }

        // 3. Bottom 4 Quick Actions Row: Keyboard | Scroll | Fullscreen | More
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          QuickActionToolItem(
            icon = Icons.Outlined.Keyboard,
            label = "Keyboard",
            active = false,
            onClick = onOpenKeyboard,
            modifier = Modifier.weight(1f)
          )
          QuickActionToolItem(
            icon = Icons.Outlined.SwapVert,
            label = "Scroll",
            active = isScrollMode,
            onClick = onToggleScrollMode,
            modifier = Modifier.weight(1f)
          )
          QuickActionToolItem(
            icon = Icons.Outlined.Fullscreen,
            label = "Fullscreen",
            active = isFullscreen,
            onClick = { onSetFullscreen(true) },
            modifier = Modifier.weight(1f)
          )
          Box(modifier = Modifier.weight(1f)) {
            QuickActionToolItem(
              icon = Icons.Outlined.MoreHoriz,
              label = "More",
              active = false,
              onClick = { showMoreMenu = true },
              modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
              expanded = showMoreMenu,
              onDismissRequest = { showMoreMenu = false }
            ) {
              DropdownMenuItem(
                text = { Text("Middle Click") },
                onClick = hapticClick {
                  showMoreMenu = false
                  onMouseClick(MouseButton.MIDDLE)
                }
              )
              DropdownMenuItem(
                text = { Text("Media Controls") },
                onClick = hapticClick {
                  showMoreMenu = false
                  onOpenMedia()
                }
              )
              DropdownMenuItem(
                text = { Text("Switch Device") },
                onClick = hapticClick {
                  showMoreMenu = false
                  onSwitchDevice()
                }
              )
            }
          }
        }
      }
    }
  }

  if (isFullscreen) {
    FullscreenTouchpadDialog(
      connectionState = connectionState,
      capabilities = capabilities,
      settings = settings,
      isScrollMode = isScrollMode,
      gestureEngine = gestureEngine,
      onToggleScrollMode = onToggleScrollMode,
      onSendCommands = onSendCommands,
      onMouseButton = onMouseButton,
      onConnectPrompt = onConnectPrompt,
      onDismiss = { onSetFullscreen(false) }
    )
  }
}

@Composable
private fun PrecisionTouchpadSurface(
  connectionState: ConnectionState,
  capabilities: DeviceCapabilities,
  surfaceStyle: TouchpadSurfaceStyle,
  isScrollMode: Boolean,
  gestureEngine: TouchpadGestureEngine,
  onSendCommands: (List<RemoteCommand>) -> Unit,
  onConnectPrompt: () -> Unit,
  modifier: Modifier = Modifier
) {
  val pulse = rememberHapticPulse()
  val longPressPulse = rememberHapticPulse(HapticFeedbackConstants.LONG_PRESS)
  val touchPointState = remember { mutableStateOf<Offset?>(null) }
  var isFingerDown by remember { mutableStateOf(false) }
  var gestureFeedbackLabel by remember { mutableStateOf<String?>(null) }
  var isDragging by remember { mutableStateOf(false) }

  val haloAlpha by animateFloatAsState(
    targetValue = if (isFingerDown) 1f else 0f,
    animationSpec = tween(durationMillis = if (isFingerDown) 80 else 220, easing = FastOutSlowInEasing),
    label = "halo_alpha"
  )
  val haloRadiusScale by animateFloatAsState(
    targetValue = if (isDragging) 1.25f else if (isFingerDown) 1f else 0.7f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
    label = "halo_scale"
  )

  LaunchedEffect(gestureFeedbackLabel) {
    if (gestureFeedbackLabel != null) {
      delay(850)
      gestureFeedbackLabel = null
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      val cancel = gestureEngine.cancelActiveTouches()
      if (cancel.commands.isNotEmpty()) {
        onSendCommands(cancel.commands)
      }
    }
  }

  val shape = RoundedCornerShape(22.dp)
  val borderColor = SlateTheme.colors.border
  val bracketColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
  val insetFrameColor = SlateTheme.colors.border.copy(alpha = 0.85f)
  val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
  val feedbackRingColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
  val feedbackCoreColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
  val hintAlpha by animateFloatAsState(
    targetValue = if (isFingerDown) 0.14f else 0.78f,
    animationSpec = tween(durationMillis = if (isFingerDown) 120 else 320),
    label = "hint_alpha"
  )

  Surface(
    modifier = modifier.clip(shape),
    shape = shape,
    color = SlateTheme.colors.touchpadSurface,
    border = BorderStroke(
      width = if (isDragging) 1.5.dp else 1.dp,
      color = if (isDragging) MaterialTheme.colorScheme.primary else borderColor
    )
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .drawWithCache {
          val w = size.width
          val h = size.height
          val frameInset = 12.dp.toPx()
          val frameCorner = 14.dp.toPx()
          val pad = 18.dp.toPx()
          val len = 14.dp.toPx()
          val stroke = 1.6.dp.toPx()
          val step = 22.dp.toPx()
          val dotRadius = 1.05.dp.toPx()
          onDrawBehind {
            when (surfaceStyle) {
              TouchpadSurfaceStyle.FLAT -> {
                // Clean anodized glass trackpad: subtle center-bottom click zone hairline cue
                drawLine(
                  color = borderColor.copy(alpha = 0.55f),
                  start = Offset(w * 0.42f, h - 14.dp.toPx()),
                  end = Offset(w * 0.58f, h - 14.dp.toPx()),
                  strokeWidth = 1.5.dp.toPx(),
                  cap = StrokeCap.Round
                )
              }

              TouchpadSurfaceStyle.BORDERED -> {
                // Crisp Frame: fine inner machined bezel + precision L-shaped corner fiducials
                drawRoundRect(
                  color = insetFrameColor,
                  topLeft = Offset(frameInset, frameInset),
                  size = Size(w - frameInset * 2f, h - frameInset * 2f),
                  cornerRadius = CornerRadius(frameCorner, frameCorner),
                  style = Stroke(width = 0.8.dp.toPx())
                )
                drawLine(bracketColor, Offset(pad, pad), Offset(pad + len, pad), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(bracketColor, Offset(pad, pad), Offset(pad, pad + len), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(bracketColor, Offset(w - pad, pad), Offset(w - pad - len, pad), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(bracketColor, Offset(w - pad, pad), Offset(w - pad, pad + len), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(bracketColor, Offset(pad, h - pad), Offset(pad + len, h - pad), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(bracketColor, Offset(pad, h - pad), Offset(pad, h - pad - len), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(bracketColor, Offset(w - pad, h - pad), Offset(w - pad - len, h - pad), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(bracketColor, Offset(w - pad, h - pad), Offset(w - pad, h - pad - len), strokeWidth = stroke, cap = StrokeCap.Round)
              }

              TouchpadSurfaceStyle.DOTTED -> {
                // Micro Grid: calibrated precision dot matrix inside subtle perimeter margin
                var x = step
                while (x < w - step * 0.5f) {
                  var y = step
                  while (y < h - step * 0.5f) {
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(x, y))
                    y += step
                  }
                  x += step
                }
              }
            }
          }
        }
        .pointerInput(isScrollMode, pulse, longPressPulse) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Main)
              val pressed = event.changes.filter { it.pressed }
              val now = SystemClock.uptimeMillis()

              when (event.type) {
                PointerEventType.Press -> {
                  val p0 = pressed.getOrNull(0)
                  val p1 = pressed.getOrNull(1)
                  if (p0 != null) {
                    isFingerDown = true
                    touchPointState.value = p0.position
                    val out = gestureEngine.onTouchStart(
                      pointerCount = pressed.size,
                      x = p0.position.x,
                      y = p0.position.y,
                      secondX = p1?.position?.x ?: 0f,
                      secondY = p1?.position?.y ?: 0f,
                      timeMs = now
                    )
                    if (out.commands.isNotEmpty()) onSendCommands(out.commands)
                    if (out.triggerHaptic) {
                      pulse()
                    }
                    isDragging = out.isDragging
                    out.activeGestureLabel?.let { gestureFeedbackLabel = it }
                  }
                }

                PointerEventType.Move -> {
                  val p0 = pressed.getOrNull(0)
                  val p1 = pressed.getOrNull(1)
                  if (p0 != null) {
                    isFingerDown = true
                    touchPointState.value = p0.position
                    val out = gestureEngine.onTouchMove(
                      pointerCount = pressed.size,
                      x = p0.position.x,
                      y = p0.position.y,
                      secondX = p1?.position?.x ?: 0f,
                      secondY = p1?.position?.y ?: 0f,
                      timeMs = now,
                      forceScrollMode = isScrollMode
                    )
                    if (out.commands.isNotEmpty()) {
                      onSendCommands(out.commands)
                    }
                    if (out.triggerHaptic) {
                      longPressPulse()
                    }
                    isDragging = out.isDragging
                    out.activeGestureLabel?.let { gestureFeedbackLabel = it }
                    event.changes.forEach { it.consume() }
                  }
                }

                PointerEventType.Release -> {
                  val out = gestureEngine.onTouchEnd(
                    remainingPointerCount = pressed.size,
                    timeMs = now
                  )
                  if (pressed.isEmpty()) {
                    isFingerDown = false
                  }
                  if (out.commands.isNotEmpty()) onSendCommands(out.commands)
                  if (out.triggerHaptic) {
                    pulse()
                  }
                  isDragging = out.isDragging
                  out.activeGestureLabel?.let { gestureFeedbackLabel = it }
                }
              }
            }
          }
        }
    ) {
      // Isolated lightweight Canvas overlay reads touch state only during draw phase (zero recomposition lag)
      Canvas(modifier = Modifier.fillMaxSize()) {
        val pos = touchPointState.value
        if (pos != null && haloAlpha > 0.01f) {
          drawCircle(
            color = feedbackRingColor.copy(alpha = feedbackRingColor.alpha * haloAlpha),
            radius = 24.dp.toPx() * haloRadiusScale,
            center = pos
          )
          drawCircle(
            color = feedbackCoreColor.copy(alpha = feedbackCoreColor.alpha * haloAlpha),
            radius = 6.dp.toPx() * haloRadiusScale,
            center = pos
          )
        }
      }

      // Top-right subtle mode badge
      Row(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        if (isScrollMode) {
          Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary
          ) {
            Text(
              text = "SCROLL",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
          }
        }
        gestureFeedbackLabel?.let { label ->
          Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, SlateTheme.colors.border)
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
          }
        }
      }

      // Subtle neutral touch cue that dims while actively sliding
      Column(
        modifier = Modifier
          .align(Alignment.Center)
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Outlined.TouchApp,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface.copy(alpha = hintAlpha * 0.55f),
          modifier = Modifier.size(34.dp)
        )
        Text(
          text = if (connectionState != ConnectionState.CONNECTED || capabilities.hasMouse) {
            stringResource(R.string.touchpad_hint)
          } else {
            "Use TV Remote mode for this target"
          },
          style = MaterialTheme.typography.bodyMedium,
          color = SlateTheme.colors.textSecondary.copy(alpha = hintAlpha)
        )
      }
    }
  }
}

@Composable
private fun MouseClickPadButton(
  label: String,
  highlightLeft: Boolean,
  onPressChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val pulse = rememberHapticPulse()
  val iconColor = MaterialTheme.colorScheme.onSurface

  LaunchedEffect(isPressed) {
    if (isPressed) {
      pulse()
    }
    onPressChange(isPressed)
  }

  Surface(
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .clickable(
        interactionSource = interactionSource,
        indication = androidx.compose.material3.ripple(),
        onClick = {}
      ),
    shape = RoundedCornerShape(16.dp),
    color = if (isPressed) SlateTheme.colors.accentSoft else MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Custom mouse icon with left or right button highlighted
      Canvas(modifier = Modifier.size(width = 16.dp, height = 20.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
          color = iconColor,
          size = Size(w, h),
          cornerRadius = CornerRadius(w * 0.5f, w * 0.5f),
          style = Stroke(width = 1.5.dp.toPx())
        )
        drawLine(
          color = iconColor,
          start = Offset(w * 0.5f, 0f),
          end = Offset(w * 0.5f, h * 0.42f),
          strokeWidth = 1.2.dp.toPx()
        )
        drawLine(
          color = iconColor,
          start = Offset(0f, h * 0.42f),
          end = Offset(w, h * 0.42f),
          strokeWidth = 1.2.dp.toPx()
        )
        val dotX = if (highlightLeft) w * 0.28f else w * 0.72f
        drawCircle(
          color = iconColor,
          radius = 1.6.dp.toPx(),
          center = Offset(dotX, h * 0.22f)
        )
      }
      Spacer(Modifier.height(5.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false
      )
    }
  }
}

@Composable
private fun QuickActionToolItem(
  icon: ImageVector,
  label: String,
  active: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Surface(
      onClick = hapticClick(onClick),
      modifier = Modifier
        .fillMaxWidth()
        .height(44.dp),
      shape = RoundedCornerShape(14.dp),
      color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(20.dp)
        )
      }
    }
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = SlateTheme.colors.textSecondary,
      maxLines = 1,
      softWrap = false,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun FullscreenTouchpadDialog(
  connectionState: ConnectionState,
  capabilities: DeviceCapabilities,
  settings: UserSettings,
  isScrollMode: Boolean,
  gestureEngine: TouchpadGestureEngine,
  onToggleScrollMode: () -> Unit,
  onSendCommands: (List<RemoteCommand>) -> Unit,
  onMouseButton: (MouseButton, Boolean) -> Unit,
  onConnectPrompt: () -> Unit,
  onDismiss: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    BackHandler(onBack = onDismiss)
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          ConnectionStatusPill(state = connectionState, reduceMotion = settings.minimalAnimations)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
              onClick = hapticClick(onToggleScrollMode),
              shape = RoundedCornerShape(12.dp),
              color = if (isScrollMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
              border = if (isScrollMode) null else BorderStroke(1.dp, SlateTheme.colors.border)
            ) {
              Text(
                text = if (isScrollMode) "Scroll On" else "Scroll",
                style = MaterialTheme.typography.labelMedium,
                color = if (isScrollMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
              )
            }
            Surface(
              onClick = hapticClick(onDismiss),
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, SlateTheme.colors.border),
              modifier = Modifier.testTag("exit_fullscreen_touchpad")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(Icons.Outlined.CloseFullscreen, contentDescription = "Exit fullscreen", modifier = Modifier.size(16.dp))
                Text("Exit", style = MaterialTheme.typography.labelMedium)
              }
            }
          }
        }

        PrecisionTouchpadSurface(
          connectionState = connectionState,
          capabilities = capabilities,
          surfaceStyle = settings.touchpadStyle,
          isScrollMode = isScrollMode,
          gestureEngine = gestureEngine,
          onSendCommands = onSendCommands,
          onConnectPrompt = onConnectPrompt,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .padding(bottom = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          MouseClickPadButton(
            label = stringResource(R.string.left_click),
            highlightLeft = true,
            onPressChange = { onMouseButton(MouseButton.LEFT, it) },
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
          )
          MouseClickPadButton(
            label = stringResource(R.string.right_click),
            highlightLeft = false,
            onPressChange = { onMouseButton(MouseButton.RIGHT, it) },
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
          )
        }
      }
    }
  }
}
