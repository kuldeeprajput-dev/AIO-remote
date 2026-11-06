package com.example.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.connection.ConnectionManager
import com.example.connection.usb.UsbStatusSnapshot
import com.example.domain.DefaultShortcuts
import com.example.domain.ShortcutItem
import com.example.input.HidKeyCode
import com.example.input.HidModifiers
import com.example.input.MediaAction
import com.example.input.MouseButton
import com.example.input.PresentationAction
import com.example.input.RemoteCommand
import com.example.input.TvAction
import com.example.models.BluetoothCapabilityReport
import com.example.models.ConnectionState
import com.example.models.DeviceCapabilities
import com.example.models.DeviceType
import com.example.models.DiscoveredDevice
import com.example.models.MediaPlaybackInfo
import com.example.models.OperatingSystem
import com.example.models.RemoteDevice
import com.example.models.TransportType
import com.example.preferences.UserPreferencesRepository
import com.example.preferences.UserSettings
import com.example.security.CredentialVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RemoteControlMode(val label: String) {
  TOUCHPAD("Touchpad"),
  TV_REMOTE("TV Remote"),
  KEYBOARD("Keyboard"),
  MEDIA("Media"),
  PRESENTATION("Presentation"),
  SHORTCUTS("Shortcuts")
}

class SlateViewModel(application: Application) : AndroidViewModel(application) {

  @Suppress("unused")
  constructor() : this(com.example.SlateApplication.instance ?: Application())

