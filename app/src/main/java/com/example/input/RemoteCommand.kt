package com.example.input

enum class MouseButton(val hidBit: Int, val protocolName: String) {
  LEFT(1 shl 0, "LEFT"),
  RIGHT(1 shl 1, "RIGHT"),
  MIDDLE(1 shl 2, "MIDDLE")
}

enum class MediaAction(val consumerBitMask: Int, val protocolName: String) {
  PLAY_PAUSE(1 shl 0, "PLAY_PAUSE"),
  NEXT(1 shl 1, "NEXT"),
  PREVIOUS(1 shl 2, "PREVIOUS"),
  STOP(1 shl 3, "STOP"),
  VOLUME_UP(1 shl 4, "VOLUME_UP"),
  VOLUME_DOWN(1 shl 5, "VOLUME_DOWN"),
  MUTE(1 shl 6, "MUTE"),
  FAST_FORWARD(1 shl 7, "FAST_FORWARD"),
  REWIND(1 shl 8, "REWIND")
}

enum class PresentationAction(val protocolName: String) {
  NEXT_SLIDE("NEXT_SLIDE"),
  PREVIOUS_SLIDE("PREVIOUS_SLIDE"),
  START_PRESENTATION("START_PRESENTATION"),
  END_PRESENTATION("END_PRESENTATION"),
  BLACK_SCREEN("BLACK_SCREEN"),
  WHITE_SCREEN("WHITE_SCREEN"),
  LASER_MOVE("LASER_MOVE"),
  LASER_OFF("LASER_OFF")
}

enum class TvAction(val protocolName: String) {
  POWER("POWER"),
  HOME("HOME"),
  BACK("BACK"),
  DPAD_UP("DPAD_UP"),
  DPAD_DOWN("DPAD_DOWN"),
  DPAD_LEFT("DPAD_LEFT"),
  DPAD_RIGHT("DPAD_RIGHT"),
  DPAD_CENTER("DPAD_CENTER"),
  VOLUME_UP("VOLUME_UP"),
  VOLUME_DOWN("VOLUME_DOWN"),
  MUTE("MUTE"),
  CHANNEL_UP("CHANNEL_UP"),
  CHANNEL_DOWN("CHANNEL_DOWN"),
  PLAY_PAUSE("PLAY_PAUSE"),
  MENU("MENU"),
  INPUT_SOURCE("INPUT_SOURCE"),
  LAUNCH_APP("LAUNCH_APP")
}

sealed interface RemoteCommand {
  val seq: Long
  val timestampMs: Long

  data class PointerMove(
    val dx: Int,
    val dy: Int,
    override val seq: Long = 0L,
    override val timestampMs: Long = System.currentTimeMillis()
  ) : RemoteCommand

  data class PointerClick(
    val button: MouseButton,
    val count: Int = 1,
    override val seq: Long = 0L,
    override val timestampMs: Long = System.currentTimeMillis()
  ) : RemoteCommand

  data class PointerDown(
    val button: MouseButton,
    override val seq: Long = 0L,
    override val timestampMs: Long = System.currentTimeMillis()
  ) : RemoteCommand

  data class PointerUp(
    val button: MouseButton,
    override val seq: Long = 0L,
    override val timestampMs: Long = System.currentTimeMillis()
  ) : RemoteCommand

  data class PointerScroll(
    val dx: Int,
    val dy: Int,
    override val seq: Long = 0L,
    override val timestampMs: Long = System.currentTimeMillis()
  ) : RemoteCommand

  data class KeyDown(
    val keyCode: HidKeyCode,
    val modifiers: Int = HidModifiers.NONE,
    override val seq: Long = 0L,
    override val timestampMs: Long = System.currentTimeMillis()
  ) : RemoteCommand

  data class KeyUp(
    val keyCode: HidKeyCode,
    val modifiers: Int = HidModifiers.NONE,
    override val seq: Long = 0L,
    override val timestampMs: Long = System.currentTimeMillis()
  ) : RemoteCommand

  data class KeyTap(
    val keyCode: HidKeyCode,
    val modifiers: Int = HidModifiers.NONE,
    override val seq: Long = 0L,
    override val timestampMs: Long = System.currentTimeMillis()
  ) : RemoteCommand

  data class TextInput(
    val text: String,
    override val seq: Long = 0L,
    override val timestampMs: Long = System.currentTimeMillis()
  ) : RemoteCommand

  data class MediaControl(
    val action: MediaAction,
    val volumePercent: Int? = null,
    override val seq: Long = 0L,
    override val timestampMs: Long = System.currentTimeMillis()
  ) : RemoteCommand

  data class PresentationCommand(
    val action: PresentationAction,
    val laserX: Float? = null,
    val laserY: Float? = null,
    override val seq: Long = 0L,
    override val timestampMs: Long = System.currentTimeMillis()
  ) : RemoteCommand

  data class TvCommand(
    val action: TvAction,
    val appPackage: String? = null,
    override val seq: Long = 0L,
    override val timestampMs: Long = System.currentTimeMillis()
  ) : RemoteCommand

  data class PinchZoom(
    val scaleDelta: Float,
    override val seq: Long = 0L,
    override val timestampMs: Long = System.currentTimeMillis()
  ) : RemoteCommand

  data class Ping(
    override val seq: Long = 0L,
    override val timestampMs: Long = System.currentTimeMillis()
  ) : RemoteCommand

  data class ReleaseAllInputs(
    override val seq: Long = 0L,
    override val timestampMs: Long = System.currentTimeMillis()
  ) : RemoteCommand
}
