package com.example.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.core.preferences.AppAppearanceState
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.AppThemeMode
import com.example.core.time.LocalClock
import com.example.domain.category.CategoryUseCases
import com.example.domain.repository.TaskRepository
import com.example.domain.task.TaskUseCases
import com.example.feature.calendar.CalendarScreen
import com.example.feature.dashboard.TodayScreen
import com.example.feature.habits.HabitsGoalsScreen
import com.example.feature.settings.StatsSettingsScreen
import com.example.feature.tasks.TasksScreen
import com.example.feature.tasks.detail.TaskDetailScreen
import kotlinx.coroutines.launch

private data class TopDestination(
  val route: Any,
  @param:StringRes val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
  val matches: (androidx.navigation.NavDestination?) -> Boolean,
)

@Composable
fun YawmiShell(
  appearanceState: AppAppearanceState,
  localClock: LocalClock,
  taskRepository: TaskRepository,
  taskUseCases: TaskUseCases,
  categoryUseCases: CategoryUseCases,
  onSelectThemeMode: (AppThemeMode) -> Unit,
  onSelectLanguage: (AppLanguage) -> Unit,
  modifier: Modifier = Modifier,
) {
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = backStackEntry?.destination
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  val destinations = remember {
    listOf(
      TopDestination(
        route = TodayRoute,
        labelRes = R.string.nav_today,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_today",
        matches = { dest -> dest?.hierarchy?.any { it.hasRoute<TodayRoute>() } == true },
      ),
      TopDestination(
        route = TasksRoute,
        labelRes = R.string.nav_tasks,
        selectedIcon = Icons.Filled.CheckCircle,
        unselectedIcon = Icons.Outlined.CheckCircle,
        testTag = "nav_tasks",
        matches = { dest -> dest?.hierarchy?.any { it.hasRoute<TasksRoute>() } == true },
      ),
      TopDestination(
        route = CalendarRoute,
        labelRes = R.string.nav_calendar,
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
        testTag = "nav_calendar",
        matches = { dest -> dest?.hierarchy?.any { it.hasRoute<CalendarRoute>() } == true },
      ),
      TopDestination(
        route = HabitsGoalsRoute,
        labelRes = R.string.nav_habits_goals,
        selectedIcon = Icons.Filled.TrackChanges,
        unselectedIcon = Icons.Outlined.TrackChanges,
        testTag = "nav_habits_goals",
        matches = { dest -> dest?.hierarchy?.any { it.hasRoute<HabitsGoalsRoute>() } == true },
      ),
      TopDestination(
        route = StatsSettingsRoute,
        labelRes = R.string.nav_stats_settings,
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        testTag = "nav_stats_settings",
        matches = { dest -> dest?.hierarchy?.any { it.hasRoute<StatsSettingsRoute>() } == true },
      ),
    )
  }

  val navigateTopLevel: (Any) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp
    val showTopLevelChrome =
      currentDestination?.hierarchy?.any { it.hasRoute<TaskDetailRoute>() } != true

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      contentWindowInsets = WindowInsets.safeDrawing,
      containerColor = MaterialTheme.colorScheme.background,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (showTopLevelChrome && !useRail) {
          NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            destinations.forEach { dest ->
              val selected = dest.matches(currentDestination)
              val label = stringResource(dest.labelRes)
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTopLevel(dest.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = { Text(text = label, maxLines = 1) },
                colors =
                  NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  ),
                modifier = Modifier.testTag(dest.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (showTopLevelChrome && useRail) {
          NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
            destinations.forEach { dest ->
              val selected = dest.matches(currentDestination)
              val label = stringResource(dest.labelRes)
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTopLevel(dest.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = { Text(text = label, maxLines = 1) },
                colors =
                  NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  ),
                modifier = Modifier.testTag(dest.testTag),
              )
            }
          }
        }

        NavHost(
          navController = navController,
          startDestination = TodayRoute,
          modifier = Modifier.weight(1f).fillMaxSize(),
        ) {
          composable<TodayRoute> {
            TodayScreen(
              language = appearanceState.language,
              taskRepository = taskRepository,
              taskUseCases = taskUseCases,
              categoryUseCases = categoryUseCases,
              localClock = localClock,
              onOpenTaskDetail = { taskId -> navController.navigate(TaskDetailRoute(taskId)) },
              onNavigateToTasks = { navigateTopLevel(TasksRoute) },
              onShowSnackbar = { msg ->
                coroutineScope.launch {
                  snackbarHostState.currentSnackbarData?.dismiss()
                  snackbarHostState.showSnackbar(msg)
                }
              },
            )
          }
          composable<TasksRoute> {
            TasksScreen(
              taskRepository = taskRepository,
              taskUseCases = taskUseCases,
              categoryUseCases = categoryUseCases,
              localClock = localClock,
              onShowSnackbar = { msg ->
                coroutineScope.launch {
                  snackbarHostState.currentSnackbarData?.dismiss()
                  snackbarHostState.showSnackbar(msg)
                }
              },
              onOpenTaskDetail = { taskId ->
                navController.navigate(TaskDetailRoute(taskId))
              },
            )
          }
          composable<TaskDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<TaskDetailRoute>()
            TaskDetailScreen(
              taskId = route.taskId,
              taskRepository = taskRepository,
              taskUseCases = taskUseCases,
              categoryUseCases = categoryUseCases,
              localClock = localClock,
              onNavigateBack = { navController.popBackStack() },
              onShowSnackbar = { msg ->
                coroutineScope.launch {
                  snackbarHostState.currentSnackbarData?.dismiss()
                  snackbarHostState.showSnackbar(msg)
                }
              },
            )
          }
          composable<CalendarRoute> {
            CalendarScreen(
              language = appearanceState.language,
              taskRepository = taskRepository,
              taskUseCases = taskUseCases,
              categoryUseCases = categoryUseCases,
              localClock = localClock,
              onOpenTaskDetail = { taskId -> navController.navigate(TaskDetailRoute(taskId)) },
              onShowSnackbar = { msg ->
                coroutineScope.launch {
                  snackbarHostState.currentSnackbarData?.dismiss()
                  snackbarHostState.showSnackbar(msg)
                }
              },
            )
          }
          composable<HabitsGoalsRoute> { HabitsGoalsScreen() }
          composable<StatsSettingsRoute> {
            StatsSettingsScreen(
              appearanceState = appearanceState,
              onSelectThemeMode = onSelectThemeMode,
              onSelectLanguage = onSelectLanguage,
            )
          }
        }
      }
    }
  }
}
