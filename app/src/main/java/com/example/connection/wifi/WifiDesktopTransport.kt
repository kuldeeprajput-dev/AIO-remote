package com.example.connection.wifi

import android.os.Build
import com.example.connection.RemoteTransport
import com.example.input.RemoteCommand
import com.example.models.ConnectionState
import com.example.models.DeviceCapabilities
import com.example.models.MediaPlaybackInfo
import com.example.models.RemoteDevice
import com.example.models.TransportType
import com.example.security.CredentialVault
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Authenticated WebSocket transport shared by Wi-Fi and USB desktop receivers. */
class WifiDesktopTransport(
  private val vault: CredentialVault
) : RemoteTransport {

  private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
  private val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(1400, TimeUnit.MILLISECONDS)
      .readTimeout(0, TimeUnit.MILLISECONDS)
      .pingInterval(15, TimeUnit.SECONDS)
      .build()
  }

  private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
  override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

  private val _capabilities = MutableStateFlow(DeviceCapabilities.WIFI_DESKTOP_DEFAULT)
  override val capabilities: StateFlow<DeviceCapabilities> = _capabilities.asStateFlow()

  private val _mediaInfo = MutableStateFlow<MediaPlaybackInfo?>(null)
  override val mediaInfo: StateFlow<MediaPlaybackInfo?> = _mediaInfo.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  override val lastError: StateFlow<String?> = _lastError.asStateFlow()

  private val _latencyMs = MutableStateFlow<Int?>(null)
  override val latencyMs: StateFlow<Int?> = _latencyMs.asStateFlow()

  private var webSocket: WebSocket? = null
  private var heartbeatJob: Job? = null
  private val seqCounter = AtomicLong(1L)

  private var pendingConnectResult: CompletableDeferred<Result<RemoteDevice>>? = null
  private val clientDeviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

  override suspend fun connect(device: RemoteDevice, pairingCode: String?): Result<RemoteDevice> {
    disconnect()
    _lastError.value = null
    val cleanCode = pairingCode?.trim()?.takeIf { it.isNotEmpty() }
    _connectionState.value = if (cleanCode != null) ConnectionState.PAIRING else ConnectionState.CONNECTING

    val viaUsb = device.transportType == TransportType.USB
    // The receiver keeps one token per client ID. USB must not replace the Wi-Fi token.
    val approvalId = if (viaUsb) "${vault.installationId}:usb:${device.id}" else vault.installationId
    val deferred = CompletableDeferred<Result<RemoteDevice>>()
    pendingConnectResult = deferred

    val host = device.address.trim()
    val port = device.port
    if (host.isBlank() || port !in 1..65535) {
      pendingConnectResult = null
      return failConnection(device)
    }

    val url = "ws://$host:$port/ws"
    val startedSocket = runCatching {
      val request = Request.Builder().url(url).build()
      webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
        private var supportsDesktopApproval = false

        private fun requestApproval(socket: WebSocket) {
          _connectionState.value = ConnectionState.PAIRING
          socket.send(WifiProtocol.encodeApprovalRequest(approvalId, clientDeviceName))
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
          runCatching {
            if (!cleanCode.isNullOrBlank()) {
              val req = WifiProtocol.encodePairRequest(
                deviceName = clientDeviceName,
                deviceId = device.id,
                pairingCode = cleanCode
              )
              webSocket.send(req)
            }
          }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
          if (this@WifiDesktopTransport.webSocket !== webSocket) return
          runCatching {
            when (val msg = WifiProtocol.decodeServerMessage(text)) {
              is WifiProtocol.IncomingMessage.AuthChallenge -> {
                if (!cleanCode.isNullOrBlank()) return@runCatching
                supportsDesktopApproval = msg.supportsDesktopApproval
                val token = vault.getDeviceToken(device.id)
                if (token == null) {
                  if (supportsDesktopApproval) {
                    requestApproval(webSocket)
                    return@runCatching
                  }
                  val err = "Update the Slate receiver on the computer to connect without a code."
                  _lastError.value = err
                  _connectionState.value = ConnectionState.CONNECTION_FAILED
                  deferred.complete(Result.failure(SecurityException(err)))
                  webSocket.close(1000, "Pairing required")
                  return@runCatching
                }
                val hmac = vault.computeHmacSha256(token, msg.nonce)
                webSocket.send(
                  WifiProtocol.encodeAuthResponse(
                    deviceId = if (supportsDesktopApproval) approvalId else device.id,
                    deviceName = clientDeviceName,
                    nonce = msg.nonce,
                    hmacSignature = hmac
                  )
                )
              }

              is WifiProtocol.IncomingMessage.PairResult -> {
                if (msg.success && !msg.token.isNullOrBlank()) {
                  vault.storeDeviceToken(device.id, msg.token)
                  val verified = device.copy(
                    name = msg.serverName?.takeIf { it.isNotBlank() } ?: device.name,
                    os = if (msg.os != com.example.models.OperatingSystem.UNKNOWN) msg.os else device.os,
                    isPaired = true,
                    isTrusted = true,
                    lastConnectedEpochMs = System.currentTimeMillis()
                  )
                  _connectionState.value = ConnectionState.CONNECTED
                  startHeartbeat()
                  if (!deferred.isCompleted) deferred.complete(Result.success(verified))
                } else {
                  val err = msg.message ?: "The receiver did not approve the connection."
                  _connectionState.value = ConnectionState.CONNECTION_FAILED
                  _lastError.value = err
                  if (!deferred.isCompleted) deferred.complete(Result.failure(SecurityException(err)))
                  webSocket.close(1000, "Pair failed")
                }
              }

              is WifiProtocol.IncomingMessage.AuthResult -> {
                if (msg.success) {
                  val verified = device.copy(
                    name = msg.serverName?.takeIf { it.isNotBlank() } ?: device.name,
                    os = if (msg.os != com.example.models.OperatingSystem.UNKNOWN) msg.os else device.os,
                    isPaired = true,
                    isTrusted = true,
                    lastConnectedEpochMs = System.currentTimeMillis()
                  )
                  _connectionState.value = ConnectionState.CONNECTED
                  startHeartbeat()
                  if (!deferred.isCompleted) deferred.complete(Result.success(verified))
                } else {
                  if (supportsDesktopApproval) {
                    requestApproval(webSocket)
                    return@runCatching
                  }
                  val err = msg.message ?: "Authentication rejected by receiver."
                  _connectionState.value = ConnectionState.CONNECTION_FAILED
                  _lastError.value = err
                  if (!deferred.isCompleted) deferred.complete(Result.failure(SecurityException(err)))
                  webSocket.close(1000, "Auth failed")
                }
              }

              is WifiProtocol.IncomingMessage.Pong -> {
                val rtt = (System.currentTimeMillis() - msg.clientTimestampMs).toInt().coerceAtLeast(1)
                _latencyMs.value = rtt
              }

              is WifiProtocol.IncomingMessage.MediaMetadata -> {
                _mediaInfo.value = if (msg.title.isBlank()) null else MediaPlaybackInfo(
                  title = msg.title,
                  artist = msg.artist,
                  isPlaying = msg.isPlaying,
                  volumePercent = msg.volumePercent,
                  isMuted = msg.isMuted
                )
              }

              is WifiProtocol.IncomingMessage.ErrorMessage -> {
                _lastError.value = msg.message
              }

              is WifiProtocol.IncomingMessage.Ack, null -> Unit
            }
          }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
          if (deferred.isCompleted && this@WifiDesktopTransport.webSocket === webSocket) {
            heartbeatJob?.cancel()
            _latencyMs.value = null
            if (_connectionState.value == ConnectionState.CONNECTED) {
              _connectionState.value = ConnectionState.DISCONNECTED
            }
          }
          if (pendingConnectResult === deferred && !deferred.isCompleted) {
            deferred.complete(failConnection(device))
          }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
          if (this@WifiDesktopTransport.webSocket === webSocket) {
            heartbeatJob?.cancel()
            _latencyMs.value = null
            if (_connectionState.value == ConnectionState.CONNECTED) {
              _connectionState.value = ConnectionState.DISCONNECTED
            }
          }
          if (pendingConnectResult === deferred && !deferred.isCompleted) {
            deferred.complete(failConnection(device))
          }
        }
      })
      true
    }.getOrDefault(false)

    val result = if (startedSocket) withTimeoutOrNull(35_000L) { deferred.await() } else null
    if (pendingConnectResult !== deferred) {
      return result ?: Result.failure(IllegalStateException("Connection cancelled."))
    }
    pendingConnectResult = null
    if (result != null) return result

    webSocket?.cancel()
    webSocket = null
    val approvalTimedOut = _connectionState.value == ConnectionState.PAIRING
    return failConnection(device, approvalTimedOut)
  }

  private fun failConnection(device: RemoteDevice, approvalTimedOut: Boolean = false): Result<RemoteDevice> {
    val connection = if (device.transportType == TransportType.USB) "USB" else "Wi-Fi"
    val message = if (approvalTimedOut) {
      "Approval timed out. Reconnect and click Allow on the computer."
    } else {
      "No Slate receiver answered at ${device.address}:${device.port} over $connection."
    }
    _lastError.value = message
    _connectionState.value = ConnectionState.CONNECTION_FAILED
    _latencyMs.value = null
    return Result.failure(IllegalStateException(message))
  }

  override suspend fun disconnect() {
    releaseAllInputs()
    heartbeatJob?.cancel()
    heartbeatJob = null
    pendingConnectResult?.complete(Result.failure(IllegalStateException("Connection cancelled.")))
    pendingConnectResult = null
    val socket = webSocket
    webSocket = null
    socket?.close(1000, "Client disconnected")
    _mediaInfo.value = null
    _latencyMs.value = null
    _lastError.value = null
    _connectionState.value = ConnectionState.DISCONNECTED
  }

  override suspend fun sendCommand(command: RemoteCommand): Boolean {
    if (_connectionState.value != ConnectionState.CONNECTED) return false
    val seq = seqCounter.getAndIncrement()
    return webSocket?.send(WifiProtocol.encodeCommand(command, seq)) ?: false
  }

  override fun releaseAllInputs() {
    if (_connectionState.value != ConnectionState.CONNECTED) return
    val seq = seqCounter.getAndIncrement()
    webSocket?.send(WifiProtocol.encodeCommand(RemoteCommand.ReleaseAllInputs(seq), seq))
  }

  private fun startHeartbeat() {
    heartbeatJob?.cancel()
    heartbeatJob = scope.launch {
      while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
        delay(8_000L)
        sendCommand(RemoteCommand.Ping())
      }
    }
  }
}
