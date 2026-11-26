package com.example.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "yawmi_preferences"
)

class DataStoreAppAppearanceRepository(private val context: Context) : AppAppearanceRepository {

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val LANGUAGE = stringPreferencesKey("language")
  }

  override val appearanceState: Flow<AppAppearanceState> =
    context.appearanceDataStore.data
      .catch { e ->
        if (e is IOException) {
          emit(androidx.datastore.preferences.core.emptyPreferences())
        } else {
          throw e
        }
      }
      .map { prefs ->
        val theme =
          prefs[Keys.THEME_MODE]?.let { raw ->
            AppThemeMode.entries.firstOrNull { it.name == raw }
          } ?: AppThemeMode.DARK
        val language =
          prefs[Keys.LANGUAGE]?.let { raw ->
            AppLanguage.entries.firstOrNull { it.name == raw }
          } ?: AppLanguage.ARABIC
        AppAppearanceState(themeMode = theme, language = language)
      }

  override suspend fun setThemeMode(mode: AppThemeMode) {
    context.appearanceDataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.name }
  }

  override suspend fun setLanguage(language: AppLanguage) {
    context.appearanceDataStore.edit { prefs -> prefs[Keys.LANGUAGE] = language.name }
  }
}
