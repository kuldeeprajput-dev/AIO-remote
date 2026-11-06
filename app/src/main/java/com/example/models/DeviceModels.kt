package com.example.models

enum class TransportType(val label: String) {
  BLUETOOTH_HID("Bluetooth"),
  WIFI_DESKTOP("Wi-Fi"),
  USB("USB"),
  ANDROID_TV("Smart TV")
}

enum class DeviceType(val label: String) {
  LAPTOP("Laptop"),
  DESKTOP("Desktop"),
  SMART_TV("Smart TV"),
  MEDIA_BOX("Streaming Box"),
  UNKNOWN("Device")
}

enum class OperatingSystem(val label: String) {
  WINDOWS("Windows"),
  MACOS("macOS"),
  LINUX("Linux"),
  ANDROID_TV("Android TV"),
  UNKNOWN("Unknown OS")
}

enum class ConnectionState(val label: String, val isActive: Boolean = false) {
  DISCONNECTED("Disconnected"),
  SEARCHING("Searching"),
  PAIRING("Pairing"),
  CONNECTING("Connecting"),
  CONNECTED("Connected", isActive = true),
  RECONNECTING("Reconnecting"),
  CONNECTION_FAILED("Connection Failed"),
  UNSUPPORTED("Unsupported"),
  PERMISSION_REQUIRED("Permission Required")
}

data class DeviceCapabilities(
  val hasMouse: Boolean = false,
  val hasKeyboard: Boolean = false,
  val hasScroll: Boolean = false,
  val hasHorizontalScroll: Boolean = false,
  val hasMediaControls: Boolean = false,
  val hasMediaMetadata: Boolean = false,
  val hasPresentationLaser: Boolean = false,
  val hasTvDpad: Boolean = false,
  val hasTvPower: Boolean = false,
  val hasTvChannels: Boolean = false,
  val hasTvVoiceSearch: Boolean = false,
  val hasTvAppLaunch: Boolean = false,
  val supportsUnicodeText: Boolean = false,
  val capabilitySummary: String = ""
) {
  companion object {
    val NONE = DeviceCapabilities(capabilitySummary = "Not connected")

    val BLUETOOTH_HID_DEFAULT = DeviceCapabilities(
      hasMouse = true,
      hasKeyboard = true,
      hasScroll = true,
      hasHorizontalScroll = true,
      hasMediaControls = true,
      hasMediaMetadata = false,
      hasPresentationLaser = false,
      hasTvDpad = true,
      hasTvPower = true,
      hasTvChannels = true,
      hasTvVoiceSearch = false,
      hasTvAppLaunch = false,
      supportsUnicodeText = false,
      capabilitySummary = "Bluetooth mouse, keyboard, media, and TV remote controls"
    )

    val WIFI_DESKTOP_DEFAULT = DeviceCapabilities(
      hasMouse = true,
      hasKeyboard = true,
      hasScroll = true,
      hasHorizontalScroll = true,
      hasMediaControls = true,
      hasMediaMetadata = true,
      hasPresentationLaser = true,
      hasTvDpad = true,
      hasTvPower = true,
      hasTvChannels = true,
      hasTvVoiceSearch = false,
      hasTvAppLaunch = false,
      supportsUnicodeText = true,
      capabilitySummary = "Wi-Fi touchpad, Unicode keyboard, media, TV remote, and presentation controls"
    )

    val ANDROID_TV_DEFAULT = DeviceCapabilities(
      hasMouse = false,
      hasKeyboard = true,
      hasScroll = false,
      hasHorizontalScroll = false,
      hasMediaControls = true,
      hasMediaMetadata = false,
      hasPresentationLaser = false,
      hasTvDpad = true,
      hasTvPower = true,
      hasTvChannels = true,
      hasTvVoiceSearch = false,
      hasTvAppLaunch = true,
      supportsUnicodeText = true,
      capabilitySummary = "Smart TV D-Pad navigation, power, volume, channels, and text search"
    )
  }
}

data class RemoteDevice(
  val id: String,
  val name: String,
  val address: String,
  val port: Int = 8765,
  val deviceType: DeviceType = DeviceType.LAPTOP,
  val os: OperatingSystem = OperatingSystem.UNKNOWN,
  val transportType: TransportType,
  val isPaired: Boolean = false,
  val isTrusted: Boolean = false,
  val lastConnectedEpochMs: Long? = null,
  val capabilities: DeviceCapabilities = when (transportType) {
    TransportType.BLUETOOTH_HID -> DeviceCapabilities.BLUETOOTH_HID_DEFAULT
    TransportType.WIFI_DESKTOP, TransportType.USB -> DeviceCapabilities.WIFI_DESKTOP_DEFAULT
    TransportType.ANDROID_TV -> DeviceCapabilities.ANDROID_TV_DEFAULT
  }
)

data class DiscoveredDevice(
  val id: String,
  val name: String,
  val address: String,
  val port: Int = 8765,
  val deviceType: DeviceType = DeviceType.UNKNOWN,
  val os: OperatingSystem = OperatingSystem.UNKNOWN,
  val transportType: TransportType,
  val isAlreadyBonded: Boolean = false,
  val requiresPairingCode: Boolean = false,
  val protocolNote: String? = null,
  val canConnect: Boolean = true
)

data class BluetoothCapabilityReport(
  val hasBluetoothAdapter: Boolean,
  val isBluetoothEnabled: Boolean,
  val isAndroid9Plus: Boolean,
  val isHidProfileSupported: Boolean,
  val missingPermissions: List<String>,
  val summaryMessage: String
)

data class MediaPlaybackInfo(
  val title: String,
  val artist: String?,
  val isPlaying: Boolean,
  val volumePercent: Int?,
  val isMuted: Boolean?
)
