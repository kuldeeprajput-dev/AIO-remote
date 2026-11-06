package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.hapticClick
import com.example.domain.ShortcutItem
import com.example.gestures.TouchpadGestureEngine
import com.example.input.HidKeyCode
import com.example.input.MediaAction
import com.example.input.MouseButton
import com.example.input.PresentationAction
import com.example.input.RemoteCommand
import com.example.input.TvAction
import com.example.models.ConnectionState
import com.example.models.DeviceCapabilities
import com.example.models.DeviceType
import com.example.models.MediaPlaybackInfo
import com.example.models.OperatingSystem
import com.example.models.RemoteDevice
import com.example.models.TransportType
import com.example.preferences.UserSettings
import com.example.ui.components.ConnectionStatusDot
import com.example.ui.components.SlateCard
import com.example.ui.components.deviceTypeIcon
import com.example.ui.screens.remote.KeyboardPane
import com.example.ui.screens.remote.MediaPane
import com.example.ui.screens.remote.PresentationPane
import com.example.ui.screens.remote.ShortcutsPane
import com.example.ui.screens.remote.TouchpadPane
import com.example.ui.screens.remote.TvRemotePane
import com.example.ui.theme.SlateTheme
import com.example.viewmodel.RemoteControlMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
  activeDevice: RemoteDevice?,
  savedDevices: List<RemoteDevice>,
  connectionState: ConnectionState,
  capabilities: DeviceCapabilities,
  mediaInfo: MediaPlaybackInfo?,
  latencyMs: Int?,
  remoteMode: RemoteControlMode,
  isScrollMode: Boolean,
  isFullscreenTouchpad: Boolean,
  settings: UserSettings,
  shortcuts: List<ShortcutItem>,
  reduceMotion: Boolean,
  gestureEngine: TouchpadGestureEngine,
  onSelectMode: (RemoteControlMode) -> Unit,
  onToggleScrollMode: () -> Unit,
  onSetFullscreenTouchpad: (Boolean) -> Unit,
  onSelectDevice: (RemoteDevice) -> Unit,
  onConnectDevice: (RemoteDevice) -> Unit,
  onNavigateToDevices: () -> Unit,
  onSendCommands: (List<RemoteCommand>) -> Unit,
  onMouseButton: (MouseButton, Boolean) -> Unit,
  onMouseClick: (MouseButton) -> Unit,
  onKeyTap: (HidKeyCode, Int) -> Unit,
  onSendText: (String) -> Unit,
  onSendClipboard: () -> Unit,
  onSendMedia: (MediaAction, Int?) -> Unit,
  onSendPresentation: (PresentationAction, Float?, Float?) -> Unit,
  onSendTv: (TvAction, String?) -> Unit,
  onExecuteShortcut: (ShortcutItem) -> Unit,
  onMoveShortcut: (Int, Int) -> Unit,
  onAddShortcut: (ShortcutItem) -> Unit,
  onRemoveShortcut: (String) -> Unit,
  onChangePresentationPreset: (com.example.preferences.PresentationSoftwarePreset) -> Unit,
  modifier: Modifier = Modifier
) {
  var showDeviceSwitcher by remember { mutableStateOf(false) }

  BoxWithConstraints(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    val isLandscape = maxWidth > maxHeight && maxHeight < 480.dp

    Column(
      modifier = Modifier
        .widthIn(max = if (isLandscape) 960.dp else 640.dp)
        .fillMaxSize()
        .padding(
          horizontal = if (isLandscape) 14.dp else 18.dp,
          vertical = if (isLandscape) 6.dp else 10.dp
        ),
      verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 12.dp)
    ) {
      if (isLandscape) {
        // Compact single-row header in Landscape: Active Device + Mode Pill Selector
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          RemoteHeaderBar(
            activeDevice = activeDevice,
            connectionState = connectionState,
            latencyMs = latencyMs,
            reduceMotion = reduceMotion,
            onSwitchDevice = { showDeviceSwitcher = true },
            modifier = Modifier.weight(0.42f)
          )

          Surface(
            modifier = Modifier.weight(0.58f),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface
          ) {
            LazyRow(
              modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp)
                .testTag("remote_mode_selector"),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              items(RemoteControlMode.entries) { mode ->
                val selected = remoteMode == mode
                Surface(
                  modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = hapticClick { onSelectMode(mode) })
                    .testTag("mode_tab_${mode.name}"),
                  shape = RoundedCornerShape(50),
                  color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                ) {
                  Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                  )
                }
              }
            }
          }
        }
      } else {
        // Portrait: Top Active Device Header matching reference screens 8-13
        RemoteHeaderBar(
          activeDevice = activeDevice,
          connectionState = connectionState,
          latencyMs = latencyMs,
          reduceMotion = reduceMotion,
          onSwitchDevice = { showDeviceSwitcher = true }
        )

        // Portrait: Rounded Pill Track Mode Selector matching reference screens 8-13
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(50),
          color = MaterialTheme.colorScheme.surface
        ) {
          LazyRow(
            modifier = Modifier
              .fillMaxWidth()
              .padding(4.dp)
              .testTag("remote_mode_selector"),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            items(RemoteControlMode.entries) { mode ->
              val selected = remoteMode == mode
              Surface(
                modifier = Modifier
                  .clip(RoundedCornerShape(50))
                  .clickable(onClick = hapticClick { onSelectMode(mode) })
                  .testTag("mode_tab_${mode.name}"),
                shape = RoundedCornerShape(50),
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
              ) {
                Text(
                  text = mode.label,
                  style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                  ),
                  color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  softWrap = false,
                  modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
              }
            }
          }
        }
      }

      // Active Mode Content
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        when (remoteMode) {
          RemoteControlMode.TOUCHPAD -> TouchpadPane(
            connectionState = connectionState,
            capabilities = capabilities,
            settings = settings,
            isScrollMode = isScrollMode,
            isFullscreen = isFullscreenTouchpad,
            gestureEngine = gestureEngine,
            onSendCommands = onSendCommands,
            onMouseButton = onMouseButton,
            onMouseClick = onMouseClick,
            onToggleScrollMode = onToggleScrollMode,
            onSetFullscreen = onSetFullscreenTouchpad,
            onOpenKeyboard = { onSelectMode(RemoteControlMode.KEYBOARD) },
            onOpenMedia = { onSelectMode(RemoteControlMode.MEDIA) },
            onSwitchDevice = { showDeviceSwitcher = true },
            onConnectPrompt = {
              if (activeDevice != null) onConnectDevice(activeDevice) else onNavigateToDevices()
            }
          )

          RemoteControlMode.KEYBOARD -> KeyboardPane(
            connectionState = connectionState,
            capabilities = capabilities,
            targetOs = activeDevice?.os ?: OperatingSystem.WINDOWS,
            clipboardOptIn = settings.clipboardSyncOptIn,
            onKeyTap = onKeyTap,
            onSendText = onSendText,
            onSendClipboard = onSendClipboard,
            onBackToTouchpad = { onSelectMode(RemoteControlMode.TOUCHPAD) }
          )

          RemoteControlMode.MEDIA -> MediaPane(
            connectionState = connectionState,
            capabilities = capabilities,
            mediaInfo = mediaInfo,
            onSendMedia = onSendMedia
          )

          RemoteControlMode.PRESENTATION -> PresentationPane(
            connectionState = connectionState,
            capabilities = capabilities,
            currentPreset = settings.presentationPreset,
            onSendPresentation = onSendPresentation,
            onSelectPreset = onChangePresentationPreset
          )

          RemoteControlMode.TV_REMOTE -> TvRemotePane(
            activeDevice = activeDevice,
            connectionState = connectionState,
            capabilities = capabilities,
            onSendTv = onSendTv,
            onSendText = onSendText,
            onOpenDevices = onNavigateToDevices
          )

          RemoteControlMode.SHORTCUTS -> ShortcutsPane(
            shortcuts = shortcuts,
            connectionState = connectionState,
            onExecuteShortcut = onExecuteShortcut,
            onMoveShortcut = onMoveShortcut,
            onAddShortcut = onAddShortcut,
            onRemoveShortcut = onRemoveShortcut
          )
        }
      }
    }
  }

  if (showDeviceSwitcher) {
    ModalBottomSheet(
      onDismissRequest = { showDeviceSwitcher = false },
      containerColor = MaterialTheme.colorScheme.background
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 8.dp)
          .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Switch Active Device", style = MaterialTheme.typography.headlineSmall)
          OutlinedButton(
            onClick = hapticClick {
              showDeviceSwitcher = false
              onNavigateToDevices()
            },
            shape = RoundedCornerShape(50)
          ) {
            Text("Manage")
          }
        }

        if (savedDevices.isEmpty()) {
          SlateCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("No saved devices", style = MaterialTheme.typography.titleSmall)
              Text(
                "Open Devices to add a laptop, PC, or Smart TV via Bluetooth or Wi-Fi.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateTheme.colors.textSecondary
              )
            }
          }
        } else {
          val uniqueSaved = remember(savedDevices) { savedDevices.distinctBy { it.id } }
          LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uniqueSaved, key = { it.id }) { dev ->
              val selected = activeDevice?.id == dev.id
              SlateCard(
                onClick = {
                  showDeviceSwitcher = false
                  onSelectDevice(dev)
                  onConnectDevice(dev)
                },
                backgroundColor = if (selected) SlateTheme.colors.accentSoft else MaterialTheme.colorScheme.surface,
                borderColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else SlateTheme.colors.border
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = deviceTypeIcon(dev.deviceType),
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                  )
                  Spacer(Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(dev.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                      "${dev.transportType.label} • ${dev.address}",
                      style = MaterialTheme.typography.bodySmall,
                      color = SlateTheme.colors.textSecondary
                    )
                  }
                  if (selected) {
                    ConnectionStatusDot(state = connectionState, reduceMotion = reduceMotion)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RemoteHeaderBar(
  activeDevice: RemoteDevice?,
  connectionState: ConnectionState,
  latencyMs: Int?,
  reduceMotion: Boolean,
  onSwitchDevice: () -> Unit,
  modifier: Modifier = Modifier
) {
  val transportShort = when (activeDevice?.transportType) {
    TransportType.WIFI_DESKTOP -> "Wi-Fi"
    TransportType.BLUETOOTH_HID -> "Bluetooth"
    TransportType.USB -> "USB"
    TransportType.ANDROID_TV -> "Android TV"
    null -> "Not paired"
  }

  val statusLine = when {
    activeDevice == null -> "Tap to choose a device"
    connectionState == ConnectionState.CONNECTED -> {
      val ping = latencyMs?.let { " · ${it}ms" }.orEmpty()
      "Connected · $transportShort$ping"
    }
    else -> "${connectionState.label} · $transportShort"
  }

  val dotColor = when {
    connectionState == ConnectionState.CONNECTED -> SlateTheme.colors.success
    connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.PAIRING -> SlateTheme.colors.warning
    else -> SlateTheme.colors.textSecondary.copy(alpha = 0.55f)
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = hapticClick(onSwitchDevice))
      .padding(vertical = 4.dp)
      .testTag("device_switch_button"),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.weight(1f)
    ) {
      Icon(
        imageVector = deviceTypeIcon(activeDevice?.deviceType ?: DeviceType.LAPTOP),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(26.dp)
      )

      Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
          text = activeDevice?.name ?: "No Device Selected",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Box(
            modifier = Modifier
              .size(7.dp)
              .clip(CircleShape)
              .background(dotColor)
          )
          Text(
            text = statusLine,
            style = MaterialTheme.typography.bodySmall,
            color = SlateTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }

    Icon(
      imageVector = Icons.Outlined.KeyboardArrowDown,
      contentDescription = "Switch device",
      tint = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.size(22.dp)
    )
  }
}
