package com.example.core.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.core.preferences.AppLanguage
import java.util.Locale

fun Context.createLocalizedContext(language: AppLanguage): Context {
  val locale = Locale.forLanguageTag(language.tag)
  val config = Configuration(resources.configuration)
  config.setLocale(locale)
  config.setLayoutDirection(locale)
  return createConfigurationContext(config)
}

@Composable
fun ProvideYawmiLocale(
  language: AppLanguage,
  content: @Composable () -> Unit,
) {
  val baseContext = LocalContext.current
  val localizedContext =
    remember(baseContext, language) { baseContext.createLocalizedContext(language) }
  val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

  CompositionLocalProvider(
    LocalContext provides localizedContext,
    LocalLayoutDirection provides layoutDirection,
    content = content,
  )
}
