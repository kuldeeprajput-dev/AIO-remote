package com.example.connection.wifi

import com.example.input.HidKeyCode
import com.example.input.MediaAction
import com.example.input.MouseButton
import com.example.input.PresentationAction
import com.example.input.RemoteCommand
import com.example.models.OperatingSystem
import org.json.JSONObject

/**
 * Shared JSON WebSocket protocol between AIO Remote (Android) and the Desktop Companion Receiver.
 */
object WifiProtocol {
  const val PROTOCOL_VERSION = 1
  const val DEFAULT_PORT = 8765
  const val NSD_SERVICE_TYPE = "_slateremote._tcp."

  sealed interface IncomingMessage {
    data class AuthChallenge(
      val serverId: String,
      val serverName: String,
      val os: OperatingSystem,
      val nonce: String,
      val supportsDesktopApproval: Boolean = false
    ) : IncomingMessage

    data class PairResult(
      val success: Boolean,
      val token: String?,
      val serverName: String?,
      val os: OperatingSystem,
      val message: String?
    ) : IncomingMessage

    data class AuthResult(
      val success: Boolean,
      val serverName: String?,
      val os: OperatingSystem,
      val message: String?
    ) : IncomingMessage

    data class Ack(val seq: Long) : IncomingMessage
    data class Pong(val seq: Long, val clientTimestampMs: Long) : IncomingMessage
    data class MediaMetadata(
      val title: String,
      val artist: String?,
      val isPlaying: Boolean,
      val volumePercent: Int?,
      val isMuted: Boolean?
    ) : IncomingMessage

    data class ErrorMessage(val code: String, val message: String) : IncomingMessage
  }

  fun encodePairRequest(
    deviceName: String,
    deviceId: String,
    pairingCode: String
  ): String = JSONObject().apply {
    put("type", "PAIR_REQUEST")
    put("version", PROTOCOL_VERSION)
    put("deviceName", deviceName)
    put("deviceId", deviceId)
    put("code", pairingCode.trim())
  }.toString()

  fun encodeApprovalRequest(deviceId: String, deviceName: String): String = JSONObject().apply {
    put("type", "APPROVAL_REQUEST")
    put("deviceId", deviceId)
    put("deviceName", deviceName)
  }.toString()

  fun encodeAuthResponse(
    deviceId: String,
    deviceName: String,
    nonce: String,
    hmacSignature: String
  ): String = JSONObject().apply {
    put("type", "AUTH_VERIFY")
    put("version", PROTOCOL_VERSION)
    put("deviceId", deviceId)
    put("deviceName", deviceName)
    put("nonce", nonce)
    put("hmac", hmacSignature)
  }.toString()

  fun encodeCommand(command: RemoteCommand, seq: Long): String {
    val obj = JSONObject()
    obj.put("seq", seq)
    obj.put("ts", command.timestampMs)

    when (command) {
      is RemoteCommand.PointerMove -> {
        obj.put("type", "POINTER_MOVE")
        obj.put("dx", command.dx)
        obj.put("dy", command.dy)
      }
      is RemoteCommand.PointerClick -> {
        obj.put("type", "POINTER_CLICK")
        obj.put("button", command.button.protocolName)
        obj.put("count", command.count)
      }
      is RemoteCommand.PointerDown -> {
        obj.put("type", "POINTER_DOWN")
        obj.put("button", command.button.protocolName)
      }
      is RemoteCommand.PointerUp -> {
        obj.put("type", "POINTER_UP")
        obj.put("button", command.button.protocolName)
      }
      is RemoteCommand.PointerScroll -> {
        obj.put("type", "POINTER_SCROLL")
        obj.put("dx", command.dx)
        obj.put("dy", command.dy)
      }
      is RemoteCommand.KeyDown -> {
        obj.put("type", "KEY_DOWN")
        obj.put("key", command.keyCode.protocolName)
        obj.put("modifiers", command.modifiers)
      }
      is RemoteCommand.KeyUp -> {
        obj.put("type", "KEY_UP")
        obj.put("key", command.keyCode.protocolName)
        obj.put("modifiers", command.modifiers)
      }
      is RemoteCommand.KeyTap -> {
        obj.put("type", "KEY_TAP")
        obj.put("key", command.keyCode.protocolName)
        obj.put("modifiers", command.modifiers)
      }
      is RemoteCommand.TextInput -> {
        obj.put("type", "TEXT_INPUT")
        obj.put("text", command.text)
      }
      is RemoteCommand.MediaControl -> {
        obj.put("type", "MEDIA_CONTROL")
        obj.put("action", command.action.protocolName)
        command.volumePercent?.let { obj.put("volumePercent", it) }
      }
      is RemoteCommand.PresentationCommand -> {
        obj.put("type", "PRESENTATION_COMMAND")
        obj.put("action", command.action.protocolName)
        command.laserX?.let { obj.put("laserX", it.toDouble()) }
        command.laserY?.let { obj.put("laserY", it.toDouble()) }
      }
      is RemoteCommand.TvCommand -> {
        obj.put("type", "TV_COMMAND")
        obj.put("action", command.action.protocolName)
      }
      is RemoteCommand.PinchZoom -> {
        obj.put("type", "PINCH_ZOOM")
        obj.put("scaleDelta", command.scaleDelta.toDouble())
      }
      is RemoteCommand.Ping -> {
        obj.put("type", "PING")
      }
      is RemoteCommand.ReleaseAllInputs -> {
        obj.put("type", "RELEASE_ALL")
      }
    }
    return obj.toString()
  }

