package com.example.connection.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.example.models.DeviceType
import com.example.models.DiscoveredDevice
import com.example.models.OperatingSystem
import com.example.models.TransportType
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Discovers devices on the same Wi-Fi network via:
 * 1. mDNS / DNS-SD across prioritized device & TV service types
 * 2. UDP LAN broadcast beacon on port 8766
 * 3. Bounded, non-blocking /24 subnet probe on a dedicated worker pool (never starving Dispatchers.IO)
 */
class WifiNsdDiscovery(context: Context) {

  private val appContext: Context = context.applicationContext ?: context
  private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
  private val lanScanDispatcher = Executors.newFixedThreadPool(8) { runnable ->
    Thread(runnable, "SlateLanScan").apply {
      isDaemon = true
      setUncaughtExceptionHandler { _, _ -> }
    }
  }.asCoroutineDispatcher()

  private val nsdManager: NsdManager? by lazy {
    runCatching { appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager }.getOrNull()
  }
  private val wifiManager: WifiManager? by lazy {
    runCatching { appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }.getOrNull()
  }
  private val connectivityManager: ConnectivityManager? by lazy {
    runCatching { appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }.getOrNull()
  }
  private var multicastLock: WifiManager.MulticastLock? = null

  private val _discoveredWifiDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
  val discoveredWifiDevices: StateFlow<List<DiscoveredDevice>> = _discoveredWifiDevices.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _scanError = MutableStateFlow<String?>(null)
  val scanError: StateFlow<String?> = _scanError.asStateFlow()

  private val _localWifiIp = MutableStateFlow<String?>(null)
  val localWifiIp: StateFlow<String?> = _localWifiIp.asStateFlow()

  private val listenersLock = Any()
  private val activeNsdListeners = mutableListOf<NsdManager.DiscoveryListener>()
  private var udpDiscoveryJob: Job? = null
  private var subnetScanJob: Job? = null
  private val lastScanStartMs = AtomicLong(0L)
  private val scanGeneration = AtomicLong(0L)

  // Serialize NsdManager.resolveService calls to prevent FAILURE_ALREADY_ACTIVE drops
  private val resolveQueue = ConcurrentLinkedQueue<Pair<NsdServiceInfo, String>>()
  private val isResolving = AtomicBoolean(false)

  // Keep under Android's concurrent NsdManager session limit while covering receiver, TVs, and desktops
  private val mdnsServiceTypes = listOf(
    WifiProtocol.NSD_SERVICE_TYPE,
    "_androidtvremote2._tcp.",
    "_googlecast._tcp.",
    "_airplay._tcp.",
    "_workstation._tcp."
  )

  fun refreshWifiStatus() {
    scope.launch {
      _localWifiIp.value = runCatching { findLocalWifiIpv4() }.getOrNull()
    }
  }

  fun startDiscovery(force: Boolean = false) {
    val now = System.currentTimeMillis()
    if (!force && _isScanning.value && (now - lastScanStartMs.get()) < 1_500L) {
      return
    }
    lastScanStartMs.set(now)

    val generation = scanGeneration.incrementAndGet()

    scope.launch {
      if (scanGeneration.get() != generation) return@launch
      val localIp = findLocalWifiIpv4()
      _localWifiIp.value = localIp
      stopDiscoveryInternal(resetScanningFlag = false)
      _scanError.value = null
      _discoveredWifiDevices.value = emptyList()

      if (localIp == null && !isOnWifiNetwork()) {
        _scanError.value = "Connect this phone to the same Wi-Fi network as your PC, laptop, or TV."
        _isScanning.value = false
        return@launch
      }
      if (scanGeneration.get() != generation) return@launch

      _isScanning.value = true

      runCatching {
        multicastLock = wifiManager?.createMulticastLock("SlateRemoteNsdLock")?.apply {
          setReferenceCounted(true)
          acquire()
        }
      }

      startUdpDiscovery(generation)
      startSubnetScan(localIp, generation)

      val manager = nsdManager ?: return@launch
      mdnsServiceTypes.forEach { serviceType ->
        if (scanGeneration.get() != generation) return@forEach
        val listener = createListener(serviceType, generation)
        runCatching {
          synchronized(listenersLock) {
            activeNsdListeners.add(listener)
          }
          manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
          synchronized(listenersLock) {
            activeNsdListeners.remove(listener)
          }
        }
      }
    }
  }

