package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.hapticClick
import com.example.connection.usb.UsbStatusSnapshot
import com.example.connection.wifi.WifiProtocol
import com.example.models.BluetoothCapabilityReport
import com.example.models.DiscoveredDevice
import com.example.models.OperatingSystem
import com.example.models.RemoteDevice
import com.example.ui.components.SkeletonDeviceCard
import com.example.ui.components.SlateCard
import com.example.ui.components.deviceTypeIcon
import com.example.ui.theme.SlateTheme

internal enum class ConnectionMethod {
  BLUETOOTH,
  WIFI_DESKTOP,
  USB,
  ENTER_IP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceConnectionSheet(
  method: ConnectionMethod,
  bluetoothCapability: BluetoothCapabilityReport,
  discoveredBluetooth: List<DiscoveredDevice>,
  isScanningBluetooth: Boolean,
  discoveredWifi: List<DiscoveredDevice>,
  isScanningWifi: Boolean,
  wifiScanError: String?,
  wifiConnecting: String?,
  usbConnecting: Boolean,
  usbConnectionError: String?,
  localWifiIp: String?,
  discoveredUsb: List<DiscoveredDevice>,
  isScanningUsb: Boolean,
  usbStatus: UsbStatusSnapshot,
  onDismiss: () -> Unit,
  onStartDiscovery: () -> Unit,
  onRequestBtPermission: () -> Unit,
  onSelectBluetoothDevice: (DiscoveredDevice) -> Unit,
  onSelectWifiOrTvDevice: (DiscoveredDevice) -> Unit,
  onSelectUsbDevice: (DiscoveredDevice) -> Unit,
  onConnectUsbTunnel: (host: String, port: Int, name: String, ((Result<RemoteDevice>) -> Unit)?) -> Unit,
  onSwitchToManualIp: () -> Unit,
  onPairManual: (host: String, port: Int, name: String, os: OperatingSystem, ((Result<RemoteDevice>) -> Unit)?) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  DisposableEffect(method) {
    if (method == ConnectionMethod.BLUETOOTH ||
      method == ConnectionMethod.WIFI_DESKTOP ||
      method == ConnectionMethod.USB
    ) {
      onStartDiscovery()
    }
    onDispose { }
  }

  val sheetTitle = when (method) {
    ConnectionMethod.BLUETOOTH -> "Bluetooth Devices"
    ConnectionMethod.WIFI_DESKTOP -> "Wi-Fi Devices"
    ConnectionMethod.USB -> "USB Connection"
    ConnectionMethod.ENTER_IP -> "Enter IP Address"
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = sheetTitle,
          style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        if (method != ConnectionMethod.ENTER_IP) {
          IconButton(onClick = hapticClick(onStartDiscovery)) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Rescan")
          }
        } else {
          IconButton(
            onClick = hapticClick(onDismiss),
            modifier = Modifier.testTag("close_enter_ip_sheet_button")
          ) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = "Dismiss IP sheet"
            )
          }
        }
      }

      when (method) {
        ConnectionMethod.BLUETOOTH -> BluetoothDiscoveryTab(
          capability = bluetoothCapability,
          devices = discoveredBluetooth,
          isScanning = isScanningBluetooth,
          onRequestPermission = onRequestBtPermission,
          onRetry = onStartDiscovery,
          onSelect = onSelectBluetoothDevice
        )

        ConnectionMethod.WIFI_DESKTOP -> WifiDiscoveryTab(
          devices = discoveredWifi,
          isScanning = isScanningWifi,
          error = wifiScanError,
          connecting = wifiConnecting,
          localWifiIp = localWifiIp,
          onRetry = onStartDiscovery,
          onSelect = onSelectWifiOrTvDevice,
          onFallbackManual = onSwitchToManualIp
        )

        ConnectionMethod.USB -> UsbDiscoveryTab(
          status = usbStatus,
          devices = discoveredUsb,
          isScanning = isScanningUsb,
          isConnecting = usbConnecting,
          error = usbConnectionError,
          onRetry = onStartDiscovery,
          onSelect = onSelectUsbDevice,
          onConnectTunnel = onConnectUsbTunnel
        )

        ConnectionMethod.ENTER_IP -> ManualIpTab(
          localWifiIp = localWifiIp,
          onPairManual = onPairManual
        )
      }
    }
  }
}

