package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF34D399),       // Emerald Light
    secondary = Color(0xFFA8A29E),     // Stone Secondary
    tertiary = Color(0xFF818CF8),      // Indigo
    background = Color(0xFF141210),    // Very deep stone dark
    surface = Color(0xFF1C1917),       // Obsidian/Stone-900 surface
    onPrimary = Color(0xFF141210),
    onSecondary = Color(0xFFFAFAF8),
    onBackground = Color(0xFFFAFAF8),
    onSurface = Color(0xFFFAFAF8),
    surfaceVariant = Color(0xFF2E2A27),
    onSurfaceVariant = Color(0xFFD6D3D1), // Stone-300
    outline = Color(0xFF44403C)        // Stone-700
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF059669),       // Brand Emerald
    secondary = Color(0xFF78716C),     // Stone Secondary
    tertiary = Color(0xFF4F46E5),      // Indigo Accent
    background = Color(0xFFFAFAF8),    // Soft premium cream/warm white
    surface = Color(0xFFFFFFFF),       // Clean white card surface
    onPrimary = Color.White,
    onSecondary = Color(0xFF1C1917),
    onBackground = Color(0xFF1C1917),  // Deep charcoal/stone text
    onSurface = Color(0xFF1C1917),
    surfaceVariant = Color(0xFFF5F5F4), // Clean secondary cream surface
    onSurfaceVariant = Color(0xFF44403C), // Warm dark grey
    outline = Color(0xFFE7E5E4)        // Clean stone outline borders
  )

private val WhiteBrightColorScheme =
  lightColorScheme(
    primary = Color(0xFF000000),       // Clean absolute black
    secondary = Color(0xFF333333),     // Slate grey
    tertiary = Color(0xFF555555),      // Dark grey
    background = Color(0xFFFFFFFF),    // Pure white background
    surface = Color(0xFFFFFFFF),       // Pure white cards / surfaces
    onPrimary = Color.White,
    onSecondary = Color(0xFF000000),
    onBackground = Color(0xFF000000),  // Crisp pure black text
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFFAFAFA), // Extremely light grey
    onSurfaceVariant = Color(0xFF333333),
    outline = Color(0xFFE5E5E5)        // Thin borders in light grey
  )

@Composable
fun MyApplicationTheme(
  themeStr: String = "system",
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Set dynamicColor to false by default for custom 'Professional Polish' branding consistency
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      themeStr == "white" -> WhiteBrightColorScheme
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  // The XML theme can only guess the status-bar glyph colour from the *system*
  // theme. Because this app lets the user force light / dark / white
  // independently, re-apply the correct appearance whenever the resolved theme
  // changes so dark glyphs never sit on the near-black background (or vice
  // versa).
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = !darkTheme
      }
    }
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