  fun stopDiscovery() {
    scanGeneration.incrementAndGet()
    stopDiscoveryInternal(resetScanningFlag = true)
  }

  private fun stopDiscoveryInternal(resetScanningFlag: Boolean) {
    if (resetScanningFlag) {
      _isScanning.value = false
    }
    udpDiscoveryJob?.cancel()
    udpDiscoveryJob = null
    subnetScanJob?.cancel()
    subnetScanJob = null
    resolveQueue.clear()
    isResolving.set(false)

    val manager = nsdManager
    val listeners = synchronized(listenersLock) {
      val copy = activeNsdListeners.toList()
      activeNsdListeners.clear()
      copy
    }
    if (manager != null) {
      listeners.forEach { listener ->
        runCatching { manager.stopServiceDiscovery(listener) }
      }
    }

    runCatching {
      if (multicastLock?.isHeld == true) {
        multicastLock?.release()
      }
    }
    multicastLock = null
  }

  private fun startUdpDiscovery(generation: Long) {
    udpDiscoveryJob?.cancel()
    udpDiscoveryJob = scope.launch {
      runCatching {
        DatagramSocket().use { socket ->
          socket.broadcast = true
          socket.soTimeout = 600
          val probeBytes = "SLATE_DISCOVER_V1".toByteArray(Charsets.UTF_8)
          val broadcastTargets = listOfNotNull(
            runCatching { InetAddress.getByName("255.255.255.255") }.getOrNull(),
            computeSubnetBroadcast(_localWifiIp.value)
          ).distinct()

          val buffer = ByteArray(1024)
          repeat(3) {
            if (!isActive || scanGeneration.get() != generation) return@use
            for (target in broadcastTargets) {
              runCatching {
                socket.send(DatagramPacket(probeBytes, probeBytes.size, target, 8766))
              }
            }

            val deadline = System.currentTimeMillis() + 600L
            while (isActive && scanGeneration.get() == generation && System.currentTimeMillis() < deadline) {
              val packet = DatagramPacket(buffer, buffer.size)
              val received = runCatching {
                socket.receive(packet)
                true
              }.getOrDefault(false)
              if (!received) break

              val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
              val hostIp = packet.address?.hostAddress ?: continue
              parseUdpBeacon(text, hostIp)?.let { upsertDiscovered(it) }
            }
            delay(400L)
          }
        }
      }
    }
  }

  /**
   * Scans the local /24 subnet on [lanScanDispatcher] so [Dispatchers.IO] is never blocked.
   * Uses a bounded pool of 8 worker coroutines to prevent socket / FD exhaustion.
   */
  private fun startSubnetScan(localIp: String?, generation: Long) {
    subnetScanJob?.cancel()
    val ip = localIp ?: return
    val parts = ip.split(".")
    if (parts.size != 4) return
    val prefix = "${parts[0]}.${parts[1]}.${parts[2]}."
    val selfHostNum = parts[3].toIntOrNull() ?: -1

    subnetScanJob = scope.launch(lanScanDispatcher) {
      runCatching {
        val arpIps = readArpNeighborIps(prefix, ip)
        val prioritizedHosts = listOf(2, 100, 101, 102, 103, 104, 105, 10, 11, 20, 50)
        val allCandidateIps = buildList {
          addAll(arpIps)
          prioritizedHosts.forEach { h ->
            if (h != selfHostNum && h in 1..254) add("$prefix$h")
          }
          for (h in 1..254) {
            if (h != selfHostNum) add("$prefix$h")
          }
        }.distinct()

        val nextIndex = AtomicInteger(0)
        val workerCount = minOf(8, allCandidateIps.size.coerceAtLeast(1))
        coroutineScope {
          repeat(workerCount) {
            launch {
              while (isActive && scanGeneration.get() == generation) {
                val idx = nextIndex.getAndIncrement()
                if (idx >= allCandidateIps.size) break
                val candidateIp = allCandidateIps[idx]
                probeLanHostFast(candidateIp, isArpKnown = candidateIp in arpIps)?.let { discovered ->
                  upsertDiscovered(discovered)
                }
              }
            }
          }
        }
      }
      if (scanGeneration.get() == generation) {
        _isScanning.value = false
      }
    }
  }

