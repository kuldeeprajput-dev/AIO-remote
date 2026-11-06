package com.example.ui.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.models.ConnectionState
import com.example.models.DeviceType
import com.example.models.TransportType
import com.example.ui.theme.SlateTheme

@Composable
fun rememberShouldReduceMotion(minimalAnimationsSetting: Boolean): Boolean {
  val context = LocalContext.current
  val systemReduced = remember(context) {
    runCatching {
      Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
  }
  return minimalAnimationsSetting || systemReduced
}

@Composable
fun SlateCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  cornerRadius: Dp = 18.dp,
  backgroundColor: Color = MaterialTheme.colorScheme.surface,
  borderColor: Color = SlateTheme.colors.border,
  contentPadding: PaddingValues = PaddingValues(16.dp),
  content: @Composable ColumnScope.() -> Unit
) {
  val shape = RoundedCornerShape(cornerRadius)
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (onClick != null && isPressed) 0.985f else 1f,
    animationSpec = spring(stiffness = 700f),
    label = "card_press"
  )

  Surface(
    modifier = modifier
      .scale(scale)
      .clip(shape)
      .then(
        if (onClick != null) {
          Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = hapticClick(onClick)
          )
        } else Modifier
      ),
    shape = shape,
    color = backgroundColor,
    border = BorderStroke(1.dp, borderColor),
    tonalElevation = 0.dp,
    shadowElevation = 0.dp
  ) {
    Column(
      modifier = Modifier.padding(contentPadding),
      content = content
    )
  }
}

@Composable
fun SlateTactileButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  containerColor: Color = MaterialTheme.colorScheme.surface,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  borderColor: Color = SlateTheme.colors.border,
  cornerRadius: Dp = 16.dp,
  content: @Composable () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed && enabled) 0.97f else 1f,
    animationSpec = spring(stiffness = 900f),
    label = "btn_scale"
  )
  val bg by animateColorAsState(
    targetValue = when {
      !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
      isPressed -> SlateTheme.colors.elevatedSurface
      else -> containerColor
    },
    animationSpec = tween(100),
    label = "btn_bg"
  )

  Surface(
    modifier = modifier
      .scale(scale)
      .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
      .clip(RoundedCornerShape(cornerRadius))
      .clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = androidx.compose.material3.ripple(),
        onClick = hapticClick(onClick)
      ),
    shape = RoundedCornerShape(cornerRadius),
    color = bg,
    contentColor = if (enabled) contentColor else SlateTheme.colors.textSecondary,
    border = BorderStroke(1.dp, borderColor)
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
      content()
    }
  }
}

@Composable
fun ConnectionStatusDot(
  state: ConnectionState,
  reduceMotion: Boolean,
  modifier: Modifier = Modifier
) {
  val color = when (state) {
    ConnectionState.CONNECTED -> SlateTheme.colors.success
    ConnectionState.CONNECTING,
    ConnectionState.PAIRING,
    ConnectionState.RECONNECTING,
    ConnectionState.SEARCHING -> SlateTheme.colors.warning
    ConnectionState.CONNECTION_FAILED,
    ConnectionState.UNSUPPORTED,
    ConnectionState.PERMISSION_REQUIRED -> MaterialTheme.colorScheme.error
    ConnectionState.DISCONNECTED -> SlateTheme.colors.textSecondary.copy(alpha = 0.45f)
  }

  val isTransitional = state == ConnectionState.CONNECTING ||
    state == ConnectionState.PAIRING ||
    state == ConnectionState.RECONNECTING ||
    state == ConnectionState.SEARCHING

  val alpha = if (isTransitional && !reduceMotion) {
    val transition = rememberInfiniteTransition(label = "status_pulse")
    val animated by transition.animateFloat(
      initialValue = 0.35f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
      label = "pulse_alpha"
    )
    animated
  } else 1f

  Box(
    modifier = modifier
      .size(8.dp)
      .alpha(alpha)
      .clip(CircleShape)
      .background(color)
  )
}

@Composable
fun ConnectionStatusPill(
  state: ConnectionState,
  reduceMotion: Boolean,
  latencyMs: Int? = null,
  modifier: Modifier = Modifier
) {
  val text = if (state == ConnectionState.CONNECTED && latencyMs != null) {
    "${state.label} • ${latencyMs}ms"
  } else {
    state.label
  }
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(50),
    color = SlateTheme.colors.elevatedSurface,
    border = BorderStroke(1.dp, SlateTheme.colors.border)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      ConnectionStatusDot(state = state, reduceMotion = reduceMotion)
      Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun SkeletonDeviceCard(modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "skeleton")
  val alpha by transition.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.75f,
    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
    label = "skel_alpha"
  )

  SlateCard(modifier = modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .alpha(alpha)
          .clip(RoundedCornerShape(12.dp))
          .background(SlateTheme.colors.elevatedSurface)
      )
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
          modifier = Modifier
            .fillMaxWidth(0.55f)
            .height(14.dp)
            .alpha(alpha)
            .clip(RoundedCornerShape(6.dp))
            .background(SlateTheme.colors.elevatedSurface)
        )
        Box(
          modifier = Modifier
            .fillMaxWidth(0.35f)
            .height(11.dp)
            .alpha(alpha)
            .clip(RoundedCornerShape(6.dp))
            .background(SlateTheme.colors.elevatedSurface)
        )
      }
    }
  }
}

fun deviceTypeIcon(type: DeviceType): ImageVector = when (type) {
  DeviceType.LAPTOP -> Icons.Outlined.LaptopMac
  DeviceType.DESKTOP -> Icons.Outlined.Computer
  DeviceType.SMART_TV, DeviceType.MEDIA_BOX -> Icons.Outlined.Tv
  DeviceType.UNKNOWN -> Icons.Outlined.LaptopMac
}

fun transportIcon(type: TransportType): ImageVector = when (type) {
  TransportType.BLUETOOTH_HID -> Icons.Outlined.Bluetooth
  TransportType.WIFI_DESKTOP -> Icons.Outlined.Wifi
  TransportType.USB -> Icons.Outlined.Usb
  TransportType.ANDROID_TV -> Icons.Outlined.Tv
}
