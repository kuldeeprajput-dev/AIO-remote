package com.example.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.hapticClick
import com.example.MainActivity
import com.example.gestures.TouchpadGestureEngine
import com.example.models.ConnectionState
import com.example.preferences.OrientationPreference
import com.example.preferences.ThemeMode
import com.example.ui.components.rememberShouldReduceMotion
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.RemoteScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SlateSplashScreen
import com.example.ui.theme.SlateRemoteTheme
import com.example.ui.theme.SlateTheme
import com.example.viewmodel.SlateViewModel
import kotlinx.coroutines.delay

object NavRoutes {
  const val DEVICES = "devices"
  const val REMOTE = "remote"
  const val SETTINGS = "settings"
}

private data class BottomDestination(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String
)

private val mainDestinations = listOf(
  BottomDestination(
    route = NavRoutes.DEVICES,
    label = "Devices",
    selectedIcon = Icons.Outlined.DesktopWindows,
    unselectedIcon = Icons.Outlined.DesktopWindows,
    testTag = "nav_devices"
  ),
  BottomDestination(
    route = NavRoutes.REMOTE,
    label = "Remote",
    selectedIcon = Icons.Outlined.SettingsRemote,
    unselectedIcon = Icons.Outlined.SettingsRemote,
    testTag = "nav_remote"
  ),
  BottomDestination(
    route = NavRoutes.SETTINGS,
    label = "Settings",
    selectedIcon = Icons.Outlined.Settings,
    unselectedIcon = Icons.Outlined.Settings,
    testTag = "nav_settings"
  )
)

