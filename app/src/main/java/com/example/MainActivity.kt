package com.example

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.navigation.SlateAppRoot
import com.example.viewmodel.SlateViewModel

class MainActivity : ComponentActivity() {

  @Volatile
  var isDarkThemeActive: Boolean = false

  @Volatile
  var shouldAutoHideSystemNavBar: Boolean = false

  @Volatile
  var isFullscreenTouchpadActive: Boolean = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    SlateApplication.installDefensiveExceptionHandler(this)
    applyEdgeToEdgeStyles(isDarkThemeActive)

    val resolvedViewModel: SlateViewModel = runCatching {
      androidx.lifecycle.ViewModelProvider(
        this,
        SlateViewModel.factory(application)
      )[SlateViewModel::class.java]
    }.getOrElse {
      SlateViewModel(application)
    }

    setContent {
      SlateAppRoot(viewModel = resolvedViewModel)
    }
    runCatching {
      window?.decorView?.post {
        syncSystemBars(
          activity = this,
          darkTheme = isDarkThemeActive,
          autoHideNavBar = shouldAutoHideSystemNavBar,
          hideStatusBar = isFullscreenTouchpadActive
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    syncSystemBars(
      activity = this,
      darkTheme = isDarkThemeActive,
      autoHideNavBar = shouldAutoHideSystemNavBar,
      hideStatusBar = isFullscreenTouchpadActive
    )
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      syncSystemBars(
        activity = this,
        darkTheme = isDarkThemeActive,
        autoHideNavBar = shouldAutoHideSystemNavBar,
        hideStatusBar = isFullscreenTouchpadActive
      )
    }
  }

  private fun applyEdgeToEdgeStyles(darkTheme: Boolean) {
    runCatching {
      val statusStyle = if (darkTheme) {
        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
      } else {
        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
      }
      val navStyle = if (darkTheme) {
        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
      } else {
        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
      }
      enableEdgeToEdge(statusBarStyle = statusStyle, navigationBarStyle = navStyle)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window?.isStatusBarContrastEnforced = false
        window?.isNavigationBarContrastEnforced = false
      }
    }
  }

  companion object {
    fun syncSystemBars(
      activity: Activity,
      darkTheme: Boolean = false,
      autoHideNavBar: Boolean = false,
      hideStatusBar: Boolean = false
    ) {
      runCatching {
        (activity as? MainActivity)?.applyEdgeToEdgeStyles(darkTheme)
        val window = activity.window ?: return
        val decorView = window.peekDecorView() ?: return
        val controller = WindowCompat.getInsetsController(window, decorView)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
        controller.systemBarsBehavior =
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (hideStatusBar) {
          controller.hide(WindowInsetsCompat.Type.systemBars())
        } else if (autoHideNavBar) {
          controller.show(WindowInsetsCompat.Type.statusBars())
          controller.hide(WindowInsetsCompat.Type.navigationBars())
        } else {
          controller.show(WindowInsetsCompat.Type.systemBars())
        }
      }
    }
  }
}
