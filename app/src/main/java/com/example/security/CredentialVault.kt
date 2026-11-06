package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed credential vault using Android Keystore (AES-256-GCM) to store
 * device pairing tokens and HMAC session secrets locally on the device.
 */
class CredentialVault(context: Context) {

  private val appContext: Context = context.applicationContext ?: context
  val installationId: String by lazy {
    val preferences = appContext.getSharedPreferences("slate_remote_identity", Context.MODE_PRIVATE)
    preferences.getString("id", null) ?: java.util.UUID.randomUUID().toString().also {
      preferences.edit().putString("id", it).apply()
    }
  }

  private val keyAlias = "slate_remote_pairing_vault_v1"
  private val vaultFile: File by lazy { File(appContext.filesDir, "slate_vault.enc") }
  private val random: SecureRandom by lazy { SecureRandom() }

  @Synchronized
  fun storeDeviceToken(deviceId: String, token: String) {
    val map = loadAllTokens().toMutableMap()
    map[deviceId] = token
    saveAllTokens(map)
  }

  @Synchronized
  fun getDeviceToken(deviceId: String): String? = loadAllTokens()[deviceId]

  @Synchronized
  fun removeDeviceToken(deviceId: String) {
    val map = loadAllTokens().toMutableMap()
    if (map.remove(deviceId) != null) {
      saveAllTokens(map)
    }
  }

  @Synchronized
  fun clearAll() {
    if (vaultFile.exists()) {
      vaultFile.delete()
    }
  }

  fun generateClientNonce(): String {
    val bytes = ByteArray(16)
    random.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
  }

  fun computeHmacSha256(secret: String, payload: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
  }

  private fun getOrCreateKey(): SecretKey? {
    return try {
      val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
      val existing = keyStore.getKey(keyAlias, null) as? SecretKey
      if (existing != null) return existing

      val keyGenerator = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        "AndroidKeyStore"
      )
      val spec = KeyGenParameterSpec.Builder(
        keyAlias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
      keyGenerator.init(spec)
      keyGenerator.generateKey()
    } catch (_: Throwable) {
      null
    }
  }

  private fun loadAllTokens(): Map<String, String> {
    if (!vaultFile.exists()) return emptyMap()
    return try {
      val raw = vaultFile.readText(Charsets.UTF_8)
      val container = JSONObject(raw)
      val key = getOrCreateKey() ?: return emptyMap()
      val iv = Base64.decode(container.getString("iv"), Base64.NO_WRAP)
      val data = Base64.decode(container.getString("ct"), Base64.NO_WRAP)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
      val plainJson = String(cipher.doFinal(data), Charsets.UTF_8)
      val obj = JSONObject(plainJson)
      buildMap {
        val keys = obj.keys()
        while (keys.hasNext()) {
          val k = keys.next()
          put(k, obj.getString(k))
        }
      }
    } catch (_: Throwable) {
      emptyMap()
    }
  }

  private fun saveAllTokens(map: Map<String, String>) {
    try {
      val key = getOrCreateKey() ?: return
      val json = JSONObject().apply {
        map.forEach { (k, v) -> put(k, v) }
      }.toString()
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, key)
      val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
      val container = JSONObject().apply {
        put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        put("ct", Base64.encodeToString(encrypted, Base64.NO_WRAP))
      }
      vaultFile.writeText(container.toString(), Charsets.UTF_8)
    } catch (_: Throwable) {
      // Never persist plaintext secrets if Keystore is unavailable
    }
  }
}
