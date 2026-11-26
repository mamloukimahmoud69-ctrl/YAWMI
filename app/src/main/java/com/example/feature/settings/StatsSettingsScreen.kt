package com.example.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.preferences.AppAppearanceState
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.AppThemeMode
import com.example.ui.theme.LocalYawmiSemanticColors

@Composable
fun StatsSettingsScreen(
  appearanceState: AppAppearanceState,
  onSelectThemeMode: (AppThemeMode) -> Unit,
  onSelectLanguage: (AppLanguage) -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalYawmiSemanticColors.current

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("screen_stats_settings"),
    contentPadding = PaddingValues(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Column(modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp)) {
        Text(
          text = stringResource(R.string.stats_settings_title),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, semantic.borderSubtle),
        shape = MaterialTheme.shapes.large,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Palette,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = stringResource(R.string.section_appearance),
              style = MaterialTheme.typography.titleMedium,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ThemeOptionChip(
              label = stringResource(R.string.theme_dark_charcoal),
              selected = appearanceState.themeMode == AppThemeMode.DARK,
              onClick = { onSelectThemeMode(AppThemeMode.DARK) },
              modifier = Modifier.weight(1f).testTag("theme_chip_dark"),
            )
            ThemeOptionChip(
              label = stringResource(R.string.theme_warm_light),
              selected = appearanceState.themeMode == AppThemeMode.LIGHT,
              onClick = { onSelectThemeMode(AppThemeMode.LIGHT) },
              modifier = Modifier.weight(1f).testTag("theme_chip_light"),
            )
            ThemeOptionChip(
              label = stringResource(R.string.theme_system),
              selected = appearanceState.themeMode == AppThemeMode.SYSTEM,
              onClick = { onSelectThemeMode(AppThemeMode.SYSTEM) },
              modifier = Modifier.weight(1f).testTag("theme_chip_system"),
            )
          }
        }
      }
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, semantic.borderSubtle),
        shape = MaterialTheme.shapes.large,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Language,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = stringResource(R.string.section_language),
              style = MaterialTheme.typography.titleMedium,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            ThemeOptionChip(
              label = stringResource(R.string.language_arabic_rtl),
              selected = appearanceState.language == AppLanguage.ARABIC,
              onClick = { onSelectLanguage(AppLanguage.ARABIC) },
              modifier = Modifier.weight(1f).testTag("lang_chip_ar"),
            )
            ThemeOptionChip(
              label = stringResource(R.string.language_english_ltr),
              selected = appearanceState.language == AppLanguage.ENGLISH,
              onClick = { onSelectLanguage(AppLanguage.ENGLISH) },
              modifier = Modifier.weight(1f).testTag("lang_chip_en"),
            )
          }
        }
      }
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, semantic.borderSubtle),
        shape = MaterialTheme.shapes.large,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Insights,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = stringResource(R.string.section_analytics_preview),
              style = MaterialTheme.typography.titleMedium,
            )
          }
          Text(
            text = stringResource(R.string.analytics_foundation_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun ThemeOptionChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
      )
    },
    colors =
      FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
      ),
    modifier = modifier.minimumInteractiveComponentSize(),
  )
}