@Composable
private fun BluetoothDiscoveryTab(
  capability: BluetoothCapabilityReport,
  devices: List<DiscoveredDevice>,
  isScanning: Boolean,
  onRequestPermission: () -> Unit,
  onRetry: () -> Unit,
  onSelect: (DiscoveredDevice) -> Unit
) {
  val context = LocalContext.current
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(300.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    if (capability.missingPermissions.isNotEmpty()) {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          Icons.Outlined.Bluetooth,
          contentDescription = null,
          modifier = Modifier.size(32.dp),
          tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text("Bluetooth Permission Required", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(10.dp))
        Button(onClick = hapticClick(onRequestPermission), shape = RoundedCornerShape(50)) {
          Text("Grant Access")
        }
      }
      return
    }

    if (capability.hasBluetoothAdapter && !capability.isBluetoothEnabled) {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          Icons.Outlined.Bluetooth,
          contentDescription = null,
          modifier = Modifier.size(32.dp),
          tint = SlateTheme.colors.textSecondary
        )
        Spacer(Modifier.height(8.dp))
        Text("Bluetooth is turned off", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
          "Turn on Bluetooth to pair and control nearby PCs or Smart TVs.",
          style = MaterialTheme.typography.bodySmall,
          color = SlateTheme.colors.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = hapticClick {
              runCatching {
                context.startActivity(
                  Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
              }
            },
            shape = RoundedCornerShape(50)
          ) {
            Text("Enable Bluetooth")
          }
          OutlinedButton(onClick = hapticClick(onRetry), shape = RoundedCornerShape(50)) {
            Text("Scan Again")
          }
        }
      }
      return
    }

    val uniqueBtDevices = remember(devices) { devices.distinctBy { it.id } }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (isScanning && uniqueBtDevices.isEmpty()) {
        items(2) { SkeletonDeviceCard() }
      } else if (uniqueBtDevices.isEmpty()) {
        item {
          SlateCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text("No Bluetooth devices found", style = MaterialTheme.typography.titleSmall)
              Text(
                "Keep Bluetooth settings open on your PC, laptop, or TV, then scan again.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateTheme.colors.textSecondary
              )
              OutlinedButton(onClick = hapticClick(onRetry), shape = RoundedCornerShape(50)) {
                Text("Scan Again")
              }
            }
          }
        }
      } else {
        items(uniqueBtDevices, key = { it.id }) { dev ->
          DiscoveredDeviceRow(device = dev, onClick = { onSelect(dev) })
        }
      }
    }
  }
}

