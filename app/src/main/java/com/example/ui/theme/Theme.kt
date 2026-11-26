package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.core.preferences.AppThemeMode

private val YawmiDarkColorScheme =
  darkColorScheme(
    primary = DarkAmberPrimary,
    onPrimary = DarkOnAmber,
    primaryContainer = DarkAmberContainer,
    onPrimaryContainer = DarkOnAmberContainer,
    secondary = DarkSecondarySand,
    onSecondary = DarkOnAmber,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = DarkTextPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceElevated,
    surfaceContainerHigh = DarkSurfaceHighest,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = DarkOverdue,
    onError = DarkOnAmber,
  )

private val YawmiLightColorScheme =
  lightColorScheme(
    primary = LightAmberPrimary,
    onPrimary = LightOnAmber,
    primaryContainer = LightAmberContainer,
    onPrimaryContainer = LightOnAmberContainer,
    secondary = LightSecondaryBronze,
    onSecondary = LightOnAmber,
    secondaryContainer = LightSurfaceElevated,
    onSecondaryContainer = LightTextPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceElevated,
    surfaceContainerHigh = LightSurfaceHighest,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = LightOverdue,
    onError = LightOnAmber,
  )

@Immutable
data class YawmiSemanticColors(
  val success: Color,
  val overdue: Color,
  val borderSubtle: Color,
)

val LocalYawmiSemanticColors =
  staticCompositionLocalOf {
    YawmiSemanticColors(
      success = DarkSuccess,
      overdue = DarkOverdue,
      borderSubtle = DarkBorder,
    )
  }

@Composable
fun YawmiTheme(
  themeMode: AppThemeMode = AppThemeMode.DARK,
  content: @Composable () -> Unit,
) {
  val useDarkTheme =
    when (themeMode) {
      AppThemeMode.DARK -> true
      AppThemeMode.LIGHT -> false
      AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

  val colorScheme = if (useDarkTheme) YawmiDarkColorScheme else YawmiLightColorScheme
  val semanticColors =
    if (useDarkTheme) {
      YawmiSemanticColors(
        success = DarkSuccess,
        overdue = DarkOverdue,
        borderSubtle = DarkBorder,
      )
    } else {
      YawmiSemanticColors(
        success = LightSuccess,
        overdue = LightOverdue,
        borderSubtle = LightBorder,
      )
    }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
      }
    }
  }

  androidx.compose.runtime.CompositionLocalProvider(
    LocalYawmiSemanticColors provides semanticColors
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = YawmiTypography,
      shapes = YawmiShapes,
      content = content,
    )
  }
}
