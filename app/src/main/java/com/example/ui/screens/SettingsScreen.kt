package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.gestures.ThreeFingerShortcutAction
import com.example.models.BluetoothCapabilityReport
import com.example.models.ConnectionState
import com.example.models.RemoteDevice
import com.example.models.TransportType
import com.example.preferences.OrientationPreference
import com.example.preferences.PresentationSoftwarePreset
import com.example.preferences.ThemeMode
import com.example.preferences.TouchpadSurfaceStyle
import com.example.preferences.UserSettings
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.SlateCard
import com.example.ui.components.deviceTypeIcon
import com.example.ui.components.hapticClick
import com.example.ui.components.rememberHapticPulse
import com.example.ui.theme.SlateTheme
import kotlin.math.roundToInt

private enum class SettingsSubPage(val title: String) {
  TOUCHPAD("Touchpad"),
  APPEARANCE("Appearance"),
  GENERAL("General"),
  PRIVACY("Privacy & Security"),
  ABOUT("About")
}

@Composable
fun SettingsScreen(
  settings: UserSettings,
  savedDevices: List<RemoteDevice>,
  bluetoothCapability: BluetoothCapabilityReport,
  activeDevice: RemoteDevice? = null,
  connectionState: ConnectionState = ConnectionState.DISCONNECTED,
  localWifiIp: String? = null,
  onNavigateToDevices: () -> Unit = {},
  onDisconnect: () -> Unit = {},
  onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit,
  onClearPairings: () -> Unit,
  modifier: Modifier = Modifier
) {
  var activeSubPage by remember { mutableStateOf<SettingsSubPage?>(null) }
  var showClearConfirm by remember { mutableStateOf(false) }

  if (activeSubPage != null) {
    BackHandler { activeSubPage = null }
  }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    val subPage = activeSubPage
    if (subPage == null) {
      // Main Settings Category List matching Screen 14 of reference image
      LazyColumn(
        modifier = Modifier
          .widthIn(max = 640.dp)
          .fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        item {
          Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 6.dp)
          )
        }

        item {
          SettingsCategoryCard(
            icon = Icons.Outlined.TouchApp,
            title = "Touchpad",
            subtitle = "Sensitivity, gestures, scrolling",
            onClick = { activeSubPage = SettingsSubPage.TOUCHPAD },
            testTag = "settings_category_touchpad"
          )
        }

        item {
          SettingsCategoryCard(
            icon = Icons.Outlined.BrightnessMedium,
            title = "Appearance",
            subtitle = "Light, Dark, System",
            onClick = { activeSubPage = SettingsSubPage.APPEARANCE },
            testTag = "settings_category_appearance"
          )
        }

        item {
          SettingsCategoryCard(
            icon = Icons.Outlined.Settings,
            title = "General",
            subtitle = "Screen, orientation, app behavior",
            onClick = { activeSubPage = SettingsSubPage.GENERAL },
            testTag = "settings_category_general"
          )
        }

        item {
          SettingsCategoryCard(
            icon = Icons.Outlined.Lock,
            title = "Privacy & Security",
            subtitle = "Permissions, pairing, data",
            onClick = { activeSubPage = SettingsSubPage.PRIVACY },
            testTag = "settings_category_privacy"
          )
        }

        item {
          SettingsCategoryCard(
            icon = Icons.Outlined.Info,
            title = "About",
            subtitle = "App version, licenses",
            onClick = { activeSubPage = SettingsSubPage.ABOUT },
            testTag = "settings_category_about"
          )
        }
      }
    } else {
      // Sub-page Detail View
      Column(
        modifier = Modifier
          .widthIn(max = 640.dp)
          .fillMaxSize()
          .padding(horizontal = 20.dp, vertical = 12.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = hapticClick { activeSubPage = null },
            modifier = Modifier.size(40.dp)
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
              tint = MaterialTheme.colorScheme.onBackground
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = subPage.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
          )
        }

        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(bottom = 24.dp)
        ) {
          when (subPage) {
            SettingsSubPage.TOUCHPAD -> item {
              SlateCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                  SettingsSliderRow(
                    title = "Pointer Sensitivity",
                    value = settings.pointerSensitivity,
                    range = 0.5f..3.5f,
                    formatValue = { v -> "${(v * 10).roundToInt() / 10f}x" },
                    onValueCommit = { v -> onUpdateSettings { it.copy(pointerSensitivity = v) } }
                  )

                  SettingsSliderRow(
                    title = "Pointer Acceleration",
                    value = settings.pointerAcceleration,
                    range = 0f..1f,
                    formatValue = { v -> "${(v * 100).roundToInt()}%" },
                    onValueCommit = { v -> onUpdateSettings { it.copy(pointerAcceleration = v) } }
                  )

                  SettingsSliderRow(
                    title = "Scroll Speed",
                    value = settings.scrollSpeed,
                    range = 0.4f..2.5f,
                    formatValue = { v -> "${(v * 10).roundToInt() / 10f}x" },
                    onValueCommit = { v -> onUpdateSettings { it.copy(scrollSpeed = v) } }
                  )

                  HorizontalDivider(color = SlateTheme.colors.border)

                  SettingsSwitchRow(
                    title = "Natural Scrolling",
                    subtitle = "Content moves in the direction of your fingers",
                    checked = settings.naturalScrolling,
                    onCheckedChange = { c -> onUpdateSettings { it.copy(naturalScrolling = c) } }
                  )
                  SettingsSwitchRow(
                    title = "Tap to Click",
                    subtitle = "Single finger tap emits left click",
                    checked = settings.tapToClick,
                    onCheckedChange = { c -> onUpdateSettings { it.copy(tapToClick = c) } }
                  )
                  SettingsSwitchRow(
                    title = "Two-Finger Right Click",
                    subtitle = "Two-finger tap emits right click",
                    checked = settings.twoFingerRightClick,
                    onCheckedChange = { c -> onUpdateSettings { it.copy(twoFingerRightClick = c) } }
                  )
                  SettingsSwitchRow(
                    title = "Left-Handed Layout",
                    subtitle = "Swap Left Click and Right Click buttons",
                    checked = settings.leftHandedLayout,
                    onCheckedChange = { c -> onUpdateSettings { it.copy(leftHandedLayout = c) } }
                  )

                  HorizontalDivider(color = SlateTheme.colors.border)

                  Text("Three-Finger Swipe Up", style = MaterialTheme.typography.titleSmall)
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                      ThreeFingerShortcutAction.TASK_SWITCHER,
                      ThreeFingerShortcutAction.SHOW_DESKTOP,
                      ThreeFingerShortcutAction.NONE
                    ).forEach { act ->
                      val selected = settings.threeFingerSwipeUp == act
                      Surface(
                        onClick = hapticClick { onUpdateSettings { it.copy(threeFingerSwipeUp = act) } },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else SlateTheme.colors.elevatedSurface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else SlateTheme.colors.border)
                      ) {
                        Text(
                          text = act.label,
                          style = MaterialTheme.typography.labelSmall,
                          color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          textAlign = TextAlign.Center,
                          modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 10.dp)
                        )
                      }
                    }
                  }
                }
              }
            }

            SettingsSubPage.APPEARANCE -> item {
              SlateCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                  Text("Theme", style = MaterialTheme.typography.titleSmall)
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemeMode.entries.forEach { mode ->
                      val selected = settings.themeMode == mode
                      Surface(
                        onClick = hapticClick { onUpdateSettings { it.copy(themeMode = mode) } },
                        modifier = Modifier
                          .weight(1f)
                          .testTag("theme_mode_${mode.name}"),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else SlateTheme.colors.elevatedSurface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else SlateTheme.colors.border)
                      ) {
                        Text(
                          text = mode.label.replace("Follow ", ""),
                          style = MaterialTheme.typography.labelMedium,
                          color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                          maxLines = 1,
                          textAlign = TextAlign.Center,
                          modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 10.dp)
                        )
                      }
                    }
                  }

                  Text("Touchpad Style", style = MaterialTheme.typography.titleSmall)
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TouchpadSurfaceStyle.entries.forEach { style ->
                      val selected = settings.touchpadStyle == style
                      val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                      Surface(
                        onClick = hapticClick { onUpdateSettings { it.copy(touchpadStyle = style) } },
                        modifier = Modifier
                          .weight(1f)
                          .testTag("touchpad_style_${style.name}"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else SlateTheme.colors.elevatedSurface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else SlateTheme.colors.border)
                      ) {
                        Column(
                          horizontalAlignment = Alignment.CenterHorizontally,
                          verticalArrangement = Arrangement.spacedBy(6.dp),
                          modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 10.dp)
                        ) {
                          androidx.compose.foundation.Canvas(
                            modifier = Modifier
                              .fillMaxWidth(0.75f)
                              .height(22.dp)
                          ) {
                            val strokeColor = fg.copy(alpha = 0.55f)
                            drawRoundRect(
                              color = strokeColor,
                              cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                              style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                            )
                            when (style) {
                              TouchpadSurfaceStyle.FLAT -> {
                                drawLine(
                                  color = strokeColor,
                                  start = androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height - 4.dp.toPx()),
                                  end = androidx.compose.ui.geometry.Offset(size.width * 0.65f, size.height - 4.dp.toPx()),
                                  strokeWidth = 1.2.dp.toPx()
                                )
                              }
                              TouchpadSurfaceStyle.BORDERED -> {
                                val pad = 4.dp.toPx()
                                drawRoundRect(
                                  color = fg.copy(alpha = 0.35f),
                                  topLeft = androidx.compose.ui.geometry.Offset(pad, pad),
                                  size = androidx.compose.ui.geometry.Size(size.width - pad * 2f, size.height - pad * 2f),
                                  cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                                  style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.9.dp.toPx())
                                )
                              }
                              TouchpadSurfaceStyle.DOTTED -> {
                                val cols = 4
                                val rows = 2
                                for (c in 1..cols) {
                                  for (r in 1..rows) {
                                    drawCircle(
                                      color = strokeColor,
                                      radius = 1.dp.toPx(),
                                      center = androidx.compose.ui.geometry.Offset(
                                        size.width * c / (cols + 1),
                                        size.height * r / (rows + 1)
                                      )
                                    )
                                  }
                                }
                              }
                            }
                          }
                          Text(
                            text = style.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = fg,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                          )
                        }
                      }
                    }
                  }

                  SettingsSwitchRow(
                    title = "Minimal Animations",
                    subtitle = "Reduce UI transitions",
                    checked = settings.minimalAnimations,
                    onCheckedChange = { c -> onUpdateSettings { it.copy(minimalAnimations = c) } }
                  )
                }
              }
            }

            SettingsSubPage.GENERAL -> item {
              SlateCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                  SettingsSwitchRow(
                    title = "Auto-Reconnect",
                    subtitle = "Reconnect to your last paired device on launch",
                    checked = settings.autoReconnect,
                    onCheckedChange = { c -> onUpdateSettings { it.copy(autoReconnect = c) } }
                  )

                  SettingsSwitchRow(
                    title = "Auto-Hide System Navigation Bar",
                    subtitle = "Hide phone Back, Home & Recents bar; swipe from bottom edge to reveal",
                    checked = settings.autoHideSystemNavBar,
                    onCheckedChange = { c -> onUpdateSettings { it.copy(autoHideSystemNavBar = c) } }
                  )

                  SettingsSwitchRow(
                    title = "Keep Screen Awake",
                    subtitle = "Prevent screen sleep while connected",
                    checked = settings.keepScreenAwake,
                    onCheckedChange = { c -> onUpdateSettings { it.copy(keepScreenAwake = c) } }
                  )

                  SettingsSwitchRow(
                    title = "Haptic Feedback",
                    subtitle = "Feel a light pulse when tapping app controls",
                    checked = settings.hapticFeedback,
                    onCheckedChange = { c -> onUpdateSettings { it.copy(hapticFeedback = c) } }
                  )

                  Text("Orientation", style = MaterialTheme.typography.titleSmall)
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrientationPreference.entries.forEach { pref ->
                      val selected = settings.orientationPreference == pref
                      Surface(
                        onClick = hapticClick { onUpdateSettings { it.copy(orientationPreference = pref) } },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else SlateTheme.colors.elevatedSurface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else SlateTheme.colors.border)
                      ) {
                        Text(
                          text = pref.label,
                          style = MaterialTheme.typography.labelSmall,
                          color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                          maxLines = 1,
                          textAlign = TextAlign.Center,
                          modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 10.dp)
                        )
                      }
                    }
                  }

                  HorizontalDivider(color = SlateTheme.colors.border)
                  Text("Presentation Key Preset", style = MaterialTheme.typography.titleSmall)
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresentationSoftwarePreset.entries.forEach { preset ->
                      val selected = settings.presentationPreset == preset
                      Surface(
                        onClick = hapticClick { onUpdateSettings { it.copy(presentationPreset = preset) } },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else SlateTheme.colors.elevatedSurface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else SlateTheme.colors.border)
                      ) {
                        Text(
                          text = preset.label.substringBefore(" "),
                          style = MaterialTheme.typography.labelSmall,
                          color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                          maxLines = 1,
                          textAlign = TextAlign.Center,
                          modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 10.dp)
                        )
                      }
                    }
                  }
                }
              }
            }

            SettingsSubPage.PRIVACY -> item {
              SlateCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                  Text("Local-First Privacy", style = MaterialTheme.typography.titleSmall)
                  Text(
                    text = "AIO Remote operates strictly over your local Bluetooth HID link or local Wi-Fi network without cloud servers, accounts, ads, or analytics. Pairing tokens are encrypted via Android Keystore (AES-256-GCM), and keyboard input is never logged or stored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTheme.colors.textSecondary
                  )
                  HorizontalDivider(color = SlateTheme.colors.border)
                  SettingsSwitchRow(
                    title = "Manual Clipboard Paste",
                    subtitle = "Allow sending phone clipboard from Keyboard mode",
                    checked = settings.clipboardSyncOptIn,
                    onCheckedChange = { c -> onUpdateSettings { it.copy(clipboardSyncOptIn = c) } }
                  )

                  OutlinedButton(
                    onClick = hapticClick { showClearConfirm = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Text("Clear All Saved Pairings & Tokens")
                  }
                }
              }
            }

            SettingsSubPage.ABOUT -> item {
              SlateCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                  Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTheme.colors.textSecondary
                  )
                  Text(
                    text = stringResource(R.string.app_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTheme.colors.textSecondary
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  if (showClearConfirm) {
    AlertDialog(
      onDismissRequest = { showClearConfirm = false },
      title = { Text("Clear All Pairings?") },
      text = { Text("This removes all saved devices and hardware-encrypted pairing tokens from AIO Remote.") },
      confirmButton = {
        Button(
          onClick = hapticClick {
            showClearConfirm = false
            onClearPairings()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Text("Clear All")
        }
      },
      dismissButton = { TextButton(onClick = hapticClick { showClearConfirm = false }) { Text("Cancel") } }
    )
  }
}

@Composable
private fun SettingsCategoryCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  testTag: String
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = hapticClick(onClick))
      .testTag(testTag),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, SlateTheme.colors.border)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 15.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(22.dp)
      )
      Spacer(modifier = Modifier.width(14.dp))
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = SlateTheme.colors.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = SlateTheme.colors.textSecondary,
        modifier = Modifier.size(20.dp)
      )
    }
  }
}