@Composable
private fun UsbDiscoveryTab(
  status: UsbStatusSnapshot,
  devices: List<DiscoveredDevice>,
  isScanning: Boolean,
  isConnecting: Boolean,
  error: String?,
  onRetry: () -> Unit,
  onSelect: (DiscoveredDevice) -> Unit,
  onConnectTunnel: (host: String, port: Int, name: String, ((Result<RemoteDevice>) -> Unit)?) -> Unit
) {
  val context = LocalContext.current

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(310.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Surface(
      shape = RoundedCornerShape(10.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, SlateTheme.colors.border)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = when {
            status.isUsbTetheringActive && status.usbLocalIp != null ->
              "USB Tethering active • ${status.usbLocalIp}"
            status.isCableConnected ->
              "USB cable detected • Ready to scan or tunnel"
            else ->
              "Connect phone to PC via USB cable"
          },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface
        )
        if (isScanning) {
          CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        }
      }
    }

    val uniqueUsbDevices = remember(devices) { devices.distinctBy { it.id } }
    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      if (isConnecting) {
        item {
          Text("Connecting over USB. Click Allow on the computer if prompted.", style = MaterialTheme.typography.bodySmall)
        }
      }
      if (error != null) {
        item {
          Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
      if (isScanning && uniqueUsbDevices.isEmpty()) {
        items(2) { SkeletonDeviceCard() }
      } else if (uniqueUsbDevices.isNotEmpty()) {
        items(uniqueUsbDevices, key = { it.id }) { dev ->
          DiscoveredDeviceRow(device = dev, onClick = if (isConnecting) null else ({ onSelect(dev) }))
        }
      } else {
        item {
          SlateCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text("Connect over USB", style = MaterialTheme.typography.titleSmall)
              Text(
                "Enable USB Tethering on your phone or run ADB port forwarding on your PC, then tap Connect or Rescan.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateTheme.colors.textSecondary
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                  onClick = hapticClick {
                    onConnectTunnel("127.0.0.1", WifiProtocol.DEFAULT_PORT, "USB PC", null)
                  },
                  enabled = !isConnecting,
                  shape = RoundedCornerShape(50),
                  modifier = Modifier.testTag("usb_connect_tunnel_button")
                ) {
                  if (isConnecting) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(14.dp),
                      strokeWidth = 2.dp,
                      color = MaterialTheme.colorScheme.onPrimary
                    )
                  } else {
                    Text("Connect USB PC", maxLines = 1, softWrap = false)
                  }
                }
                OutlinedButton(
                  onClick = hapticClick {
                    val launched = runCatching {
                      context.startActivity(
                        Intent("android.settings.TETHER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                      )
                      true
                    }.getOrDefault(false)
                    if (!launched) {
                      runCatching {
                        context.startActivity(
                          Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                      }
                    }
                  },
                  shape = RoundedCornerShape(50)
                ) {
                  Text("USB Settings", maxLines = 1, softWrap = false)
                }
              }
            }
          }
        }
      }
    }

    if (uniqueUsbDevices.isNotEmpty()) {
      OutlinedButton(
        onClick = hapticClick(onRetry),
        shape = RoundedCornerShape(50),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Rescan USB")
      }
    }
  }
}

@Composable
private fun WifiDiscoveryTab(
  devices: List<DiscoveredDevice>,
  isScanning: Boolean,
  error: String?,
  connecting: String?,
  localWifiIp: String?,
  onRetry: () -> Unit,
  onSelect: (DiscoveredDevice) -> Unit,
  onFallbackManual: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(300.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Surface(
      shape = RoundedCornerShape(10.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, SlateTheme.colors.border)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = localWifiIp?.let { "Same Wi-Fi • Phone IP: $it" } ?: "Scanning same Wi-Fi network…",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface
        )
        if (isScanning) {
          CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        }
      }
    }

    val uniqueWifiDevices = remember(devices) { devices.distinctBy { it.id }.sortedByDescending { it.canConnect } }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (connecting != null) {
        item {
          Text(
            "Connecting to $connecting. Approve AIO Remote on that computer if prompted.",
            style = MaterialTheme.typography.bodySmall
          )
        }
      }
      if (!error.isNullOrBlank()) {
        item {
          Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
      if (isScanning && uniqueWifiDevices.isEmpty()) {
        items(2) { SkeletonDeviceCard() }
      } else if (uniqueWifiDevices.isEmpty()) {
        item {
          SlateCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("No Wi-Fi devices found yet", style = MaterialTheme.typography.titleSmall)
              Text(
                "Start the Slate desktop receiver on your computer, keep both devices on the same Wi-Fi, then rescan or enter the computer’s IP address.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateTheme.colors.textSecondary
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = hapticClick(onRetry), shape = RoundedCornerShape(50)) {
                  Text("Rescan", maxLines = 1, softWrap = false)
                }
                Button(onClick = hapticClick(onFallbackManual), shape = RoundedCornerShape(50)) {
                  Text("Enter IP", maxLines = 1, softWrap = false)
                }
              }
            }
          }
        }
      } else {
        items(uniqueWifiDevices, key = { it.id }) { dev ->
          DiscoveredDeviceRow(device = dev, onClick = if (dev.canConnect && connecting == null) ({ onSelect(dev) }) else null)
        }
      }
    }
  }
}

