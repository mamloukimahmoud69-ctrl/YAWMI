package com.example.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable object TodayRoute

@Serializable object TasksRoute

@Serializable data class TaskDetailRoute(val taskId: String)

@Serializable object CalendarRoute

@Serializable object HabitsGoalsRoute

@Serializable object StatsSettingsRoute