@Composable
private fun SettingsSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SlateTheme.colors.textSecondary)
    }
    val pulse = rememberHapticPulse()
    Switch(
      checked = checked,
      onCheckedChange = {
        pulse()
        onCheckedChange(it)
      }
    )
  }
}

@Composable
private fun StatusBadge(
  label: String,
  isPositive: Boolean
) {
  Surface(
    shape = RoundedCornerShape(50),
    color = if (isPositive) SlateTheme.colors.accentSoft else MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
      color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSliderRow(
  title: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  formatValue: (Float) -> String,
  onValueCommit: (Float) -> Unit
) {
  var draftValue by remember(value) { mutableFloatStateOf(value) }
  val interactionSource = remember { MutableInteractionSource() }
  val sliderColors = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = SlateTheme.colors.border
  )

  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Text(
        text = formatValue(draftValue),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary
      )
    }
    Slider(
      value = draftValue,
      onValueChange = { draftValue = it },
      onValueChangeFinished = { onValueCommit(draftValue) },
      valueRange = range,
      interactionSource = interactionSource,
      colors = sliderColors,
      thumb = {
        Box(
          modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.onPrimary)
          )
        }
      },
      track = { sliderState ->
        SliderDefaults.Track(
          sliderState = sliderState,
          colors = sliderColors,
          thumbTrackGapSize = 0.dp,
          drawStopIndicator = null,
          modifier = Modifier.height(6.dp)
        )
      }
    )
  }
}
