package com.example.connection.usb

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import com.example.connection.wifi.WifiProtocol
import com.example.models.DeviceType
import com.example.models.DiscoveredDevice
import com.example.models.OperatingSystem
import com.example.models.TransportType
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

data class UsbStatusSnapshot(
  val isCableConnected: Boolean = false,
  val isUsbTetheringActive: Boolean = false,
  val isAdbEnabled: Boolean = false,
  val usbLocalIp: String? = null,
  val usbInterfaceName: String? = null,
  val usbAccessoryCount: Int = 0,
  val usbHostDeviceCount: Int = 0
)

/**
 * Discovers USB-connected PCs and receivers over:
 * 1. USB Tethering / RNDIS / NCM interfaces (`usb0`, `rndis0`, `ncm0`) and their ARP peer IPs
 * 2. ADB reverse / USB port forwarding (`127.0.0.1:8765`)
 * 3. Direct USB hardware state via [UsbManager] and sticky `USB_STATE` broadcasts
 */
class UsbDesktopDiscovery(context: Context) {

  private val appContext: Context = context.applicationContext ?: context
  private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

  private val usbManager: UsbManager? by lazy {
    runCatching { appContext.getSystemService(Context.USB_SERVICE) as? UsbManager }.getOrNull()
  }

  private val _usbStatus = MutableStateFlow(UsbStatusSnapshot())
  val usbStatus: StateFlow<UsbStatusSnapshot> = _usbStatus.asStateFlow()

  private val _discoveredUsbDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
  val discoveredUsbDevices: StateFlow<List<DiscoveredDevice>> = _discoveredUsbDevices.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private var scanJob: Job? = null

  fun refreshUsbStatus(): UsbStatusSnapshot {
    val snapshot = readHardwareSnapshot()
    _usbStatus.value = snapshot
    return snapshot
  }

  fun startDiscovery() {
    scanJob?.cancel()
    _isScanning.value = true
    scanJob = scope.launch {
      try {
        val snapshot = readHardwareSnapshot()
        _usbStatus.value = snapshot

        val found = LinkedHashMap<String, DiscoveredDevice>()

        // 1. Check localhost ADB USB reverse tunnel (127.0.0.1:8765)
        if (isPortOpen("127.0.0.1", WifiProtocol.DEFAULT_PORT, timeoutMs = 180)) {
          val adbDev = DiscoveredDevice(
            id = "usb_127.0.0.1",
            name = "USB PC Tunnel",
            address = "127.0.0.1",
            port = WifiProtocol.DEFAULT_PORT,
            deviceType = DeviceType.LAPTOP,
            os = OperatingSystem.UNKNOWN,
            transportType = TransportType.USB,
            isAlreadyBonded = false,
            requiresPairingCode = false,
            protocolNote = "ADB USB Tunnel • 127.0.0.1:${WifiProtocol.DEFAULT_PORT}"
          )
          found[adbDev.id] = adbDev
        }

        // 2. Check USB Tethering / RNDIS / NCM peers from ARP & interface gateway
        val ifaceInfo = findActiveUsbInterface()
        if (ifaceInfo != null && isActive) {
          val (ifaceName, localIp) = ifaceInfo
          val peerCandidates = buildList {
            addAll(readUsbArpNeighbors(ifaceName, localIp))
            val parts = localIp.split(".")
            if (parts.size == 4) {
              val prefix = "${parts[0]}.${parts[1]}.${parts[2]}."
              val selfLast = parts[3].toIntOrNull() ?: -1
              listOf(1, 2, 10, 42, 100, 129, 254).forEach { h ->
                if (h != selfLast) add("$prefix$h")
              }
            }
          }.distinct()

          // Send UDP Slate beacon over USB interface broadcast if available
          probeUdpUsbPeers(localIp)?.forEach { dev ->
            found[dev.id] = dev
          }

          for (candidateIp in peerCandidates) {
            if (!isActive) break
            probeUsbPeer(candidateIp)?.let { dev ->
              found[dev.id] = dev
            }
          }
        }

        _discoveredUsbDevices.value = found.values.toList()
      } finally {
        _isScanning.value = false
      }
    }
  }

