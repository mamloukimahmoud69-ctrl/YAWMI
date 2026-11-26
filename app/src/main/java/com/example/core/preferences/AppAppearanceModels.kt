package com.example.core.preferences

import kotlinx.coroutines.flow.Flow

enum class AppThemeMode {
  DARK,
  LIGHT,
  SYSTEM,
}

enum class AppLanguage(val tag: String, val isRtl: Boolean) {
  ARABIC(tag = "ar", isRtl = true),
  ENGLISH(tag = "en", isRtl = false),
}

data class AppAppearanceState(
  val themeMode: AppThemeMode = AppThemeMode.DARK,
  val language: AppLanguage = AppLanguage.ARABIC,
)

interface AppAppearanceRepository {
  val appearanceState: Flow<AppAppearanceState>

  suspend fun setThemeMode(mode: AppThemeMode)

  suspend fun setLanguage(language: AppLanguage)
}