@Composable
fun SlateAppRoot(viewModel: SlateViewModel) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val savedDevices by viewModel.savedDevices.collectAsStateWithLifecycle()
  val activeDevice by viewModel.activeDevice.collectAsStateWithLifecycle()
  val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
  val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
  val mediaInfo by viewModel.mediaInfo.collectAsStateWithLifecycle()
  val lastError by viewModel.lastError.collectAsStateWithLifecycle()
  val latencyMs by viewModel.latencyMs.collectAsStateWithLifecycle()
  val bluetoothCapability by viewModel.bluetoothCapability.collectAsStateWithLifecycle()
  val discoveredBluetooth by viewModel.discoveredBluetooth.collectAsStateWithLifecycle()
  val isScanningBluetooth by viewModel.isScanningBluetooth.collectAsStateWithLifecycle()
  val discoveredWifi by viewModel.discoveredWifi.collectAsStateWithLifecycle()
  val isScanningWifi by viewModel.isScanningWifi.collectAsStateWithLifecycle()
  val wifiScanError by viewModel.wifiScanError.collectAsStateWithLifecycle()
  val localWifiIp by viewModel.localWifiIp.collectAsStateWithLifecycle()
  val discoveredUsb by viewModel.discoveredUsb.collectAsStateWithLifecycle()
  val isScanningUsb by viewModel.isScanningUsb.collectAsStateWithLifecycle()
  val usbStatus by viewModel.usbStatus.collectAsStateWithLifecycle()
  val remoteMode by viewModel.remoteMode.collectAsStateWithLifecycle()
  val isScrollMode by viewModel.isScrollMode.collectAsStateWithLifecycle()
  val isFullscreenTouchpad by viewModel.isFullscreenTouchpad.collectAsStateWithLifecycle()
  val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()

  var showSplash by rememberSaveable { mutableStateOf(true) }
  LaunchedEffect(Unit) {
    delay(650L)
    showSplash = false
  }

  val darkTheme = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }

  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  // Release any held mouse button/key if app goes to background or screen locks
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
        viewModel.onAppPausedOrLocked()
      } else if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshBluetoothState()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Keep screen awake when connected and enabled in settings
  LaunchedEffect(connectionState, settings.keepScreenAwake) {
    val activity = context.findActivity() ?: return@LaunchedEffect
    runCatching {
      if (connectionState == ConnectionState.CONNECTED && settings.keepScreenAwake) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      } else {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }
  }

  // Respect orientation preference
  LaunchedEffect(settings.orientationPreference) {
    val activity = context.findActivity() ?: return@LaunchedEffect
    runCatching {
      val target = when (settings.orientationPreference) {
        OrientationPreference.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        OrientationPreference.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        OrientationPreference.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
      }
      if (activity.requestedOrientation != target) {
        activity.requestedOrientation = target
      }
    }
  }

  // Sync status bar & navigation bar contrast + fullscreen immersion with theme and screen state
  val windowInfo = LocalWindowInfo.current
  LaunchedEffect(
    darkTheme,
    settings.autoHideSystemNavBar,
    isFullscreenTouchpad,
    remoteMode,
    showSplash,
    settings.onboardingCompleted,
    windowInfo.isWindowFocused
  ) {
    val activity = context.findActivity() ?: return@LaunchedEffect
    (activity as? MainActivity)?.let {
      it.isDarkThemeActive = darkTheme
      it.shouldAutoHideSystemNavBar = settings.autoHideSystemNavBar
      it.isFullscreenTouchpadActive = isFullscreenTouchpad
    }
    MainActivity.syncSystemBars(
      activity = activity,
      darkTheme = darkTheme,
      autoHideNavBar = settings.autoHideSystemNavBar,
      hideStatusBar = isFullscreenTouchpad
    )
  }

  SlateRemoteTheme(darkTheme = darkTheme, hapticFeedbackEnabled = settings.hapticFeedback) {
    if (showSplash) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = hapticClick { showSplash = false }
          )
      ) {
        SlateSplashScreen(onDismiss = { showSplash = false })
      }
    } else if (!settings.onboardingCompleted) {
      OnboardingScreen(
        onFinishOnboarding = { viewModel.completeOnboarding() }
      )
    } else {
      val navController = rememberNavController()
      val navBackStackEntry by navController.currentBackStackEntryAsState()
      val currentDestination = navBackStackEntry?.destination
      val snackbarHostState = remember { SnackbarHostState() }
      val reduceMotion = rememberShouldReduceMotion(settings.minimalAnimations)
      val gestureEngine = remember { TouchpadGestureEngine(settings.toGestureConfig()) }
      LaunchedEffect(settings) {
        gestureEngine.config = settings.toGestureConfig()
      }

      LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
          snackbarHostState.showSnackbar(msg)
        }
      }

      val navigateToTab: (String) -> Unit = { route ->
        runCatching {
          val startId = runCatching { navController.graph.findStartDestination().id }.getOrNull()
          navController.navigate(route) {
            if (startId != null) {
              popUpTo(startId) {
                saveState = true
              }
            }
            launchSingleTop = true
            restoreState = true
          }
        }
      }

      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        LaunchedEffect(currentDestination?.route, darkTheme) {
          val activity = context.findActivity() ?: return@LaunchedEffect
          MainActivity.syncSystemBars(
            activity = activity,
            darkTheme = darkTheme,
            autoHideNavBar = settings.autoHideSystemNavBar,
            hideStatusBar = isFullscreenTouchpad
          )
        }

        Scaffold(
          containerColor = MaterialTheme.colorScheme.background,
          snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
          contentWindowInsets = WindowInsets.statusBars,
          bottomBar = {
            if (!useRail && !isFullscreenTouchpad) {
              Column {
                HorizontalDivider(color = SlateTheme.colors.border)
                NavigationBar(
                  containerColor = MaterialTheme.colorScheme.background,
                  tonalElevation = 0.dp,
                  windowInsets = WindowInsets.navigationBars
                ) {
                  mainDestinations.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                      selected = selected,
                      onClick = hapticClick { navigateToTab(dest.route) },
                      icon = {
                        Icon(
                          imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                          contentDescription = dest.label
                        )
                      },
                      label = {
                        Text(
                          text = dest.label,
                          style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                          )
                        )
                      },
                      colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = SlateTheme.colors.textSecondary,
                        unselectedTextColor = SlateTheme.colors.textSecondary
                      ),
                      modifier = Modifier.testTag(dest.testTag)
                    )
                  }
                }
              }
            }
          }
        ) { innerPadding ->
          Row(
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding)
          ) {
            if (useRail && !isFullscreenTouchpad) {
              NavigationRail(
                containerColor = MaterialTheme.colorScheme.background,
                windowInsets = WindowInsets.navigationBars
              ) {
                Spacer(modifier = Modifier.weight(1f))
                mainDestinations.forEach { dest ->
                  val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                  NavigationRailItem(
                    selected = selected,
                    onClick = hapticClick { navigateToTab(dest.route) },
                    icon = {
                      Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = dest.label
                      )
                    },
                    label = {
                      Text(
                        text = dest.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                      )
                    },
                    colors = NavigationRailItemDefaults.colors(
                      selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                      selectedTextColor = MaterialTheme.colorScheme.onBackground,
                      indicatorColor = MaterialTheme.colorScheme.primary,
                      unselectedIconColor = SlateTheme.colors.textSecondary,
                      unselectedTextColor = SlateTheme.colors.textSecondary
                    ),
                    modifier = Modifier.testTag(dest.testTag)
                  )
                }
                Spacer(modifier = Modifier.weight(1f))
              }
              VerticalDivider(color = SlateTheme.colors.border)
            }

            NavHost(
              navController = navController,
              startDestination = NavRoutes.DEVICES,
              modifier = Modifier.weight(1f)
            ) {
              composable(NavRoutes.DEVICES) {
                DevicesScreen(
                  savedDevices = savedDevices,
                  activeDevice = activeDevice,
                  connectionState = connectionState,
                  latencyMs = latencyMs,
                  lastError = lastError,
                  bluetoothCapability = bluetoothCapability,
                  discoveredBluetooth = discoveredBluetooth,
                  isScanningBluetooth = isScanningBluetooth,
                  discoveredWifi = discoveredWifi,
                  isScanningWifi = isScanningWifi,
                  wifiScanError = wifiScanError,
                  localWifiIp = localWifiIp,
                  reduceMotion = reduceMotion,
                  onConnectSaved = viewModel::connectToSavedDevice,
                  onDisconnect = viewModel::disconnectDevice,
                  onRemoveDevice = viewModel::removeDevice,
                  onOpenRemote = { navigateToTab(NavRoutes.REMOTE) },
                  onStartDiscovery = viewModel::startDiscovery,
                  onStopDiscovery = viewModel::stopDiscovery,
                  onRefreshBluetooth = viewModel::refreshBluetoothState,
                  onConnectDiscovered = viewModel::connectDiscoveredDevice,
                  onConnectManualWifi = viewModel::connectManualWifi,
                  discoveredUsb = discoveredUsb,
                  isScanningUsb = isScanningUsb,
                  usbStatus = usbStatus,
                  onStartBluetoothDiscovery = viewModel::startBluetoothDiscovery,
                  onStartWifiDiscovery = viewModel::startWifiDiscovery,
                  onStartUsbDiscovery = viewModel::startUsbDiscovery,
                  onConnectUsbTunnel = { host, port, name, onDone ->
                    viewModel.connectUsbEndpoint(
                      host = host,
                      port = port,
                      deviceName = name,
                      onComplete = onDone
                    )
                  }
                )
              }

              composable(NavRoutes.REMOTE) {
                RemoteScreen(
                  activeDevice = activeDevice,
                  savedDevices = savedDevices,
                  connectionState = connectionState,
                  capabilities = capabilities,
                  mediaInfo = mediaInfo,
                  latencyMs = latencyMs,
                  remoteMode = remoteMode,
                  isScrollMode = isScrollMode,
                  isFullscreenTouchpad = isFullscreenTouchpad,
                  settings = settings,
                  shortcuts = shortcuts,
                  reduceMotion = reduceMotion,
                  gestureEngine = gestureEngine,
                  onSelectMode = viewModel::setRemoteMode,
                  onToggleScrollMode = viewModel::toggleScrollMode,
                  onSetFullscreenTouchpad = viewModel::setFullscreenTouchpad,
                  onSelectDevice = viewModel::selectDevice,
                  onConnectDevice = viewModel::connectToSavedDevice,
                  onNavigateToDevices = { navigateToTab(NavRoutes.DEVICES) },
                  onSendCommands = viewModel::sendCommands,
                  onMouseButton = viewModel::sendMouseButton,
                  onMouseClick = viewModel::clickMouse,
                  onKeyTap = viewModel::sendKeyTap,
                  onSendText = viewModel::sendText,
                  onSendClipboard = viewModel::sendClipboardIfOptedIn,
                  onSendMedia = viewModel::sendMedia,
                  onSendPresentation = viewModel::sendPresentation,
                  onSendTv = viewModel::sendTvCommand,
                  onExecuteShortcut = viewModel::executeShortcut,
                  onMoveShortcut = viewModel::moveShortcut,
                  onAddShortcut = viewModel::addShortcut,
                  onRemoveShortcut = viewModel::removeShortcut,
                  onChangePresentationPreset = { preset ->
                    viewModel.updateSettings { it.copy(presentationPreset = preset) }
                  }
                )
              }

              composable(NavRoutes.SETTINGS) {
                SettingsScreen(
                  settings = settings,
                  savedDevices = savedDevices,
                  bluetoothCapability = bluetoothCapability,
                  activeDevice = activeDevice,
                  connectionState = connectionState,
                  localWifiIp = localWifiIp,
                  onNavigateToDevices = { navigateToTab(NavRoutes.DEVICES) },
                  onDisconnect = viewModel::disconnectDevice,
                  onUpdateSettings = viewModel::updateSettings,
                  onClearPairings = viewModel::clearAllPairings
                )
              }
            }
          }
        }
      }
    }
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