  fun stopDiscovery() {
    scanJob?.cancel()
    scanJob = null
    _isScanning.value = false
  }

  private fun readHardwareSnapshot(): UsbStatusSnapshot {
    val usbIntent: Intent? = runCatching {
      appContext.registerReceiver(null, IntentFilter("android.hardware.usb.action.USB_STATE"))
    }.getOrNull()

    val cableConnected = usbIntent?.getBooleanExtra("connected", false) == true ||
      usbIntent?.getBooleanExtra("configured", false) == true
    val rndisExtra = usbIntent?.getBooleanExtra("rndis", false) == true
    val adbExtra = usbIntent?.getBooleanExtra("adb", false) == true

    val activeUsbNet = findActiveUsbInterface()
    val accessoryCount = runCatching { usbManager?.accessoryList?.size ?: 0 }.getOrDefault(0)
    val hostDeviceCount = runCatching { usbManager?.deviceList?.size ?: 0 }.getOrDefault(0)

    return UsbStatusSnapshot(
      isCableConnected = cableConnected || activeUsbNet != null || accessoryCount > 0 || hostDeviceCount > 0,
      isUsbTetheringActive = rndisExtra || activeUsbNet != null,
      isAdbEnabled = adbExtra,
      usbLocalIp = activeUsbNet?.second,
      usbInterfaceName = activeUsbNet?.first,
      usbAccessoryCount = accessoryCount,
      usbHostDeviceCount = hostDeviceCount
    )
  }

  private fun findActiveUsbInterface(): Pair<String, String>? = runCatching {
    NetworkInterface.getNetworkInterfaces()?.toList()
      ?.filter { iface ->
        val name = iface.name.lowercase()
        (name.startsWith("usb") || name.startsWith("rndis") || name.startsWith("ncm") || name.startsWith("ecm")) &&
          iface.isUp && !iface.isLoopback
      }
      ?.firstNotNullOfOrNull { iface ->
        val ipv4 = iface.inetAddresses.toList()
          .filterIsInstance<Inet4Address>()
          .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
          ?.hostAddress
        if (ipv4 != null) iface.name to ipv4 else null
      }
  }.getOrNull()

  private fun readUsbArpNeighbors(ifaceName: String, selfIp: String): List<String> = runCatching {
    val arpFile = File("/proc/net/arp")
    if (!arpFile.canRead()) return emptyList()
    arpFile.readLines()
      .drop(1)
      .mapNotNull { line ->
        val cols = line.trim().split(Regex("\\s+"))
        val ip = cols.getOrNull(0) ?: return@mapNotNull null
        val flags = cols.getOrNull(2) ?: ""
        val mac = cols.getOrNull(3) ?: ""
        val dev = cols.getOrNull(5) ?: ""
        val isUsbDev = dev.equals(ifaceName, ignoreCase = true) ||
          dev.startsWith("usb", ignoreCase = true) ||
          dev.startsWith("rndis", ignoreCase = true) ||
          dev.startsWith("ncm", ignoreCase = true)
        if (isUsbDev && ip != selfIp && flags != "0x0" && mac != "00:00:00:00:00:00") {
          ip
        } else {
          null
        }
      }
  }.getOrDefault(emptyList())

  private fun probeUdpUsbPeers(localIp: String): List<DiscoveredDevice>? = runCatching {
    val parts = localIp.split(".")
    if (parts.size != 4) return null
    val broadcast = InetAddress.getByName("${parts[0]}.${parts[1]}.${parts[2]}.255")
    val probeBytes = "SLATE_DISCOVER_V1".toByteArray(Charsets.UTF_8)
    val results = mutableListOf<DiscoveredDevice>()
    DatagramSocket().use { socket ->
      socket.broadcast = true
      socket.soTimeout = 350
      socket.send(DatagramPacket(probeBytes, probeBytes.size, broadcast, 8766))
      val buffer = ByteArray(1024)
      val deadline = System.currentTimeMillis() + 450L
      while (System.currentTimeMillis() < deadline) {
        val packet = DatagramPacket(buffer, buffer.size)
        val ok = runCatching {
          socket.receive(packet)
          true
        }.getOrDefault(false)
        if (!ok) break
        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
        val hostIp = packet.address?.hostAddress ?: continue
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: continue
        if (obj.optString("service") != "slate-remote") continue
        val name = obj.optString("name", hostIp).ifBlank { "USB PC ($hostIp)" }
        val port = obj.optInt("port", WifiProtocol.DEFAULT_PORT)
        val os = parseOs(obj.optString("os"))
        results.add(
          DiscoveredDevice(
            id = "usb_$hostIp",
            name = name,
            address = hostIp,
            port = port,
            deviceType = DeviceType.LAPTOP,
            os = os,
            transportType = TransportType.USB,
            isAlreadyBonded = false,
            requiresPairingCode = false,
            protocolNote = "USB Receiver • $hostIp:$port"
          )
        )
      }
    }
    results
  }.getOrNull()