@Composable
private fun ManualIpTab(
  localWifiIp: String?,
  onPairManual: (host: String, port: Int, name: String, os: OperatingSystem, ((Result<RemoteDevice>) -> Unit)?) -> Unit
) {
  val subnetPrefix = remember(localWifiIp) {
    val parts = localWifiIp?.split(".")
    if (parts != null && parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}." else ""
  }
  var name by remember { mutableStateOf("") }
  var host by remember { mutableStateOf(subnetPrefix) }
  var portText by remember { mutableStateOf(WifiProtocol.DEFAULT_PORT.toString()) }
  var os by remember { mutableStateOf(OperatingSystem.UNKNOWN) }
  var isBusy by remember { mutableStateOf(false) }
  var inlineError by remember { mutableStateOf<String?>(null) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = host,
        onValueChange = { host = it; inlineError = null },
        label = { Text("IP Address") },
        placeholder = { Text(if (subnetPrefix.isNotEmpty()) "${subnetPrefix}42" else "192.168.1.42") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(2f)
      )
      OutlinedTextField(
        value = portText,
        onValueChange = { portText = it.filter(Char::isDigit).take(5) },
        label = { Text("Port") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f)
      )
    }

    OutlinedTextField(
      value = name,
      onValueChange = { name = it },
      label = { Text("Device Name (Optional)") },
      placeholder = { Text("My PC / TV") },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth()
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf(
        OperatingSystem.WINDOWS,
        OperatingSystem.MACOS,
        OperatingSystem.LINUX,
        OperatingSystem.ANDROID_TV
      ).forEach { targetOs ->
        val selected = os == targetOs
        val chipLabel = if (targetOs == OperatingSystem.ANDROID_TV) "Smart TV" else targetOs.label
        Surface(
          onClick = hapticClick { os = targetOs },
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(50),
          color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else SlateTheme.colors.border)
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
              text = chipLabel,
              style = MaterialTheme.typography.labelSmall,
              color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              softWrap = false
            )
          }
        }
      }
    }

    inlineError?.let {
      Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }

    val validHost = host.trim().isNotBlank() && !host.trim().endsWith(".")

    Button(
      onClick = hapticClick {
        isBusy = true
        inlineError = null
        val cleanHost = host.trim()
        onPairManual(
          cleanHost,
          portText.toIntOrNull() ?: WifiProtocol.DEFAULT_PORT,
          name.ifBlank { "Wi-Fi Device ($cleanHost)" },
          os
        ) { res ->
          isBusy = false
          res.onFailure { err -> inlineError = err.message }
        }
      },
      enabled = !isBusy && validHost,
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp),
      shape = RoundedCornerShape(50)
    ) {
      if (isBusy) {
        Text("Approve on the computer if prompted")
      } else {
        Text("Connect")
      }
    }
  }
}

@Composable
private fun DiscoveredDeviceRow(
  device: DiscoveredDevice,
  onClick: (() -> Unit)?
) {
  SlateCard(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
    contentPadding = PaddingValues(14.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = deviceTypeIcon(device.deviceType),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(22.dp)
      )
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = device.name,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = device.protocolNote ?: device.address,
          style = MaterialTheme.typography.bodySmall,
          color = SlateTheme.colors.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      if (device.isAlreadyBonded) {
        Surface(
          shape = RoundedCornerShape(50),
          color = SlateTheme.colors.elevatedSurface
        ) {
          Text(
            text = "Paired",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      } else if (onClick != null) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
          contentDescription = null,
          tint = SlateTheme.colors.textSecondary,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}
