package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.input.HidAsciiMapper
import com.example.input.HidKeyCode
import com.example.input.HidModifiers
import com.example.models.ConnectionState
import com.example.models.DeviceCapabilities
import com.example.models.RemoteDevice
import com.example.models.TransportType
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.SlateRemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import com.example.models.BluetoothCapabilityReport
import com.example.models.OperatingSystem
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.remote.KeyboardPane
import com.example.ui.screens.remote.TvRemotePane
import com.example.preferences.ThemeMode
import com.example.preferences.UserSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CapabilitiesAndStateTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `device capabilities accurately filter transport features`() {
    val bt = DeviceCapabilities.BLUETOOTH_HID_DEFAULT
    assertTrue(bt.hasMouse)
    assertTrue(bt.hasKeyboard)
    assertFalse(bt.hasPresentationLaser)
    assertFalse(bt.supportsUnicodeText)

    val wifi = DeviceCapabilities.WIFI_DESKTOP_DEFAULT
    assertTrue(wifi.hasPresentationLaser)
    assertTrue(wifi.supportsUnicodeText)
    assertTrue(wifi.hasMediaMetadata)

    val tv = DeviceCapabilities.ANDROID_TV_DEFAULT
    assertFalse(tv.hasMouse)
    assertTrue(tv.hasTvDpad)
    assertTrue(tv.hasTvPower)

    val usbDevice = RemoteDevice(
      id = "usb_127.0.0.1",
      name = "USB PC",
      address = "127.0.0.1",
      transportType = TransportType.USB
    )
    assertTrue(usbDevice.capabilities.hasMouse)
    assertTrue(usbDevice.capabilities.supportsUnicodeText)
  }

  @Test
  fun `hid ascii mapper translates characters and uppercase modifiers`() {
    val lower = HidAsciiMapper.mapChar('a')
    assertEquals(HidKeyCode.A, lower?.keyCode)
    assertEquals(HidModifiers.NONE, lower?.modifiers)

    val upper = HidAsciiMapper.mapChar('A')
    assertEquals(HidKeyCode.A, upper?.keyCode)
    assertEquals(HidModifiers.LEFT_SHIFT, upper?.modifiers)

    assertTrue(HidAsciiMapper.supportsString("Hello 123!"))
    assertFalse(HidAsciiMapper.supportsString("Café 🚀"))
  }

  @Test
  fun `connection state active flag distinguishes connected from transitional states`() {
    assertTrue(ConnectionState.CONNECTED.isActive)
    assertFalse(ConnectionState.SEARCHING.isActive)
    assertFalse(ConnectionState.PAIRING.isActive)
    assertFalse(ConnectionState.CONNECTING.isActive)
    assertFalse(ConnectionState.DISCONNECTED.isActive)
    assertFalse(ConnectionState.UNSUPPORTED.isActive)
  }

  @Test
  fun `default theme mode is light and system navigation bar stays visible`() {
    assertEquals(ThemeMode.LIGHT, UserSettings().themeMode)
    assertFalse(UserSettings().autoHideSystemNavBar)
  }

  @Test
  fun `onboarding flow renders welcome and completes 3 feature pages`() {
    var finished = false
    composeTestRule.setContent {
      SlateRemoteTheme {
        OnboardingScreen(onFinishOnboarding = { finished = true })
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("get_started_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    // Page 1 -> Page 2 -> Page 3 -> Finish
    composeTestRule.onNodeWithTag("onboarding_continue_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("onboarding_continue_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("onboarding_continue_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertTrue(finished)
  }

  @Test
  fun `devices keyboard and tv remote panes render cleanly`() {
    composeTestRule.setContent {
      SlateRemoteTheme {
        DevicesScreen(
          savedDevices = emptyList(),
          activeDevice = null,
          connectionState = ConnectionState.DISCONNECTED,
          latencyMs = null,
          lastError = null,
          bluetoothCapability = BluetoothCapabilityReport(true, true, true, true, emptyList(), "Ready"),
          discoveredBluetooth = emptyList(),
          isScanningBluetooth = false,
          discoveredWifi = emptyList(),
          isScanningWifi = false,
          wifiScanError = null,
          localWifiIp = "192.168.1.25",
          reduceMotion = true,
          onConnectSaved = {},
          onDisconnect = {},
          onRemoveDevice = {},
          onOpenRemote = {},
          onStartDiscovery = {},
          onStopDiscovery = {},
          onRefreshBluetooth = {},
          onConnectDiscovered = { _, _, _ -> },
          onConnectManualWifi = { _, _, _, _, _, _ -> }
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("empty_saved_devices").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("More options").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("menu_bluetooth_devices").assertIsDisplayed()
    composeTestRule.onNodeWithTag("menu_wifi_devices").assertIsDisplayed()
    composeTestRule.onNodeWithTag("menu_usb_devices").assertIsDisplayed()
    composeTestRule.onNodeWithTag("menu_enter_ip").assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithText("Scan & pair via Bluetooth").fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithText("Scan receivers on same Wi-Fi").fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithText("Connect manually by IP").fetchSemanticsNodes().size)
  }

  @Test
  fun `keyboard pane exposes primary interactive controls`() {
    composeTestRule.setContent {
      SlateRemoteTheme {
        KeyboardPane(
          connectionState = ConnectionState.CONNECTED,
          capabilities = DeviceCapabilities.WIFI_DESKTOP_DEFAULT,
          targetOs = OperatingSystem.WINDOWS,
          clipboardOptIn = false,
          onKeyTap = { _, _ -> },
          onSendText = {},
          onSendClipboard = {},
          onBackToTouchpad = {}
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("keyboard_text_input").assertIsDisplayed()
  }

  @Test
  fun `tv pane exposes primary interactive controls`() {
    composeTestRule.setContent {
      SlateRemoteTheme {
        TvRemotePane(
          activeDevice = null,
          connectionState = ConnectionState.CONNECTED,
          capabilities = DeviceCapabilities.ANDROID_TV_DEFAULT,
          onSendTv = { _, _ -> },
          onSendText = {},
          onOpenDevices = {}
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("tv_dpad_ok").assertIsDisplayed()
    composeTestRule.onNodeWithTag("tv_back_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("tv_home_button").assertIsDisplayed()
  }
}
