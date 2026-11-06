package com.example.connection.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.models.BluetoothCapabilityReport
import com.example.models.DeviceType

object BluetoothCapabilityHelper {

  fun requiredBluetoothPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      listOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE
      )
    } else {
      listOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION
      )
    }

  fun missingPermissions(context: Context): List<String> = runCatching {
    requiredBluetoothPermissions().filter { perm ->
      ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
    }
  }.getOrDefault(emptyList())

  fun hasConnectPermission(context: Context): Boolean = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH
      ) == PackageManager.PERMISSION_GRANTED
    }
  }.getOrDefault(false)

  @SuppressLint("MissingPermission")
  fun buildCapabilityReport(
    context: Context,
    isHidProfileProxyAvailable: Boolean?
  ): BluetoothCapabilityReport {
    return try {
      val manager = runCatching {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
      }.getOrNull()
      val adapter: BluetoothAdapter? = runCatching { manager?.adapter }.getOrNull()
      val missing = missingPermissions(context)
      val isAndroid9Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
      val hasAdapter = adapter != null
      val isEnabled = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
      val hidSupported = isAndroid9Plus && hasAdapter && (isHidProfileProxyAvailable != false)

      val summary = when {
        !hasAdapter -> "This device does not have Bluetooth hardware. Use Wi-Fi mode instead."
        !isAndroid9Plus -> "Bluetooth HID requires Android 9 (API 28) or higher. Use Wi-Fi mode."
        missing.isNotEmpty() -> "Bluetooth permissions are required to scan, pair, and connect."
        !isEnabled -> "Bluetooth is currently turned off. Enable Bluetooth to connect to paired laptops, PCs, or TVs."
        isHidProfileProxyAvailable == false ->
          "This phone's Bluetooth firmware disabled the HID profile. Use Wi-Fi mode to connect on the same network."
        isHidProfileProxyAvailable == true ->
          "Bluetooth ready. Your phone can control any laptop, PC, or Smart TV as a mouse, keyboard, and remote."
        else -> "Checking Bluetooth HID support..."
      }

      BluetoothCapabilityReport(
        hasBluetoothAdapter = hasAdapter,
        isBluetoothEnabled = isEnabled,
        isAndroid9Plus = isAndroid9Plus,
        isHidProfileSupported = hidSupported,
        missingPermissions = missing,
        summaryMessage = summary
      )
    } catch (_: Throwable) {
      BluetoothCapabilityReport(
        hasBluetoothAdapter = false,
        isBluetoothEnabled = false,
        isAndroid9Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
        isHidProfileSupported = false,
        missingPermissions = emptyList(),
        summaryMessage = "Use Wi-Fi or grant Bluetooth permission to connect."
      )
    }
  }

  @SuppressLint("MissingPermission")
  fun inferDeviceType(device: BluetoothDevice): DeviceType {
    return try {
      val majorClass = device.bluetoothClass?.majorDeviceClass
      when (majorClass) {
        android.bluetooth.BluetoothClass.Device.Major.COMPUTER -> DeviceType.LAPTOP
        android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> DeviceType.SMART_TV
        else -> DeviceType.DESKTOP
      }
    } catch (_: Throwable) {
      DeviceType.UNKNOWN
    }
  }
}
