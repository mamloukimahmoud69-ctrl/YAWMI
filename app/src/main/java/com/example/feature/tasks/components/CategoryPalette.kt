package com.example.feature.tasks.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.LocalYawmiSemanticColors

object CategoryTokens {
  val COLOR_TOKENS = listOf("amber", "emerald", "sand", "coral", "slate")
  val ICON_TOKENS = listOf("folder", "work", "home", "health", "book")

  @Composable
  fun resolveColor(token: String): Color {
    val semantic = LocalYawmiSemanticColors.current
    return when (token.lowercase()) {
      "amber" -> MaterialTheme.colorScheme.primary
      "emerald" -> semantic.success
      "sand" -> MaterialTheme.colorScheme.secondary
      "coral" -> semantic.overdue
      "slate" -> MaterialTheme.colorScheme.onSurfaceVariant
      else -> MaterialTheme.colorScheme.primary
    }
  }

  fun resolveIcon(iconName: String): ImageVector =
    when (iconName.lowercase()) {
      "work" -> Icons.Outlined.WorkOutline
      "home" -> Icons.Outlined.Home
      "health" -> Icons.Outlined.FavoriteBorder
      "book" -> Icons.Outlined.Book
      else -> Icons.Outlined.Folder
    }
}
