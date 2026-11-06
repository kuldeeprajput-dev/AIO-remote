package com.example

import com.example.connection.wifi.WifiProtocol
import com.example.input.HidKeyCode
import com.example.input.HidModifiers
import com.example.input.MediaAction
import com.example.input.MouseButton
import com.example.input.PresentationAction
import com.example.input.RemoteCommand
import com.example.models.OperatingSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WifiProtocolTest {

  @Test
  fun `command serialization and deserialization preserve seq and payload`() {
    val commands = listOf(
      RemoteCommand.PointerMove(12, -8, timestampMs = 100L),
      RemoteCommand.PointerClick(MouseButton.RIGHT, 1, timestampMs = 101L),
      RemoteCommand.PointerScroll(0, -3, timestampMs = 102L),
      RemoteCommand.KeyTap(HidKeyCode.C, HidModifiers.LEFT_CTRL, timestampMs = 103L),
      RemoteCommand.TextInput("Hello Slate!", timestampMs = 104L),
      RemoteCommand.MediaControl(MediaAction.PLAY_PAUSE, volumePercent = 65, timestampMs = 105L),
      RemoteCommand.PresentationCommand(PresentationAction.LASER_MOVE, 0.4f, 0.7f, timestampMs = 106L)
    )

    commands.forEachIndexed { idx, cmd ->
      val seq = idx + 1L
      val json = WifiProtocol.encodeCommand(cmd, seq)
      val decoded = WifiProtocol.decodeCommand(json)
      assertEquals(seq, decoded?.seq)
      assertEquals(cmd::class, decoded!!::class)
    }
  }

  @Test
  fun `server pairing and auth challenge messages decode accurately`() {
    val challengeJson = """
      {"type":"AUTH_CHALLENGE","serverId":"srv1","serverName":"MacBook","os":"MACOS","nonce":"abc123"}
    """.trimIndent()
    val challenge = WifiProtocol.decodeServerMessage(challengeJson) as WifiProtocol.IncomingMessage.AuthChallenge
    assertEquals("MacBook", challenge.serverName)
    assertEquals(OperatingSystem.MACOS, challenge.os)
    assertEquals("abc123", challenge.nonce)

    val pairJson = """
      {"type":"PAIR_RESULT","success":true,"token":"tok_xyz","serverName":"MacBook","os":"MACOS"}
    """.trimIndent()
    val pair = WifiProtocol.decodeServerMessage(pairJson) as WifiProtocol.IncomingMessage.PairResult
    assertTrue(pair.success)
    assertEquals("tok_xyz", pair.token)
  }

  @Test
  fun `malformed json returns null safely`() {
    assertNull(WifiProtocol.decodeCommand("{not-json"))
    assertNull(WifiProtocol.decodeServerMessage("""{"type":"UNKNOWN"}"""))
  }
}