  private fun readArpNeighborIps(prefix: String, selfIp: String): List<String> = runCatching {
    val arpFile = File("/proc/net/arp")
    if (!arpFile.canRead()) return emptyList()
    arpFile.readLines()
      .drop(1)
      .mapNotNull { line ->
        val cols = line.trim().split(Regex("\\s+"))
        val ip = cols.getOrNull(0) ?: return@mapNotNull null
        val flags = cols.getOrNull(2) ?: ""
        val mac = cols.getOrNull(3) ?: ""
        if (ip.startsWith(prefix) && ip != selfIp && flags != "0x0" && mac != "00:00:00:00:00:00") {
          ip
        } else {
          null
        }
      }
  }.getOrDefault(emptyList())

  private enum class ProbeResult {
    OPEN,
    REFUSED_ALIVE,
    UNREACHABLE
  }

  private fun probeTcpPort(ip: String, port: Int, timeoutMs: Int): ProbeResult {
    return try {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(ip, port), timeoutMs)
        ProbeResult.OPEN
      }
    } catch (e: ConnectException) {
      // A TCP RST ("Connection refused") means a live device is at this IP and responded immediately!
      if (e.message?.contains("refused", ignoreCase = true) == true) {
        ProbeResult.REFUSED_ALIVE
      } else {
        ProbeResult.UNREACHABLE
      }
    } catch (_: Throwable) {
      ProbeResult.UNREACHABLE
    }
  }

  private fun probeLanHostFast(hostIp: String, isArpKnown: Boolean): DiscoveredDevice? {
    val lastOctet = hostIp.substringAfterLast('.').toIntOrNull() ?: return null
    if (lastOctet !in 1..254) return null

    // 1. Probe AIO Remote Receiver port (8765) first
    val r8765 = probeTcpPort(hostIp, WifiProtocol.DEFAULT_PORT, timeoutMs = 110)
    if (r8765 == ProbeResult.OPEN) {
      // If Wi-Fi broadcast is filtered, ask the receiver directly for its name.
      queryReceiverBeacon(hostIp)?.let { return it }
      return DiscoveredDevice(
        id = "wifi_$hostIp",
        name = "Slate Receiver ($hostIp)",
        address = hostIp,
        port = WifiProtocol.DEFAULT_PORT,
        deviceType = DeviceType.LAPTOP,
        os = OperatingSystem.UNKNOWN,
        transportType = TransportType.WIFI_DESKTOP,
        isAlreadyBonded = false,
        requiresPairingCode = false,
        protocolNote = "Slate Wi-Fi Receiver • $hostIp"
      )
    }

    if (r8765 != ProbeResult.REFUSED_ALIVE && !isArpKnown) return null
    return DiscoveredDevice(
      id = "wifi_$hostIp",
      name = hostIp,
      address = hostIp,
      transportType = TransportType.WIFI_DESKTOP,
      protocolNote = "No compatible remote receiver found",
      canConnect = false
    )
  }

  private fun inferOsFromHostname(hostname: String): OperatingSystem {
    val lower = hostname.lowercase()
    return when {
      lower.contains("win") || lower.contains("desktop-") || lower.contains("laptop-") -> OperatingSystem.WINDOWS
      lower.contains("mac") || lower.contains("mbp") || lower.contains("imac") || lower.contains("air") -> OperatingSystem.MACOS
      lower.contains("ubuntu") || lower.contains("linux") || lower.contains("fedora") || lower.contains("arch") || lower.contains("debian") -> OperatingSystem.LINUX
      lower.contains("tv") || lower.contains("chromecast") || lower.contains("shield") -> OperatingSystem.ANDROID_TV
      else -> OperatingSystem.UNKNOWN
    }
  }

  private fun queryReceiverBeacon(hostIp: String): DiscoveredDevice? = runCatching {
    DatagramSocket().use { socket ->
      socket.connect(InetAddress.getByName(hostIp), 8766)
      socket.soTimeout = 300
      val probe = "SLATE_DISCOVER_V1".toByteArray(Charsets.UTF_8)
      socket.send(DatagramPacket(probe, probe.size))
      val reply = DatagramPacket(ByteArray(1024), 1024)
      socket.receive(reply)
      parseUdpBeacon(String(reply.data, reply.offset, reply.length, Charsets.UTF_8), hostIp)
    }
  }.getOrNull()

  private fun parseUdpBeacon(raw: String, hostIp: String): DiscoveredDevice? = runCatching {
    val obj = JSONObject(raw)
    if (obj.optString("service") != "slate-remote") return null
    val name = obj.optString("name", hostIp).ifBlank { hostIp }
    val port = obj.optInt("port", WifiProtocol.DEFAULT_PORT)
    val os = parseOs(obj.optString("os"))
    DiscoveredDevice(
      id = "wifi_$hostIp",
      name = name,
      address = hostIp,
      port = port,
      deviceType = if (os == OperatingSystem.ANDROID_TV) DeviceType.SMART_TV else DeviceType.LAPTOP,
      os = os,
      transportType = TransportType.WIFI_DESKTOP,
      isAlreadyBonded = false,
      requiresPairingCode = false,
      protocolNote = "${os.label} • $hostIp:$port"
    )
  }.getOrNull()

  private fun enqueueResolve(serviceInfo: NsdServiceInfo, serviceType: String) {
    resolveQueue.add(serviceInfo to serviceType)
    processNextResolve()
  }

  private fun processNextResolve() {
    if (!isResolving.compareAndSet(false, true)) return
    val next = resolveQueue.poll()
    if (next == null) {
      isResolving.set(false)
      return
    }
    val (info, serviceType) = next
    val manager = nsdManager ?: run {
      isResolving.set(false)
      return
    }
    @Suppress("DEPRECATION")
    val started = runCatching {
      manager.resolveService(info, object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
          runCatching {
            isResolving.set(false)
            processNextResolve()
          }
        }

        override fun onServiceResolved(resolved: NsdServiceInfo) {
          runCatching {
            handleResolvedNsdService(resolved, serviceType)
          }
          runCatching {
            isResolving.set(false)
            processNextResolve()
          }
        }
      })
      true
    }.getOrDefault(false)

    if (!started) {
      isResolving.set(false)
      processNextResolve()
    }
  }

  private fun handleResolvedNsdService(info: NsdServiceInfo, serviceType: String) {
    @Suppress("DEPRECATION")
    val hostAddress = runCatching { info.host?.hostAddress }.getOrNull() ?: return
    if (hostAddress == _localWifiIp.value || hostAddress == "127.0.0.1") return

    val rawName = info.serviceName?.takeIf { it.isNotBlank() } ?: hostAddress
    val cleanName = rawName.replace(Regex("\\s*\\[[0-9a-fA-F:]{11,17}]\\s*$"), "").trim().ifBlank { hostAddress }
    val osAttr = runCatching {
      info.attributes["os"]?.let { String(it, Charsets.UTF_8) } ?: ""
    }.getOrDefault("")

    val isTvService = serviceType.contains("androidtv", ignoreCase = true) ||
      serviceType.contains("googlecast", ignoreCase = true) ||
      cleanName.contains("TV", ignoreCase = true)

    val os = when {
      isTvService -> OperatingSystem.ANDROID_TV
      serviceType.contains("airplay", ignoreCase = true) ||
        serviceType.contains("companion-link", ignoreCase = true) -> OperatingSystem.MACOS
      serviceType.contains("workstation", ignoreCase = true) -> OperatingSystem.LINUX
      osAttr.isNotBlank() -> parseOs(osAttr)
      else -> inferOsFromHostname(cleanName)
    }

    val deviceType = if (isTvService) DeviceType.SMART_TV else DeviceType.LAPTOP
    val port = if (serviceType == WifiProtocol.NSD_SERVICE_TYPE && info.port > 0) {
      info.port
    } else {
      WifiProtocol.DEFAULT_PORT
    }

    val note = when {
      isTvService -> "Smart TV • $hostAddress"
      os != OperatingSystem.UNKNOWN -> "${os.label} • $hostAddress"
      else -> "Same Wi-Fi • $hostAddress"
    }

    val device = DiscoveredDevice(
      id = "wifi_$hostAddress",
      name = cleanName,
      address = hostAddress,
      port = port,
      deviceType = deviceType,
      os = os,
      transportType = TransportType.WIFI_DESKTOP,
      isAlreadyBonded = false,
      requiresPairingCode = false,
      protocolNote = if (serviceType == WifiProtocol.NSD_SERVICE_TYPE) note
        else "$hostAddress • No compatible remote receiver found",
      canConnect = serviceType == WifiProtocol.NSD_SERVICE_TYPE
    )
    upsertDiscovered(device)
  }

  private fun upsertDiscovered(device: DiscoveredDevice) {
    _discoveredWifiDevices.update { current ->
      val map = current.associateBy { it.address }.toMutableMap()
      val existing = map[device.address]
      if (existing == null ||
        (device.canConnect && !existing.canConnect) ||
        (device.canConnect == existing.canConnect &&
          (existing.name.contains(device.address) || !device.name.contains(device.address)))
      ) {
        map[device.address] = device
        map.values.distinctBy { it.id }
      } else {
        current
      }
    }
  }

  private fun createListener(expectedType: String, generation: Long): NsdManager.DiscoveryListener =
    object : NsdManager.DiscoveryListener {
      override fun onDiscoveryStarted(serviceType: String) {
        runCatching {
          if (scanGeneration.get() == generation) {
            _isScanning.value = true
          }
        }
      }

      override fun onServiceFound(serviceInfo: NsdServiceInfo) {
        runCatching {
          if (scanGeneration.get() == generation) {
            enqueueResolve(serviceInfo, expectedType)
          }
        }
      }

      override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

      override fun onDiscoveryStopped(serviceType: String) {
        runCatching {
          synchronized(listenersLock) {
            activeNsdListeners.remove(this)
          }
        }
      }

      override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        runCatching {
          synchronized(listenersLock) {
            activeNsdListeners.remove(this)
          }
        }
      }

      override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        runCatching {
          synchronized(listenersLock) {
            activeNsdListeners.remove(this)
          }
        }
      }
    }

  private fun isOnWifiNetwork(): Boolean = runCatching {
    val cm = connectivityManager ?: return@runCatching false
    val network = cm.activeNetwork ?: return@runCatching false
    val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
      caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
  }.getOrDefault(false)

  @Suppress("DEPRECATION")
  private fun findLocalWifiIpv4(): String? = runCatching {
    val fromInterfaces = NetworkInterface.getNetworkInterfaces()?.toList()
      ?.sortedBy { iface ->
        when {
          iface.name.startsWith("wlan") -> 0
          iface.name.startsWith("eth") -> 1
          iface.name.startsWith("ap") -> 2
          else -> 3
        }
      }
      ?.firstNotNullOfOrNull { iface ->
        runCatching {
          if (!iface.isUp || iface.isLoopback) return@runCatching null
          iface.inetAddresses.toList()
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
        }.getOrNull()
      }
    if (!fromInterfaces.isNullOrBlank()) return@runCatching fromInterfaces

    val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
    if (ipInt != 0) {
      val ordered = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
        Integer.reverseBytes(ipInt)
      } else {
        ipInt
      }
      val bytes = byteArrayOf(
        ((ordered ushr 24) and 0xFF).toByte(),
        ((ordered ushr 16) and 0xFF).toByte(),
        ((ordered ushr 8) and 0xFF).toByte(),
        (ordered and 0xFF).toByte()
      )
      InetAddress.getByAddress(bytes).hostAddress
    } else {
      null
    }
  }.getOrNull()

  private fun computeSubnetBroadcast(ip: String?): InetAddress? {
    val parts = ip?.split(".") ?: return null
    if (parts.size != 4) return null
    return runCatching {
      InetAddress.getByName("${parts[0]}.${parts[1]}.${parts[2]}.255")
    }.getOrNull()
  }

  private fun parseOs(value: String): OperatingSystem = when {
    value.contains("win", ignoreCase = true) -> OperatingSystem.WINDOWS
    value.contains("mac", ignoreCase = true) || value.contains("darwin", ignoreCase = true) -> OperatingSystem.MACOS
    value.contains("linux", ignoreCase = true) -> OperatingSystem.LINUX
    value.contains("android", ignoreCase = true) || value.contains("tv", ignoreCase = true) -> OperatingSystem.ANDROID_TV
    else -> OperatingSystem.UNKNOWN
  }
}
