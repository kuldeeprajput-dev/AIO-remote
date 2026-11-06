package com.example.connection

import com.example.input.RemoteCommand
import com.example.models.ConnectionState
import com.example.models.DeviceCapabilities
import com.example.models.MediaPlaybackInfo
import com.example.models.RemoteDevice
import kotlinx.coroutines.flow.StateFlow

interface RemoteTransport {
  val connectionState: StateFlow<ConnectionState>
  val capabilities: StateFlow<DeviceCapabilities>
  val mediaInfo: StateFlow<MediaPlaybackInfo?>
  val lastError: StateFlow<String?>
  val latencyMs: StateFlow<Int?>

  suspend fun connect(device: RemoteDevice, pairingCode: String? = null): Result<RemoteDevice>
  suspend fun disconnect()
  suspend fun sendCommand(command: RemoteCommand): Boolean
  fun releaseAllInputs()
}
