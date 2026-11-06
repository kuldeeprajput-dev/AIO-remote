package com.example.ui.screens.remote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PresentToAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.hapticClick
import com.example.domain.MacroStep
import com.example.domain.ShortcutAction
import com.example.domain.ShortcutIconKey
import com.example.domain.ShortcutItem
import com.example.input.HidKeyCode
import com.example.input.HidModifiers
import com.example.models.ConnectionState
import com.example.ui.theme.SlateTheme
import java.util.UUID

@Composable
fun ShortcutsPane(
  shortcuts: List<ShortcutItem>,
  connectionState: ConnectionState,
  onExecuteShortcut: (ShortcutItem) -> Unit,
  onMoveShortcut: (Int, Int) -> Unit,
  onAddShortcut: (ShortcutItem) -> Unit,
  onRemoveShortcut: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var showAddDialog by remember { mutableStateOf(false) }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val columns = if (maxWidth > maxHeight && maxWidth >= 500.dp) 3 else 2
    val cardHeight = if (maxHeight < 460.dp) 82.dp else 98.dp

    val uniqueShortcuts = remember(shortcuts) { shortcuts.distinctBy { it.id } }

    LazyVerticalGrid(
      columns = GridCells.Fixed(columns),
      contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.fillMaxSize()
    ) {
      items(uniqueShortcuts, key = { it.id }) { item ->
        Surface(
          onClick = hapticClick {
            onExecuteShortcut(item)
          },
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, SlateTheme.colors.border),
          modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .testTag("shortcut_tile_${item.id}")
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = shortcutIconFor(item.icon),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = formatTwoLineShortcutLabel(item.name),
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      item(span = { GridItemSpan(columns) }) {
        val dashedColor = SlateTheme.colors.border
        Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp)
          .height(54.dp)
          .clip(RoundedCornerShape(14.dp))
          .clickable(onClick = hapticClick { showAddDialog = true })
          .testTag("add_shortcut_button"),
        contentAlignment = Alignment.Center
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          drawRoundRect(
            color = dashedColor,
            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
            style = Stroke(
              width = 1.4.dp.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
            )
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Add Shortcut",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        }
      }
    }
  }

  if (showAddDialog) {
    AddShortcutMacroDialog(
      onDismiss = { showAddDialog = false },
      onSave = { newItem ->
        onAddShortcut(newItem)
        showAddDialog = false
      }
    )
  }
}

private fun formatTwoLineShortcutLabel(name: String): String {
  val trimmed = name.trim()
  val spaceIndex = trimmed.indexOf(' ')
  return if (spaceIndex > 0 && !trimmed.contains('\n')) {
    trimmed.substring(0, spaceIndex) + "\n" + trimmed.substring(spaceIndex + 1)
  } else {
    trimmed
  }
}

@Composable
private fun AddShortcutMacroDialog(
  onDismiss: () -> Unit,
  onSave: (ShortcutItem) -> Unit
) {
  var name by remember { mutableStateOf("") }
  var selectedKey by remember { mutableStateOf(HidKeyCode.T) }
  var modMask by remember { mutableIntStateOf(HidModifiers.LEFT_CTRL) }
  var isMacroTwoStep by remember { mutableStateOf(false) }

  val availableKeys = listOf(
    HidKeyCode.T, HidKeyCode.W, HidKeyCode.N, HidKeyCode.R,
    HidKeyCode.L, HidKeyCode.SPACE, HidKeyCode.TAB, HidKeyCode.ESCAPE, HidKeyCode.F5
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create Key Shortcut") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Shortcut Name") },
          placeholder = { Text("New Tab") },
          singleLine = true
        )
        Text("Modifiers", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(
            "Ctrl" to HidModifiers.LEFT_CTRL,
            "Alt" to HidModifiers.LEFT_ALT,
            "Shift" to HidModifiers.LEFT_SHIFT,
            "Win/Cmd" to HidModifiers.LEFT_GUI
          ).forEach { (label, bit) ->
            val on = (modMask and bit) != 0
            Surface(
              onClick = hapticClick { modMask = modMask xor bit },
              shape = RoundedCornerShape(8.dp),
              color = if (on) MaterialTheme.colorScheme.primary else SlateTheme.colors.elevatedSurface
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
              )
            }
          }
        }
        Text("Primary Key: ${selectedKey.protocolName}", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          availableKeys.take(5).forEach { k ->
            val on = selectedKey == k
            Surface(
              onClick = hapticClick { selectedKey = k },
              shape = RoundedCornerShape(8.dp),
              color = if (on) MaterialTheme.colorScheme.primary else SlateTheme.colors.elevatedSurface
            ) {
              Text(
                text = k.protocolName,
                style = MaterialTheme.typography.labelSmall,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
              )
            }
          }
        }
        Surface(
          onClick = hapticClick { isMacroTwoStep = !isMacroTwoStep },
          shape = RoundedCornerShape(10.dp),
          color = SlateTheme.colors.elevatedSurface
        ) {
          Text(
            text = if (isMacroTwoStep) "Mode: 2-Step Macro (adds 120ms + Enter)" else "Mode: Single Key Combo",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(10.dp)
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = hapticClick {
          val badge = buildString {
            if ((modMask and HidModifiers.LEFT_CTRL) != 0) append("C+")
            if ((modMask and HidModifiers.LEFT_ALT) != 0) append("A+")
            if ((modMask and HidModifiers.LEFT_SHIFT) != 0) append("S+")
            if ((modMask and HidModifiers.LEFT_GUI) != 0) append("G+")
            append(selectedKey.protocolName)
          }
          val action = if (isMacroTwoStep) {
            ShortcutAction.Macro(
              listOf(
                MacroStep(selectedKey, modMask, 120L),
                MacroStep(HidKeyCode.ENTER, HidModifiers.NONE, 0L)
              )
            )
          } else {
            ShortcutAction.KeyCombination(selectedKey, modMask)
          }
          onSave(
            ShortcutItem(
              id = "custom_${UUID.randomUUID()}",
              name = name.trim(),
              icon = if (isMacroTwoStep) ShortcutIconKey.MACRO else ShortcutIconKey.BROWSER,
              action = action,
              badgeLabel = badge
            )
          )
        },
        enabled = name.isNotBlank()
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = hapticClick(onDismiss)) { Text("Cancel") }
    }
  )
}

private fun shortcutIconFor(key: ShortcutIconKey): ImageVector = when (key) {
  ShortcutIconKey.LOCK -> Icons.Outlined.Lock
  ShortcutIconKey.APPS -> Icons.Outlined.FilterNone
  ShortcutIconKey.DESKTOP -> Icons.Outlined.DesktopWindows
  ShortcutIconKey.SCREENSHOT -> Icons.Outlined.Image
  ShortcutIconKey.BROWSER -> Icons.Outlined.Language
  ShortcutIconKey.FILES -> Icons.Outlined.Folder
  ShortcutIconKey.PLAY -> Icons.Outlined.PlayArrow
  ShortcutIconKey.VOLUME -> Icons.AutoMirrored.Outlined.VolumeOff
  ShortcutIconKey.SLIDES -> Icons.Outlined.PresentToAll
  ShortcutIconKey.MACRO -> Icons.Outlined.Bolt
}