  private fun probeUsbPeer(hostIp: String): DiscoveredDevice? {
    if (isPortOpen(hostIp, WifiProtocol.DEFAULT_PORT, timeoutMs = 160)) {
      return DiscoveredDevice(
        id = "usb_$hostIp",
        name = "USB Receiver ($hostIp)",
        address = hostIp,
        port = WifiProtocol.DEFAULT_PORT,
        deviceType = DeviceType.LAPTOP,
        os = OperatingSystem.UNKNOWN,
        transportType = TransportType.USB,
        isAlreadyBonded = false,
        requiresPairingCode = false,
        protocolNote = "USB Tethering • $hostIp:${WifiProtocol.DEFAULT_PORT}"
      )
    }

    val smbOpen = isPortOpen(hostIp, 445, timeoutMs = 120)
    if (smbOpen) {
      return DiscoveredDevice(
        id = "usb_$hostIp",
        name = "Windows PC (USB)",
        address = hostIp,
        port = WifiProtocol.DEFAULT_PORT,
        deviceType = DeviceType.LAPTOP,
        os = OperatingSystem.WINDOWS,
        transportType = TransportType.USB,
        isAlreadyBonded = false,
        requiresPairingCode = false,
        protocolNote = "USB Tethering • $hostIp"
      )
    }

    val sshOpen = isPortOpen(hostIp, 22, timeoutMs = 120)
    if (sshOpen || isPortRefusedAlive(hostIp, WifiProtocol.DEFAULT_PORT, timeoutMs = 120)) {
      return DiscoveredDevice(
        id = "usb_$hostIp",
        name = "USB Connected PC ($hostIp)",
        address = hostIp,
        port = WifiProtocol.DEFAULT_PORT,
        deviceType = DeviceType.LAPTOP,
        os = if (sshOpen) OperatingSystem.LINUX else OperatingSystem.UNKNOWN,
        transportType = TransportType.USB,
        isAlreadyBonded = false,
        requiresPairingCode = false,
        protocolNote = "USB Tethering • $hostIp"
      )
    }

    return null
  }

  private fun isPortOpen(host: String, port: Int, timeoutMs: Int): Boolean = try {
    Socket().use { socket ->
      socket.connect(InetSocketAddress(host, port), timeoutMs)
      true
    }
  } catch (_: Throwable) {
    false
  }

  private fun isPortRefusedAlive(host: String, port: Int, timeoutMs: Int): Boolean = try {
    Socket().use { socket ->
      socket.connect(InetSocketAddress(host, port), timeoutMs)
      true
    }
  } catch (e: ConnectException) {
    e.message?.contains("refused", ignoreCase = true) == true
  } catch (_: Throwable) {
    false
  }

  private fun parseOs(value: String): OperatingSystem = when {
    value.contains("win", ignoreCase = true) -> OperatingSystem.WINDOWS
    value.contains("mac", ignoreCase = true) || value.contains("darwin", ignoreCase = true) -> OperatingSystem.MACOS
    value.contains("linux", ignoreCase = true) -> OperatingSystem.LINUX
    value.contains("android", ignoreCase = true) || value.contains("tv", ignoreCase = true) -> OperatingSystem.ANDROID_TV
    else -> OperatingSystem.UNKNOWN
  }
}
