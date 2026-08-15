package com.runtrack.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.runtrack.app.domain.WorkoutType

private object Routes {
    const val HOME = "home"
    const val SETUP = "workout/setup"
    const val ACTIVE = "workout/active"
    const val PAUSED = "workout/paused"
    const val FINISH = "workout/finish"
    const val HISTORY = "history"
    const val STATS = "stats/overview"
    const val CHARTS = "stats/charts"
    const val RECORDS = "stats/records"
    const val COMPARISON = "stats/comparison"
    const val PROFILE = "profile"
    const val CALENDAR = "profile/calendar"
    const val ALL_ROUTES = "profile/routes"
    const val SETTINGS = "profile/settings"
    const val CONNECTIONS = "profile/connections"
    const val RESULT_OVERVIEW = "result/{workoutId}/overview"
    const val RESULT_MAP = "result/{workoutId}/map"
    const val RESULT_ROUTE = "result/{workoutId}/route"
    const val DETAILS = "result/{workoutId}/details"
    const val EXPORT = "result/{workoutId}/export"

    fun result(id: String, suffix: String) = "result/$id/$suffix"
}

@Composable
fun RunTrackPrototypeApp(viewModel: RunTrackViewModel = viewModel()) {
    val nav = rememberNavController()
    val selectedId by viewModel.selectedWorkoutId.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tracking by viewModel.liveTracking.collectAsStateWithLifecycle()
    val view = LocalView.current
    val keepScreen = settings.keepScreenOn && tracking?.status == com.runtrack.app.domain.WorkoutStatus.ACTIVE

    DisposableEffect(keepScreen) {
        view.keepScreenOn = keepScreen
        onDispose { view.keepScreenOn = false }
    }

    fun routeForIndex(index: Int): String? = when (index) {
        0 -> Routes.HOME
        1 -> Routes.SETUP
        2 -> Routes.ACTIVE
        3 -> Routes.PAUSED
        4 -> Routes.FINISH
        5 -> selectedId?.let { Routes.result(it, "overview") }
        6 -> selectedId?.let { Routes.result(it, "map") }
        7 -> selectedId?.let { Routes.result(it, "route") }
        8 -> selectedId?.let { Routes.result(it, "details") }
        9 -> Routes.HISTORY
        10 -> Routes.STATS
        11 -> Routes.CHARTS
        12 -> Routes.RECORDS
        13 -> Routes.COMPARISON
        14 -> Routes.PROFILE
        15 -> Routes.CALENDAR
        16 -> Routes.ALL_ROUTES
        17 -> Routes.SETTINGS
        18 -> Routes.CONNECTIONS
        19 -> selectedId?.let { Routes.result(it, "export") }
        else -> null
    }

    fun navigateIndex(index: Int) {
        val target = routeForIndex(index) ?: return
        nav.navigate(target) {
            launchSingleTop = true
            if (index == 0 || index == 9 || index == 10 || index == 14) {
                popUpTo(Routes.HOME) { saveState = true }
                restoreState = true
            }
        }
    }

    LaunchedEffect(tracking?.status, selectedId) {
        if (tracking?.status == com.runtrack.app.domain.WorkoutStatus.FINISHING && selectedId != null) {
            nav.navigate(Routes.FINISH) { launchSingleTop = true }
        }
    }

    MaterialTheme {
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                NativeMainScreen(
                    viewModel = viewModel,
                    onQuickStart = { type -> viewModel.chooseWorkoutType(type); nav.navigate(Routes.SETUP) },
                    onHistory = { navigateIndex(9) },
                    onStats = { navigateIndex(10) },
                    onProfile = { navigateIndex(14) },
                    onSettings = { navigateIndex(17) },
                    onOpenWorkout = { id -> viewModel.selectWorkout(id); nav.navigate(Routes.result(id, "overview")) },
                )
            }
            composable(Routes.SETUP) {
                NativeRemainingScreen(1, viewModel) { index ->
                    if (index == 0) {
                        if (!nav.popBackStack()) {
                            nav.navigate(Routes.HOME) { launchSingleTop = true }
                        }
                    } else {
                        navigateIndex(index)
                    }
                }
            }
            composable(Routes.ACTIVE) { NativeRemainingScreen(2, viewModel, ::navigateIndex) }
            composable(Routes.PAUSED) { NativeRemainingScreen(3, viewModel, ::navigateIndex) }
            composable(Routes.FINISH) {
                NativeRemainingScreen(4, viewModel, ::navigateIndex) { id ->
                    viewModel.selectWorkout(id)
                    nav.navigate(Routes.result(id, "overview")) {
                        popUpTo(Routes.HOME)
                        launchSingleTop = true
                    }
                }
            }
            composable(Routes.HISTORY) {
                NativeHistoryScreen(viewModel, { navigateIndex(0) }, { navigateIndex(10) }, { navigateIndex(14) }) { id ->
                    viewModel.selectWorkout(id); nav.navigate(Routes.result(id, "overview"))
                }
            }
            composable(Routes.STATS) { NativeRemainingScreen(10, viewModel, ::navigateIndex) }
            composable(Routes.CHARTS) { NativeRemainingScreen(11, viewModel, ::navigateIndex) }
            composable(Routes.RECORDS) { NativeRemainingScreen(12, viewModel, ::navigateIndex) }
            composable(Routes.COMPARISON) { NativeRemainingScreen(13, viewModel, ::navigateIndex) }
            composable(Routes.PROFILE) { NativeRemainingScreen(14, viewModel, ::navigateIndex) }
            composable(Routes.CALENDAR) { NativeRemainingScreen(15, viewModel, ::navigateIndex) }
            composable(Routes.ALL_ROUTES) { NativeRemainingScreen(16, viewModel, ::navigateIndex) }
            composable(Routes.SETTINGS) { NativeRemainingScreen(17, viewModel, ::navigateIndex) }
            composable(Routes.CONNECTIONS) { NativeRemainingScreen(18, viewModel, ::navigateIndex) }

            listOf(
                Routes.RESULT_OVERVIEW to 5,
                Routes.RESULT_MAP to 6,
                Routes.RESULT_ROUTE to 7,
                Routes.DETAILS to 8,
                Routes.EXPORT to 19,
            ).forEach { (route, index) ->
                composable(route, arguments = listOf(navArgument("workoutId") { type = NavType.StringType })) { entry ->
                    val id = requireNotNull(entry.arguments?.getString("workoutId"))
                    LaunchedEffect(id) { viewModel.selectWorkout(id) }
                    NativeRemainingScreen(index, viewModel, ::navigateIndex)
                }
            }
        }

        if (tracking?.status == com.runtrack.app.domain.WorkoutStatus.RECOVERY_REQUIRED) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Незавершённая тренировка") },
                text = { Text("RunTrack обнаружил сохранённую сессию, но не может доказать, что GPS-служба продолжала работать. Продолжите запись или завершите сохранённое состояние.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.resumeRecoveredWorkout {
                            nav.navigate(Routes.ACTIVE) { launchSingleTop = true }
                        }
                    }) { Text("Продолжить") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.requestFinish {
                            nav.navigate(Routes.FINISH) { launchSingleTop = true }
                        }
                    }) { Text("Завершить") }
                },
            )
        }
    }
}