  fun decodeCommand(rawJson: String): RemoteCommand? = runCatching {
    val obj = JSONObject(rawJson)
    val seq = obj.optLong("seq", 0L)
    val ts = obj.optLong("ts", 0L)
    when (obj.getString("type")) {
      "POINTER_MOVE" -> RemoteCommand.PointerMove(obj.getInt("dx"), obj.getInt("dy"), seq, ts)
      "POINTER_CLICK" -> RemoteCommand.PointerClick(
        MouseButton.valueOf(obj.getString("button")),
        obj.optInt("count", 1),
        seq,
        ts
      )
      "POINTER_DOWN" -> RemoteCommand.PointerDown(MouseButton.valueOf(obj.getString("button")), seq, ts)
      "POINTER_UP" -> RemoteCommand.PointerUp(MouseButton.valueOf(obj.getString("button")), seq, ts)
      "POINTER_SCROLL" -> RemoteCommand.PointerScroll(obj.getInt("dx"), obj.getInt("dy"), seq, ts)
      "KEY_DOWN" -> RemoteCommand.KeyDown(
        HidKeyCode.fromProtocolName(obj.getString("key")) ?: HidKeyCode.NONE,
        obj.optInt("modifiers", 0),
        seq,
        ts
      )
      "KEY_UP" -> RemoteCommand.KeyUp(
        HidKeyCode.fromProtocolName(obj.getString("key")) ?: HidKeyCode.NONE,
        obj.optInt("modifiers", 0),
        seq,
        ts
      )
      "KEY_TAP" -> RemoteCommand.KeyTap(
        HidKeyCode.fromProtocolName(obj.getString("key")) ?: HidKeyCode.NONE,
        obj.optInt("modifiers", 0),
        seq,
        ts
      )
      "TEXT_INPUT" -> RemoteCommand.TextInput(obj.getString("text"), seq, ts)
      "MEDIA_CONTROL" -> RemoteCommand.MediaControl(
        MediaAction.valueOf(obj.getString("action")),
        if (obj.has("volumePercent")) obj.getInt("volumePercent") else null,
        seq,
        ts
      )
      "PRESENTATION_COMMAND" -> RemoteCommand.PresentationCommand(
        PresentationAction.valueOf(obj.getString("action")),
        if (obj.has("laserX")) obj.getDouble("laserX").toFloat() else null,
        if (obj.has("laserY")) obj.getDouble("laserY").toFloat() else null,
        seq,
        ts
      )
      "PINCH_ZOOM" -> RemoteCommand.PinchZoom(obj.getDouble("scaleDelta").toFloat(), seq, ts)
      "PING" -> RemoteCommand.Ping(seq, ts)
      "RELEASE_ALL" -> RemoteCommand.ReleaseAllInputs(seq, ts)
      else -> null
    }
  }.getOrNull()

  fun decodeServerMessage(rawJson: String): IncomingMessage? = runCatching {
    val obj = JSONObject(rawJson)
    when (obj.getString("type")) {
      "AUTH_CHALLENGE" -> IncomingMessage.AuthChallenge(
        serverId = obj.getString("serverId"),
        serverName = obj.optString("serverName", "Desktop"),
        os = parseOs(obj.optString("os")),
        nonce = obj.getString("nonce"),
        supportsDesktopApproval = obj.optBoolean("desktopApproval")
      )
      "PAIR_RESULT" -> IncomingMessage.PairResult(
        success = obj.getBoolean("success"),
        token = if (obj.has("token") && !obj.isNull("token")) obj.getString("token") else null,
        serverName = if (obj.has("serverName")) obj.getString("serverName") else null,
        os = parseOs(obj.optString("os")),
        message = if (obj.has("message")) obj.getString("message") else null
      )
      "AUTH_RESULT" -> IncomingMessage.AuthResult(
        success = obj.getBoolean("success"),
        serverName = if (obj.has("serverName")) obj.getString("serverName") else null,
        os = parseOs(obj.optString("os")),
        message = if (obj.has("message")) obj.getString("message") else null
      )
      "ACK" -> IncomingMessage.Ack(seq = obj.getLong("seq"))
      "PONG" -> IncomingMessage.Pong(
        seq = obj.optLong("seq", 0L),
        clientTimestampMs = obj.optLong("ts", 0L)
      )
      "MEDIA_META" -> IncomingMessage.MediaMetadata(
        title = obj.optString("title", ""),
        artist = if (obj.has("artist") && !obj.isNull("artist")) obj.getString("artist") else null,
        isPlaying = obj.optBoolean("isPlaying", false),
        volumePercent = if (obj.has("volumePercent") && !obj.isNull("volumePercent")) obj.getInt("volumePercent") else null,
        isMuted = if (obj.has("isMuted") && !obj.isNull("isMuted")) obj.getBoolean("isMuted") else null
      )
      "ERROR" -> IncomingMessage.ErrorMessage(
        code = obj.optString("code", "UNKNOWN"),
        message = obj.optString("message", "Unknown server error")
      )
      else -> null
    }
  }.getOrNull()

  private fun parseOs(value: String): OperatingSystem = when (value.uppercase()) {
    "WINDOWS" -> OperatingSystem.WINDOWS
    "MACOS", "DARWIN" -> OperatingSystem.MACOS
    "LINUX" -> OperatingSystem.LINUX
    else -> OperatingSystem.UNKNOWN
  }
}
