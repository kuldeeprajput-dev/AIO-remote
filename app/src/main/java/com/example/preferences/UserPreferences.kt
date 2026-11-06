package com.example.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.DefaultShortcuts
import com.example.domain.MacroStep
import com.example.domain.ShortcutAction
import com.example.domain.ShortcutIconKey
import com.example.domain.ShortcutItem
import com.example.gestures.ThreeFingerShortcutAction
import com.example.gestures.TouchpadGestureConfig
import com.example.input.HidKeyCode
import com.example.input.MediaAction
import com.example.input.PresentationAction
import com.example.models.DeviceCapabilities
import com.example.models.DeviceType
import com.example.models.OperatingSystem
import com.example.models.RemoteDevice
import com.example.models.TransportType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.slateDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "slate_remote_prefs",
  corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

enum class ThemeMode(val label: String) {
  SYSTEM("Follow System"),
  LIGHT("Light"),
  DARK("Dark")
}

enum class TouchpadSurfaceStyle(val label: String) {
  FLAT("Flat Slate"),
  BORDERED("Crisp Frame"),
  DOTTED("Micro Grid")
}

enum class OrientationPreference(val label: String) {
  AUTO("Automatic"),
  PORTRAIT("Portrait"),
  LANDSCAPE("Landscape")
}

enum class PresentationSoftwarePreset(
  val label: String,
  val nextKey: HidKeyCode,
  val prevKey: HidKeyCode,
  val startKey: HidKeyCode,
  val blackKey: HidKeyCode,
  val whiteKey: HidKeyCode
) {
  POWERPOINT("PowerPoint / Impress", HidKeyCode.RIGHT_ARROW, HidKeyCode.LEFT_ARROW, HidKeyCode.F5, HidKeyCode.B, HidKeyCode.W),
  GOOGLE_SLIDES("Google Slides", HidKeyCode.RIGHT_ARROW, HidKeyCode.LEFT_ARROW, HidKeyCode.F5, HidKeyCode.B, HidKeyCode.W),
  KEYNOTE("Apple Keynote", HidKeyCode.SPACE, HidKeyCode.LEFT_ARROW, HidKeyCode.P, HidKeyCode.B, HidKeyCode.W)
}

data class UserSettings(
  val onboardingCompleted: Boolean = false,
  val preferredTransport: TransportType = TransportType.BLUETOOTH_HID,
  val autoReconnect: Boolean = true,
  val pointerSensitivity: Float = 1.4f,
  val pointerAcceleration: Float = 0.35f,
  val scrollSpeed: Float = 1.0f,
  val naturalScrolling: Boolean = true,
  val tapToClick: Boolean = true,
  val twoFingerRightClick: Boolean = true,
  val hapticFeedback: Boolean = true,
  val leftHandedLayout: Boolean = false,
  val threeFingerSwipeUp: ThreeFingerShortcutAction = ThreeFingerShortcutAction.TASK_SWITCHER,
  val threeFingerSwipeDown: ThreeFingerShortcutAction = ThreeFingerShortcutAction.SHOW_DESKTOP,
  val themeMode: ThemeMode = ThemeMode.LIGHT,
  val touchpadStyle: TouchpadSurfaceStyle = TouchpadSurfaceStyle.BORDERED,
  val minimalAnimations: Boolean = false,
  val keepScreenAwake: Boolean = true,
  val autoHideSystemNavBar: Boolean = false,
  val orientationPreference: OrientationPreference = OrientationPreference.AUTO,
  val clipboardSyncOptIn: Boolean = true,
  val presentationPreset: PresentationSoftwarePreset = PresentationSoftwarePreset.POWERPOINT,
  val activeDeviceId: String? = null
) {
  fun toGestureConfig(): TouchpadGestureConfig = TouchpadGestureConfig(
    sensitivity = pointerSensitivity,
    pointerAcceleration = pointerAcceleration,
    scrollSpeed = scrollSpeed,
    naturalScrolling = naturalScrolling,
    tapToClick = tapToClick,
    twoFingerRightClick = twoFingerRightClick,
    threeFingerSwipeUp = threeFingerSwipeUp,
    threeFingerSwipeDown = threeFingerSwipeDown
  )
}

