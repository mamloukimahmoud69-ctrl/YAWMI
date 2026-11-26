package com.example.feature.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalYawmiSemanticColors

@Composable
fun FoundationDestinationPlaceholder(
  title: String,
  description: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalYawmiSemanticColors.current
  Box(
    modifier = modifier.fillMaxSize().padding(20.dp),
    contentAlignment = Alignment.Center,
  ) {
    Card(
      modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, semantic.borderSubtle),
      shape = MaterialTheme.shapes.large,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.size(56.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(28.dp),
            )
          }
        }
        Text(
          text = title,
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
        )
        Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = MaterialTheme.shapes.small,
        ) {
          Text(
            text = stringResource(R.string.foundation_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          )
        }
      }
    }
  }
}
