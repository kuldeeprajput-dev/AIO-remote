package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.models.DeviceType
import com.example.models.OperatingSystem
import com.example.models.RemoteDevice
import com.example.models.TransportType
import com.example.preferences.UserPreferencesRepository
import com.example.viewmodel.SlateViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34, 36])
class SlateAppIntegrationTest {

  @get:Rule
  val composeTestRule = createEmptyComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("AIO Remote", appName)
  }

  @Test
  fun `viewModel factory supports both create overloads`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val factory = SlateViewModel.factory(app)
    val vmPlain = factory.create(SlateViewModel::class.java)
    val vmExtras = factory.create(SlateViewModel::class.java, MutableCreationExtras())
    assertNotNull(vmPlain)
    assertNotNull(vmExtras)
  }

  @Test
  fun `wifi transport connect handles invalid host and port without throwing`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val transport = com.example.connection.wifi.WifiDesktopTransport(
      com.example.security.CredentialVault(context)
    )
    val malformed = RemoteDevice(
      id = "wifi_bad",
      name = "Bad Device",
      address = "not a valid host !!!",
      port = 99999,
      deviceType = DeviceType.LAPTOP,
      os = OperatingSystem.WINDOWS,
      transportType = TransportType.WIFI_DESKTOP
    )
    val result = transport.connect(malformed)
    assert(result.isFailure)
  }

  @Test
  fun `preferences repository deduplicates saved devices cleanly`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = UserPreferencesRepository(context)
    val device = RemoteDevice(
      id = "dev_test_1",
      name = "Test Laptop",
      address = "192.168.1.42",
      deviceType = DeviceType.LAPTOP,
      os = OperatingSystem.WINDOWS,
      transportType = TransportType.WIFI_DESKTOP
    )
    repo.saveOrUpdateDevice(device)
    repo.saveOrUpdateDevice(device.copy(name = "Test Laptop Updated"))
    val saved: List<RemoteDevice> = repo.savedDevicesFlow.first()
    assertEquals(1, saved.count { d -> d.id == "dev_test_1" })
    assertEquals("Test Laptop Updated", saved.first { d -> d.id == "dev_test_1" }.name)
    repo.removeDevice("dev_test_1")
  }

  @Test
  fun `fresh launch onboarding through all screens and settings without uncaught errors`() {
    val uncaught = AtomicReference<Throwable?>(null)
    val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
      uncaught.compareAndSet(null, throwable)
    }
    try {
      ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.waitForIdle()

        if (composeTestRule.onAllNodesWithTag("skip_onboarding_button").fetchSemanticsNodes().isNotEmpty()) {
          composeTestRule.onNodeWithTag("skip_onboarding_button").performClick()
          composeTestRule.waitForIdle()
        }

        // Devices tab: verify no automatic Wi-Fi section or bottom Add Device card
        composeTestRule.onNodeWithTag("nav_devices").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodesWithTag("add_device_button").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("AVAILABLE ON SAME WI-FI").fetchSemanticsNodes().size)

        // Open 3-dot menu and verify separated Bluetooth / Wi-Fi / USB / Enter IP without descriptions
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_bluetooth_devices").assertExists()
        composeTestRule.onNodeWithTag("menu_wifi_devices").assertExists()
        composeTestRule.onNodeWithTag("menu_usb_devices").assertExists()
        composeTestRule.onNodeWithTag("menu_enter_ip").assertExists()
        assertEquals(0, composeTestRule.onAllNodesWithText("Pair with Code").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Scan & pair via Bluetooth").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("menu_enter_ip").performClick()
        composeTestRule.waitForIdle()

        // Dismiss Enter IP sheet cleanly
        composeTestRule.onNodeWithTag("close_enter_ip_sheet_button").performClick()
        composeTestRule.waitForIdle()

        // Verify USB sheet opens cleanly
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_usb_devices").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("USB Connection").assertExists()
        composeTestRule.onNodeWithTag("usb_connect_tunnel_button").assertExists()
        scenario.recreate()
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.waitForIdle()

        // Open Search popup (Saved Devices only), verify no Available Nearby, and assert search input
        composeTestRule.onNodeWithContentDescription("Search saved devices").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Search Saved Devices").assertExists()
        assertEquals(0, composeTestRule.onAllNodesWithText("Available Nearby").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("search_saved_input").assertExists()
        scenario.recreate()
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.waitForIdle()

        // Remote tab & all 6 control modes
        composeTestRule.onNodeWithTag("nav_remote").performClick()
        composeTestRule.waitForIdle()

        for (mode in listOf("KEYBOARD", "MEDIA", "PRESENTATION", "TV_REMOTE", "SHORTCUTS", "TOUCHPAD")) {
          composeTestRule.onNodeWithTag("mode_tab_$mode").performClick()
          composeTestRule.waitForIdle()
        }

        // Settings tab: verify Connection tab removed and Dark Mode / Touchpad Style work
        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodesWithTag("settings_category_connection").fetchSemanticsNodes().size)

        composeTestRule.onNodeWithTag("settings_category_appearance").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("theme_mode_DARK").performClick()
        composeTestRule.onNodeWithTag("touchpad_style_DOTTED").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()

        for (cat in listOf("touchpad", "general", "privacy", "about")) {
          val tag = "settings_category_$cat"
          composeTestRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasTestTag(tag))
          composeTestRule.waitForIdle()
          composeTestRule.onNodeWithTag(tag).performClick()
          composeTestRule.waitForIdle()
          composeTestRule.onNodeWithContentDescription("Back").performClick()
          composeTestRule.waitForIdle()
        }

        // Simulate Activity recreation (e.g. orientation/configuration change)
        scenario.recreate()
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.waitForIdle()
      }
      assertNull("Uncaught background exception during fresh launch flow: ${uncaught.get()}", uncaught.get())
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(prevHandler)
    }
  }

  @Test
  fun `post onboarding direct launch with saved devices and auto reconnect survives without crash`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    runBlocking {
      val repo = UserPreferencesRepository(context)
      repo.updateSettings { it.copy(onboardingCompleted = true, autoReconnect = true) }
      repo.saveOrUpdateDevice(
        RemoteDevice(
          id = "wifi_192.168.1.55",
          name = "Office Laptop",
          address = "192.168.1.55",
          port = 8765,
          deviceType = DeviceType.LAPTOP,
          os = OperatingSystem.WINDOWS,
          transportType = TransportType.WIFI_DESKTOP,
          isPaired = true,
          isTrusted = true
        )
      )
      repo.saveOrUpdateDevice(
        RemoteDevice(
          id = "bt_001122334455",
          name = "Living Room PC",
          address = "00:11:22:33:44:55",
          deviceType = DeviceType.LAPTOP,
          os = OperatingSystem.LINUX,
          transportType = TransportType.BLUETOOTH_HID,
          isPaired = true,
          isTrusted = true
        )
      )
    }

    val uncaught = AtomicReference<Throwable?>(null)
    val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
      uncaught.compareAndSet(null, throwable)
    }
    try {
      ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        composeTestRule.mainClock.advanceTimeBy(1_500L)
        composeTestRule.waitForIdle()

        // Directly on Devices screen (onboarding already completed)
        composeTestRule.onAllNodesWithText("Devices").onFirst().assertExists()
        composeTestRule.onNodeWithTag("saved_device_wifi_192.168.1.55").assertExists()

        // Search saved devices filters saved items only and tapping item closes sheet & connects
        composeTestRule.onNodeWithContentDescription("Search saved devices").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_saved_device_wifi_192.168.1.55").assertExists().performClick()
        composeTestRule.waitForIdle()

        // Open Overflow Menu -> Wi-Fi Devices
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_wifi_devices").performClick()
        composeTestRule.waitForIdle()

        scenario.recreate()
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.waitForIdle()
      }
      assertNull("Uncaught background exception during post-onboarding launch: ${uncaught.get()}", uncaught.get())
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(prevHandler)
    }
  }
}
