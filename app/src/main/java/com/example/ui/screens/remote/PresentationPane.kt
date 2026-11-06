package com.example.ui.screens.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.components.hapticClick
import com.example.input.PresentationAction
import com.example.models.ConnectionState
import com.example.models.DeviceCapabilities
import com.example.preferences.PresentationSoftwarePreset

@Composable
fun PresentationPane(
  connectionState: ConnectionState,
  capabilities: DeviceCapabilities,
  currentPreset: PresentationSoftwarePreset,
  onSendPresentation: (PresentationAction, Float?, Float?) -> Unit,
  onSelectPreset: (PresentationSoftwarePreset) -> Unit,
  modifier: Modifier = Modifier
) {

  val trigger: (PresentationAction) -> Unit = { action ->
    onSendPresentation(action, null, null)
  }

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .padding(top = 4.dp)
  ) {
    val isLandscape = maxWidth > maxHeight && maxHeight < 460.dp

    if (isLandscape) {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          PresentationGridCard(
            icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            label = "Previous",
            iconSize = 26.dp,
            onClick = { trigger(PresentationAction.PREVIOUS_SLIDE) },
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
              .testTag("presentation_prev_slide")
          )
          PresentationGridCard(
            icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            label = "Next",
            iconSize = 26.dp,
            onClick = { trigger(PresentationAction.NEXT_SLIDE) },
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
              .testTag("presentation_next_slide")
          )
          PresentationGridCard(
            icon = Icons.Outlined.PlayArrow,
            label = "Start Slideshow",
            iconSize = 24.dp,
            onClick = { trigger(PresentationAction.START_PRESENTATION) },
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
          )
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          PresentationGridCard(
            icon = Icons.Outlined.Stop,
            label = "End Slideshow",
            iconSize = 22.dp,
            onClick = { trigger(PresentationAction.END_PRESENTATION) },
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
          )
          PresentationGridCard(
            icon = Icons.Outlined.Tv,
            label = "Black Screen",
            iconSize = 22.dp,
            onClick = { trigger(PresentationAction.BLACK_SCREEN) },
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
          )
          PresentationGridCard(
            icon = Icons.Outlined.DesktopWindows,
            label = "White Screen",
            iconSize = 22.dp,
            onClick = { trigger(PresentationAction.WHITE_SCREEN) },
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
          )
        }
      }
    } else {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Row 1: Previous | Next (matching reference screen 11)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1.15f),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          PresentationGridCard(
            icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            label = "Previous",
            iconSize = 32.dp,
            onClick = { trigger(PresentationAction.PREVIOUS_SLIDE) },
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
              .testTag("presentation_prev_slide")
          )

          PresentationGridCard(
            icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            label = "Next",
            iconSize = 32.dp,
            onClick = { trigger(PresentationAction.NEXT_SLIDE) },
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
              .testTag("presentation_next_slide")
          )
        }

        // Row 2: Start Slideshow | End Slideshow
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          PresentationGridCard(
            icon = Icons.Outlined.PlayArrow,
            label = "Start\nSlideshow",
            iconSize = 26.dp,
            onClick = { trigger(PresentationAction.START_PRESENTATION) },
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
          )

          PresentationGridCard(
            icon = Icons.Outlined.Stop,
            label = "End\nSlideshow",
            iconSize = 24.dp,
            onClick = { trigger(PresentationAction.END_PRESENTATION) },
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
          )
        }

        // Row 3: Black Screen | White Screen
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          PresentationGridCard(
            icon = Icons.Outlined.Tv,
            label = "Black\nScreen",
            iconSize = 24.dp,
            onClick = { trigger(PresentationAction.BLACK_SCREEN) },
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
          )

          PresentationGridCard(
            icon = Icons.Outlined.DesktopWindows,
            label = "White\nScreen",
            iconSize = 24.dp,
            onClick = { trigger(PresentationAction.WHITE_SCREEN) },
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
          )
        }
      }
    }
  }
}

@Composable
private fun PresentationGridCard(
  icon: ImageVector,
  label: String,
  iconSize: Dp,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    onClick = hapticClick(onClick),
    modifier = modifier,
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(iconSize)
        )
        Spacer(Modifier.height(8.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center
        )
      }
    }
  }
}