class UserPreferencesRepository(context: Context) {

  private val appContext: Context = context.applicationContext ?: context
  private val dataStore: DataStore<Preferences>
    get() = appContext.slateDataStore

  private object Keys {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val PREFERRED_TRANSPORT = stringPreferencesKey("preferred_transport")
    val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
    val POINTER_SENSITIVITY = floatPreferencesKey("pointer_sensitivity")
    val POINTER_ACCELERATION = floatPreferencesKey("pointer_acceleration")
    val SCROLL_SPEED = floatPreferencesKey("scroll_speed")
    val NATURAL_SCROLLING = booleanPreferencesKey("natural_scrolling")
    val TAP_TO_CLICK = booleanPreferencesKey("tap_to_click")
    val TWO_FINGER_RIGHT_CLICK = booleanPreferencesKey("two_finger_right_click")
    val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
    val LEFT_HANDED_LAYOUT = booleanPreferencesKey("left_handed_layout")
    val THREE_FINGER_UP = stringPreferencesKey("three_finger_up")
    val THREE_FINGER_DOWN = stringPreferencesKey("three_finger_down")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val TOUCHPAD_STYLE = stringPreferencesKey("touchpad_style")
    val MINIMAL_ANIMATIONS = booleanPreferencesKey("minimal_animations")
    val KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
    val AUTO_HIDE_SYSTEM_NAV_BAR = booleanPreferencesKey("auto_hide_system_nav_bar")
    val ORIENTATION_PREF = stringPreferencesKey("orientation_pref")
    val CLIPBOARD_SYNC = booleanPreferencesKey("clipboard_sync")
    val PRESENTATION_PRESET = stringPreferencesKey("presentation_preset")
    val ACTIVE_DEVICE_ID = stringPreferencesKey("active_device_id")
    val SAVED_DEVICES_JSON = stringPreferencesKey("saved_devices_json")
    val SHORTCUTS_JSON = stringPreferencesKey("shortcuts_json")
  }

  private val safePreferencesFlow: Flow<Preferences> = flow<Preferences> {
    emitAll(dataStore.data)
  }.catch {
    emit(emptyPreferences())
  }

