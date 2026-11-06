package com.example.connection

import android.content.Context
import com.example.connection.bluetooth.BluetoothCapabilityHelper
import com.example.connection.bluetooth.BluetoothHidTransport
import com.example.connection.tv.AndroidTvTransport
import com.example.connection.usb.UsbDesktopDiscovery
import com.example.connection.wifi.WifiDesktopTransport
import com.example.connection.wifi.WifiNsdDiscovery
import com.example.domain.ShortcutAction
import com.example.domain.ShortcutItem
import com.example.input.RemoteCommand
import com.example.models.BluetoothCapabilityReport
import com.example.models.ConnectionState
import com.example.models.DeviceCapabilities
import com.example.models.DiscoveredDevice
import com.example.models.MediaPlaybackInfo
import com.example.models.OperatingSystem
import com.example.models.RemoteDevice
import com.example.models.TransportType
import com.example.preferences.UserPreferencesRepository
import com.example.security.CredentialVault
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Central connection orchestrator bridging UI/Gesture inputs to the selected transport
 * ([BluetoothHidTransport], [WifiDesktopTransport], or [AndroidTvTransport]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionManager(
  context: Context,
  private val preferences: UserPreferencesRepository,
  val vault: CredentialVault
) {
  private val appContext: Context = context.applicationContext ?: context
  private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
  private val commandDispatcher = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "SlateCommandDispatcher").apply {
      isDaemon = true
      setUncaughtExceptionHandler { _, _ -> }
    }
  }.asCoroutineDispatcher()
  private val commandChannel = Channel<RemoteCommand>(capacity = Channel.UNLIMITED)

  val bluetoothTransport = BluetoothHidTransport(appContext)
  val wifiTransport = WifiDesktopTransport(vault)
  val usbTransport = WifiDesktopTransport(vault)
  val tvTransport = AndroidTvTransport()
  val wifiDiscovery = WifiNsdDiscovery(appContext)
  val usbDiscovery = UsbDesktopDiscovery(appContext)

  private val _activeDevice = MutableStateFlow<RemoteDevice?>(null)
  val activeDevice: StateFlow<RemoteDevice?> = _activeDevice.asStateFlow()

  private val _bluetoothCapability = MutableStateFlow(
    BluetoothCapabilityReport(
      hasBluetoothAdapter = true,
      isBluetoothEnabled = false,
      isAndroid9Plus = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P,
      isHidProfileSupported = true,
      missingPermissions = emptyList(),
      summaryMessage = "Checking Bluetooth HID support..."
    )
  )
  val bluetoothCapability: StateFlow<BluetoothCapabilityReport> = _bluetoothCapability.asStateFlow()

  private val currentTransportFlow: StateFlow<RemoteTransport?> = _activeDevice
    .flatMapLatest { device ->
      flowOf(device?.let { transportFor(it.transportType) })
    }
    .stateIn(scope, SharingStarted.Eagerly, null)

  val connectionState: StateFlow<ConnectionState> = currentTransportFlow.flatMapLatest { transport ->
    transport?.connectionState ?: flowOf(ConnectionState.DISCONNECTED)
  }.stateIn(scope, SharingStarted.Eagerly, ConnectionState.DISCONNECTED)

  val activeCapabilities: StateFlow<DeviceCapabilities> = currentTransportFlow.flatMapLatest { transport ->
    transport?.capabilities ?: flowOf(DeviceCapabilities.NONE)
  }.stateIn(scope, SharingStarted.Eagerly, DeviceCapabilities.NONE)

  val mediaInfo: StateFlow<MediaPlaybackInfo?> = currentTransportFlow.flatMapLatest { transport ->
    transport?.mediaInfo ?: flowOf(null)
  }.stateIn(scope, SharingStarted.Eagerly, null)

  val lastError: StateFlow<String?> = currentTransportFlow.flatMapLatest { transport ->
    transport?.lastError ?: flowOf(null)
  }.stateIn(scope, SharingStarted.Eagerly, null)

  val latencyMs: StateFlow<Int?> = currentTransportFlow.flatMapLatest { transport ->
    transport?.latencyMs ?: flowOf(null)
  }.stateIn(scope, SharingStarted.Eagerly, null)

  init {
    scope.launch(Dispatchers.IO) {
      runCatching { refreshBluetoothCapability() }
    }
    scope.launch {
      bluetoothTransport.hidProxySupported.collect { supported ->
        runCatching {
          _bluetoothCapability.value = BluetoothCapabilityHelper.buildCapabilityReport(appContext, supported)
        }
      }
    }
    startCommandDispatcher()
  }

  private fun startCommandDispatcher() {
    scope.launch(commandDispatcher) {
      val batch = ArrayList<RemoteCommand>(8)
      for (first in commandChannel) {
        val device = _activeDevice.value ?: continue
        val transport = transportFor(device.transportType)

        batch.clear()
        batch.add(first)
        while (true) {
          val next = commandChannel.tryReceive().getOrNull() ?: break
          val prev = batch.lastOrNull()
          if (prev is RemoteCommand.PointerMove && next is RemoteCommand.PointerMove) {
            batch[batch.lastIndex] = RemoteCommand.PointerMove(
              dx = (prev.dx + next.dx).coerceIn(-127, 127),
              dy = (prev.dy + next.dy).coerceIn(-127, 127),
              timestampMs = next.timestampMs
            )
          } else if (prev is RemoteCommand.PointerScroll && next is RemoteCommand.PointerScroll) {
            batch[batch.lastIndex] = RemoteCommand.PointerScroll(
              dx = (prev.dx + next.dx).coerceIn(-127, 127),
              dy = (prev.dy + next.dy).coerceIn(-127, 127),
              timestampMs = next.timestampMs
            )
          } else {
            batch.add(next)
          }
        }

        for (cmd in batch) {
          runCatching { transport.sendCommand(cmd) }
        }
      }
    }
  }

  fun refreshBluetoothCapability() {
    runCatching { bluetoothTransport.probeHidSupport() }
    runCatching {
      _bluetoothCapability.value = BluetoothCapabilityHelper.buildCapabilityReport(
        appContext,
        bluetoothTransport.hidProxySupported.value
      )
    }
  }

  suspend fun connectToDevice(device: RemoteDevice, pairingCode: String? = null): Result<RemoteDevice> {
    val previous = _activeDevice.value
    if (previous != null && previous.id != device.id) {
      runCatching { transportFor(previous.transportType).disconnect() }
    }
    _activeDevice.value = device
    scope.launch(Dispatchers.IO) {
      runCatching { preferences.updateSettings { it.copy(activeDeviceId = device.id) } }
    }

    val transport = transportFor(device.transportType)
    val result = transport.connect(device, pairingCode)
    result.onSuccess { connectedDevice ->
      _activeDevice.value = connectedDevice
      scope.launch(Dispatchers.IO) {
        runCatching { preferences.saveOrUpdateDevice(connectedDevice) }
      }
    }
    return result
  }

  suspend fun selectActiveDevice(device: RemoteDevice) {
    val current = _activeDevice.value
    if (current?.id != device.id && connectionState.value.isActive) {
      disconnectCurrent()
    }
    _activeDevice.value = device
    scope.launch(Dispatchers.IO) {
      runCatching { preferences.updateSettings { it.copy(activeDeviceId = device.id) } }
    }
  }

  suspend fun disconnectCurrent() {
    val current = _activeDevice.value ?: return
    runCatching { transportFor(current.transportType).disconnect() }
  }

  suspend fun removeSavedDevice(device: RemoteDevice) {
    if (_activeDevice.value?.id == device.id) {
      disconnectCurrent()
      _activeDevice.value = null
    }
    runCatching { vault.removeDeviceToken(device.id) }
    runCatching { preferences.removeDevice(device.id) }
  }

  suspend fun clearAllPairings() {
    disconnectCurrent()
    _activeDevice.value = null
    runCatching { vault.clearAll() }
    runCatching { preferences.clearAllDevices() }
  }

  fun sendCommandAsync(command: RemoteCommand) {
    if (_activeDevice.value == null) return
    commandChannel.trySend(command)
  }

  suspend fun sendCommand(command: RemoteCommand): Boolean {
    val device = _activeDevice.value ?: return false
    return runCatching { transportFor(device.transportType).sendCommand(command) }.getOrDefault(false)
  }

  suspend fun executeShortcut(shortcut: ShortcutItem) {
    val device = _activeDevice.value ?: return
    val os = shortcut.targetOs ?: device.os
    when (val action = shortcut.action) {
      is ShortcutAction.KeyCombination -> {
        val useMac = os == OperatingSystem.MACOS && action.macKeyCode != null
        val key = if (useMac) (action.macKeyCode ?: action.keyCode) else action.keyCode
        val mods = if (useMac) (action.macModifiers ?: action.modifiers) else action.modifiers
        sendCommand(RemoteCommand.KeyTap(key, mods))
      }
      is ShortcutAction.Media -> sendCommand(RemoteCommand.MediaControl(action.action))
      is ShortcutAction.Presentation -> sendCommand(RemoteCommand.PresentationCommand(action.action))
      is ShortcutAction.Macro -> {
        for (step in action.steps) {
          sendCommand(RemoteCommand.KeyTap(step.keyCode, step.modifiers))
          if (step.delayAfterMs > 0) delay(step.delayAfterMs.coerceIn(10L, 2000L))
        }
      }
    }
  }

  fun releaseAllInputs() {
    val device = _activeDevice.value ?: return
    runCatching { transportFor(device.transportType).releaseAllInputs() }
  }

  fun startAllDiscovery() {
    runCatching { wifiDiscovery.startDiscovery() }
    if (BluetoothCapabilityHelper.missingPermissions(appContext).isEmpty()) {
      runCatching { bluetoothTransport.startDiscovery() }
    }
    runCatching { usbDiscovery.startDiscovery() }
  }

  fun startBluetoothDiscovery() {
    refreshBluetoothCapability()
    if (BluetoothCapabilityHelper.missingPermissions(appContext).isEmpty()) {
      runCatching { bluetoothTransport.startDiscovery() }
    }
  }

  fun startWifiDiscovery() {
    runCatching { wifiDiscovery.startDiscovery(force = true) }
  }

  fun startUsbDiscovery() {
    runCatching { usbDiscovery.startDiscovery() }
  }

  fun stopAllDiscovery() {
    runCatching { wifiDiscovery.stopDiscovery() }
    runCatching { bluetoothTransport.stopDiscovery() }
    runCatching { usbDiscovery.stopDiscovery() }
  }

  fun buildDeviceFromDiscovered(discovered: DiscoveredDevice): RemoteDevice = RemoteDevice(
    id = discovered.id,
    name = discovered.name,
    address = discovered.address,
    port = discovered.port,
    deviceType = discovered.deviceType,
    os = discovered.os,
    transportType = discovered.transportType,
    isPaired = discovered.isAlreadyBonded
  )

  private fun transportFor(type: TransportType): RemoteTransport = when (type) {
    TransportType.BLUETOOTH_HID -> bluetoothTransport
    TransportType.WIFI_DESKTOP -> wifiTransport
    TransportType.USB -> usbTransport
    TransportType.ANDROID_TV -> tvTransport
  }
}
