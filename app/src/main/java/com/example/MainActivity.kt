package com.example

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.screens.MainScreenContainer
import com.example.viewmodel.ActiveScreen
import com.example.viewmodel.HadithViewModel
import com.example.worker.DailyHadithWorker

class MainActivity : ComponentActivity() {

  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { _ -> }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Prompt for notification permission on Android 13+ (API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(
          this,
          android.Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
      ) {
        requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
      }
    }

    // Deep link from a daily-hadith notification.
    //
    // Only honoured on a *fresh* launch (savedInstanceState == null). On a
    // configuration change (rotation, split-screen resize, theme change) the
    // Activity is recreated with the same launching Intent still attached, but
    // the ViewModel survives and already holds the user's real navigation
    // state -- re-applying the extra there would yank the user back to the
    // notification's hadith and discard wherever they had navigated to.
    if (savedInstanceState == null) {
      val initialHadithId =
        intent?.getIntExtra(DailyHadithWorker.EXTRA_HADITH_ID, -1) ?: -1
      if (initialHadithId > 0) {
        // The ViewModel is activity-scoped and created lazily; resolving it
        // here (rather than inside a LaunchedEffect) applies the navigation
        // exactly once, before the first composition.
        val viewModel = androidx.lifecycle.ViewModelProvider(this)[HadithViewModel::class.java]
        viewModel.navigateTo(ActiveScreen.HADITH_DETAIL, detailId = initialHadithId)
      }
    }

    setContent {
      val viewModel: HadithViewModel = viewModel()

      val themeStr by viewModel.theme.collectAsState()
      val isSystemDark = isSystemInDarkTheme()
      val darkTheme = when (themeStr) {
        "dark" -> true
        "light" -> false
        "white" -> false
        else -> isSystemDark
      }
      MyApplicationTheme(themeStr = themeStr, darkTheme = darkTheme) {
        MainScreenContainer(viewModel = viewModel)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    // Reached only when the task is already live (singleTop + SINGLE_TOP flag),
    // so this is a genuine new notification tap, not a recreation.
    val hadithId = intent.getIntExtra(DailyHadithWorker.EXTRA_HADITH_ID, -1)
    if (hadithId > 0) {
      val viewModel = androidx.lifecycle.ViewModelProvider(this)[HadithViewModel::class.java]
      viewModel.navigateTo(ActiveScreen.HADITH_DETAIL, detailId = hadithId)
    }
  }
}
