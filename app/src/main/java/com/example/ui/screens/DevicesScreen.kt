package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.hapticClick
import com.example.connection.bluetooth.BluetoothCapabilityHelper
import com.example.connection.usb.UsbStatusSnapshot
import com.example.models.BluetoothCapabilityReport
import com.example.models.ConnectionState
import com.example.models.DiscoveredDevice
import com.example.models.OperatingSystem
import com.example.models.RemoteDevice
import com.example.models.TransportType
import com.example.ui.components.SlateCard
import com.example.ui.components.deviceTypeIcon
import com.example.ui.theme.SlateTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
  savedDevices: List<RemoteDevice>,
  activeDevice: RemoteDevice?,
  connectionState: ConnectionState,
  latencyMs: Int?,
  lastError: String?,
  bluetoothCapability: BluetoothCapabilityReport,
  discoveredBluetooth: List<DiscoveredDevice>,
  isScanningBluetooth: Boolean,
  discoveredWifi: List<DiscoveredDevice>,
  isScanningWifi: Boolean,
  wifiScanError: String?,
  localWifiIp: String?,
  reduceMotion: Boolean,
  onConnectSaved: (RemoteDevice) -> Unit,
  onDisconnect: () -> Unit,
  onRemoveDevice: (RemoteDevice) -> Unit,
  onOpenRemote: () -> Unit,
  onStartDiscovery: () -> Unit,
  onStopDiscovery: () -> Unit,
  onRefreshBluetooth: () -> Unit,
  onConnectDiscovered: (DiscoveredDevice, String?, ((Result<RemoteDevice>) -> Unit)?) -> Unit,
  onConnectManualWifi: (host: String, port: Int, code: String, name: String, os: OperatingSystem, ((Result<RemoteDevice>) -> Unit)?) -> Unit,
  discoveredUsb: List<DiscoveredDevice> = emptyList(),
  isScanningUsb: Boolean = false,
  usbStatus: UsbStatusSnapshot = UsbStatusSnapshot(),
  onStartBluetoothDiscovery: () -> Unit = onStartDiscovery,
  onStartWifiDiscovery: () -> Unit = onStartDiscovery,
  onStartUsbDiscovery: () -> Unit = onStartDiscovery,
  onConnectUsbTunnel: (host: String, port: Int, name: String, ((Result<RemoteDevice>) -> Unit)?) -> Unit = { _, _, _, _ -> },
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var activeMethodSheet by remember { mutableStateOf<ConnectionMethod?>(null) }
  var showPermissionRationale by remember { mutableStateOf(false) }
  var selectedSavedDeviceMenu by remember { mutableStateOf<RemoteDevice?>(null) }
  var showSearchPopup by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var showMoreMenu by remember { mutableStateOf(false) }
  var usbConnecting by remember { mutableStateOf(false) }
  var usbConnectionError by remember { mutableStateOf<String?>(null) }
  var wifiConnecting by remember { mutableStateOf<String?>(null) }
  var wifiConnectionError by remember { mutableStateOf<String?>(null) }

  val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) {
    onRefreshBluetooth()
    onStartDiscovery()
  }

  val uniqueSavedDevices = remember(savedDevices) { savedDevices.distinctBy { it.id } }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyColumn(
      modifier = Modifier
        .widthIn(max = 600.dp)
        .fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Devices",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
          )

          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = hapticClick {
                searchQuery = ""
                showSearchPopup = true
              }
            ) {
              Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search saved devices",
                tint = MaterialTheme.colorScheme.onBackground
              )
            }
            Box {
              IconButton(onClick = hapticClick { showMoreMenu = true }) {
                Icon(
                  imageVector = Icons.Outlined.MoreVert,
                  contentDescription = "More options",
                  tint = MaterialTheme.colorScheme.onBackground
                )
              }
              DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface
              ) {

                DropdownMenuItem(
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Outlined.Usb,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.size(20.dp)
                    )
                  },
                  text = {
                    Text(
                      text = "USB",
                      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  },
                  onClick = hapticClick {
                    showMoreMenu = false
                    activeMethodSheet = ConnectionMethod.USB
                  },
                  modifier = Modifier.testTag("menu_usb_devices")
                )

                DropdownMenuItem(
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Outlined.Wifi,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.size(20.dp)
                    )
                  },
                  text = {
                    Text(
                      text = "Wi-Fi Devices",
                      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  },
                  onClick = hapticClick {
                    showMoreMenu = false
                    activeMethodSheet = ConnectionMethod.WIFI_DESKTOP
                  },
                  modifier = Modifier.testTag("menu_wifi_devices")
                )
                DropdownMenuItem(
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Outlined.Bluetooth,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.size(20.dp)
                    )
                  },
                  text = {
                    Text(
                      text = "Bluetooth Devices",
                      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  },
                  onClick = hapticClick {
                    showMoreMenu = false
                    activeMethodSheet = ConnectionMethod.BLUETOOTH
                  },
                  modifier = Modifier.testTag("menu_bluetooth_devices")
                )

                DropdownMenuItem(
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Outlined.Dns,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.size(20.dp)
                    )
                  },
                  text = {
                    Text(
                      text = "Enter IP Address",
                      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  },
                  onClick = hapticClick {
                    showMoreMenu = false
                    activeMethodSheet = ConnectionMethod.ENTER_IP
                  },
                  modifier = Modifier.testTag("menu_enter_ip")
                )
              }
            }
          }
        }
      }

      if (!lastError.isNullOrBlank()) {
        item {
          SlateCard(
            borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
            contentPadding = PaddingValues(12.dp)
          ) {
            Text(
              text = lastError,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error
            )
          }
        }
      }

      if (uniqueSavedDevices.isEmpty()) {
        item {
          SlateCard(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("empty_saved_devices"),
            contentPadding = PaddingValues(20.dp)
          ) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Outlined.Devices,
                contentDescription = null,
                tint = SlateTheme.colors.textSecondary,
                modifier = Modifier.size(28.dp)
              )
              Text(
                text = "No saved devices",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Open the top-right menu (⋮) to connect via Bluetooth, Wi-Fi, USB, or IP address.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateTheme.colors.textSecondary
              )
            }
          }
        }
      } else {
        items(uniqueSavedDevices, key = { it.id }) { device ->
          val isActive = activeDevice?.id == device.id
          val deviceState = if (isActive) connectionState else ConnectionState.DISCONNECTED
          SavedDeviceCard(
            device = device,
            state = deviceState,
            onClick = {
              if (deviceState == ConnectionState.CONNECTED) {
                selectedSavedDeviceMenu = device
              } else if (device.transportType == TransportType.BLUETOOTH_HID &&
                BluetoothCapabilityHelper.missingPermissions(context).isNotEmpty()
              ) {
                showPermissionRationale = true
              } else {
                onConnectSaved(device)
              }
            }
          )
        }
      }
    }
  }

  // Quick action dialog for an already-connected device
  selectedSavedDeviceMenu?.let { dev ->
    AlertDialog(
      onDismissRequest = { selectedSavedDeviceMenu = null },
      title = { Text(dev.name) },
      text = {
        Text("${dev.transportType.label} • ${dev.address}")
      },
      confirmButton = {
        Button(
          onClick = hapticClick {
            selectedSavedDeviceMenu = null
            onOpenRemote()
          }
        ) {
          Text("Open Remote")
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          TextButton(
            onClick = hapticClick {
              selectedSavedDeviceMenu = null
              onRemoveDevice(dev)
            }
          ) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Forget")
          }
          TextButton(
            onClick = hapticClick {
              selectedSavedDeviceMenu = null
              onDisconnect()
            }
          ) {
            Text("Disconnect")
          }
        }
      }
    )
  }

  if (showPermissionRationale) {
    AlertDialog(
      onDismissRequest = { showPermissionRationale = false },
      title = { Text("Bluetooth Access") },
      text = {
        Text("AIO Remote uses Bluetooth to find nearby computers and act as a wireless mouse and keyboard.")
      },
      confirmButton = {
        Button(
          onClick = hapticClick {
            showPermissionRationale = false
            bluetoothPermissionLauncher.launch(
              BluetoothCapabilityHelper.requiredBluetoothPermissions().toTypedArray()
            )
          }
        ) {
          Text("Continue")
        }
      },
      dismissButton = {
        TextButton(onClick = hapticClick { showPermissionRationale = false }) { Text("Cancel") }
      }
    )
  }

  activeMethodSheet?.let { sheetMethod ->
    DeviceConnectionSheet(
      method = sheetMethod,
      bluetoothCapability = bluetoothCapability,
      discoveredBluetooth = discoveredBluetooth,
      isScanningBluetooth = isScanningBluetooth,
      discoveredWifi = discoveredWifi,
      isScanningWifi = isScanningWifi,
      wifiScanError = wifiConnectionError ?: wifiScanError,
      wifiConnecting = wifiConnecting,
      usbConnecting = usbConnecting,
      usbConnectionError = usbConnectionError,
      localWifiIp = localWifiIp,
      discoveredUsb = discoveredUsb,
      isScanningUsb = isScanningUsb,
      usbStatus = usbStatus,
      onDismiss = {
        onStopDiscovery()
        wifiConnectionError = null
        usbConnectionError = null
        activeMethodSheet = null
      },
      onStartDiscovery = {
        when (sheetMethod) {
          ConnectionMethod.BLUETOOTH -> onStartBluetoothDiscovery()
          ConnectionMethod.WIFI_DESKTOP -> onStartWifiDiscovery()
          ConnectionMethod.USB -> onStartUsbDiscovery()
          ConnectionMethod.ENTER_IP -> Unit
        }
      },
      onRequestBtPermission = { showPermissionRationale = true },
      onSelectBluetoothDevice = { dev ->
        onConnectDiscovered(dev, null) { res ->
          if (res.isSuccess) {
            activeMethodSheet = null
          }
        }
      },
      onSelectWifiOrTvDevice = { dev ->
        if (wifiConnecting == null) {
          wifiConnecting = dev.name
          wifiConnectionError = null
          onConnectDiscovered(dev, null) { res ->
            wifiConnecting = null
            if (activeMethodSheet == ConnectionMethod.WIFI_DESKTOP) {
              if (res.isSuccess) {
                onStopDiscovery()
                activeMethodSheet = null
              } else {
                wifiConnectionError = res.exceptionOrNull()?.message
              }
            }
          }
        }
      },
      onSelectUsbDevice = { dev ->
        if (!usbConnecting) {
          usbConnecting = true
          usbConnectionError = null
          onConnectDiscovered(dev, null) { result ->
            usbConnecting = false
            if (activeMethodSheet == ConnectionMethod.USB) {
              if (result.isSuccess) {
                onStopDiscovery()
                activeMethodSheet = null
              } else {
                usbConnectionError = result.exceptionOrNull()?.message
              }
            }
          }
        }
      },
      onConnectUsbTunnel = { host, port, name, onDone ->
        usbConnecting = true
        usbConnectionError = null
        onConnectUsbTunnel(host, port, name) { result ->
          usbConnecting = false
          onDone?.invoke(result)
          if (activeMethodSheet == ConnectionMethod.USB) {
            if (result.isSuccess) {
              onStopDiscovery()
              activeMethodSheet = null
            } else {
              usbConnectionError = result.exceptionOrNull()?.message
            }
          }
        }
      },
      onSwitchToManualIp = {
        onStopDiscovery()
        activeMethodSheet = ConnectionMethod.ENTER_IP
      },
      onPairManual = { host, port, name, os, onDone ->
        onConnectManualWifi(host, port, "", name, os) { res ->
          onDone?.invoke(res)
          if (res.isSuccess) {
            activeMethodSheet = null
          }
        }
      }
    )
  }

  if (showSearchPopup) {
    val matchingSaved = remember(uniqueSavedDevices, searchQuery) {
      val q = searchQuery.trim()
      if (q.isEmpty()) uniqueSavedDevices
      else uniqueSavedDevices.filter {
        it.name.contains(q, ignoreCase = true) ||
          it.address.contains(q, ignoreCase = true) ||
          it.transportType.label.contains(q, ignoreCase = true)
      }
    }

    ModalBottomSheet(
      onDismissRequest = { showSearchPopup = false },
      containerColor = MaterialTheme.colorScheme.background
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .imePadding()
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          text = "Search Saved Devices",
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground
        )

        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, SlateTheme.colors.border)
        ) {
          Row(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.Search,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(20.dp)
            )
            Box(
              modifier = Modifier.weight(1f),
              contentAlignment = Alignment.CenterStart
            ) {
              if (searchQuery.isEmpty()) {
                Text(
                  text = "Search by name or IP address…",
                  style = MaterialTheme.typography.bodyMedium,
                  color = SlateTheme.colors.textSecondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                  color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("search_saved_input")
              )
            }
          }
        }

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (matchingSaved.isNotEmpty()) {
            matchingSaved.forEach { dev ->
              val isActive = activeDevice?.id == dev.id
              val deviceState = if (isActive) connectionState else ConnectionState.DISCONNECTED
              SavedDeviceCard(
                device = dev,
                state = deviceState,
                onClick = {
                  showSearchPopup = false
                  onConnectSaved(dev)
                },
                cardTag = "search_saved_device_${dev.id}"
              )
            }
          } else {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, SlateTheme.colors.border)
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = if (uniqueSavedDevices.isEmpty()) {
                    "No saved devices yet"
                  } else {
                    "No saved devices match \"$searchQuery\""
                  },
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = if (uniqueSavedDevices.isEmpty()) {
                    "Connect a device from the top-right menu (⋮) to save it here."
                  } else {
                    "Try a different name or IP address."
                  },
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