  private val safeExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }

  private val preferences: UserPreferencesRepository =
    com.example.SlateApplication.getPreferences(application)
  private val vault: CredentialVault =
    com.example.SlateApplication.getVault(application)
  val connectionManager: ConnectionManager =
    com.example.SlateApplication.getConnectionManager(application, preferences, vault)

  private val _settings = MutableStateFlow(UserSettings())
  val settings: StateFlow<UserSettings> = _settings.asStateFlow()

  private val _savedDevices = MutableStateFlow<List<RemoteDevice>>(emptyList())
  val savedDevices: StateFlow<List<RemoteDevice>> = _savedDevices.asStateFlow()

  private val _shortcuts = MutableStateFlow<List<ShortcutItem>>(DefaultShortcuts.defaults())
  val shortcuts: StateFlow<List<ShortcutItem>> = _shortcuts.asStateFlow()

  val activeDevice: StateFlow<RemoteDevice?> = connectionManager.activeDevice
  val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState
  val capabilities: StateFlow<DeviceCapabilities> = connectionManager.activeCapabilities
  val mediaInfo: StateFlow<MediaPlaybackInfo?> = connectionManager.mediaInfo
  val lastError: StateFlow<String?> = connectionManager.lastError
  val latencyMs: StateFlow<Int?> = connectionManager.latencyMs
  val bluetoothCapability: StateFlow<BluetoothCapabilityReport> = connectionManager.bluetoothCapability

  val discoveredBluetooth: StateFlow<List<DiscoveredDevice>> = connectionManager.bluetoothTransport.discoveredDevices
  val isScanningBluetooth: StateFlow<Boolean> = connectionManager.bluetoothTransport.isScanning
  val discoveredWifi: StateFlow<List<DiscoveredDevice>> = connectionManager.wifiDiscovery.discoveredWifiDevices
  val isScanningWifi: StateFlow<Boolean> = connectionManager.wifiDiscovery.isScanning
  val wifiScanError: StateFlow<String?> = connectionManager.wifiDiscovery.scanError
  val localWifiIp: StateFlow<String?> = connectionManager.wifiDiscovery.localWifiIp
  val discoveredUsb: StateFlow<List<DiscoveredDevice>> = connectionManager.usbDiscovery.discoveredUsbDevices
  val isScanningUsb: StateFlow<Boolean> = connectionManager.usbDiscovery.isScanning
  val usbStatus: StateFlow<UsbStatusSnapshot> = connectionManager.usbDiscovery.usbStatus

  private val _remoteMode = MutableStateFlow(RemoteControlMode.TOUCHPAD)
  val remoteMode: StateFlow<RemoteControlMode> = _remoteMode.asStateFlow()

  private val _isScrollMode = MutableStateFlow(false)
  val isScrollMode: StateFlow<Boolean> = _isScrollMode.asStateFlow()

  private val _isFullscreenTouchpad = MutableStateFlow(false)
  val isFullscreenTouchpad: StateFlow<Boolean> = _isFullscreenTouchpad.asStateFlow()

  private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

  private var initialAutoReconnectDone = false

  private fun launchSafe(
    context: kotlin.coroutines.CoroutineContext = kotlin.coroutines.EmptyCoroutineContext,
    block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit
  ) {
    viewModelScope.launch(safeExceptionHandler + context) {
      runCatching { block() }
    }
  }

  init {
    runCatching { connectionManager.wifiDiscovery.refreshWifiStatus() }

    launchSafe {
      preferences.settingsFlow.collect { loaded ->
        _settings.update { current ->
          loaded.copy(onboardingCompleted = current.onboardingCompleted || loaded.onboardingCompleted)
        }
      }
    }

    launchSafe {
      preferences.savedDevicesFlow.collect { loaded ->
        _savedDevices.value = loaded
        if (!initialAutoReconnectDone && loaded.isNotEmpty()) {
          initialAutoReconnectDone = true
          val currentSettings = _settings.value
          val preferred = loaded.firstOrNull { it.id == currentSettings.activeDeviceId } ?: loaded.firstOrNull()
          if (preferred != null) {
            connectionManager.selectActiveDevice(preferred)
            if (currentSettings.autoReconnect && preferred.isPaired) {
              if (preferred.transportType == TransportType.WIFI_DESKTOP ||
                preferred.transportType == TransportType.USB
              ) {
                connectionManager.connectToDevice(preferred)
              }
            }
          }
        }
      }
    }

    launchSafe {
      preferences.shortcutsFlow.collect { loaded ->
        _shortcuts.value = loaded
      }
    }
  }

  fun completeOnboarding() {
    _settings.update { it.copy(onboardingCompleted = true) }
    launchSafe(Dispatchers.IO) {
      preferences.updateSettings { it.copy(onboardingCompleted = true) }
    }
  }

  fun updateSettings(transform: (UserSettings) -> UserSettings) {
    _settings.update(transform)
    launchSafe(Dispatchers.IO) {
      preferences.updateSettings(transform)
    }
  }

  fun setRemoteMode(mode: RemoteControlMode) {
    _remoteMode.value = mode
  }

  fun toggleScrollMode() {
    _isScrollMode.value = !_isScrollMode.value
  }

  fun setFullscreenTouchpad(enabled: Boolean) {
    _isFullscreenTouchpad.value = enabled
  }

  fun refreshBluetoothState() {
    connectionManager.refreshBluetoothCapability()
    connectionManager.wifiDiscovery.refreshWifiStatus()
    connectionManager.usbDiscovery.refreshUsbStatus()
  }

  fun startDiscovery() {
    connectionManager.refreshBluetoothCapability()
    connectionManager.startAllDiscovery()
  }

  fun startBluetoothDiscovery() {
    connectionManager.startBluetoothDiscovery()
  }

  fun startWifiDiscovery() {
    connectionManager.startWifiDiscovery()
  }

  fun startUsbDiscovery() {
    connectionManager.startUsbDiscovery()
  }

  fun stopDiscovery() {
    connectionManager.stopAllDiscovery()
  }

  private fun upsertDeviceInMemory(device: RemoteDevice) {
    _savedDevices.update { current ->
      val mutable = current.toMutableList()
      val idx = mutable.indexOfFirst { it.id == device.id }
      if (idx >= 0) {
        mutable[idx] = device
      } else {
        mutable.add(0, device)
      }
      mutable
    }
  }

  fun connectToSavedDevice(device: RemoteDevice) {
    launchSafe {
      val result = connectionManager.connectToDevice(device)
      result.onSuccess { connected ->
        upsertDeviceInMemory(connected)
      }.onFailure { err ->
        _snackbarEvents.tryEmit(err.message ?: "Failed to connect to ${device.name}")
      }
    }
  }

  fun selectDevice(device: RemoteDevice) {
    launchSafe {
      connectionManager.selectActiveDevice(device)
    }
  }

  fun disconnectDevice() {
    launchSafe {
      connectionManager.disconnectCurrent()
    }
  }

  fun removeDevice(device: RemoteDevice) {
    _savedDevices.update { list -> list.filterNot { it.id == device.id } }
    launchSafe {
      connectionManager.removeSavedDevice(device)
      _snackbarEvents.tryEmit("Removed ${device.name}")
    }
  }

  fun clearAllPairings() {
    _savedDevices.value = emptyList()
    launchSafe {
      connectionManager.clearAllPairings()
      _snackbarEvents.tryEmit("Cleared all saved pairings and tokens")
    }
  }

  fun connectDiscoveredDevice(
    discovered: DiscoveredDevice,
    pairingCode: String? = null,
    onComplete: ((Result<RemoteDevice>) -> Unit)? = null
  ) {
    launchSafe {
      val device = connectionManager.buildDeviceFromDiscovered(discovered)
      val result = connectionManager.connectToDevice(device, pairingCode)
      result.onSuccess { connected ->
        upsertDeviceInMemory(connected)
        _snackbarEvents.tryEmit("Connected to ${connected.name}")
      }.onFailure { err ->
        _snackbarEvents.tryEmit(err.message ?: "Could not connect to ${discovered.name}")
      }
      onComplete?.invoke(result)
    }
  }

  fun connectManualWifi(
    host: String,
    port: Int,
    pairingCode: String,
    deviceName: String,
    os: OperatingSystem,
    onComplete: ((Result<RemoteDevice>) -> Unit)? = null
  ) {
    val cleanHost = host.trim()
    if (cleanHost.isBlank()) {
      val msg = "Enter a valid IP address or hostname."
      val err = IllegalArgumentException(msg)
      _snackbarEvents.tryEmit(msg)
      onComplete?.invoke(Result.failure(err))
      return
    }
    val cleanCode = pairingCode.trim().takeIf { it.isNotEmpty() }
    val inferredType = if (os == OperatingSystem.ANDROID_TV ||
      deviceName.contains("TV", ignoreCase = true)
    ) {
      DeviceType.SMART_TV
    } else {
      DeviceType.LAPTOP
    }
    val device = RemoteDevice(
      id = "wifi_$cleanHost",
      name = deviceName.trim().ifBlank { "Wi-Fi Device ($cleanHost)" },
      address = cleanHost,
      port = port,
      deviceType = inferredType,
      os = os,
      transportType = TransportType.WIFI_DESKTOP
    )
    launchSafe {
      val result = connectionManager.connectToDevice(device, cleanCode)
      result.onSuccess { connected ->
        upsertDeviceInMemory(connected)
        _snackbarEvents.tryEmit("Connected to ${connected.name}")
      }.onFailure { err ->
        _snackbarEvents.tryEmit(err.message ?: "Connection failed")
      }
      onComplete?.invoke(result)
    }
  }

  fun connectUsbEndpoint(
    host: String = "127.0.0.1",
    port: Int = 8765,
    deviceName: String = "USB PC",
    os: OperatingSystem = OperatingSystem.UNKNOWN,
    onComplete: ((Result<RemoteDevice>) -> Unit)? = null
  ) {
    val cleanHost = host.trim().ifBlank { "127.0.0.1" }
    val device = RemoteDevice(
      id = "usb_$cleanHost",
      name = deviceName.trim().ifBlank { "USB PC ($cleanHost)" },
      address = cleanHost,
      port = port,
      deviceType = DeviceType.LAPTOP,
      os = os,
      transportType = TransportType.USB
    )
    launchSafe {
      val result = connectionManager.connectToDevice(device, null)
      result.onSuccess { connected ->
        upsertDeviceInMemory(connected)
        _snackbarEvents.tryEmit("Connected via USB to ${connected.name}")
      }.onFailure { err ->
        _snackbarEvents.tryEmit(err.message ?: "USB connection failed")
      }
      onComplete?.invoke(result)
    }
  }

  fun sendCommands(commands: List<RemoteCommand>) {
    commands.forEach { connectionManager.sendCommandAsync(it) }
  }

  fun sendMouseButton(button: MouseButton, isDown: Boolean) {
    val cmd = if (isDown) RemoteCommand.PointerDown(button) else RemoteCommand.PointerUp(button)
    connectionManager.sendCommandAsync(cmd)
  }

  fun clickMouse(button: MouseButton) {
    connectionManager.sendCommandAsync(RemoteCommand.PointerClick(button, count = 1))
  }

  fun sendKeyTap(keyCode: HidKeyCode, modifiers: Int = HidModifiers.NONE) {
    connectionManager.sendCommandAsync(RemoteCommand.KeyTap(keyCode, modifiers))
  }

  fun sendText(text: String) {
    if (text.isEmpty()) return
    connectionManager.sendCommandAsync(RemoteCommand.TextInput(text))
  }

  fun sendClipboardIfOptedIn() {
    if (!settings.value.clipboardSyncOptIn) {
      _snackbarEvents.tryEmit("Enable Clipboard Sharing in Settings first.")
      return
    }
    val text = runCatching {
      val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
      val clip: ClipData? = clipboard?.primaryClip
      if (clip != null && clip.itemCount > 0) {
        clip.getItemAt(0)?.coerceToText(getApplication())?.toString()
      } else {
        null
      }
    }.getOrNull()
    if (text.isNullOrEmpty()) {
      _snackbarEvents.tryEmit("Clipboard is empty.")
      return
    }
    sendText(text)
    _snackbarEvents.tryEmit("Sent ${text.length} characters from clipboard")
  }

  fun sendMedia(action: MediaAction, volumePercent: Int? = null) {
    connectionManager.sendCommandAsync(RemoteCommand.MediaControl(action, volumePercent))
  }

  fun sendPresentation(action: PresentationAction, laserX: Float? = null, laserY: Float? = null) {
    val preset = settings.value.presentationPreset
    if (action == PresentationAction.LASER_MOVE || action == PresentationAction.LASER_OFF) {
      connectionManager.sendCommandAsync(RemoteCommand.PresentationCommand(action, laserX, laserY))
      return
    }
    if (activeDevice.value?.transportType == TransportType.BLUETOOTH_HID) {
      val key = when (action) {
        PresentationAction.NEXT_SLIDE -> preset.nextKey
        PresentationAction.PREVIOUS_SLIDE -> preset.prevKey
        PresentationAction.START_PRESENTATION -> preset.startKey
        PresentationAction.END_PRESENTATION -> HidKeyCode.ESCAPE
        PresentationAction.BLACK_SCREEN -> preset.blackKey
        PresentationAction.WHITE_SCREEN -> preset.whiteKey
        else -> return
      }
      connectionManager.sendCommandAsync(RemoteCommand.KeyTap(key))
    } else {
      connectionManager.sendCommandAsync(RemoteCommand.PresentationCommand(action))
    }
  }

  fun sendTvCommand(action: TvAction, appPackage: String? = null) {
    connectionManager.sendCommandAsync(RemoteCommand.TvCommand(action, appPackage))
  }

  fun executeShortcut(item: ShortcutItem) {
    launchSafe {
      connectionManager.executeShortcut(item)
    }
  }

  fun moveShortcut(fromIndex: Int, toIndex: Int) {
    val current = _shortcuts.value.toMutableList()
    if (fromIndex !in current.indices || toIndex !in current.indices) return
    val moved = current.removeAt(fromIndex)
    current.add(toIndex, moved)
    _shortcuts.value = current
    launchSafe(Dispatchers.IO) {
      preferences.saveShortcuts(current)
    }
  }

  fun addShortcut(item: ShortcutItem) {
    val next = _shortcuts.value + item
    _shortcuts.value = next
    launchSafe(Dispatchers.IO) {
      preferences.saveShortcuts(next)
    }
  }

  fun removeShortcut(id: String) {
    val next = _shortcuts.value.filterNot { it.id == id }
    _shortcuts.value = next
    launchSafe(Dispatchers.IO) {
      preferences.saveShortcuts(next)
    }
  }

  fun onAppPausedOrLocked() {
    connectionManager.releaseAllInputs()
  }

  override fun onCleared() {
    super.onCleared()
    runCatching { connectionManager.stopAllDiscovery() }
    runCatching { connectionManager.releaseAllInputs() }
  }

  companion object {
    fun factory(application: Application): androidx.lifecycle.ViewModelProvider.Factory =
      object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
          return SlateViewModel(application) as T
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(
          modelClass: Class<T>,
          extras: androidx.lifecycle.viewmodel.CreationExtras
        ): T {
          return SlateViewModel(application) as T
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(
          modelClass: kotlin.reflect.KClass<T>,
          extras: androidx.lifecycle.viewmodel.CreationExtras
        ): T {
          return SlateViewModel(application) as T
        }
      }
  }
}
