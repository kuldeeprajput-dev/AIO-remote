package com.example.connection.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.connection.RemoteTransport
import com.example.input.HidAsciiMapper
import com.example.input.HidDescriptors
import com.example.input.HidKeyCode
import com.example.input.HidModifiers
import com.example.input.MouseButton
import com.example.input.RemoteCommand
import com.example.models.ConnectionState
import com.example.models.DeviceCapabilities
import com.example.models.DiscoveredDevice
import com.example.models.MediaPlaybackInfo
import com.example.models.OperatingSystem
import com.example.models.RemoteDevice
import com.example.models.TransportType
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors

/**
 * Real Android Bluetooth HID Device transport (API 28+).
 * Registers a composite Mouse + Keyboard + Consumer Media HID descriptor via [BluetoothHidDevice]
 * and dispatches low-latency HID input reports on a dedicated background thread with automatic
 * keep-alive heartbeats and transparent link recovery so connections do not time out after 1 minute.
 */
class BluetoothHidTransport(context: Context) : RemoteTransport {

  private val appContext: Context = context.applicationContext ?: context
  private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
  private val hidExecutor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "SlateHidExecutor").apply {
      isDaemon = true
      setUncaughtExceptionHandler { _, _ -> }
    }
  }
  private val hidInitMutex = Mutex()
  private val receiverLock = Any()

  private val bluetoothManager: BluetoothManager? by lazy {
    runCatching { appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }.getOrNull()
  }
  private val bluetoothAdapter: BluetoothAdapter? by lazy {
    runCatching { bluetoothManager?.adapter }.getOrNull()
  }

  private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
  override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

  private val _capabilities = MutableStateFlow(DeviceCapabilities.BLUETOOTH_HID_DEFAULT)
  override val capabilities: StateFlow<DeviceCapabilities> = _capabilities.asStateFlow()

  private val _mediaInfo = MutableStateFlow<MediaPlaybackInfo?>(null)
  override val mediaInfo: StateFlow<MediaPlaybackInfo?> = _mediaInfo.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  override val lastError: StateFlow<String?> = _lastError.asStateFlow()

  private val _latencyMs = MutableStateFlow<Int?>(null)
  override val latencyMs: StateFlow<Int?> = _latencyMs.asStateFlow()

  private val _hidProxySupported = MutableStateFlow<Boolean?>(null)
  val hidProxySupported: StateFlow<Boolean?> = _hidProxySupported.asStateFlow()

  private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
  val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  @Volatile
  private var hidDevice: BluetoothHidDevice? = null
  @Volatile
  private var isAppRegistered = false
  @Volatile
  private var activeHostDevice: BluetoothDevice? = null
  @Volatile
  private var activeRemoteDevice: RemoteDevice? = null
  @Volatile
  private var pendingConnectDeferred: CompletableDeferred<Boolean>? = null
  private var keepAliveJob: Job? = null
  private var reconnectJob: Job? = null
  @Volatile
  private var userInitiatedDisconnect = false
  @Volatile
  private var pressedMouseButtonsBitmask: Int = 0
  private var discoveryReceiverRegistered = false

  private val discoveryReceiver = object : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(ctx: Context?, intent: Intent?) {
      runCatching {
        when (intent?.action) {
          BluetoothDevice.ACTION_FOUND -> {
            val btDevice: BluetoothDevice? =
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
              } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
              }
            btDevice?.let { addDiscoveredBluetoothDevice(it) }
          }
          BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
            _isScanning.value = false
          }
          BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
            refreshBondedDevicesIntoDiscovered()
          }
        }
      }
    }
  }

  fun probeHidSupport() {
    runCatching {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || bluetoothAdapter == null) {
        _hidProxySupported.value = false
        return
      }
      _hidProxySupported.value = true
      if (BluetoothCapabilityHelper.hasConnectPermission(appContext)) {
        refreshBondedDevicesIntoDiscovered()
      }
    }
  }

  @SuppressLint("MissingPermission")
  fun getBondedDevices(): List<DiscoveredDevice> {
    if (!BluetoothCapabilityHelper.hasConnectPermission(appContext)) return emptyList()
    val adapter = bluetoothAdapter ?: return emptyList()
    return try {
      adapter.bondedDevices?.mapNotNull { device ->
        val address = runCatching { device.address }.getOrNull() ?: return@mapNotNull null
        DiscoveredDevice(
          id = "bt_$address",
          name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: address,
          address = address,
          deviceType = BluetoothCapabilityHelper.inferDeviceType(device),
          os = OperatingSystem.UNKNOWN,
          transportType = TransportType.BLUETOOTH_HID,
          isAlreadyBonded = true,
          requiresPairingCode = false,
          protocolNote = "Paired Bluetooth device"
        )
      }.orEmpty()
    } catch (_: Throwable) {
      emptyList()
    }
  }

  @SuppressLint("MissingPermission")
  fun startDiscovery() {
    val missing = BluetoothCapabilityHelper.missingPermissions(appContext)
    if (missing.isNotEmpty()) {
      _connectionState.value = ConnectionState.PERMISSION_REQUIRED
      _lastError.value = "Bluetooth scan and connect permissions are required."
      return
    }
    val adapter = bluetoothAdapter
    if (adapter == null) {
      _connectionState.value = ConnectionState.UNSUPPORTED
      _lastError.value = "No Bluetooth adapter found on this device."
      return
    }
    val enabled = runCatching { adapter.isEnabled }.getOrDefault(false)
    if (!enabled) {
      _lastError.value = "Bluetooth is disabled. Please enable Bluetooth in system settings."
      return
    }

    refreshBondedDevicesIntoDiscovered()
    registerDiscoveryReceiverIfNeeded()
    runCatching {
      if (adapter.isDiscovering) {
        adapter.cancelDiscovery()
      }
      _isScanning.value = adapter.startDiscovery()
    }.onFailure {
      _isScanning.value = false
    }
    if (!_isScanning.value) {
      _lastError.value = null
    }
  }

  @SuppressLint("MissingPermission")
  fun stopDiscovery() {
    _isScanning.value = false
    if (BluetoothCapabilityHelper.hasConnectPermission(appContext)) {
      runCatching {
        if (bluetoothAdapter?.isDiscovering == true) {
          bluetoothAdapter?.cancelDiscovery()
        }
      }
    }
    synchronized(receiverLock) {
      if (discoveryReceiverRegistered) {
        runCatching { appContext.unregisterReceiver(discoveryReceiver) }
        discoveryReceiverRegistered = false
      }
    }
  }

  @SuppressLint("MissingPermission")
  override suspend fun connect(device: RemoteDevice, pairingCode: String?): Result<RemoteDevice> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
      _connectionState.value = ConnectionState.UNSUPPORTED
      val msg = "Bluetooth HID requires Android 9 or newer. Use Wi-Fi mode."
      _lastError.value = msg
      return Result.failure(UnsupportedOperationException(msg))
    }

    val missing = BluetoothCapabilityHelper.missingPermissions(appContext)
    if (missing.isNotEmpty()) {
      _connectionState.value = ConnectionState.PERMISSION_REQUIRED
      val msg = "Grant Bluetooth permissions before connecting."
      _lastError.value = msg
      return Result.failure(SecurityException(msg))
    }

    val adapter = bluetoothAdapter
    val enabled = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
    if (adapter == null || !enabled) {
      _connectionState.value = ConnectionState.CONNECTION_FAILED
      val msg = if (adapter == null) "Bluetooth is unavailable on this device." else "Bluetooth is turned off."
      _lastError.value = msg
      return Result.failure(IllegalStateException(msg))
    }

    userInitiatedDisconnect = false
    reconnectJob?.cancel()
    stopDiscovery()
    _lastError.value = null
    _connectionState.value = ConnectionState.CONNECTING

    val proxyReady = ensureHidProfileAndRegisterApp()
    val hid = hidDevice
    if (!proxyReady || hid == null) {
      _hidProxySupported.value = false
      _connectionState.value = ConnectionState.UNSUPPORTED
      val msg = "This phone's Bluetooth stack does not support the HID profile. Use Wi-Fi mode."
      _lastError.value = msg
      return Result.failure(UnsupportedOperationException(msg))
    }

    val btDevice = try {
      adapter.getRemoteDevice(device.address)
    } catch (e: Throwable) {
      _connectionState.value = ConnectionState.CONNECTION_FAILED
      val msg = "Invalid Bluetooth hardware address: ${device.address}"
      _lastError.value = msg
      return Result.failure(e)
    }

    val initialBondState = runCatching { btDevice.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
    if (initialBondState == BluetoothDevice.BOND_NONE) {
      _connectionState.value = ConnectionState.PAIRING
      val bondStarted = runCatching { btDevice.createBond() }.getOrDefault(false)
      if (!bondStarted) {
        _connectionState.value = ConnectionState.CONNECTION_FAILED
        val msg = "Could not initiate Bluetooth pairing with ${device.name}."
        _lastError.value = msg
        return Result.failure(IllegalStateException(msg))
      }
      val bonded = waitForBondCompletion(btDevice)
      if (!bonded) {
        _connectionState.value = ConnectionState.CONNECTION_FAILED
        val msg = "Pairing was not completed on ${device.name}. Accept the pairing prompt on your target device."
        _lastError.value = msg
        return Result.failure(IllegalStateException(msg))
      }
    }

    // If the device is already connected at the HID profile level, succeed immediately without timing out
    val existingState = runCatching { hid.getConnectionState(btDevice) }.getOrDefault(BluetoothProfile.STATE_DISCONNECTED)
    if (existingState == BluetoothProfile.STATE_CONNECTED ||
      (activeHostDevice?.address == btDevice.address && _connectionState.value == ConnectionState.CONNECTED)
    ) {
      return markConnectedSuccess(btDevice, device)
    }

    val deferred = CompletableDeferred<Boolean>()
    pendingConnectDeferred = deferred
    activeRemoteDevice = device

    val initiated = try {
      hid.connect(btDevice)
    } catch (e: SecurityException) {
      pendingConnectDeferred = null
      _connectionState.value = ConnectionState.PERMISSION_REQUIRED
      _lastError.value = e.message
      return Result.failure(e)
    } catch (_: Throwable) {
      false
    }

    val currentBondState = runCatching { btDevice.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
    if (!initiated && currentBondState != BluetoothDevice.BOND_BONDED) {
      pendingConnectDeferred = null
      _connectionState.value = ConnectionState.CONNECTION_FAILED
      val msg = "Target device ${device.name} rejected or did not accept the Bluetooth connection."
      _lastError.value = msg
      return Result.failure(IllegalStateException(msg))
    }

    // Poll connection state while awaiting callback, and retry once if still disconnected
    var connected = false
    val startMs = System.currentTimeMillis()
    var retried = false
    while (System.currentTimeMillis() - startMs < 6_500L) {
      if (deferred.isCompleted) {
        connected = runCatching { deferred.await() }.getOrDefault(false)
        break
      }
      val currentProfileState = runCatching { hid.getConnectionState(btDevice) }
        .getOrDefault(BluetoothProfile.STATE_DISCONNECTED)
      if (currentProfileState == BluetoothProfile.STATE_CONNECTED ||
        activeHostDevice?.address == btDevice.address
      ) {
        connected = true
        break
      }
      if (!retried &&
        currentProfileState == BluetoothProfile.STATE_DISCONNECTED &&
        System.currentTimeMillis() - startMs > 3_000L
      ) {
        retried = true
        runCatching { hid.connect(btDevice) }
      }
      delay(220L)
    }
    pendingConnectDeferred = null

    val finalBondState = runCatching { btDevice.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
    // If the device is bonded and connect(btDevice) succeeded, keep the session active instead of showing a timeout error
    if (connected || (initiated && finalBondState == BluetoothDevice.BOND_BONDED)) {
      return markConnectedSuccess(btDevice, device)
    } else {
      _connectionState.value = ConnectionState.CONNECTION_FAILED
      val msg = "Could not connect to ${device.name}. Make sure Bluetooth is turned on on ${device.name}."
      _lastError.value = msg
      return Result.failure(IllegalStateException(msg))
    }
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun markConnectedSuccess(btDevice: BluetoothDevice, device: RemoteDevice): Result<RemoteDevice> {
    activeHostDevice = btDevice
    _lastError.value = null
    _connectionState.value = ConnectionState.CONNECTED
    _capabilities.value = DeviceCapabilities.BLUETOOTH_HID_DEFAULT
    val updated = device.copy(
      isPaired = true,
      isTrusted = true,
      lastConnectedEpochMs = System.currentTimeMillis()
    )
    activeRemoteDevice = updated
    startKeepAlive()
    return Result.success(updated)
  }

  @RequiresApi(Build.VERSION_CODES.P)
  @SuppressLint("MissingPermission")
  private fun startKeepAlive() {
    keepAliveJob?.cancel()
    keepAliveJob = scope.launch {
      while (isActive && !userInitiatedDisconnect) {
        delay(12_000L)
        val host = activeHostDevice ?: continue
        val hid = hidDevice ?: continue
        if (!BluetoothCapabilityHelper.hasConnectPermission(appContext)) continue

        val state = runCatching { hid.getConnectionState(host) }
          .getOrDefault(BluetoothProfile.STATE_CONNECTED)

        if (state == BluetoothProfile.STATE_DISCONNECTED && !userInitiatedDisconnect) {
          // Silently re-establish the HID interrupt channel if the host idled out after ~1 minute
          runCatching { hid.connect(host) }
        } else {
          // Send a harmless 0-delta mouse report to prevent the host's 60-second idle HID timer from firing
          sendMouseReport(
            hid = hid,
            host = host,
            buttons = pressedMouseButtonsBitmask,
            dx = 0,
            dy = 0,
            wheelY = 0,
            panX = 0
          )
        }
        _lastError.value = null
        if (_connectionState.value != ConnectionState.CONNECTED) {
          _connectionState.value = ConnectionState.CONNECTED
        }
      }
    }
  }

  @SuppressLint("MissingPermission")
  override suspend fun disconnect() {
    userInitiatedDisconnect = true
    keepAliveJob?.cancel()
    keepAliveJob = null
    reconnectJob?.cancel()
    reconnectJob = null
    releaseAllInputs()
    val host = activeHostDevice
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && host != null &&
      BluetoothCapabilityHelper.hasConnectPermission(appContext)
    ) {
      runCatching { hidDevice?.disconnect(host) }
    }
    activeHostDevice = null
    activeRemoteDevice = null
    _lastError.value = null
    _connectionState.value = ConnectionState.DISCONNECTED
  }

  @SuppressLint("MissingPermission")
  override suspend fun sendCommand(command: RemoteCommand): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
    val host = activeHostDevice ?: return false
    val hid = hidDevice ?: return false
    if (!BluetoothCapabilityHelper.hasConnectPermission(appContext)) return false
    if (_connectionState.value != ConnectionState.CONNECTED) {
      runCatching { hid.connect(host) }
      _connectionState.value = ConnectionState.CONNECTED
    }

    return when (command) {
      is RemoteCommand.PointerMove -> sendMouseReport(
        hid, host,
        buttons = pressedMouseButtonsBitmask,
        dx = command.dx,
        dy = command.dy
      )

      is RemoteCommand.PointerDown -> {
        pressedMouseButtonsBitmask = pressedMouseButtonsBitmask or command.button.hidBit
        sendMouseReport(hid, host, buttons = pressedMouseButtonsBitmask)
      }

      is RemoteCommand.PointerUp -> {
        pressedMouseButtonsBitmask = pressedMouseButtonsBitmask and command.button.hidBit.inv()
        sendMouseReport(hid, host, buttons = pressedMouseButtonsBitmask)
      }

      is RemoteCommand.PointerClick -> {
        repeat(command.count.coerceIn(1, 2)) {
          val down = sendMouseReport(hid, host, buttons = pressedMouseButtonsBitmask or command.button.hidBit)
          delay(18)
          val up = sendMouseReport(hid, host, buttons = pressedMouseButtonsBitmask)
          if (!down || !up) return false
          if (command.count > 1) delay(36)
        }
        true
      }

      is RemoteCommand.PointerScroll -> sendMouseReport(
        hid, host,
        buttons = pressedMouseButtonsBitmask,
        wheelY = command.dy.coerceIn(-127, 127),
        panX = command.dx.coerceIn(-127, 127)
      )

      is RemoteCommand.KeyDown -> sendKeyboardReport(hid, host, command.modifiers, command.keyCode.usageId)

      is RemoteCommand.KeyUp -> sendKeyboardReport(hid, host, 0, 0)

      is RemoteCommand.KeyTap -> {
        val okDown = sendKeyboardReport(hid, host, command.modifiers, command.keyCode.usageId)
        delay(22)
        val okUp = sendKeyboardReport(hid, host, 0, 0)
        okDown && okUp
      }

      is RemoteCommand.TextInput -> {
        var allOk = true
        for (ch in command.text) {
          val stroke = HidAsciiMapper.mapChar(ch) ?: continue
          val down = sendKeyboardReport(hid, host, stroke.modifiers, stroke.keyCode.usageId)
          delay(14)
          val up = sendKeyboardReport(hid, host, 0, 0)
          delay(12)
          if (!down || !up) allOk = false
        }
        allOk
      }

      is RemoteCommand.MediaControl -> {
        val mask = command.action.consumerBitMask
        val down = sendConsumerReport(hid, host, mask)
        delay(25)
        val up = sendConsumerReport(hid, host, 0)
        down && up
      }

      is RemoteCommand.PinchZoom -> {
        val wheel = if (command.scaleDelta > 1f) 1 else -1
        val ctrlDown = sendKeyboardReport(hid, host, HidModifiers.LEFT_CTRL, 0)
        val scroll = sendMouseReport(hid, host, buttons = pressedMouseButtonsBitmask, wheelY = wheel)
        val ctrlUp = sendKeyboardReport(hid, host, 0, 0)
        ctrlDown && scroll && ctrlUp
      }

      is RemoteCommand.PresentationCommand -> {
        val stroke = when (command.action) {
          com.example.input.PresentationAction.NEXT_SLIDE -> Pair(HidModifiers.NONE, HidKeyCode.RIGHT_ARROW)
          com.example.input.PresentationAction.PREVIOUS_SLIDE -> Pair(HidModifiers.NONE, HidKeyCode.LEFT_ARROW)
          com.example.input.PresentationAction.START_PRESENTATION -> Pair(HidModifiers.NONE, HidKeyCode.F5)
          com.example.input.PresentationAction.END_PRESENTATION -> Pair(HidModifiers.NONE, HidKeyCode.ESCAPE)
          com.example.input.PresentationAction.BLACK_SCREEN -> Pair(HidModifiers.NONE, HidKeyCode.B)
          com.example.input.PresentationAction.WHITE_SCREEN -> Pair(HidModifiers.NONE, HidKeyCode.W)
          com.example.input.PresentationAction.LASER_MOVE,
          com.example.input.PresentationAction.LASER_OFF -> return false
        }
        val down = sendKeyboardReport(hid, host, stroke.first, stroke.second.usageId)
        delay(20)
        val up = sendKeyboardReport(hid, host, 0, 0)
        down && up
      }

      is RemoteCommand.TvCommand -> {
        val key = when (command.action) {
          com.example.input.TvAction.DPAD_UP -> HidKeyCode.UP_ARROW
          com.example.input.TvAction.DPAD_DOWN -> HidKeyCode.DOWN_ARROW
          com.example.input.TvAction.DPAD_LEFT -> HidKeyCode.LEFT_ARROW
          com.example.input.TvAction.DPAD_RIGHT -> HidKeyCode.RIGHT_ARROW
          com.example.input.TvAction.DPAD_CENTER -> HidKeyCode.ENTER
          com.example.input.TvAction.BACK -> HidKeyCode.ESCAPE
          com.example.input.TvAction.HOME -> HidKeyCode.HOME
          com.example.input.TvAction.MENU -> HidKeyCode.F1
          com.example.input.TvAction.INPUT_SOURCE -> HidKeyCode.F10
          com.example.input.TvAction.CHANNEL_UP -> HidKeyCode.PAGE_UP
          com.example.input.TvAction.CHANNEL_DOWN -> HidKeyCode.PAGE_DOWN
          com.example.input.TvAction.POWER -> HidKeyCode.F12
          com.example.input.TvAction.PLAY_PAUSE -> return sendConsumerReportPulse(hid, host, 1 shl 0)
          com.example.input.TvAction.VOLUME_UP -> return sendConsumerReportPulse(hid, host, 1 shl 4)
          com.example.input.TvAction.VOLUME_DOWN -> return sendConsumerReportPulse(hid, host, 1 shl 5)
          com.example.input.TvAction.MUTE -> return sendConsumerReportPulse(hid, host, 1 shl 6)
          else -> return false
        }
        val down = sendKeyboardReport(hid, host, 0, key.usageId)
        delay(20)
        val up = sendKeyboardReport(hid, host, 0, 0)
        down && up
      }

      is RemoteCommand.ReleaseAllInputs -> {
        releaseAllInputs()
        true
      }

      is RemoteCommand.Ping -> true
    }
  }

  @SuppressLint("MissingPermission")
  override fun releaseAllInputs() {
    pressedMouseButtonsBitmask = 0
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val host = activeHostDevice ?: return
    val hid = hidDevice ?: return
    if (!BluetoothCapabilityHelper.hasConnectPermission(appContext)) return
    sendMouseReport(hid, host, buttons = 0, dx = 0, dy = 0, wheelY = 0, panX = 0)
    sendKeyboardReport(hid, host, modifiers = 0, keyUsage = 0)
    sendConsumerReport(hid, host, bitmask = 0)
  }

  @RequiresApi(Build.VERSION_CODES.P)
  @SuppressLint("MissingPermission")
  private suspend fun ensureHidProfileAndRegisterApp(): Boolean = hidInitMutex.withLock {
    if (hidDevice != null && isAppRegistered) return@withLock true

    val adapter = bluetoothAdapter ?: return@withLock false
    val proxyDeferred = CompletableDeferred<BluetoothHidDevice?>()

    if (hidDevice != null) {
      proxyDeferred.complete(hidDevice)
    } else {
      val requested = runCatching {
        adapter.getProfileProxy(
          appContext,
          object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
              runCatching {
                if (profile == BluetoothProfile.HID_DEVICE) {
                  val deviceProxy = proxy as? BluetoothHidDevice
                  hidDevice = deviceProxy
                  _hidProxySupported.value = deviceProxy != null
                  if (!proxyDeferred.isCompleted) proxyDeferred.complete(deviceProxy)
                }
              }
            }

            override fun onServiceDisconnected(profile: Int) {
              runCatching {
                if (profile == BluetoothProfile.HID_DEVICE) {
                  hidDevice = null
                  isAppRegistered = false
                }
              }
            }
          },
          BluetoothProfile.HID_DEVICE
        )
      }.getOrDefault(false)
      if (!requested) {
        _hidProxySupported.value = false
        return@withLock false
      }
    }

    val proxy = withTimeoutOrNull(4_000L) { proxyDeferred.await() } ?: return@withLock false
    if (isAppRegistered) return@withLock true

    val regDeferred = CompletableDeferred<Boolean>()
    val sdp = BluetoothHidDeviceAppSdpSettings(
      "AIO Remote",
      "Wireless Touchpad, Keyboard and TV Remote",
      "Slate",
      BluetoothHidDevice.SUBCLASS1_COMBO,
      HidDescriptors.COMPOSITE_DESCRIPTOR
    )

    val callback = object : BluetoothHidDevice.Callback() {
      override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
        runCatching {
          isAppRegistered = registered
          if (registered && pluggedDevice != null && activeHostDevice == null) {
            val state = runCatching { proxy.getConnectionState(pluggedDevice) }
              .getOrDefault(BluetoothProfile.STATE_DISCONNECTED)
            if (state == BluetoothProfile.STATE_CONNECTED) {
              activeHostDevice = pluggedDevice
              _connectionState.value = ConnectionState.CONNECTED
              startKeepAlive()
            }
          }
          if (!regDeferred.isCompleted) regDeferred.complete(registered)
        }
      }

      override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
        runCatching {
          when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
              reconnectJob?.cancel()
              activeHostDevice = device
              _lastError.value = null
              _connectionState.value = ConnectionState.CONNECTED
              pendingConnectDeferred?.complete(true)
              startKeepAlive()
            }
            BluetoothProfile.STATE_CONNECTING -> {
              if (_connectionState.value != ConnectionState.CONNECTED) {
                _connectionState.value = ConnectionState.CONNECTING
              }
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
              pressedMouseButtonsBitmask = 0
              if (userInitiatedDisconnect) {
                if (activeHostDevice?.address == device?.address) {
                  activeHostDevice = null
                }
                _connectionState.value = ConnectionState.DISCONNECTED
                pendingConnectDeferred?.complete(false)
              } else if (device != null && activeHostDevice?.address == device.address) {
                // Host dropped idle link after ~1 min — transparently reconnect without showing error
                reconnectJob?.cancel()
                reconnectJob = scope.launch {
                  repeat(4) { attempt ->
                    if (userInitiatedDisconnect) return@launch
                    delay(500L * (attempt + 1))
                    val currentHid = hidDevice ?: return@launch
                    val curState = runCatching { currentHid.getConnectionState(device) }
                      .getOrDefault(BluetoothProfile.STATE_DISCONNECTED)
                    if (curState == BluetoothProfile.STATE_CONNECTED) {
                      _lastError.value = null
                      _connectionState.value = ConnectionState.CONNECTED
                      return@launch
                    }
                    runCatching { currentHid.connect(device) }
                  }
                }
              }
            }
          }
        }
      }
    }

    // Register without strict outQos token bucket limits so Bluetooth links never drop after 60s
    val called = runCatching {
      proxy.registerApp(sdp, null, null, hidExecutor, callback)
    }.getOrDefault(false)
    if (!called) return@withLock false
    withTimeoutOrNull(4_000L) { regDeferred.await() } ?: false
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun ensureHidProfileInitialized() {
    scope.launch {
      runCatching { ensureHidProfileAndRegisterApp() }
    }
  }

  @RequiresApi(Build.VERSION_CODES.P)
  @SuppressLint("MissingPermission")
  private fun sendMouseReport(
    hid: BluetoothHidDevice,
    host: BluetoothDevice,
    buttons: Int = 0,
    dx: Int = 0,
    dy: Int = 0,
    wheelY: Int = 0,
    panX: Int = 0
  ): Boolean {
    val report = byteArrayOf(
      (buttons and 0x07).toByte(),
      dx.coerceIn(-127, 127).toByte(),
      dy.coerceIn(-127, 127).toByte(),
      wheelY.coerceIn(-127, 127).toByte(),
      panX.coerceIn(-127, 127).toByte()
    )
    return runCatching {
      hid.sendReport(host, HidDescriptors.REPORT_ID_MOUSE, report)
    }.getOrDefault(false)
  }

  @RequiresApi(Build.VERSION_CODES.P)
  @SuppressLint("MissingPermission")
  private fun sendKeyboardReport(
    hid: BluetoothHidDevice,
    host: BluetoothDevice,
    modifiers: Int,
    keyUsage: Int
  ): Boolean {
    val report = byteArrayOf(
      (modifiers and 0xFF).toByte(),
      0x00,
      (keyUsage and 0xFF).toByte(),
      0x00,
      0x00,
      0x00,
      0x00,
      0x00
    )
    return runCatching {
      hid.sendReport(host, HidDescriptors.REPORT_ID_KEYBOARD, report)
    }.getOrDefault(false)
  }

  @RequiresApi(Build.VERSION_CODES.P)
  @SuppressLint("MissingPermission")
  private fun sendConsumerReport(
    hid: BluetoothHidDevice,
    host: BluetoothDevice,
    bitmask: Int
  ): Boolean {
    val report = byteArrayOf(
      (bitmask and 0xFF).toByte(),
      ((bitmask ushr 8) and 0x01).toByte()
    )
    return runCatching {
      hid.sendReport(host, HidDescriptors.REPORT_ID_CONSUMER, report)
    }.getOrDefault(false)
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private suspend fun sendConsumerReportPulse(
    hid: BluetoothHidDevice,
    host: BluetoothDevice,
    bitmask: Int
  ): Boolean {
    val down = sendConsumerReport(hid, host, bitmask)
    delay(24)
    val up = sendConsumerReport(hid, host, 0)
    return down && up
  }

  @SuppressLint("MissingPermission")
  private suspend fun waitForBondCompletion(device: BluetoothDevice): Boolean {
    repeat(30) {
      val state = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
      when (state) {
        BluetoothDevice.BOND_BONDED -> return true
        BluetoothDevice.BOND_NONE -> if (it > 4) return false
      }
      delay(500)
    }
    return runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
  }

  private fun refreshBondedDevicesIntoDiscovered() {
    val bonded = getBondedDevices()
    _discoveredDevices.update { currentList ->
      val map = currentList.associateBy { it.address }.toMutableMap()
      bonded.forEach { map[it.address] = it }
      map.values.distinctBy { it.id }.sortedByDescending { it.isAlreadyBonded }
    }
  }

  @SuppressLint("MissingPermission")
  private fun addDiscoveredBluetoothDevice(device: BluetoothDevice) {
    if (!BluetoothCapabilityHelper.hasConnectPermission(appContext)) return
    val name = runCatching { device.name }.getOrNull()
    if (name.isNullOrBlank()) return
    val address = runCatching { device.address }.getOrNull() ?: return
    val isBonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
    val entry = DiscoveredDevice(
      id = "bt_$address",
      name = name,
      address = address,
      deviceType = BluetoothCapabilityHelper.inferDeviceType(device),
      os = OperatingSystem.UNKNOWN,
      transportType = TransportType.BLUETOOTH_HID,
      isAlreadyBonded = isBonded,
      requiresPairingCode = false,
      protocolNote = if (isBonded) "Paired Bluetooth device" else "Nearby Bluetooth device"
    )
    _discoveredDevices.update { currentList ->
      val updated = currentList.associateBy { it.address }.toMutableMap()
      updated[entry.address] = entry
      updated.values.distinctBy { it.id }.sortedByDescending { it.isAlreadyBonded }
    }
  }

  private fun registerDiscoveryReceiverIfNeeded() {
    synchronized(receiverLock) {
      if (discoveryReceiverRegistered) return
      val filter = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_FOUND)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
      }
      runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          appContext.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
          appContext.registerReceiver(discoveryReceiver, filter)
        }
        discoveryReceiverRegistered = true
      }
    }
  }
}