@Composable
private fun SavedDeviceCard(
  device: RemoteDevice,
  state: ConnectionState,
  onClick: () -> Unit,
  cardTag: String = "saved_device_${device.id}"
) {
  val isConnected = state == ConnectionState.CONNECTED
  val isConnecting = state == ConnectionState.CONNECTING || state == ConnectionState.PAIRING
  val cardBg = if (isConnected) SlateTheme.colors.accentSoft else MaterialTheme.colorScheme.surface
  val borderColor = if (isConnected) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
  } else {
    SlateTheme.colors.border
  }

  val transportShort = when (device.transportType) {
    TransportType.WIFI_DESKTOP -> "Wi-Fi"
    TransportType.BLUETOOTH_HID -> "Bluetooth"
    TransportType.USB -> "USB"
    TransportType.ANDROID_TV -> "Android TV"
  }
  val statusText = when {
    isConnected -> "Connected · $transportShort"
    isConnecting -> "Connecting · $transportShort"
    else -> "Saved · $transportShort"
  }
  val dotColor = when {
    isConnected -> SlateTheme.colors.success
    isConnecting -> SlateTheme.colors.warning
    else -> SlateTheme.colors.textSecondary.copy(alpha = 0.5f)
  }

  Surface(
    onClick = hapticClick(onClick),
    modifier = Modifier
      .fillMaxWidth()
      .testTag(cardTag),
    shape = RoundedCornerShape(16.dp),
    color = cardBg,
    border = BorderStroke(1.dp, borderColor)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 15.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = deviceTypeIcon(device.deviceType),
        contentDescription = device.deviceType.label,
        tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(26.dp)
      )

      Spacer(Modifier.width(14.dp))

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp)
      ) {
        Text(
          text = device.name,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(dotColor)
          )
          Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = SlateTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
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
