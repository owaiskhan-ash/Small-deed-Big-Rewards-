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
import androidx.compose.runtime.LaunchedEffect
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
    
    val initialHadithId = intent?.getIntExtra(DailyHadithWorker.EXTRA_HADITH_ID, -1) ?: -1

    setContent {
      val viewModel: HadithViewModel = viewModel()

      LaunchedEffect(initialHadithId) {
        if (initialHadithId > 0) {
          viewModel.navigateTo(ActiveScreen.HADITH_DETAIL, detailId = initialHadithId)
        }
      }

      val themeStr by viewModel.theme.collectAsState()
      val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
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
    val hadithId = intent.getIntExtra(DailyHadithWorker.EXTRA_HADITH_ID, -1)
    if (hadithId > 0) {
      val viewModel = androidx.lifecycle.ViewModelProvider(this)[HadithViewModel::class.java]
      viewModel.navigateTo(ActiveScreen.HADITH_DETAIL, detailId = hadithId)
    }
  }
}
