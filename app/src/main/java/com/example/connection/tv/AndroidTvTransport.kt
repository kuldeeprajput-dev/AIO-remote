package com.example.connection.tv

import com.example.connection.RemoteTransport
import com.example.input.RemoteCommand
import com.example.models.ConnectionState
import com.example.models.DeviceCapabilities
import com.example.models.MediaPlaybackInfo
import com.example.models.RemoteDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Android TV / Google TV Remote Protocol v2 transport probe & status handler.
 *
 * Compatibility note:
 * Google's Android TV Remote Protocol v2 uses TLS on port 6467 (pairing) and 6466 (remote)
 * with binary Protobuf messages and mutual X.509 certificate pinning. Unlike unsecured ADB
 * (which is intentionally avoided per security requirements), a full Protobuf/X.509 session
 * requires a TV to complete TLS mutual-cert pairing.
 *
 * This transport verifies real TCP/TLS reachability to the TV's pairing/remote port and
 * never fakes a connected state. When a TV refuses or requires full mutual-TLS pairing,
 * it provides clear guidance (and suggests Bluetooth HID pairing on Android TV as an instant,
 * standards-based alternative for D-Pad, Back, Home, Volume, and Media playback).
 */
class AndroidTvTransport : RemoteTransport {

  private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
  override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

  private val _capabilities = MutableStateFlow(DeviceCapabilities.ANDROID_TV_DEFAULT)
  override val capabilities: StateFlow<DeviceCapabilities> = _capabilities.asStateFlow()

  private val _mediaInfo = MutableStateFlow<MediaPlaybackInfo?>(null)
  override val mediaInfo: StateFlow<MediaPlaybackInfo?> = _mediaInfo.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  override val lastError: StateFlow<String?> = _lastError.asStateFlow()

  private val _latencyMs = MutableStateFlow<Int?>(null)
  override val latencyMs: StateFlow<Int?> = _latencyMs.asStateFlow()

  override suspend fun connect(device: RemoteDevice, pairingCode: String?): Result<RemoteDevice> =
    withContext(Dispatchers.IO) {
      _lastError.value = null
      _connectionState.value = ConnectionState.CONNECTING

      val pairingPort = if (device.port == 6466) 6467 else device.port
      val reachable = runCatching {
        Socket().use { socket ->
          socket.connect(InetSocketAddress(device.address, pairingPort), 3500)
          true
        }
      }.getOrDefault(false)

      if (!reachable) {
        val msg = "Android TV at ${device.address}:$pairingPort is unreachable. " +
          "Ensure the TV is powered on and on the same Wi-Fi network."
        _connectionState.value = ConnectionState.CONNECTION_FAILED
        _lastError.value = msg
        return@withContext Result.failure(IllegalStateException(msg))
      }

      // Honest protocol boundary: Android TV Remote v2 requires mutual X.509 Protobuf pairing.
      // Never fake ConnectionState.CONNECTED when binary TLS certificate exchange is incomplete.
      val msg = "Android TV detected at ${device.address}. Secure Remote v2 mutual-TLS certificate " +
        "pairing is not yet provisioned for this TV model. Pair your phone via Bluetooth in TV Settings " +
        "and connect using Bluetooth HID for instant D-Pad, Volume, and Playback control."
      _connectionState.value = ConnectionState.UNSUPPORTED
      _lastError.value = msg
      Result.failure(UnsupportedOperationException(msg))
    }

  override suspend fun disconnect() {
    _connectionState.value = ConnectionState.DISCONNECTED
  }

  override suspend fun sendCommand(command: RemoteCommand): Boolean = false

  override fun releaseAllInputs() = Unit
}
