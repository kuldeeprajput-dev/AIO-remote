package com.example.ui.screens.remote

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardCapslock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.components.hapticClick
import com.example.input.HidAsciiMapper
import com.example.input.HidKeyCode
import com.example.input.HidModifiers
import com.example.models.ConnectionState
import com.example.models.DeviceCapabilities
import com.example.models.OperatingSystem
import com.example.ui.components.SlateTactileButton
import com.example.ui.theme.SlateTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardPane(
  connectionState: ConnectionState,
  capabilities: DeviceCapabilities,
  targetOs: OperatingSystem,
  clipboardOptIn: Boolean,
  onKeyTap: (HidKeyCode, Int) -> Unit,
  onSendText: (String) -> Unit,
  onSendClipboard: () -> Unit,
  onBackToTouchpad: () -> Unit,
  modifier: Modifier = Modifier
) {
  var inputBuffer by remember { mutableStateOf("") }
  var inputSelection by remember { mutableStateOf(TextRange.Zero) }
  var activeModifiers by remember { mutableIntStateOf(HidModifiers.NONE) }
  var isShifted by remember { mutableStateOf(false) }
  var isNumericLayer by remember { mutableStateOf(false) }
  var showExtraKeysSheet by remember { mutableStateOf(false) }

  val isMac = targetOs == OperatingSystem.MACOS
  val primaryMod = if (isMac) HidModifiers.LEFT_GUI else HidModifiers.LEFT_CTRL
  val primaryModName = if (isMac) "Cmd" else "Ctrl"

  val emitCharacter: (Char) -> Unit = { rawChar ->
    val effectiveChar = if (isShifted && rawChar.isLetter()) rawChar.uppercaseChar() else rawChar
    inputBuffer += effectiveChar
    inputSelection = TextRange(inputBuffer.length)

    val effectiveMods = activeModifiers or (if (isShifted) HidModifiers.LEFT_SHIFT else 0)
    if (activeModifiers != HidModifiers.NONE) {
      val mapped = HidAsciiMapper.mapChar(effectiveChar.lowercaseChar())
      if (mapped != null) {
        onKeyTap(mapped.keyCode, effectiveMods or mapped.modifiers)
      } else {
        onSendText(effectiveChar.toString())
      }
      activeModifiers = HidModifiers.NONE
    } else {
      onSendText(effectiveChar.toString())
    }
    if (isShifted) isShifted = false
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isLandscape = maxWidth > maxHeight && maxHeight < 460.dp
    val keyHeight = if (isLandscape) 33.dp else 42.dp
    val keyRowSpacing = if (isLandscape) 4.dp else 6.dp
    val sectionSpacing = if (isLandscape) 5.dp else 10.dp
    val toolbarHeight = if (isLandscape) 35.dp else 42.dp

    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(sectionSpacing)
    ) {
      // 1. Top "Type here..." Text Box matching reference screen 9
      val textBoxModifier = if (isLandscape) {
        Modifier
          .fillMaxWidth()
          .height(48.dp)
      } else {
        Modifier
          .weight(1f)
          .fillMaxWidth()
      }

      Surface(
        modifier = textBoxModifier,
        shape = RoundedCornerShape(if (isLandscape) 12.dp else 16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, SlateTheme.colors.border)
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = if (isLandscape) 10.dp else 16.dp),
          contentAlignment = if (isLandscape) Alignment.CenterStart else Alignment.TopStart
        ) {
          BasicTextField(
            value = TextFieldValue(inputBuffer, selection = inputSelection),
            onValueChange = { next ->
              inputSelection = next.selection
              if (next.text.length == inputBuffer.length + 1 && next.text.startsWith(inputBuffer)) {
                val added = next.text.last()
                if (activeModifiers != HidModifiers.NONE) {
                  val mapped = HidAsciiMapper.mapChar(added)
                  if (mapped != null) {
                    onKeyTap(mapped.keyCode, activeModifiers or mapped.modifiers)
                    activeModifiers = HidModifiers.NONE
                  }
                } else {
                  onSendText(added.toString())
                }
              } else if (next.text.length == inputBuffer.length - 1 && inputBuffer.startsWith(next.text)) {
                onKeyTap(HidKeyCode.BACKSPACE, activeModifiers)
              } else if (next.text.length > inputBuffer.length + 1) {
                val delta = next.text.removePrefix(inputBuffer)
                onSendText(delta)
              }
              inputBuffer = next.text
              inputSelection = next.selection
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
              color = MaterialTheme.colorScheme.onSurface
            ),
            singleLine = isLandscape,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
              onSend = {
                onKeyTap(HidKeyCode.ENTER, activeModifiers)
                activeModifiers = HidModifiers.NONE
                inputBuffer = ""
                inputSelection = TextRange.Zero
              }
            ),
            decorationBox = { innerTextField ->
              Box(modifier = Modifier.fillMaxSize()) {
                if (inputBuffer.isEmpty()) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = "Type here...",
                      style = MaterialTheme.typography.bodyLarge,
                      color = SlateTheme.colors.textSecondary
                    )
                  }
                }
                innerTextField()
              }
            },
            modifier = Modifier
              .fillMaxSize()
              .testTag("keyboard_text_input")
          )
        }
      }

      // 2. Special-Key Pill Bar: Esc | Tab | Ctrl | Alt | Win/Super | ...
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .height(toolbarHeight),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface
      ) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 3.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          ToolbarPillButton(
            label = "Esc",
            selected = false,
            onClick = {
              onKeyTap(HidKeyCode.ESCAPE, activeModifiers)
              activeModifiers = HidModifiers.NONE
            },
            modifier = Modifier.weight(1f)
          )
          ToolbarPillButton(
            label = "Tab",
            selected = false,
            onClick = {
              onKeyTap(HidKeyCode.TAB, activeModifiers)
              activeModifiers = HidModifiers.NONE
            },
            modifier = Modifier.weight(1f)
          )
          ToolbarPillButton(
            label = "Ctrl",
            selected = (activeModifiers and HidModifiers.LEFT_CTRL) != 0,
            onClick = { activeModifiers = activeModifiers xor HidModifiers.LEFT_CTRL },
            modifier = Modifier.weight(1f)
          )
          ToolbarPillButton(
            label = "Alt",
            selected = (activeModifiers and HidModifiers.LEFT_ALT) != 0,
            onClick = { activeModifiers = activeModifiers xor HidModifiers.LEFT_ALT },
            modifier = Modifier.weight(1f)
          )
          val guiSelected = (activeModifiers and HidModifiers.LEFT_GUI) != 0
          Surface(
            onClick = hapticClick { activeModifiers = activeModifiers xor HidModifiers.LEFT_GUI },
            modifier = Modifier
              .weight(0.9f)
              .fillMaxHeight(),
            shape = RoundedCornerShape(50),
            color = if (guiSelected) MaterialTheme.colorScheme.primary else Color.Transparent
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Outlined.GridView,
                contentDescription = if (isMac) "Command" else "Windows key",
                tint = if (guiSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
              )
            }
          }
          Surface(
            onClick = hapticClick { showExtraKeysSheet = true },
            modifier = Modifier
              .weight(0.9f)
              .fillMaxHeight()
              .testTag("keyboard_more_button"),
            shape = RoundedCornerShape(50),
            color = Color.Transparent
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Outlined.MoreHoriz,
                contentDescription = "More keys and shortcuts",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }

      // 3. Built-in Visual QWERTY Keyboard Deck matching reference screen 9
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isLandscape) 14.dp else 18.dp),
        color = MaterialTheme.colorScheme.surface
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = if (isLandscape) 5.dp else 8.dp),
          verticalArrangement = Arrangement.spacedBy(keyRowSpacing)
        ) {
          val row1 = if (isNumericLayer) {
            listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')
          } else {
            listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p')
          }
          val row2 = if (isNumericLayer) {
            listOf('-', '/', ':', ';', '(', ')', '$', '&', '@')
          } else {
            listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l')
          }
          val row3 = if (isNumericLayer) {
            listOf('.', ',', '?', '!', '\'', '"', '+')
          } else {
            listOf('z', 'x', 'c', 'v', 'b', 'n', 'm')
          }

          // Row 1
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            row1.forEach { ch ->
              val display = if (isShifted && ch.isLetter()) ch.uppercaseChar().toString() else ch.toString()
              SoftKeyCap(
                label = display,
                keyHeight = keyHeight,
                onClick = { emitCharacter(ch) },
                modifier = Modifier.weight(1f)
              )
            }
          }

          // Row 2
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            row2.forEach { ch ->
              val display = if (isShifted && ch.isLetter()) ch.uppercaseChar().toString() else ch.toString()
              SoftKeyCap(
                label = display,
                keyHeight = keyHeight,
                onClick = { emitCharacter(ch) },
                modifier = Modifier.weight(1f)
              )
            }
          }

          // Row 3 (Shift + Z..M + Backspace)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Surface(
              onClick = hapticClick { isShifted = !isShifted },
              modifier = Modifier
                .weight(1.35f)
                .height(keyHeight),
              shape = RoundedCornerShape(8.dp),
              color = if (isShifted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
              shadowElevation = 0.5.dp
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Outlined.KeyboardCapslock,
                  contentDescription = "Shift",
                  tint = if (isShifted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.size(18.dp)
                )
              }
            }

            row3.forEach { ch ->
              val display = if (isShifted && ch.isLetter()) ch.uppercaseChar().toString() else ch.toString()
              SoftKeyCap(
                label = display,
                keyHeight = keyHeight,
                onClick = { emitCharacter(ch) },
                modifier = Modifier.weight(1f)
              )
            }

            Surface(
              onClick = hapticClick {
                if (inputBuffer.isNotEmpty()) {
                  inputBuffer = inputBuffer.dropLast(1)
                  inputSelection = TextRange(inputBuffer.length)
                }
                onKeyTap(HidKeyCode.BACKSPACE, activeModifiers)
                activeModifiers = HidModifiers.NONE
              },
              modifier = Modifier
                .weight(1.35f)
                .height(keyHeight),
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.background,
              shadowElevation = 0.5.dp
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.AutoMirrored.Outlined.Backspace,
                  contentDescription = "Backspace",
                  tint = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }

          // Row 4 (?123 + Emoji + Spacebar + . + Blue Enter)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            SoftKeyCap(
              label = if (isNumericLayer) "ABC" else "?123",
              keyHeight = keyHeight,
              onClick = { isNumericLayer = !isNumericLayer },
              modifier = Modifier.weight(1.3f)
            )

            Surface(
              onClick = hapticClick { showExtraKeysSheet = true },
              modifier = Modifier
                .weight(1f)
                .height(keyHeight),
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.background,
              shadowElevation = 0.5.dp
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Outlined.SentimentSatisfied,
                  contentDescription = "More symbols and shortcuts",
                  tint = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.size(18.dp)
                )
              }
            }

            // Wide Spacebar
            Surface(
              onClick = hapticClick {
                inputBuffer += " "
                inputSelection = TextRange(inputBuffer.length)
                onKeyTap(HidKeyCode.SPACE, activeModifiers)
                activeModifiers = HidModifiers.NONE
              },
              modifier = Modifier
                .weight(3.8f)
                .height(keyHeight),
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.background,
              shadowElevation = 0.5.dp
            ) {
              Box(modifier = Modifier.fillMaxSize())
            }

            SoftKeyCap(
              label = ".",
              keyHeight = keyHeight,
              onClick = { emitCharacter('.') },
              modifier = Modifier.weight(0.95f)
            )

            // Blue Return / Enter Button
            Surface(
              onClick = hapticClick {
                inputBuffer = ""
                inputSelection = TextRange.Zero
                onKeyTap(HidKeyCode.ENTER, activeModifiers)
                activeModifiers = HidModifiers.NONE
              },
              modifier = Modifier
                .weight(1.45f)
                .height(keyHeight),
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.primary
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.AutoMirrored.Outlined.KeyboardReturn,
                  contentDescription = "Enter",
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }
      }
    }
  }

  // Extra Keys Bottom Sheet (Function Keys F1-F12, Arrows, OS Shortcuts, Paste Clipboard)
  if (showExtraKeysSheet) {
    ModalBottomSheet(
      onDismissRequest = { showExtraKeysSheet = false },
      containerColor = MaterialTheme.colorScheme.background
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
              text = "Function Keys & Shortcuts",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "${targetOs.label} • F1–F12 & Arrow Keys",
              style = MaterialTheme.typography.bodySmall,
              color = SlateTheme.colors.textSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          Surface(
            onClick = hapticClick {
              showExtraKeysSheet = false
              onSendClipboard()
            },
            shape = RoundedCornerShape(12.dp),
            color = SlateTheme.colors.accentSoft,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Outlined.ContentPaste,
                contentDescription = "Paste Clipboard",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
              )
              Text(
                text = "Paste",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false
              )
            }
          }
        }

        val combos = listOf(
          "$primaryModName+C" to Pair(HidKeyCode.C, primaryMod),
          "$primaryModName+V" to Pair(HidKeyCode.V, primaryMod),
          "$primaryModName+A" to Pair(HidKeyCode.A, primaryMod),
          "$primaryModName+Z" to Pair(HidKeyCode.Z, primaryMod),
          "$primaryModName+S" to Pair(HidKeyCode.S, primaryMod),
          "Del" to Pair(HidKeyCode.DELETE, HidModifiers.NONE)
        )

        combos.chunked(3).forEach { row ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            row.forEach { (label, stroke) ->
              SlateTactileButton(
                onClick = { onKeyTap(stroke.first, stroke.second) },
                enabled = true,
                modifier = Modifier.weight(1f),
                cornerRadius = 12.dp
              ) {
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
              }
            }
          }
        }

        // Arrow Keys Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          SlateTactileButton(
            onClick = { onKeyTap(HidKeyCode.LEFT_ARROW, activeModifiers) },
            enabled = true,
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Left")
          }
          SlateTactileButton(
            onClick = { onKeyTap(HidKeyCode.UP_ARROW, activeModifiers) },
            enabled = true,
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Up")
          }
          SlateTactileButton(
            onClick = { onKeyTap(HidKeyCode.DOWN_ARROW, activeModifiers) },
            enabled = true,
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Down")
          }
          SlateTactileButton(
            onClick = { onKeyTap(HidKeyCode.RIGHT_ARROW, activeModifiers) },
            enabled = true,
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Right")
          }
        }

        val fKeys = listOf(
          HidKeyCode.F1, HidKeyCode.F2, HidKeyCode.F3, HidKeyCode.F4,
          HidKeyCode.F5, HidKeyCode.F6, HidKeyCode.F7, HidKeyCode.F8,
          HidKeyCode.F9, HidKeyCode.F10, HidKeyCode.F11, HidKeyCode.F12
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          items(fKeys) { fKey ->
            SlateTactileButton(
              onClick = { onKeyTap(fKey, activeModifiers) },
              enabled = true,
              cornerRadius = 10.dp
            ) {
              Text(fKey.protocolName, style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ToolbarPillButton(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    onClick = hapticClick(onClick),
    modifier = modifier.fillMaxHeight(),
    shape = RoundedCornerShape(50),
    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false
      )
    }
  }
}

@Composable
private fun SoftKeyCap(
  label: String,
  keyHeight: Dp,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    onClick = hapticClick(onClick),
    modifier = modifier.height(keyHeight),
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.background,
    shadowElevation = 0.5.dp
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = label,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false
      )
    }
  }
}
