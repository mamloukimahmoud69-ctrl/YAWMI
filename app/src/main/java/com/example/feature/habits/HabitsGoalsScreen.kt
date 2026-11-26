package com.example.feature.habits

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.feature.common.FoundationDestinationPlaceholder

@Composable
fun HabitsGoalsScreen(modifier: Modifier = Modifier) {
  FoundationDestinationPlaceholder(
    title = stringResource(R.string.habits_goals_title),
    description = stringResource(R.string.habits_goals_foundation_desc),
    icon = Icons.Outlined.TrackChanges,
    modifier = modifier.testTag("screen_habits_goals"),
  )
}
