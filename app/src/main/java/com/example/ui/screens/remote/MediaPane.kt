package com.example.ui.screens.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.hapticClick
import com.example.input.MediaAction
import com.example.models.ConnectionState
import com.example.models.DeviceCapabilities
import com.example.models.MediaPlaybackInfo
import com.example.ui.theme.SlateTheme
import kotlin.math.roundToInt

@Composable
fun MediaPane(
  connectionState: ConnectionState,
  capabilities: DeviceCapabilities,
  mediaInfo: MediaPlaybackInfo?,
  onSendMedia: (MediaAction, Int?) -> Unit,
  modifier: Modifier = Modifier
) {
  var sliderValue by remember(mediaInfo?.volumePercent) {
    mutableFloatStateOf((mediaInfo?.volumePercent ?: 45).toFloat())
  }

  val tapMedia: (MediaAction, Int?) -> Unit = { action, vol ->
    onSendMedia(action, vol)
  }

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .padding(vertical = 4.dp)
  ) {
    val isLandscape = maxWidth > maxHeight && maxHeight < 460.dp

    if (isLandscape) {
      Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left Column: Artwork + Title + Progress
        Column(
          modifier = Modifier
            .weight(0.85f)
            .fillMaxHeight(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Surface(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateTheme.colors.border)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = SlateTheme.colors.textSecondary,
                modifier = Modifier.size(38.dp)
              )
            }
          }
          Spacer(Modifier.height(10.dp))
          Text(
            text = mediaInfo?.title?.takeIf { it.isNotBlank() } ?: "Not Playing",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = SlateTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
          )
          Spacer(Modifier.height(10.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(4.dp)
              .clip(RoundedCornerShape(50))
              .background(SlateTheme.colors.border)
          )
        }

        // Right Column: Playback + Volume + 4 Buttons
        Column(
          modifier = Modifier
            .weight(1.35f)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.SpaceEvenly,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = hapticClick { tapMedia(MediaAction.PREVIOUS, null) },
              modifier = Modifier
                .size(48.dp)
                .testTag("media_prev_button")
            ) {
              Icon(
                imageVector = Icons.Outlined.SkipPrevious,
                contentDescription = "Previous track",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(30.dp)
              )
            }

            Surface(
              onClick = hapticClick { tapMedia(MediaAction.PLAY_PAUSE, null) },
              modifier = Modifier
                .size(58.dp)
                .testTag("media_play_pause_button"),
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primary,
              shadowElevation = 4.dp
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = if (mediaInfo?.isPlaying == false) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                  contentDescription = "Play or Pause",
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(28.dp)
                )
              }
            }

            IconButton(
              onClick = hapticClick { tapMedia(MediaAction.NEXT, null) },
              modifier = Modifier
                .size(48.dp)
                .testTag("media_next_button")
            ) {
              Icon(
                imageVector = Icons.Outlined.SkipNext,
                contentDescription = "Next track",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(30.dp)
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.VolumeDown,
              contentDescription = "Volume down",
              tint = MaterialTheme.colorScheme.onBackground,
              modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = hapticClick {
                  sliderValue = (sliderValue - 8f).coerceAtLeast(0f)
                  tapMedia(MediaAction.VOLUME_DOWN, sliderValue.roundToInt())
                })
                .testTag("media_vol_down_button")
            )

            Slider(
              value = sliderValue,
              onValueChange = { sliderValue = it },
              onValueChangeFinished = {
                tapMedia(MediaAction.VOLUME_UP, sliderValue.roundToInt())
              },
              valueRange = 0f..100f,
              colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = SlateTheme.colors.border
              ),
              modifier = Modifier.weight(1f)
            )

            Icon(
              imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
              contentDescription = "Volume up",
              tint = MaterialTheme.colorScheme.onBackground,
              modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = hapticClick {
                  sliderValue = (sliderValue + 8f).coerceAtMost(100f)
                  tapMedia(MediaAction.VOLUME_UP, sliderValue.roundToInt())
                })
                .testTag("media_vol_up_button")
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            MediaSecondaryPillButton(
              icon = Icons.Outlined.Shuffle,
              contentDescription = "Rewind / Shuffle",
              onClick = { tapMedia(MediaAction.REWIND, null) },
              modifier = Modifier.weight(1f)
            )
            MediaSecondaryPillButton(
              icon = Icons.Outlined.Repeat,
              contentDescription = "Fast forward / Repeat",
              onClick = { tapMedia(MediaAction.FAST_FORWARD, null) },
              modifier = Modifier.weight(1f)
            )
            MediaSecondaryPillButton(
              icon = Icons.Outlined.Stop,
              contentDescription = "Stop",
              onClick = { tapMedia(MediaAction.STOP, null) },
              modifier = Modifier.weight(1f)
            )
            MediaSecondaryPillButton(
              icon = Icons.AutoMirrored.Outlined.VolumeOff,
              contentDescription = "Mute",
              onClick = { tapMedia(MediaAction.MUTE, null) },
              modifier = Modifier
                .weight(1f)
                .testTag("media_mute_button")
            )
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
        // 1. Album Artwork Placeholder & Status Label matching reference screen 10
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Spacer(Modifier.height(6.dp))

          Surface(
            modifier = Modifier.size(104.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateTheme.colors.border)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = SlateTheme.colors.textSecondary,
                modifier = Modifier.size(48.dp)
              )
            }
          }

          Text(
            text = mediaInfo?.title?.takeIf { it.isNotBlank() } ?: "Not Playing",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = SlateTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
          )

          Spacer(Modifier.height(6.dp))

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(4.dp)
              .clip(RoundedCornerShape(50))
              .background(SlateTheme.colors.border)
          )
        }

        // 2. Central Playback Controls: Prev | Blue Circle Pause/Play | Next
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = hapticClick { tapMedia(MediaAction.PREVIOUS, null) },
            modifier = Modifier
              .size(52.dp)
              .testTag("media_prev_button")
          ) {
            Icon(
              imageVector = Icons.Outlined.SkipPrevious,
              contentDescription = "Previous track",
              tint = MaterialTheme.colorScheme.onBackground,
              modifier = Modifier.size(34.dp)
            )
          }

          Surface(
            onClick = hapticClick { tapMedia(MediaAction.PLAY_PAUSE, null) },
            modifier = Modifier
              .size(68.dp)
              .testTag("media_play_pause_button"),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 4.dp
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = if (mediaInfo?.isPlaying == false) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                contentDescription = "Play or Pause",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(34.dp)
              )
            }
          }

          IconButton(
            onClick = hapticClick { tapMedia(MediaAction.NEXT, null) },
            modifier = Modifier
              .size(52.dp)
              .testTag("media_next_button")
          ) {
            Icon(
              imageVector = Icons.Outlined.SkipNext,
              contentDescription = "Next track",
              tint = MaterialTheme.colorScheme.onBackground,
              modifier = Modifier.size(34.dp)
            )
          }
        }

        // 3. Volume Slider & 4 Bottom Action Buttons
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.VolumeDown,
              contentDescription = "Volume down",
              tint = MaterialTheme.colorScheme.onBackground,
              modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = hapticClick {
                  sliderValue = (sliderValue - 8f).coerceAtLeast(0f)
                  tapMedia(MediaAction.VOLUME_DOWN, sliderValue.roundToInt())
                })
                .testTag("media_vol_down_button")
            )

            Slider(
              value = sliderValue,
              onValueChange = { sliderValue = it },
              onValueChangeFinished = {
                tapMedia(MediaAction.VOLUME_UP, sliderValue.roundToInt())
              },
              valueRange = 0f..100f,
              colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = SlateTheme.colors.border
              ),
              modifier = Modifier.weight(1f)
            )

            Icon(
              imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
              contentDescription = "Volume up",
              tint = MaterialTheme.colorScheme.onBackground,
              modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = hapticClick {
                  sliderValue = (sliderValue + 8f).coerceAtMost(100f)
                  tapMedia(MediaAction.VOLUME_UP, sliderValue.roundToInt())
                })
                .testTag("media_vol_up_button")
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            MediaSecondaryPillButton(
              icon = Icons.Outlined.Shuffle,
              contentDescription = "Rewind / Shuffle",
              onClick = { tapMedia(MediaAction.REWIND, null) },
              modifier = Modifier.weight(1f)
            )
            MediaSecondaryPillButton(
              icon = Icons.Outlined.Repeat,
              contentDescription = "Fast forward / Repeat",
              onClick = { tapMedia(MediaAction.FAST_FORWARD, null) },
              modifier = Modifier.weight(1f)
            )
            MediaSecondaryPillButton(
              icon = Icons.Outlined.Stop,
              contentDescription = "Stop",
              onClick = { tapMedia(MediaAction.STOP, null) },
              modifier = Modifier.weight(1f)
            )
            MediaSecondaryPillButton(
              icon = Icons.AutoMirrored.Outlined.VolumeOff,
              contentDescription = "Mute",
              onClick = { tapMedia(MediaAction.MUTE, null) },
              modifier = Modifier
                .weight(1f)
                .testTag("media_mute_button")
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MediaSecondaryPillButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    onClick = hapticClick(onClick),
    modifier = modifier.height(48.dp),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(20.dp)
      )
    }
  }
}
