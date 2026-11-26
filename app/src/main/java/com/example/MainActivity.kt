package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.di.AppContainer
import com.example.core.i18n.ProvideYawmiLocale
import com.example.core.preferences.AppAppearanceState
import com.example.presentation.navigation.YawmiShell
import com.example.ui.theme.YawmiTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val container = (application as YawmiApplication).container
    setContent { YawmiRootApp(container = container) }
  }
}

@Composable
fun YawmiRootApp(container: AppContainer) {
  val appearanceState by
    container.appearanceRepository.appearanceState.collectAsStateWithLifecycle(
      initialValue = AppAppearanceState()
    )
  val scope = rememberCoroutineScope()

  ProvideYawmiLocale(language = appearanceState.language) {
    YawmiTheme(themeMode = appearanceState.themeMode) {
      YawmiShell(
        appearanceState = appearanceState,
        localClock = container.localClock,
        taskRepository = container.taskRepository,
        taskUseCases = container.taskUseCases,
        categoryUseCases = container.categoryUseCases,
        onSelectThemeMode = { mode ->
          scope.launch { container.appearanceRepository.setThemeMode(mode) }
        },
        onSelectLanguage = { lang ->
          scope.launch { container.appearanceRepository.setLanguage(lang) }
        },
      )
    }
  }
}
