package com.example

import android.app.Application
import android.content.Context
import android.os.Looper
import com.example.connection.ConnectionManager
import com.example.preferences.UserPreferencesRepository
import com.example.security.CredentialVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Application entry point providing process-wide singletons and a defensive thread exception guard
 * so background network/Bluetooth/discovery glitches can never terminate the app process.
 */
class SlateApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    instance = this
    installDefensiveExceptionHandler(this)
    loadPreviousCrashIfAny(this)
  }

  companion object {
    @Volatile
    var instance: SlateApplication? = null
      private set

    private val lock = Any()

    @Volatile
    private var sharedPreferences: UserPreferencesRepository? = null

    @Volatile
    private var sharedVault: CredentialVault? = null

    @Volatile
    private var sharedConnectionManager: ConnectionManager? = null

    private val _lastCrashReport = MutableStateFlow<String?>(null)
    val lastCrashReport: StateFlow<String?> = _lastCrashReport.asStateFlow()

    fun getPreferences(context: Context): UserPreferencesRepository {
      val appCtx = context.applicationContext ?: context
      return sharedPreferences ?: synchronized(lock) {
        sharedPreferences ?: UserPreferencesRepository(appCtx).also { sharedPreferences = it }
      }
    }

    fun getVault(context: Context): CredentialVault {
      val appCtx = context.applicationContext ?: context
      return sharedVault ?: synchronized(lock) {
        sharedVault ?: CredentialVault(appCtx).also { sharedVault = it }
      }
    }

    fun getConnectionManager(
      context: Context,
      preferences: UserPreferencesRepository = getPreferences(context),
      vault: CredentialVault = getVault(context)
    ): ConnectionManager {
      val appCtx = context.applicationContext ?: context
      return sharedConnectionManager ?: synchronized(lock) {
        sharedConnectionManager ?: ConnectionManager(appCtx, preferences, vault).also {
          sharedConnectionManager = it
        }
      }
    }

    fun clearCrashReport(context: Context) {
      _lastCrashReport.value = null
      runCatching {
        File(context.applicationContext?.filesDir ?: context.filesDir, "slate_last_crash.txt").delete()
      }
    }

    private fun loadPreviousCrashIfAny(context: Context) {
      runCatching {
        val file = File(context.filesDir, "slate_last_crash.txt")
        if (file.exists()) {
          val text = file.readText(Charsets.UTF_8).trim()
          if (text.isNotEmpty()) {
            _lastCrashReport.value = text
          }
          file.delete()
        }
      }
    }

    fun installDefensiveExceptionHandler(context: Context) {
      runCatching {
        val appCtx = context.applicationContext ?: context
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        // Avoid wrapping multiple times
        if (previousHandler?.javaClass?.name?.contains("SlateSafeExceptionHandler") == true) {
          return@runCatching
        }
        Thread.setDefaultUncaughtExceptionHandler(
          SlateSafeExceptionHandler(appCtx, previousHandler)
        )
      }
    }
  }
}

private class SlateSafeExceptionHandler(
  private val appContext: Context,
  private val delegate: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {
  override fun uncaughtException(thread: Thread, throwable: Throwable) {
    val mainThread = runCatching { Looper.getMainLooper()?.thread }.getOrNull()
    if (mainThread != null && thread !== mainThread) {
      // Absorb uncaught background worker exceptions so they never kill the UI process
      return
    }
    runCatching {
      val trace = buildString {
        appendLine("Thread: ${thread.name}")
        appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
        appendLine(throwable.stackTraceToString().take(3500))
      }
      File(appContext.filesDir, "slate_last_crash.txt").writeText(trace, Charsets.UTF_8)
    }
    delegate?.uncaughtException(thread, throwable)
  }
}
