package com.example.ui.screens.remote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Input
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.components.hapticClick
import com.example.input.TvAction
import com.example.models.ConnectionState
import com.example.models.DeviceCapabilities
import com.example.models.RemoteDevice
import com.example.ui.theme.SlateTheme

@Composable
fun TvRemotePane(
  activeDevice: RemoteDevice?,
  connectionState: ConnectionState,
  capabilities: DeviceCapabilities,
  onSendTv: (TvAction, String?) -> Unit,
  onSendText: (String) -> Unit,
  onOpenDevices: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showKeyboardDialog by remember { mutableStateOf(false) }
  var tvTextQuery by remember { mutableStateOf("") }

  val triggerTv: (TvAction) -> Unit = { action ->
    onSendTv(action, null)
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isLandscape = maxWidth > maxHeight && maxHeight < 460.dp
    val compact = maxHeight < 520.dp
    val dpadSize = when {
      isLandscape -> 170.dp
      compact -> 182.dp
      else -> 206.dp
    }

    if (isLandscape) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left Column: Circular D-Pad
        Box(
          modifier = Modifier
            .weight(0.95f)
            .fillMaxHeight(),
          contentAlignment = Alignment.Center
        ) {
          CircularTvDpad(
            size = dpadSize,
            onAction = triggerTv
          )
        }

        // Right Column: Power/Home/Menu + Back/Keyboard/Input/Settings + Volume Pill
        Column(
          modifier = Modifier
            .weight(1.15f)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.SpaceEvenly,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            TvCircleButton(
              icon = Icons.Outlined.PowerSettingsNew,
              contentDescription = "Power",
              iconTint = Color(0xFFEF4444),
              size = 46.dp,
              onClick = { triggerTv(TvAction.POWER) }
            )
            TvCircleButton(
              icon = Icons.Outlined.Home,
              contentDescription = "Home",
              size = 46.dp,
              modifier = Modifier.testTag("tv_home_button"),
              onClick = { triggerTv(TvAction.HOME) }
            )
            TvCircleButton(
              icon = Icons.Outlined.Menu,
              contentDescription = "Menu",
              size = 46.dp,
              onClick = { triggerTv(TvAction.MENU) }
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            TvCircleButton(
              icon = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
              size = 44.dp,
              modifier = Modifier.testTag("tv_back_button"),
              onClick = { triggerTv(TvAction.BACK) }
            )
            TvCircleButton(
              icon = Icons.Outlined.Keyboard,
              contentDescription = "TV Keyboard",
              size = 44.dp,
              onClick = { showKeyboardDialog = true }
            )
            TvCircleButton(
              icon = Icons.AutoMirrored.Outlined.Input,
              contentDescription = "Input Source",
              size = 44.dp,
              onClick = { triggerTv(TvAction.INPUT_SOURCE) }
            )
            TvCircleButton(
              icon = Icons.Outlined.Settings,
              contentDescription = "TV Settings",
              size = 44.dp,
              modifier = Modifier.testTag("tv_play_button"),
              onClick = { triggerTv(TvAction.MENU) }
            )
          }

          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, SlateTheme.colors.border)
          ) {
            Row(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
              horizontalArrangement = Arrangement.SpaceEvenly,
              verticalAlignment = Alignment.CenterVertically
            ) {
              IconButton(onClick = hapticClick { triggerTv(TvAction.MUTE) }) {
                Icon(
                  imageVector = Icons.AutoMirrored.Outlined.VolumeOff,
                  contentDescription = "Mute",
                  tint = MaterialTheme.colorScheme.onSurface
                )
              }
              IconButton(onClick = hapticClick { triggerTv(TvAction.VOLUME_DOWN) }) {
                Icon(
                  imageVector = Icons.Outlined.Remove,
                  contentDescription = "Volume Down",
                  tint = MaterialTheme.colorScheme.onSurface
                )
              }
              IconButton(onClick = hapticClick { triggerTv(TvAction.PLAY_PAUSE) }) {
                Icon(
                  imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                  contentDescription = "Volume",
                  tint = MaterialTheme.colorScheme.onSurface
                )
              }
              IconButton(onClick = hapticClick { triggerTv(TvAction.VOLUME_UP) }) {
                Icon(
                  imageVector = Icons.Outlined.Add,
                  contentDescription = "Volume Up",
                  tint = MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }
      }
    } else {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        // Top Row: 3 Circular Buttons (Red Power, Home, Menu)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          TvCircleButton(
            icon = Icons.Outlined.PowerSettingsNew,
            contentDescription = "Power",
            iconTint = Color(0xFFEF4444),
            size = 54.dp,
            onClick = { triggerTv(TvAction.POWER) }
          )
          TvCircleButton(
            icon = Icons.Outlined.Home,
            contentDescription = "Home",
            size = 54.dp,
            modifier = Modifier.testTag("tv_home_button"),
            onClick = { triggerTv(TvAction.HOME) }
          )
          TvCircleButton(
            icon = Icons.Outlined.Menu,
            contentDescription = "Menu",
            size = 54.dp,
            onClick = { triggerTv(TvAction.MENU) }
          )
        }

        // Center: Large Light-Gray Circular D-Pad with White Center "OK" Circle
        CircularTvDpad(
          size = dpadSize,
          onAction = triggerTv
        )

        // Row Below D-Pad: 4 Circular Buttons (Back, Keyboard, Input, Settings)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          TvCircleButton(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            size = 50.dp,
            modifier = Modifier.testTag("tv_back_button"),
            onClick = { triggerTv(TvAction.BACK) }
          )
          TvCircleButton(
            icon = Icons.Outlined.Keyboard,
            contentDescription = "TV Keyboard",
            size = 50.dp,
            onClick = { showKeyboardDialog = true }
          )
          TvCircleButton(
            icon = Icons.AutoMirrored.Outlined.Input,
            contentDescription = "Input Source",
            size = 50.dp,
            onClick = { triggerTv(TvAction.INPUT_SOURCE) }
          )
          TvCircleButton(
            icon = Icons.Outlined.Settings,
            contentDescription = "TV Settings",
            size = 50.dp,
            modifier = Modifier.testTag("tv_play_button"),
            onClick = { triggerTv(TvAction.MENU) }
          )
        }

        // Bottom Pill Volume Bar: Mute | - | Speaker | +
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, SlateTheme.colors.border)
        ) {
          Row(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(onClick = hapticClick { triggerTv(TvAction.MUTE) }) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.VolumeOff,
                contentDescription = "Mute",
                tint = MaterialTheme.colorScheme.onSurface
              )
            }
            IconButton(onClick = hapticClick { triggerTv(TvAction.VOLUME_DOWN) }) {
              Icon(
                imageVector = Icons.Outlined.Remove,
                contentDescription = "Volume Down",
                tint = MaterialTheme.colorScheme.onSurface
              )
            }
            IconButton(onClick = hapticClick { triggerTv(TvAction.PLAY_PAUSE) }) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = "Volume",
                tint = MaterialTheme.colorScheme.onSurface
              )
            }
            IconButton(onClick = hapticClick { triggerTv(TvAction.VOLUME_UP) }) {
              Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "Volume Up",
                tint = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }
      }
    }
  }

  if (showKeyboardDialog) {
    AlertDialog(
      onDismissRequest = { showKeyboardDialog = false },
      title = { Text("Send Text to TV") },
      text = {
        OutlinedTextField(
          value = tvTextQuery,
          onValueChange = { tvTextQuery = it },
          placeholder = { Text("Type search or text…") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(
            onSend = {
              if (tvTextQuery.isNotBlank()) {
                onSendText(tvTextQuery)
                tvTextQuery = ""
                showKeyboardDialog = false
              }
            }
          ),
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        Button(
          onClick = hapticClick {
            if (tvTextQuery.isNotBlank()) {
              onSendText(tvTextQuery)
              tvTextQuery = ""
            }
            showKeyboardDialog = false
          }
        ) {
          Text("Send")
        }
      },
      dismissButton = {
        TextButton(onClick = hapticClick { showKeyboardDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun TvCircleButton(
  icon: ImageVector,
  contentDescription: String,
  size: Dp,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  iconTint: Color = MaterialTheme.colorScheme.onSurface
) {
  Surface(
    onClick = hapticClick(onClick),
    modifier = modifier.size(size),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, SlateTheme.colors.border)
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = iconTint,
        modifier = Modifier.size(22.dp)
      )
    }
  }
}

@Composable
private fun CircularTvDpad(
  size: Dp,
  onAction: (TvAction) -> Unit
) {
  val dirBtnSize = if (size < 192.dp) 48.dp else 54.dp
  val okSize = if (size < 192.dp) 62.dp else 70.dp
  val arrowInset = 4.dp

  Surface(
    modifier = Modifier.size(size),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, SlateTheme.colors.border)
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      DpadDirectionButton(
        onClick = { onAction(TvAction.DPAD_UP) },
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = arrowInset)
          .size(dirBtnSize)
          .testTag("tv_dpad_up")
      ) {
        Icon(
          imageVector = Icons.Outlined.KeyboardArrowUp,
          contentDescription = "Up",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(26.dp)
        )
      }
      DpadDirectionButton(
        onClick = { onAction(TvAction.DPAD_DOWN) },
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = arrowInset)
          .size(dirBtnSize)
          .testTag("tv_dpad_down")
      ) {
        Icon(
          imageVector = Icons.Outlined.KeyboardArrowDown,
          contentDescription = "Down",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(26.dp)
        )
      }
      DpadDirectionButton(
        onClick = { onAction(TvAction.DPAD_LEFT) },
        modifier = Modifier
          .align(Alignment.CenterStart)
          .padding(start = arrowInset)
          .size(dirBtnSize)
          .testTag("tv_dpad_left")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
          contentDescription = "Left",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(26.dp)
        )
      }
      DpadDirectionButton(
        onClick = { onAction(TvAction.DPAD_RIGHT) },
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(end = arrowInset)
          .size(dirBtnSize)
          .testTag("tv_dpad_right")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
          contentDescription = "Right",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(26.dp)
        )
      }

      // Center OK circle adapting cleanly to Light & Dark theme
      Surface(
        onClick = hapticClick { onAction(TvAction.DPAD_CENTER) },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, SlateTheme.colors.border),
        modifier = Modifier
          .align(Alignment.Center)
          .size(okSize)
          .testTag("tv_dpad_ok")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = "OK",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
          )
        }
      }
    }
  }
}

@Composable
private fun DpadDirectionButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  Box(
    modifier = modifier
      .clip(CircleShape)
      .clickable(onClick = hapticClick(onClick)),
    contentAlignment = Alignment.Center
  ) {
    content()
  }
}