  val settingsFlow: Flow<UserSettings> = safePreferencesFlow.map { prefs ->
    runCatching {
      UserSettings(
        onboardingCompleted = runCatching { prefs[Keys.ONBOARDING_COMPLETED] }.getOrNull() ?: false,
        preferredTransport = runCatching { prefs[Keys.PREFERRED_TRANSPORT] }.getOrNull()
          ?.let { runCatching { TransportType.valueOf(it) }.getOrNull() }
          ?: TransportType.BLUETOOTH_HID,
        autoReconnect = runCatching { prefs[Keys.AUTO_RECONNECT] }.getOrNull() ?: true,
        pointerSensitivity = runCatching { prefs[Keys.POINTER_SENSITIVITY] }.getOrNull() ?: 1.4f,
        pointerAcceleration = runCatching { prefs[Keys.POINTER_ACCELERATION] }.getOrNull() ?: 0.35f,
        scrollSpeed = runCatching { prefs[Keys.SCROLL_SPEED] }.getOrNull() ?: 1.0f,
        naturalScrolling = runCatching { prefs[Keys.NATURAL_SCROLLING] }.getOrNull() ?: true,
        tapToClick = runCatching { prefs[Keys.TAP_TO_CLICK] }.getOrNull() ?: true,
        twoFingerRightClick = runCatching { prefs[Keys.TWO_FINGER_RIGHT_CLICK] }.getOrNull() ?: true,
        hapticFeedback = runCatching { prefs[Keys.HAPTIC_FEEDBACK] }.getOrNull() ?: true,
        leftHandedLayout = runCatching { prefs[Keys.LEFT_HANDED_LAYOUT] }.getOrNull() ?: false,
        threeFingerSwipeUp = runCatching { prefs[Keys.THREE_FINGER_UP] }.getOrNull()
          ?.let { runCatching { ThreeFingerShortcutAction.valueOf(it) }.getOrNull() }
          ?: ThreeFingerShortcutAction.TASK_SWITCHER,
        threeFingerSwipeDown = runCatching { prefs[Keys.THREE_FINGER_DOWN] }.getOrNull()
          ?.let { runCatching { ThreeFingerShortcutAction.valueOf(it) }.getOrNull() }
          ?: ThreeFingerShortcutAction.SHOW_DESKTOP,
        themeMode = runCatching { prefs[Keys.THEME_MODE] }.getOrNull()
          ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
          ?: ThemeMode.LIGHT,
        touchpadStyle = runCatching { prefs[Keys.TOUCHPAD_STYLE] }.getOrNull()
          ?.let { runCatching { TouchpadSurfaceStyle.valueOf(it) }.getOrNull() }
          ?: TouchpadSurfaceStyle.BORDERED,
        minimalAnimations = runCatching { prefs[Keys.MINIMAL_ANIMATIONS] }.getOrNull() ?: false,
        keepScreenAwake = runCatching { prefs[Keys.KEEP_SCREEN_AWAKE] }.getOrNull() ?: true,
        autoHideSystemNavBar = runCatching { prefs[Keys.AUTO_HIDE_SYSTEM_NAV_BAR] }.getOrNull() ?: false,
        orientationPreference = runCatching { prefs[Keys.ORIENTATION_PREF] }.getOrNull()
          ?.let { runCatching { OrientationPreference.valueOf(it) }.getOrNull() }
          ?: OrientationPreference.AUTO,
        clipboardSyncOptIn = runCatching { prefs[Keys.CLIPBOARD_SYNC] }.getOrNull() ?: true,
        presentationPreset = runCatching { prefs[Keys.PRESENTATION_PRESET] }.getOrNull()
          ?.let { runCatching { PresentationSoftwarePreset.valueOf(it) }.getOrNull() }
          ?: PresentationSoftwarePreset.POWERPOINT,
        activeDeviceId = runCatching { prefs[Keys.ACTIVE_DEVICE_ID] }.getOrNull()
      )
    }.getOrDefault(UserSettings())
  }.catch { emit(UserSettings()) }

  val savedDevicesFlow: Flow<List<RemoteDevice>> = safePreferencesFlow.map { prefs ->
    val raw = runCatching { prefs[Keys.SAVED_DEVICES_JSON] }.getOrNull() ?: return@map emptyList()
    decodeDevices(raw)
  }.catch { emit(emptyList()) }

  val shortcutsFlow: Flow<List<ShortcutItem>> = safePreferencesFlow.map { prefs ->
    val raw = runCatching { prefs[Keys.SHORTCUTS_JSON] }.getOrNull()
    if (raw.isNullOrBlank()) DefaultShortcuts.defaults() else decodeShortcuts(raw)
  }.catch { emit(DefaultShortcuts.defaults()) }

  suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
    runCatching {
      dataStore.edit { prefs ->
        val current = UserSettings(
          onboardingCompleted = runCatching { prefs[Keys.ONBOARDING_COMPLETED] }.getOrNull() ?: false,
          preferredTransport = runCatching { prefs[Keys.PREFERRED_TRANSPORT] }.getOrNull()
            ?.let { runCatching { TransportType.valueOf(it) }.getOrNull() }
            ?: TransportType.BLUETOOTH_HID,
          autoReconnect = runCatching { prefs[Keys.AUTO_RECONNECT] }.getOrNull() ?: true,
          pointerSensitivity = runCatching { prefs[Keys.POINTER_SENSITIVITY] }.getOrNull() ?: 1.4f,
          pointerAcceleration = runCatching { prefs[Keys.POINTER_ACCELERATION] }.getOrNull() ?: 0.35f,
          scrollSpeed = runCatching { prefs[Keys.SCROLL_SPEED] }.getOrNull() ?: 1.0f,
          naturalScrolling = runCatching { prefs[Keys.NATURAL_SCROLLING] }.getOrNull() ?: true,
          tapToClick = runCatching { prefs[Keys.TAP_TO_CLICK] }.getOrNull() ?: true,
          twoFingerRightClick = runCatching { prefs[Keys.TWO_FINGER_RIGHT_CLICK] }.getOrNull() ?: true,
          hapticFeedback = runCatching { prefs[Keys.HAPTIC_FEEDBACK] }.getOrNull() ?: true,
          leftHandedLayout = runCatching { prefs[Keys.LEFT_HANDED_LAYOUT] }.getOrNull() ?: false,
          threeFingerSwipeUp = runCatching { prefs[Keys.THREE_FINGER_UP] }.getOrNull()
            ?.let { runCatching { ThreeFingerShortcutAction.valueOf(it) }.getOrNull() }
            ?: ThreeFingerShortcutAction.TASK_SWITCHER,
          threeFingerSwipeDown = runCatching { prefs[Keys.THREE_FINGER_DOWN] }.getOrNull()
            ?.let { runCatching { ThreeFingerShortcutAction.valueOf(it) }.getOrNull() }
            ?: ThreeFingerShortcutAction.SHOW_DESKTOP,
          themeMode = runCatching { prefs[Keys.THEME_MODE] }.getOrNull()
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.LIGHT,
          touchpadStyle = runCatching { prefs[Keys.TOUCHPAD_STYLE] }.getOrNull()
            ?.let { runCatching { TouchpadSurfaceStyle.valueOf(it) }.getOrNull() }
            ?: TouchpadSurfaceStyle.BORDERED,
          minimalAnimations = runCatching { prefs[Keys.MINIMAL_ANIMATIONS] }.getOrNull() ?: false,
          keepScreenAwake = runCatching { prefs[Keys.KEEP_SCREEN_AWAKE] }.getOrNull() ?: true,
          autoHideSystemNavBar = runCatching { prefs[Keys.AUTO_HIDE_SYSTEM_NAV_BAR] }.getOrNull() ?: false,
          orientationPreference = runCatching { prefs[Keys.ORIENTATION_PREF] }.getOrNull()
            ?.let { runCatching { OrientationPreference.valueOf(it) }.getOrNull() }
            ?: OrientationPreference.AUTO,
          clipboardSyncOptIn = runCatching { prefs[Keys.CLIPBOARD_SYNC] }.getOrNull() ?: true,
          presentationPreset = runCatching { prefs[Keys.PRESENTATION_PRESET] }.getOrNull()
            ?.let { runCatching { PresentationSoftwarePreset.valueOf(it) }.getOrNull() }
            ?: PresentationSoftwarePreset.POWERPOINT,
          activeDeviceId = runCatching { prefs[Keys.ACTIVE_DEVICE_ID] }.getOrNull()
        )
        val next = transform(current)
        prefs[Keys.ONBOARDING_COMPLETED] = next.onboardingCompleted
        prefs[Keys.PREFERRED_TRANSPORT] = next.preferredTransport.name
        prefs[Keys.AUTO_RECONNECT] = next.autoReconnect
        prefs[Keys.POINTER_SENSITIVITY] = next.pointerSensitivity
        prefs[Keys.POINTER_ACCELERATION] = next.pointerAcceleration
        prefs[Keys.SCROLL_SPEED] = next.scrollSpeed
        prefs[Keys.NATURAL_SCROLLING] = next.naturalScrolling
        prefs[Keys.TAP_TO_CLICK] = next.tapToClick
        prefs[Keys.TWO_FINGER_RIGHT_CLICK] = next.twoFingerRightClick
        prefs[Keys.HAPTIC_FEEDBACK] = next.hapticFeedback
        prefs[Keys.LEFT_HANDED_LAYOUT] = next.leftHandedLayout
        prefs[Keys.THREE_FINGER_UP] = next.threeFingerSwipeUp.name
        prefs[Keys.THREE_FINGER_DOWN] = next.threeFingerSwipeDown.name
        prefs[Keys.THEME_MODE] = next.themeMode.name
        prefs[Keys.TOUCHPAD_STYLE] = next.touchpadStyle.name
        prefs[Keys.MINIMAL_ANIMATIONS] = next.minimalAnimations
        prefs[Keys.KEEP_SCREEN_AWAKE] = next.keepScreenAwake
        prefs[Keys.AUTO_HIDE_SYSTEM_NAV_BAR] = next.autoHideSystemNavBar
        prefs[Keys.ORIENTATION_PREF] = next.orientationPreference.name
        prefs[Keys.CLIPBOARD_SYNC] = next.clipboardSyncOptIn
        prefs[Keys.PRESENTATION_PRESET] = next.presentationPreset.name
        if (next.activeDeviceId != null) {
          prefs[Keys.ACTIVE_DEVICE_ID] = next.activeDeviceId
        } else {
          prefs.remove(Keys.ACTIVE_DEVICE_ID)
        }
      }
    }
  }

  suspend fun saveOrUpdateDevice(device: RemoteDevice) {
    runCatching {
      dataStore.edit { prefs ->
        val list = decodeDevices(runCatching { prefs[Keys.SAVED_DEVICES_JSON] }.getOrNull() ?: "[]").toMutableList()
        val idx = list.indexOfFirst { it.id == device.id }
        if (idx >= 0) {
          list[idx] = device
        } else {
          list.add(0, device)
        }
        prefs[Keys.SAVED_DEVICES_JSON] = encodeDevices(list)
      }
    }
  }

  suspend fun removeDevice(deviceId: String) {
    runCatching {
      dataStore.edit { prefs ->
        val list = decodeDevices(runCatching { prefs[Keys.SAVED_DEVICES_JSON] }.getOrNull() ?: "[]").filterNot { it.id == deviceId }
        prefs[Keys.SAVED_DEVICES_JSON] = encodeDevices(list)
        if (runCatching { prefs[Keys.ACTIVE_DEVICE_ID] }.getOrNull() == deviceId) {
          prefs.remove(Keys.ACTIVE_DEVICE_ID)
        }
      }
    }
  }

  suspend fun clearAllDevices() {
    runCatching {
      dataStore.edit { prefs ->
        prefs.remove(Keys.SAVED_DEVICES_JSON)
        prefs.remove(Keys.ACTIVE_DEVICE_ID)
      }
    }
  }

  suspend fun saveShortcuts(items: List<ShortcutItem>) {
    runCatching {
      dataStore.edit { prefs ->
        prefs[Keys.SHORTCUTS_JSON] = encodeShortcuts(items)
      }
    }
  }

  private fun encodeDevices(devices: List<RemoteDevice>): String {
    val arr = JSONArray()
    devices.forEach { d ->
      arr.put(JSONObject().apply {
        put("id", d.id)
        put("name", d.name)
        put("address", d.address)
        put("port", d.port)
        put("deviceType", d.deviceType.name)
        put("os", d.os.name)
        put("transportType", d.transportType.name)
        put("isPaired", d.isPaired)
        put("isTrusted", d.isTrusted)
        d.lastConnectedEpochMs?.let { put("lastConnectedEpochMs", it) }
      })
    }
    return arr.toString()
  }

  private fun decodeDevices(raw: String): List<RemoteDevice> = runCatching {
    val arr = JSONArray(raw)
    buildList {
      for (i in 0 until arr.length()) {
        runCatching {
          val o = arr.getJSONObject(i)
          val transport = TransportType.valueOf(o.getString("transportType"))
          add(
            RemoteDevice(
              id = o.getString("id"),
              name = o.getString("name"),
              address = o.getString("address"),
              port = o.optInt("port", 8765),
              deviceType = runCatching { DeviceType.valueOf(o.getString("deviceType")) }.getOrDefault(DeviceType.UNKNOWN),
              os = runCatching { OperatingSystem.valueOf(o.getString("os")) }.getOrDefault(OperatingSystem.UNKNOWN),
              transportType = transport,
              isPaired = o.optBoolean("isPaired", false),
              isTrusted = o.optBoolean("isTrusted", false),
              lastConnectedEpochMs = if (o.has("lastConnectedEpochMs")) o.getLong("lastConnectedEpochMs") else null,
              capabilities = when (transport) {
                TransportType.BLUETOOTH_HID -> DeviceCapabilities.BLUETOOTH_HID_DEFAULT
                TransportType.WIFI_DESKTOP, TransportType.USB -> DeviceCapabilities.WIFI_DESKTOP_DEFAULT
                TransportType.ANDROID_TV -> DeviceCapabilities.ANDROID_TV_DEFAULT
              }
            )
          )
        }
      }
    }.distinctBy { it.id }
  }.getOrDefault(emptyList())

  private fun encodeShortcuts(items: List<ShortcutItem>): String {
    val arr = JSONArray()
    items.forEach { item ->
      arr.put(JSONObject().apply {
        put("id", item.id)
        put("name", item.name)
        put("icon", item.icon.name)
        item.targetOs?.let { put("targetOs", it.name) }
        put("badgeLabel", item.badgeLabel)
        when (val a = item.action) {
          is ShortcutAction.KeyCombination -> {
            put("type", "KEY")
            put("keyCode", a.keyCode.name)
            put("modifiers", a.modifiers)
            a.macKeyCode?.let { put("macKeyCode", it.name) }
            a.macModifiers?.let { put("macModifiers", it) }
          }
          is ShortcutAction.Media -> {
            put("type", "MEDIA")
            put("mediaAction", a.action.name)
          }
          is ShortcutAction.Presentation -> {
            put("type", "PRESENTATION")
            put("presentationAction", a.action.name)
          }
          is ShortcutAction.Macro -> {
            put("type", "MACRO")
            val steps = JSONArray()
            a.steps.forEach { s ->
              steps.put(JSONObject().apply {
                put("keyCode", s.keyCode.name)
                put("modifiers", s.modifiers)
                put("delayAfterMs", s.delayAfterMs)
              })
            }
            put("steps", steps)
          }
        }
      })
    }
    return arr.toString()
  }

  private fun decodeShortcuts(raw: String): List<ShortcutItem> = runCatching {
    val arr = JSONArray(raw)
    val decoded = buildList {
      for (i in 0 until arr.length()) {
        runCatching {
          val o = arr.getJSONObject(i)
          val type = o.getString("type")
          val action: ShortcutAction = when (type) {
            "KEY" -> ShortcutAction.KeyCombination(
              keyCode = HidKeyCode.valueOf(o.getString("keyCode")),
              modifiers = o.getInt("modifiers"),
              macKeyCode = if (o.has("macKeyCode")) HidKeyCode.valueOf(o.getString("macKeyCode")) else null,
              macModifiers = if (o.has("macModifiers")) o.getInt("macModifiers") else null
            )
            "MEDIA" -> ShortcutAction.Media(MediaAction.valueOf(o.getString("mediaAction")))
            "PRESENTATION" -> ShortcutAction.Presentation(PresentationAction.valueOf(o.getString("presentationAction")))
            "MACRO" -> {
              val stepsArr = o.getJSONArray("steps")
              val steps = buildList {
                for (j in 0 until stepsArr.length()) {
                  val s = stepsArr.getJSONObject(j)
                  add(MacroStep(
                    keyCode = HidKeyCode.valueOf(s.getString("keyCode")),
                    modifiers = s.optInt("modifiers", 0),
                    delayAfterMs = s.optLong("delayAfterMs", 80L)
                  ))
                }
              }
              ShortcutAction.Macro(steps)
            }
            else -> return@runCatching
          }
          add(
            ShortcutItem(
              id = o.getString("id"),
              name = o.getString("name"),
              icon = ShortcutIconKey.valueOf(o.getString("icon")),
              targetOs = if (o.has("targetOs")) OperatingSystem.valueOf(o.getString("targetOs")) else null,
              action = action,
              badgeLabel = o.getString("badgeLabel")
            )
          )
        }
      }
    }.distinctBy { it.id }
    decoded.ifEmpty { DefaultShortcuts.defaults() }
  }.getOrElse { DefaultShortcuts.defaults() }
}
