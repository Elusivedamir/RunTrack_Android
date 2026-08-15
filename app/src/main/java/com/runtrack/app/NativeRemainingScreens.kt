package com.runtrack.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.provider.Settings
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runtrack.app.data.RoutePointEntity
import com.runtrack.app.data.WeatherSnapshotEntity
import com.runtrack.app.data.WorkoutEntity
import com.runtrack.app.data.WorkoutWithRoute
import com.runtrack.app.domain.*
import com.runtrack.app.export.*
import com.runtrack.app.health.HealthConnectAvailability
import com.runtrack.app.maps.RunTrackRouteMap
import com.runtrack.app.tracking.BleHeartRateState
import com.runtrack.app.weather.WeatherFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

private val RtBg = Color(0xFF07131B)
private val RtSurface = Color(0xFF101C25)
private val RtSurface2 = Color(0xFF14222C)
private val RtGreen = Color(0xFF62D72F)
private val RtMuted = Color(0xFF8E9AA3)
private val RtText2 = Color(0xFFB8C2C9)
private val RtYellow = Color(0xFFF4B400)
private val RtBlue = Color(0xFF42A5F5)
private val RtRed = Color(0xFFFF6B6B)

private fun formatGoalDistance(meters: Double): String =
    String.format(Locale.getDefault(), "%.2f км", meters / 1000.0)

private fun formatGoalDuration(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

@Composable
fun NativeRemainingScreen(
    index: Int,
    viewModel: RunTrackViewModel,
    onNavigate: (Int) -> Unit,
    onWorkoutSaved: (String) -> Unit = {},
) {
    when (index) {
        1 -> WorkoutSetupScreen(viewModel, onNavigate)
        2 -> ActiveWorkoutScreen(viewModel, onNavigate)
        3 -> PausedWorkoutScreen(viewModel, onNavigate)
        4 -> FinishWorkoutScreen(viewModel, onNavigate, onWorkoutSaved)
        5 -> ResultOverviewScreen(viewModel, onNavigate)
        6 -> ResultMapScreen(viewModel, onNavigate)
        7 -> ResultRouteScreen(viewModel, onNavigate)
        8 -> WorkoutDetailsScreen(viewModel, onNavigate)
        10 -> StatsOverviewScreen(viewModel, onNavigate)
        11 -> StatsChartsScreen(viewModel, onNavigate)
        12 -> RecordsScreen(viewModel, onNavigate)
        13 -> ComparisonScreen(viewModel, onNavigate)
        14 -> ProfileScreen(viewModel, onNavigate)
        15 -> CalendarScreen(viewModel, onNavigate)
        16 -> AllRoutesScreen(viewModel, onNavigate)
        17 -> SettingsScreen(viewModel, onNavigate)
        18 -> ConnectionsScreen(viewModel, onNavigate)
        19 -> ExportScreen(viewModel, onNavigate)
        else -> MissingDataPage("Экран", onNavigate)
    }
}

@Composable
private fun NativePage(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    bottomNavSelected: Int? = null,
    onNavigate: (Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RtBg)
    ) {
        NativeTopBar(title = title, subtitle = subtitle, onBack = onBack)
        content()
        if (bottomNavSelected != null) {
            AppBottomBar(selected = bottomNavSelected, onNavigate = onNavigate)
        }
    }
}

@Composable
private fun NativeTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = "Назад",
                    tint = RtText2,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Spacer(Modifier.width(6.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = RtMuted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun AppBottomBar(selected: Int, onNavigate: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(Color(0xFF0B151C))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppNavItem("Главная", Icons.Outlined.Home, selected == 0) { onNavigate(0) }
        AppNavItem("История", Icons.Outlined.History, selected == 1) { onNavigate(9) }
        AppNavItem("Статистика", Icons.Outlined.BarChart, selected == 2) { onNavigate(10) }
        AppNavItem("Профиль", Icons.Outlined.Person, selected == 3) { onNavigate(14) }
    }
}

@Composable
private fun RowScope.AppNavItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (selected) RtGreen else RtMuted,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = title,
            color = if (selected) RtGreen else RtMuted,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RtGreen, contentColor = RtBg),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = RtBg)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val accent = if (danger) RtRed else RtText2
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
        else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = Color.White,
) {
    Column(
        modifier = modifier
            .background(RtSurface, RoundedCornerShape(13.dp))
            .padding(12.dp),
    ) {
        Text(label, color = RtMuted, fontSize = 10.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    value: String,
    accent: Color = RtGreen,
    onClick: (() -> Unit)? = null,
) {
    val base = Modifier
        .fillMaxWidth()
        .background(RtSurface, RoundedCornerShape(13.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(12.dp)

    Row(modifier = base, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(accent.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(value, color = RtMuted, fontSize = 10.sp)
        }
        if (onClick != null) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = RtMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun WorkoutSetupScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val type by viewModel.workoutType.collectAsStateWithLifecycle()
    val goal by viewModel.goal.collectAsStateWithLifecycle()
    val gps by viewModel.gpsReadiness.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val stepSensorAvailable = remember(context) {
        val manager = context.getSystemService(SensorManager::class.java)
        manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null ||
            manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onPermissionStateChanged()
    }

    var distanceMetersDraft by rememberSaveable {
        mutableStateOf(goal.distanceMeters?.takeIf { it.isFinite() && it > 0.0 } ?: 5_000.0)
    }
    var durationMillisDraft by rememberSaveable {
        mutableStateOf(goal.durationMillis?.takeIf { it > 0L } ?: 45L * 60_000L)
    }
    var goalEditor by rememberSaveable { mutableStateOf<String?>(null) }
    var goalDraft by rememberSaveable { mutableStateOf("") }
    var goalError by rememberSaveable { mutableStateOf<String?>(null) }

    var startCountdownActive by remember { mutableStateOf(false) }
    var startCountdownSeconds by remember { mutableIntStateOf(0) }
    var startCountdownStartedAtElapsed by remember { mutableLongStateOf(0L) }
    var startCountdownDeadlineElapsed by remember { mutableLongStateOf(0L) }
    var allowWeakGpsStart by remember { mutableStateOf(false) }
    var weakGpsWarning by remember { mutableStateOf(false) }
    var pendingStepPermissionStart by remember { mutableStateOf<Boolean?>(null) }

    fun beginStartCountdown(allowWeakGps: Boolean) {
        val now = SystemClock.elapsedRealtime()
        allowWeakGpsStart = allowWeakGps
        startCountdownStartedAtElapsed = now
        startCountdownDeadlineElapsed = now + START_COUNTDOWN_INITIAL_MILLIS
        startCountdownSeconds = 10
        startCountdownActive = true
    }

    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        val pendingAllowWeakGps = pendingStepPermissionStart
        pendingStepPermissionStart = null
        if (pendingAllowWeakGps != null) beginStartCountdown(pendingAllowWeakGps)
    }

    fun beginStartWithOptionalStepPermission(allowWeakGps: Boolean) {
        val needsPermission =
            type != WorkoutType.BIKE &&
                stepSensorAvailable &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                ) != PackageManager.PERMISSION_GRANTED

        if (needsPermission) {
            pendingStepPermissionStart = allowWeakGps
            activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            beginStartCountdown(allowWeakGps)
        }
    }

    DisposableEffect(viewModel) {
        viewModel.startGpsReadinessMonitoring()
        onDispose { viewModel.stopGpsReadinessMonitoring() }
    }
    LaunchedEffect(type) { viewModel.onPermissionStateChanged() }

    val gpsTitle = when {
        !gps.finePermissionGranted -> "Нужно разрешение GPS"
        !gps.locationEnabled -> "Геолокация выключена"
        gps.ready -> "GPS готов"
        gps.fixAttempted && gps.accuracyMeters != null -> "GPS неточен"
        gps.fixAttempted -> "GPS-сигнал не найден"
        else -> "Поиск GPS…"
    }
    val gpsSubtitle = when {
        gps.ready && gps.accuracyMeters != null -> "Точность ±${gps.accuracyMeters!!.toInt()} м"
        !gps.finePermissionGranted -> "Разрешите точную геолокацию"
        !gps.locationEnabled -> "Включите Location в настройках Android"
        gps.fixAttempted && gps.accuracyMeters != null ->
            "Точность ±${gps.accuracyMeters!!.toInt()} м · продолжаем поиск"
        gps.fixAttempted -> "Нет свежей точки · продолжаем поиск автоматически"
        else -> "Получаем свежую точку позиции"
    }
    val busy = operation is UiOperationState.Running

    LaunchedEffect(startCountdownActive) {
        if (!startCountdownActive) return@LaunchedEffect

        while (startCountdownActive) {
            val now = SystemClock.elapsedRealtime()
            val remainingMillis = startCountdownDeadlineElapsed - now

            if (remainingMillis <= 0L) {
                val manualWeakGpsOverride = allowWeakGpsStart
                startCountdownSeconds = 0
                startCountdownActive = false
                allowWeakGpsStart = false
                viewModel.startWorkout(allowWeakGps = manualWeakGpsOverride) { onNavigate(2) }
                break
            }

            startCountdownSeconds =
                ((remainingMillis + 999L) / 1_000L).toInt().coerceIn(1, 300)
            delay(100L)
        }
    }

    val countdownLabel = if (startCountdownActive) {
        val minutes = startCountdownSeconds / 60
        val seconds = startCountdownSeconds % 60
        "Старт через %02d:%02d · +5 сек".format(minutes, seconds)
    } else {
        "Начать тренировку"
    }

    NativePage(title = "Новая тренировка", subtitle = "Настрой параметры перед стартом", onBack = { onNavigate(0) }, onNavigate = onNavigate) {
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            SectionTitle("Тип тренировки")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ActivityChoice("Бег", Icons.Outlined.DirectionsRun, RtGreen, type == WorkoutType.RUN, Modifier.weight(1f)) { viewModel.chooseWorkoutType(WorkoutType.RUN) }
                ActivityChoice("Ходьба", Icons.Outlined.DirectionsWalk, RtYellow, type == WorkoutType.WALK, Modifier.weight(1f)) { viewModel.chooseWorkoutType(WorkoutType.WALK) }
                ActivityChoice("Велосипед", Icons.Outlined.DirectionsBike, RtBlue, type == WorkoutType.BIKE, Modifier.weight(1f)) { viewModel.chooseWorkoutType(WorkoutType.BIKE) }
            }
            Spacer(Modifier.height(18.dp)); SectionTitle("Цель"); Spacer(Modifier.height(9.dp))
            GoalChoice("Без цели", "Свободная тренировка", goal.kind == GoalKind.NONE) { viewModel.clearGoal() }
            Spacer(Modifier.height(8.dp))
            GoalChoice(
                formatGoalDistance(distanceMetersDraft),
                "Дистанция",
                goal.kind == GoalKind.DISTANCE,
            ) {
                goalDraft = String.format(Locale.US, "%.2f", distanceMetersDraft / 1000.0)
                    .trimEnd('0')
                    .trimEnd('.')
                goalError = null
                goalEditor = "distance"
            }
            Spacer(Modifier.height(8.dp))
            GoalChoice(
                formatGoalDuration(durationMillisDraft),
                "Время",
                goal.kind == GoalKind.DURATION,
            ) {
                goalDraft = (durationMillisDraft / 60_000L).coerceAtLeast(1L).toString()
                goalError = null
                goalEditor = "duration"
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().background(RtSurface, RoundedCornerShape(13.dp)).clickable { viewModel.refreshGpsReadiness() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background((if (gps.ready) RtGreen else RtYellow).copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.MyLocation, null, tint = if (gps.ready) RtGreen else RtYellow, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(gpsTitle, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(gpsSubtitle, color = RtMuted, fontSize = 10.sp)
                }
                Icon(if (gps.ready) Icons.Outlined.CheckCircle else Icons.Outlined.Refresh, null, tint = if (gps.ready) RtGreen else RtMuted, modifier = Modifier.size(20.dp))
            }
            if (operation is UiOperationState.Error) {
                Spacer(Modifier.height(8.dp))
                Text((operation as UiOperationState.Error).message, color = RtRed, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))
            PrimaryAction(
                text = countdownLabel,
                icon = if (startCountdownActive) Icons.Outlined.Timer else Icons.Outlined.PlayArrow,
                loading = busy,
                onClick = {
                    if (startCountdownActive) {
                        val maxDeadline =
                            startCountdownStartedAtElapsed + START_COUNTDOWN_MAX_MILLIS
                        startCountdownDeadlineElapsed =
                            (startCountdownDeadlineElapsed + START_COUNTDOWN_INCREMENT_MILLIS)
                                .coerceAtMost(maxDeadline)

                        val remainingMillis =
                            (startCountdownDeadlineElapsed - SystemClock.elapsedRealtime())
                                .coerceAtLeast(0L)
                        startCountdownSeconds =
                            ((remainingMillis + 999L) / 1_000L).toInt().coerceIn(0, 300)
                    } else if (!gps.finePermissionGranted) {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    } else if (!gps.locationEnabled) {
                        viewModel.refreshGpsReadiness()
                    } else if (!gps.ready) {
                        weakGpsWarning = true
                    } else {
                        beginStartWithOptionalStepPermission(allowWeakGps = false)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
        }
    }

    if (weakGpsWarning) {
        val accuracyText = gps.accuracyMeters?.let { "Текущая точность около ±${it.toInt()} м. " } ?: ""
        AlertDialog(
            onDismissRequest = { weakGpsWarning = false },
            title = { Text("Слабый GPS-сигнал") },
            text = {
                Text(
                    "${accuracyText}Можно начать тренировку сейчас, но маршрут и расстояние " +
                        "могут появиться только после получения достаточно точной GPS-точки."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        weakGpsWarning = false
                        beginStartWithOptionalStepPermission(allowWeakGps = true)
                    }
                ) {
                    Text("Начать всё равно")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        weakGpsWarning = false
                        viewModel.refreshGpsReadiness()
                    }
                ) {
                    Text("Подождать GPS")
                }
            },
        )
    }

    if (goalEditor != null) {
        val editingDistance = goalEditor == "distance"
        AlertDialog(
            onDismissRequest = {
                goalEditor = null
                goalError = null
            },
            title = {
                Text(if (editingDistance) "Цель по дистанции" else "Цель по времени")
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = goalDraft,
                        onValueChange = {
                            goalDraft = it
                            goalError = null
                        },
                        label = { Text(if (editingDistance) "Километры" else "Минуты") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (editingDistance) KeyboardType.Decimal else KeyboardType.Number,
                        ),
                    )
                    goalError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = RtRed, fontSize = 10.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editingDistance) {
                        val km = goalDraft.trim().replace(',', '.').toDoubleOrNull()
                        if (km == null || !km.isFinite() || km <= 0.0) {
                            goalError = "Введите дистанцию больше 0 км"
                        } else {
                            val meters = km * 1000.0
                            if (!meters.isFinite()) {
                                goalError = "Слишком большое значение дистанции"
                            } else {
                                distanceMetersDraft = meters
                                viewModel.setGoal(
                                    WorkoutGoal(
                                        kind = GoalKind.DISTANCE,
                                        distanceMeters = meters,
                                    )
                                )
                                goalEditor = null
                                goalError = null
                            }
                        }
                    } else {
                        val minutes = goalDraft.trim().toLongOrNull()
                        if (minutes == null || minutes <= 0L || minutes > Long.MAX_VALUE / 60_000L) {
                            goalError = "Введите целое число минут больше 0"
                        } else {
                            val millis = minutes * 60_000L
                            durationMillisDraft = millis
                            viewModel.setGoal(
                                WorkoutGoal(
                                    kind = GoalKind.DURATION,
                                    durationMillis = millis,
                                )
                            )
                            goalEditor = null
                            goalError = null
                        }
                    }
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    goalEditor = null
                    goalError = null
                }) {
                    Text("Отмена")
                }
            },
        )
    }
}

private const val START_COUNTDOWN_INITIAL_MILLIS = 10_000L
private const val START_COUNTDOWN_INCREMENT_MILLIS = 5_000L
private const val START_COUNTDOWN_MAX_MILLIS = 5L * 60_000L

@Composable
private fun ActivityChoice(
    title: String,
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(96.dp)
            .background(
                if (selected) accent.copy(alpha = 0.17f) else RtSurface2,
                RoundedCornerShape(13.dp)
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(29.dp))
        Spacer(Modifier.height(7.dp))
        Text(title, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun GoalChoice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) RtGreen.copy(alpha = 0.10f) else RtSurface, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(if (selected) RtGreen else RtSurface2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(8.dp).background(RtBg, CircleShape))
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = RtMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ActiveWorkoutScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val snapshot by viewModel.liveTracking.collectAsStateWithLifecycle()
    val relation by viewModel.selectedWorkout.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackHandler { Toast.makeText(context, "Тренировка продолжается. Используйте Пауза или Финиш.", Toast.LENGTH_SHORT).show() }
    val current = snapshot
    val type = current?.type ?: WorkoutType.RUN
    val metrics = current?.let { WorkoutMath.metrics(it.distanceMeters, it.elapsedMillis, it.movingMillis) }
    val routeSegments = remember(relation?.route) {
        relation?.route?.groupBy { it.segmentIndex }?.toSortedMap()?.values
            ?.map { segment -> RouteGeometry.downsampleForRender(segment.sortedBy { it.movingElapsedMillis }.map { it.asLocationSample() }) }
            .orEmpty()
    }
    val calories = current?.let { WorkoutMath.estimatedCalories(type, it.movingMillis, it.distanceMeters, settings.weightKg) } ?: 0
    val weather = relation?.weatherSnapshots?.maxByOrNull { it.capturedAt }
    val performance = if (type == WorkoutType.BIKE) RunTrackFormatter.speed(metrics?.averageSpeedMps ?: 0.0, settings.units) else RunTrackFormatter.pace(metrics?.paceSecondsPerKm, settings.units)
    val workoutSubtitle = "Тренировка идёт"
    NativePage(title = workoutTypeTitle(type), subtitle = workoutSubtitle, onNavigate = onNavigate) {
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LiveRouteMap(routeSegments, settings.mapLayer, { viewModel.setMapLayer(settings.mapLayer.toggle()) }, Modifier.fillMaxWidth().height(245.dp))
            Spacer(Modifier.height(18.dp))
            Text(RunTrackFormatter.distanceNumber(current?.distanceMeters ?: 0.0, settings.units), color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Bold)
            Text(if (settings.units == UnitSystem.METRIC) "км" else "mi", color = RtMuted, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricBox("Время", RunTrackFormatter.duration(current?.elapsedMillis ?: 0L), Modifier.weight(1f))
                MetricBox(if (type == WorkoutType.BIKE) "Скорость" else "Темп", performance, Modifier.weight(1f))
                MetricBox("Ккал", RunTrackFormatter.caloriesNumber(calories), Modifier.weight(1f))
            }
            Spacer(Modifier.height(9.dp))
            WeatherSummaryCard(weather, settings.units, "Погода появится после первого GPS-обновления")
            current?.goal?.takeIf { it.kind != GoalKind.NONE }?.let {
                Spacer(Modifier.height(9.dp))
                Text(goalProgressText(current, settings.units), color = if (current.goalReached) RtGreen else RtMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryAction("Пауза", { viewModel.pauseWorkout { onNavigate(3) } }, Modifier.weight(1f), Icons.Outlined.Pause, loading = operation is UiOperationState.Running)
                SecondaryAction("Финиш", { viewModel.requestFinish { onNavigate(4) } }, Modifier.weight(1f), Icons.Outlined.Stop, danger = true, loading = operation is UiOperationState.Running)
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun PausedWorkoutScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val snapshot by viewModel.liveTracking.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackHandler { Toast.makeText(context, "Тренировка на паузе. Продолжите или завершите её.", Toast.LENGTH_SHORT).show() }
    val current = snapshot
    val type = current?.type ?: WorkoutType.RUN
    val metrics = current?.let { WorkoutMath.metrics(it.distanceMeters, it.elapsedMillis, it.movingMillis) }
    val performance = if (type == WorkoutType.BIKE) RunTrackFormatter.speed(metrics?.averageSpeedMps ?: 0.0, settings.units) else RunTrackFormatter.pace(metrics?.paceSecondsPerKm, settings.units)
    val calories = current?.let { WorkoutMath.estimatedCalories(type, it.movingMillis, it.distanceMeters, settings.weightKg) } ?: 0
    NativePage(title = "Пауза", subtitle = "Тренировка приостановлена", onNavigate = onNavigate) {
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(28.dp))
            Box(Modifier.size(92.dp).background(RtYellow.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Pause, null, tint = RtYellow, modifier = Modifier.size(45.dp)) }
            Spacer(Modifier.height(18.dp))
            Text(RunTrackFormatter.distance(current?.distanceMeters ?: 0.0, settings.units), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(RunTrackFormatter.duration(current?.elapsedMillis ?: 0L), color = RtMuted, fontSize = 18.sp)
            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricBox(if (type == WorkoutType.BIKE) "Средняя скорость" else "Средний темп", performance, Modifier.weight(1f))
                MetricBox("Калории", RunTrackFormatter.calories(calories), Modifier.weight(1f))
            }
            current?.goal?.takeIf { it.kind != GoalKind.NONE }?.let {
                Spacer(Modifier.height(9.dp))
                Text(goalProgressText(current, settings.units), color = if (current.goalReached) RtGreen else RtMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))
            PrimaryAction("Продолжить", { viewModel.resumeWorkout { onNavigate(2) } }, Modifier.fillMaxWidth(), Icons.Outlined.PlayArrow, loading = operation is UiOperationState.Running)
            Spacer(Modifier.height(9.dp))
            SecondaryAction("Завершить тренировку", { viewModel.requestFinish { onNavigate(4) } }, Modifier.fillMaxWidth(), Icons.Outlined.Stop, danger = true, loading = operation is UiOperationState.Running)
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun FinishWorkoutScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit, onWorkoutSaved: (String) -> Unit) {
    val snapshot by viewModel.liveTracking.collectAsStateWithLifecycle()
    val relation by viewModel.selectedWorkout.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackHandler { Toast.makeText(context, "Сохраните результат или повторите сохранение после ошибки.", Toast.LENGTH_SHORT).show() }
    val current = snapshot
    val type = current?.type ?: relation?.workout?.typeOrNull() ?: WorkoutType.RUN
    val metrics = current?.let { WorkoutMath.metrics(it.distanceMeters, it.elapsedMillis, it.movingMillis) }
    val routeSegments = relation?.route?.groupBy { it.segmentIndex }?.toSortedMap()?.values
        ?.map { segment -> segment.sortedBy { it.movingElapsedMillis }.map { it.asLocationSample() } }.orEmpty()
    val elevation = WorkoutMath.elevationGainLossSegments(routeSegments)
    val calories = current?.let { WorkoutMath.estimatedCalories(type, it.movingMillis, it.distanceMeters, settings.weightKg) } ?: 0
    val performance = if (type == WorkoutType.BIKE) RunTrackFormatter.speed(metrics?.averageSpeedMps ?: 0.0, settings.units) else RunTrackFormatter.pace(metrics?.paceSecondsPerKm, settings.units)
    NativePage(title = "Тренировка завершена", subtitle = "Проверь результат перед сохранением", onNavigate = onNavigate) {
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.size(88.dp).background(RtGreen.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.CheckCircle, null, tint = RtGreen, modifier = Modifier.size(46.dp)) }
            Spacer(Modifier.height(16.dp))
            Text(RunTrackFormatter.distance(current?.distanceMeters ?: 0.0, settings.units), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Text(RunTrackFormatter.duration(current?.elapsedMillis ?: 0L), color = RtMuted, fontSize = 17.sp)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricBox(if (type == WorkoutType.BIKE) "Скорость" else "Темп", performance, Modifier.weight(1f))
                MetricBox("Калории", RunTrackFormatter.caloriesNumber(calories), Modifier.weight(1f))
                MetricBox("Набор", RunTrackFormatter.elevation(elevation?.first, settings.units), Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp)); InfoRow(Icons.Outlined.FavoriteBorder, "Средний пульс", relation?.heartRateSamples?.map { it.bpm }?.takeIf { it.isNotEmpty() }?.average()?.toInt()?.let { "$it уд/мин" } ?: "Нет данных", RtRed)
            if (operation is UiOperationState.Error) { Spacer(Modifier.height(8.dp)); Text((operation as UiOperationState.Error).message, color = RtRed, fontSize = 10.sp) }
            Spacer(Modifier.weight(1f))
            PrimaryAction("Сохранить результат", { viewModel.saveFinishedWorkout(onWorkoutSaved) }, Modifier.fillMaxWidth(), Icons.Outlined.Check, loading = operation is UiOperationState.Running)
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ResultOverviewScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val relation by viewModel.selectedWorkout.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val r = relation
    if (r == null) { MissingDataPage("Результат", onNavigate); return }
    val w = r.workout; val type = w.typeOrNull() ?: WorkoutType.RUN
    val metrics = WorkoutMath.metrics(w.distanceMeters, w.elapsedMillis, w.movingMillis)
    val timedRoute = remember(r.route) { r.route.sortedBy { it.movingElapsedMillis }.map { TimedRoutePoint(it.asLocationSample(), it.movingElapsedMillis, it.segmentIndex) } }
    val bestOneKm = remember(timedRoute) { RecordCalculator.bestDistanceWindowMillis(timedRoute, 1_000.0) }
    ResultPage(w.displayTitle(), w.resultSubtitle(), 0, onNavigate) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricBox("Дистанция", RunTrackFormatter.distance(w.distanceMeters, settings.units), Modifier.weight(1f), RtGreen)
            MetricBox("Время", RunTrackFormatter.duration(w.elapsedMillis), Modifier.weight(1f))
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricBox(if (type == WorkoutType.BIKE) "Средняя скорость" else "Средний темп", if (type == WorkoutType.BIKE) RunTrackFormatter.speed(w.averageSpeedMps, settings.units) else RunTrackFormatter.pace(metrics.paceSecondsPerKm, settings.units), Modifier.weight(1f))
            MetricBox("Калории", RunTrackFormatter.calories(w.caloriesEstimate), Modifier.weight(1f))
        }
        Spacer(Modifier.height(9.dp))
        WeatherSummaryCard(
            r.weatherSnapshots.maxByOrNull { it.capturedAt },
            settings.units,
            "Погода для этой тренировки не сохранена",
        )
        Spacer(Modifier.height(14.dp)); SectionTitle("Показатели"); Spacer(Modifier.height(8.dp))
        InfoRow(Icons.Outlined.Speed, "Лучший темп", bestOneKm?.let { RunTrackFormatter.pace(it / 1000.0, settings.units) } ?: "Нет данных", RtGreen)
        Spacer(Modifier.height(8.dp)); InfoRow(Icons.Outlined.FavoriteBorder, "Пульс", w.heartRateAverageBpm?.let { avg -> "$avg средний · ${w.heartRateMaxBpm ?: avg} максимум" } ?: "Нет данных", RtRed)
        Spacer(Modifier.height(8.dp)); InfoRow(Icons.Outlined.Terrain, "Высота", elevationPairText(w, settings.units), RtBlue)
        Spacer(Modifier.height(12.dp)); SecondaryAction("Все детали", { onNavigate(8) }, Modifier.fillMaxWidth(), Icons.Outlined.Tune)
    }
}

@Composable
private fun ResultMapScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val relation by viewModel.selectedWorkout.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val r = relation
    if (r == null) { MissingDataPage("Карта", onNavigate); return }
    val routeSegments = r.route.groupBy { it.segmentIndex }.toSortedMap().values
        .map { segment -> segment.sortedBy { it.movingElapsedMillis }.map { it.asLocationSample() } }
    val routePointCount = routeSegments.sumOf { it.size }
    ResultPage(r.workout.displayTitle(), r.workout.resultSubtitle(), 1, onNavigate) {
        RouteCanvas(Modifier.fillMaxWidth().height(355.dp), routeSegments, settings.mapLayer) { viewModel.setMapLayer(settings.mapLayer.toggle()) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricBox("Дистанция", RunTrackFormatter.distance(r.workout.distanceMeters, settings.units), Modifier.weight(1f), RtGreen)
            MetricBox("Набор высоты", RunTrackFormatter.elevation(r.workout.elevationGainMeters, settings.units), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp)); InfoRow(Icons.Outlined.LocationOn, "Маршрут", if (routePointCount >= 2) "Старт и финиш сохранены" else "Недостаточно GPS-точек", RtBlue)
    }
}

@Composable
private fun ResultRouteScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val relation by viewModel.selectedWorkout.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val r = relation
    if (r == null) { MissingDataPage("Маршрут", onNavigate); return }
    val timed = r.route.sortedBy { it.movingElapsedMillis }.map { TimedRoutePoint(it.asLocationSample(), it.movingElapsedMillis, it.segmentIndex) }
    val splitMeters = if (settings.units == UnitSystem.METRIC) 1000.0 else 1609.344
    val splits = remember(timed, splitMeters) { SplitCalculator.splits(timed, splitMeters) }
    ResultPage(r.workout.displayTitle(), r.workout.resultSubtitle(), 2, onNavigate) {
        Box(Modifier.fillMaxWidth().height(290.dp).background(RtSurface, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            RouteArtCanvas(timed.groupBy { it.segmentIndex }.toSortedMap().values.map { segment -> segment.map { it.sample } }, Modifier.fillMaxSize().padding(20.dp))
        }
        Spacer(Modifier.height(14.dp)); SectionTitle(if (settings.units == UnitSystem.METRIC) "Разбивка по километрам" else "Разбивка по милям"); Spacer(Modifier.height(8.dp))
        if (splits.isEmpty()) Text("Недостаточно данных для разбивки", color = RtMuted, fontSize = 10.sp)
        else splits.forEach { split -> PaceRow("${split.index}", RunTrackFormatter.pace(split.paceSecondsPerKm, settings.units), 0.82f) }
    }
}

@Composable
private fun WorkoutDetailsScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val relation by viewModel.selectedWorkout.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val r = relation
    if (r == null) { MissingDataPage("Детали тренировки", onNavigate); return }
    val w = r.workout; val type = w.typeOrNull() ?: WorkoutType.RUN
    val metrics = WorkoutMath.metrics(w.distanceMeters, w.elapsedMillis, w.movingMillis)
    val cadence = StepMetrics.cadenceStepsPerMinute(
        w.stepCount,
        w.movingMillis,
        w.stepTrackingReliable,
    )
    val strideLengthMeters = StepMetrics.strideLengthMeters(
        w.stepCount,
        w.distanceMeters,
        w.stepTrackingReliable,
    )
    NativePage(title = "Детали тренировки", subtitle = w.resultSubtitle(), onBack = { onNavigate(5) }, onNavigate = onNavigate) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { MetricBox("Дистанция", RunTrackFormatter.distance(w.distanceMeters, settings.units), Modifier.weight(1f), RtGreen); MetricBox("Время", RunTrackFormatter.duration(w.elapsedMillis), Modifier.weight(1f)) } }
            item { InfoRow(Icons.Outlined.Speed, if (type == WorkoutType.BIKE) "Средняя скорость" else "Средний темп", if (type == WorkoutType.BIKE) RunTrackFormatter.speed(w.averageSpeedMps, settings.units) else RunTrackFormatter.pace(metrics.paceSecondsPerKm, settings.units), RtGreen) }
            item { InfoRow(Icons.Outlined.Timer, "В движении", RunTrackFormatter.duration(w.movingMillis), RtBlue) }
            item { InfoRow(Icons.Outlined.FavoriteBorder, "Пульс", w.heartRateAverageBpm?.let { avg -> "$avg / ${w.heartRateMaxBpm ?: avg} уд/мин" } ?: "Нет данных", RtRed) }
            item { InfoRow(Icons.Outlined.LocalFireDepartment, "Калории", RunTrackFormatter.calories(w.caloriesEstimate), RtYellow) }
            item { InfoRow(Icons.Outlined.Terrain, "Высота", elevationPairText(w, settings.units), RtBlue) }
            item {
                InfoRow(
                    Icons.Outlined.DirectionsRun,
                    "Каденс",
                    RunTrackFormatter.cadence(cadence),
                    RtGreen,
                )
            }
            item {
                InfoRow(
                    Icons.Outlined.Straighten,
                    "Длина шага",
                    RunTrackFormatter.strideLength(strideLengthMeters, settings.units),
                    RtText2,
                )
            }
            item { SecondaryAction("Экспорт тренировки", { onNavigate(19) }, Modifier.fillMaxWidth(), Icons.Outlined.Share) }
        }
    }
}

@Composable
private fun ResultPage(
    title: String,
    subtitle: String,
    activeTab: Int,
    onNavigate: (Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    NativePage(title = title, subtitle = subtitle, onBack = { onNavigate(9) }, onNavigate = onNavigate) {
        ResultTabs(activeTab = activeTab, onNavigate = onNavigate)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), contentPadding = PaddingValues(bottom = 14.dp)) { item { Column { content() } } }
    }
}

@Composable
private fun ResultTabs(activeTab: Int, onNavigate: (Int) -> Unit) {
    val labels = listOf("Обзор", "Карта", "Маршрут")
    val targets = listOf(5, 6, 7)
    Row(
        modifier = Modifier.padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .background(
                        if (i == activeTab) RtGreen else RtSurface2,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onNavigate(targets[i]) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (i == activeTab) RtBg else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun PaceRow(label: String, pace: String, fraction: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = RtText2, fontSize = 11.sp, modifier = Modifier.width(42.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(RtSurface2, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(RtGreen, RoundedCornerShape(4.dp))
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(pace, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(38.dp))
    }
}

@Composable
private fun StatsOverviewScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val stats by viewModel.stats.collectAsStateWithLifecycle(); val settings by viewModel.settings.collectAsStateWithLifecycle()
    val s = stats.currentWeek
    StatsPage("Статистика", 0, onNavigate) {
        SectionTitle("Эта неделя"); Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { MetricBox("Тренировок", s.workouts.toString(), Modifier.weight(1f), RtGreen); MetricBox("Дистанция", RunTrackFormatter.distance(s.distanceMeters, settings.units), Modifier.weight(1f)) }
        Spacer(Modifier.height(9.dp)); Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { MetricBox("Время", RunTrackFormatter.duration(s.elapsedMillis), Modifier.weight(1f)); MetricBox("Калории", RunTrackFormatter.caloriesNumber(s.calories), Modifier.weight(1f)) }
        Spacer(Modifier.height(15.dp)); SectionTitle("По активности"); Spacer(Modifier.height(8.dp))
        val max = (s.byTypeDistanceMeters.values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
        ActivityProgress("Бег", RunTrackFormatter.distance(s.byTypeDistanceMeters[WorkoutType.RUN] ?: 0.0, settings.units), RtGreen, ((s.byTypeDistanceMeters[WorkoutType.RUN] ?: 0.0) / max).toFloat(), Icons.Outlined.DirectionsRun)
        ActivityProgress("Ходьба", RunTrackFormatter.distance(s.byTypeDistanceMeters[WorkoutType.WALK] ?: 0.0, settings.units), RtYellow, ((s.byTypeDistanceMeters[WorkoutType.WALK] ?: 0.0) / max).toFloat(), Icons.Outlined.DirectionsWalk)
        ActivityProgress("Велосипед", RunTrackFormatter.distance(s.byTypeDistanceMeters[WorkoutType.BIKE] ?: 0.0, settings.units), RtBlue, ((s.byTypeDistanceMeters[WorkoutType.BIKE] ?: 0.0) / max).toFloat(), Icons.Outlined.DirectionsBike)
        Spacer(Modifier.height(14.dp)); InfoRow(Icons.Outlined.TrendingUp, "Прогресс", stats.weeklyDistanceChangePercent?.let { String.format("%+.0f%% к прошлой неделе", it) } ?: "Недостаточно данных", RtGreen) { onNavigate(11) }
    }
}

@Composable
private fun StatsChartsScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val stats by viewModel.stats.collectAsStateWithLifecycle(); val settings by viewModel.settings.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val byDay = stats.daily.associateBy { it.epochDay }
    val recent = (6L downTo 0L).map { offset -> byDay[today.minusDays(offset).toEpochDay()] ?: DailyStatPoint(today.minusDays(offset).toEpochDay(), 0.0, 0L) }
    val maxDistance = (recent.maxOfOrNull { it.distanceMeters } ?: 0.0).coerceAtLeast(1.0)
    val distanceValues = recent.map { (it.distanceMeters / maxDistance).toFloat() }
    val maxDuration = (recent.maxOfOrNull { it.durationMillis } ?: 0L).coerceAtLeast(1L)
    val durationValues = recent.map { it.durationMillis.toFloat() / maxDuration.toFloat() }
    StatsPage("Статистика", 1, onNavigate) {
        SectionTitle("Дистанция за 7 дней"); Spacer(Modifier.height(8.dp)); ChartCard(Modifier.fillMaxWidth().height(210.dp), false, distanceValues)
        Spacer(Modifier.height(14.dp)); SectionTitle("Время тренировок"); Spacer(Modifier.height(8.dp)); ChartCard(Modifier.fillMaxWidth().height(190.dp), true, durationValues)
        Spacer(Modifier.height(12.dp))
        val avg = if (recent.isEmpty()) 0.0 else recent.sumOf { it.distanceMeters } / recent.size
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { MetricBox("Средняя", RunTrackFormatter.distance(avg, settings.units), Modifier.weight(1f)); MetricBox("Максимум", RunTrackFormatter.distance(recent.maxOfOrNull { it.distanceMeters } ?: 0.0, settings.units), Modifier.weight(1f), RtGreen) }
    }
}

@Composable
private fun RecordsScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val stats by viewModel.stats.collectAsStateWithLifecycle(); val history by viewModel.history.collectAsStateWithLifecycle(); val settings by viewModel.settings.collectAsStateWithLifecycle()
    StatsPage("Рекорды", 2, onNavigate) {
        var any = false
        stats.fastest5kMillis?.let { (_, ms) -> any = true; RecordCard("Самые быстрые 5 км", RunTrackFormatter.duration(ms), RunTrackFormatter.pace(ms / 5_000.0, settings.units), RtGreen); Spacer(Modifier.height(9.dp)) }
        stats.longestRun?.let { record -> any = true; val w = history.firstOrNull { it.id == record.workoutId }; RecordCard("Самая длинная пробежка", RunTrackFormatter.distance(record.value, settings.units), w?.let { RunTrackFormatter.duration(it.elapsedMillis) } ?: "", RtBlue); Spacer(Modifier.height(9.dp)) }
        stats.best1kMillis?.let { (_, ms) -> any = true; RecordCard("Лучший километровый темп", RunTrackFormatter.pace(ms / 1000.0, UnitSystem.METRIC), RunTrackFormatter.duration(ms), RtYellow); Spacer(Modifier.height(9.dp)) }
        stats.maxElevationGain?.let { record -> any = true; RecordCard("Максимальный набор", RunTrackFormatter.elevation(record.value, settings.units), history.firstOrNull { it.id == record.workoutId }?.let { RunTrackFormatter.distance(it.distanceMeters, settings.units) } ?: "", Color(0xFFAB7CFF)) }
        if (!any) Text("Недостаточно данных для рекордов", color = RtMuted, fontSize = 11.sp)
    }
}

@Composable
private fun ComparisonScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val stats by viewModel.stats.collectAsStateWithLifecycle(); val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = stats.currentWeek; val previous = stats.previousWeek
    fun delta(c: Double, p: Double, higherIsBetter: Boolean = true): Pair<String, Color> {
        val percent = StatisticsCalculator.percentChange(c, p) ?: return "—" to RtMuted
        val improved = if (higherIsBetter) percent > 0 else percent < 0
        val color = when { percent == 0.0 -> RtMuted; improved -> RtGreen; else -> RtRed }
        return String.format("%+.0f%%", percent) to color
    }
    val distanceDelta = delta(current.distanceMeters, previous.distanceMeters)
    val workoutDelta = delta(current.workouts.toDouble(), previous.workouts.toDouble())
    val timeDelta = delta(current.elapsedMillis.toDouble(), previous.elapsedMillis.toDouble())
    val paceCurrent = stats.currentWeekRunPaceSecondsPerKm
    val pacePrevious = stats.previousWeekRunPaceSecondsPerKm
    val paceDelta = if (paceCurrent != null && pacePrevious != null) {
        val seconds = paceCurrent - pacePrevious
        val color = when { kotlin.math.abs(seconds) < 0.5 -> RtMuted; seconds < 0 -> RtGreen; else -> RtRed }
        val sign = if (seconds > 0) "+" else if (seconds < 0) "−" else ""
        "$sign${kotlin.math.abs(seconds).toInt()} сек" to color
    } else "—" to RtMuted
    StatsPage("Сравнение", 3, onNavigate) {
        Text("Эта неделя vs прошлая", color = RtMuted, fontSize = 11.sp); Spacer(Modifier.height(10.dp))
        ComparisonRow("Дистанция", RunTrackFormatter.distance(current.distanceMeters, settings.units), RunTrackFormatter.distance(previous.distanceMeters, settings.units), distanceDelta.first, distanceDelta.second)
        ComparisonRow("Тренировки", current.workouts.toString(), previous.workouts.toString(), workoutDelta.first, workoutDelta.second)
        ComparisonRow("Время", RunTrackFormatter.duration(current.elapsedMillis), RunTrackFormatter.duration(previous.elapsedMillis), timeDelta.first, timeDelta.second)
        ComparisonRow("Средний темп", paceCurrent?.let { RunTrackFormatter.pace(it, settings.units) } ?: "Нет данных", pacePrevious?.let { RunTrackFormatter.pace(it, settings.units) } ?: "Нет данных", paceDelta.first, paceDelta.second)
        Spacer(Modifier.height(14.dp)); SectionTitle("Тренд"); Spacer(Modifier.height(8.dp))
        val recent = stats.daily.takeLast(8); val max = (recent.maxOfOrNull { it.distanceMeters } ?: 0.0).coerceAtLeast(1.0)
        ChartCard(Modifier.fillMaxWidth().height(220.dp), false, recent.map { (it.distanceMeters / max).toFloat() })
    }
}

@Composable
private fun StatsPage(
    title: String,
    active: Int,
    onNavigate: (Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    NativePage(
        title = title,
        bottomNavSelected = 2,
        onNavigate = onNavigate,
    ) {
        StatsTabs(active, onNavigate)
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(bottom = 14.dp),
        ) {
            item { Column { content() } }
        }
    }
}

@Composable
private fun StatsTabs(active: Int, onNavigate: (Int) -> Unit) {
    val labels = listOf("Обзор", "Графики", "Рекорды", "Сравнение")
    val targets = listOf(10, 11, 12, 13)
    Row(
        modifier = Modifier.padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { i, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(
                        if (i == active) RtGreen else RtSurface2,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onNavigate(targets[i]) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (i == active) RtBg else Color.White,
                    fontSize = 9.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ActivityProgress(
    title: String,
    value: String,
    accent: Color,
    fraction: Float,
    icon: ImageVector,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(title, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text(value, color = RtMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(7.dp).background(RtSurface2, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(accent, RoundedCornerShape(4.dp)))
            }
        }
    }
}

@Composable
private fun RecordCard(title: String, value: String, detail: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RtSurface, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(accent.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.EmojiEvents, null, tint = accent, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = RtMuted, fontSize = 10.sp)
        }
        Text(value, color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ComparisonRow(label: String, current: String, previous: String, delta: String, deltaColor: Color = RtMuted) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).background(RtSurface, RoundedCornerShape(13.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(current, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("было $previous", color = RtMuted, fontSize = 9.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(delta, color = deltaColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProfileScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val stats by viewModel.stats.collectAsStateWithLifecycle(); val settings by viewModel.settings.collectAsStateWithLifecycle()
    val bmi = remember(settings.weightKg, settings.heightCm) {
        WorkoutMath.bodyMassIndex(settings.weightKg, settings.heightCm)
    }
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    val bodySummary = buildList {
        settings.weightKg?.let { add(String.format(locale, "%.1f кг", it)) }
        settings.heightCm?.let { add(String.format(locale, "%.0f см", it)) }
        bmi?.let { add(String.format(locale, "ИМТ %.1f", it)) }
    }.joinToString(" · ")
    val profileSubtitle =
        if (bodySummary.isBlank()) "RunTrack · вес и рост не указаны"
        else "RunTrack · $bodySummary"

    var editProfile by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var weightDraft by remember { mutableStateOf("") }
    var heightDraft by remember { mutableStateOf("") }
    var profileError by remember { mutableStateOf<String?>(null) }
    fun openEdit() {
        nameDraft = settings.profileName
        weightDraft = settings.weightKg?.let { String.format(Locale.US, "%.1f", it) } ?: ""
        heightDraft = settings.heightCm?.let { String.format(Locale.US, "%.1f", it) } ?: ""
        profileError = null
        editProfile = true
    }
    NativePage(title = "Профиль", bottomNavSelected = 3, onNavigate = onNavigate) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
            item { Row(Modifier.fillMaxWidth().background(RtSurface, RoundedCornerShape(16.dp)).clickable { openEdit() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(62.dp).background(RtGreen.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, null, tint = RtGreen, modifier = Modifier.size(34.dp)) }; Spacer(Modifier.width(14.dp)); Column { Text(settings.profileName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Text(profileSubtitle, color = RtMuted, fontSize = 11.sp) } } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { MetricBox("Всего", stats.all.workouts.toString(), Modifier.weight(1f), RtGreen); MetricBox("Дистанция", RunTrackFormatter.distance(stats.all.distanceMeters, settings.units), Modifier.weight(1f)); MetricBox("Время", RunTrackFormatter.duration(stats.all.elapsedMillis), Modifier.weight(1f)) } }
            item { InfoRow(Icons.Outlined.CalendarMonth, "Календарь", "Тренировки по дням", RtGreen) { onNavigate(15) } }
            item { InfoRow(Icons.Outlined.Map, "Все маршруты", "Карта сохранённых тренировок", RtBlue) { onNavigate(16) } }
            item { InfoRow(Icons.Outlined.Settings, "Настройки", "Единицы, уведомления, приватность", RtText2) { onNavigate(17) } }
            item { InfoRow(Icons.Outlined.Link, "Подключения", "Устройства и сервисы", RtYellow) { onNavigate(18) } }
        }
    }
    if (editProfile) AlertDialog(
        onDismissRequest = { editProfile = false },
        title = { Text("Локальный профиль") },
        text = { Column {
            OutlinedTextField(
                nameDraft,
                { nameDraft = it; profileError = null },
                label = { Text("Имя") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                weightDraft,
                { weightDraft = it.replace(',', '.'); profileError = null },
                label = { Text("Вес, кг (необязательно)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                heightDraft,
                { heightDraft = it.replace(',', '.'); profileError = null },
                label = { Text("Рост, см (необязательно)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Вес используется для оценки калорий. Рост вместе с весом используется для расчёта ИМТ.",
                color = RtMuted,
                fontSize = 10.sp,
            )
            profileError?.let { Text(it, color = RtRed, fontSize = 10.sp) }
        } },
        confirmButton = { TextButton(onClick = {
            val name = nameDraft.trim()
            val weight = weightDraft.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            val height = heightDraft.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            when {
                name.isEmpty() || name.length > 50 ->
                    profileError = "Имя: от 1 до 50 символов"
                weightDraft.isNotBlank() &&
                    (weight == null || !weight.isFinite() || weight !in 30.0..300.0) ->
                    profileError = "Вес должен быть от 30 до 300 кг"
                heightDraft.isNotBlank() &&
                    (height == null || !height.isFinite() || height !in 80.0..250.0) ->
                    profileError = "Рост должен быть от 80 до 250 см"
                else -> {
                    viewModel.setProfileName(name)
                    viewModel.setWeightKg(weight)
                    viewModel.setHeightCm(height)
                    editProfile = false
                }
            }
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = { editProfile = false }) { Text("Отмена") } },
    )
}

@Composable
private fun CalendarScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val history by viewModel.history.collectAsStateWithLifecycle(); val settings by viewModel.settings.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var month = YearMonth.parse(monthValue)
    var selectedDay by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfMonth) }
    var drag by remember { mutableFloatStateOf(0f) }
    val dates = remember(history, month, zone) { history.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }.filterKeys { YearMonth.from(it) == month } }
    val selectedDate = runCatching { month.atDay(selectedDay.coerceAtMost(month.lengthOfMonth())) }.getOrNull()
    val selectedWorkouts = selectedDate?.let { dates[it] }.orEmpty()
    NativePage(title = "Календарь", subtitle = month.format(DateTimeFormatter.ofPattern("LLLL yyyy")), onBack = { onNavigate(14) }, bottomNavSelected = 3, onNavigate = onNavigate) {
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Box(Modifier.pointerInput(monthValue) {
                detectHorizontalDragGestures(onDragStart = { drag = 0f }, onHorizontalDrag = { _, amount -> drag += amount }, onDragEnd = {
                    if (abs(drag) > 80f) { month = if (drag < 0) month.plusMonths(1) else month.minusMonths(1); monthValue = month.toString(); selectedDay = 1 }
                })
            }) { CalendarGrid(month, dates.keys.map { it.dayOfMonth }.toSet(), selectedDay) { selectedDay = it } }
            Spacer(Modifier.height(16.dp)); SectionTitle(selectedDate?.format(DateTimeFormatter.ofPattern("d MMMM")) ?: "День"); Spacer(Modifier.height(8.dp))
            if (selectedWorkouts.isEmpty()) Text("В этот день тренировок нет", color = RtMuted, fontSize = 10.sp)
            selectedWorkouts.forEach { w ->
                val type = w.typeOrNull() ?: WorkoutType.RUN
                InfoRow(type.icon(), w.displayTitle(), "${RunTrackFormatter.distance(w.distanceMeters, settings.units)} · ${RunTrackFormatter.duration(w.elapsedMillis)}", type.accent()) { viewModel.selectWorkout(w.id); onNavigate(5) }
                Spacer(Modifier.height(8.dp))
            }
            Text("Свайп по календарю — сменить месяц", color = RtMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun CalendarGrid(month: YearMonth, activeDays: Set<Int>, selectedDay: Int?, onSelect: (Int) -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val firstOffset = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val padded = List<Int?>(firstOffset) { null } + (1..month.lengthOfMonth()).toList()
    val weekDays = (0..6).map { offset -> firstDay.plus(offset.toLong()) }
    Column(Modifier.fillMaxWidth().background(RtSurface, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth()) { weekDays.forEach { day -> Text(day.getDisplayName(java.time.format.TextStyle.SHORT, locale).replaceFirstChar { it.uppercase(locale) }, color = RtMuted, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
        Spacer(Modifier.height(8.dp))
        padded.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day != null) Column(Modifier.clickable { onSelect(day) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day.toString(), color = if (day == selectedDay) RtBg else Color.White, fontSize = 11.sp, modifier = if (day == selectedDay) Modifier.background(RtGreen, CircleShape).padding(horizontal = 7.dp, vertical = 4.dp) else Modifier)
                            if (day in activeDays && day != selectedDay) { Spacer(Modifier.height(2.dp)); Box(Modifier.size(4.dp).background(RtGreen, CircleShape)) }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

@Composable
private fun AllRoutesScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val routes by viewModel.allRoutes.collectAsStateWithLifecycle(); val settings by viewModel.settings.collectAsStateWithLifecycle(); val stats by viewModel.stats.collectAsStateWithLifecycle()
    val samples = remember(routes) {
        routes.flatMap { r ->
            r.route.groupBy { it.segmentIndex }.toSortedMap().values
                .map { segment -> segment.sortedBy { it.movingElapsedMillis }.map { it.asLocationSample() } }
        }.filter { it.isNotEmpty() }
    }
    NativePage(title = "Все маршруты", subtitle = "Сохранённые тренировки", onBack = { onNavigate(14) }, bottomNavSelected = 3, onNavigate = onNavigate) {
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            RouteCanvas(Modifier.fillMaxWidth().weight(1f), samples, settings.mapLayer) { viewModel.setMapLayer(settings.mapLayer.toggle()) }
            Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { MetricBox("Маршрутов", routes.count { it.route.isNotEmpty() }.toString(), Modifier.weight(1f), RtGreen); MetricBox("Всего", RunTrackFormatter.distance(stats.all.distanceMeters, settings.units), Modifier.weight(1f)) }; Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun SettingsScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setNotificationsEnabled(granted)
        if (!granted) Toast.makeText(context, "Уведомления не включены", Toast.LENGTH_SHORT).show()
    }
    fun setOptionalNotifications(enabled: Boolean) {
        if (!enabled) viewModel.setNotificationsEnabled(false)
        else if (android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) viewModel.setNotificationsEnabled(true)
        else notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    var unitsDialog by remember { mutableStateOf(false) }
    var privacyDialog by remember { mutableStateOf(false) }
    var backupPassDialog by remember { mutableStateOf(false) }
    var restorePassDialog by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var pendingBackup by remember { mutableStateOf<ByteArray?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    val backupSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = pendingBackup
        pendingBackup = null
        if (uri != null && bytes != null) viewModel.saveBackupToUri(uri, bytes) {
            Toast.makeText(context, "Резервная копия сохранена", Toast.LENGTH_SHORT).show()
        }
    }
    val restoreOpenLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingRestoreUri = uri; passphrase = ""; localError = null; restorePassDialog = true }
    }
    val busy = operation is UiOperationState.Running

    NativePage(title = "Настройки", onBack = { onNavigate(14) }, bottomNavSelected = 3, onNavigate = onNavigate) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
            item { SettingSwitch(Icons.Outlined.Notifications, "Уведомления", "Необязательные итоги тренировок", settings.notificationsEnabled, ::setOptionalNotifications) }
            item { SettingSwitch(Icons.Outlined.VolumeUp, "Голосовые подсказки", "«Старт» и каждый полный километр · офлайн", settings.voiceAnnouncementsEnabled, viewModel::setVoiceAnnouncementsEnabled) }
            item { SettingSwitch(Icons.Outlined.Visibility, "Не выключать экран", "Только во время активной тренировки", settings.keepScreenOn, viewModel::setKeepScreenOn) }
            item { InfoRow(Icons.Outlined.Straighten, "Единицы измерения", if (settings.units == UnitSystem.METRIC) "Километры · кг · °C" else "Мили · lb · °F", RtBlue) { unitsDialog = true } }
            item { InfoRow(Icons.Outlined.Language, "Язык", "Русский · системная локаль", RtText2) { openAppLanguageSettings(context) } }
            item { InfoRow(Icons.Outlined.Lock, "Приватность", "Backup, restore и удаление данных", RtGreen) { privacyDialog = true } }
            item { InfoRow(Icons.Outlined.Link, "Подключения", "Устройства и сервисы", RtYellow) { onNavigate(18) } }
        }
    }
    if (unitsDialog) AlertDialog(onDismissRequest = { unitsDialog = false }, title = { Text("Единицы измерения") }, text = { Column { RadioButtonRow("Километры", settings.units == UnitSystem.METRIC) { viewModel.setUnits(UnitSystem.METRIC); unitsDialog = false }; RadioButtonRow("Мили", settings.units == UnitSystem.IMPERIAL) { viewModel.setUnits(UnitSystem.IMPERIAL); unitsDialog = false } } }, confirmButton = {})
    if (privacyDialog) AlertDialog(
        onDismissRequest = { privacyDialog = false },
        title = { Text("Приватность и данные") },
        text = { Column {
            Text("RunTrack работает local-first. Точные GPS-координаты по умолчанию остаются на устройстве.")
            Spacer(Modifier.height(12.dp))
            TextButton(enabled = !busy, onClick = { privacyDialog = false; passphrase = ""; localError = null; backupPassDialog = true }) { Text("Создать зашифрованную копию") }
            TextButton(enabled = !busy, onClick = { privacyDialog = false; restoreOpenLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) }) { Text("Восстановить из файла") }
            TextButton(enabled = !busy, onClick = { privacyDialog = false; deleteConfirm = true }) { Text("Удалить все данные", color = RtRed) }
        } },
        confirmButton = { TextButton(onClick = { privacyDialog = false }) { Text("Закрыть") } },
    )
    if (backupPassDialog) AlertDialog(
        onDismissRequest = { backupPassDialog = false; passphrase = "" },
        title = { Text("Пароль резервной копии") },
        text = { Column {
            Text("Минимум 8 символов. Этот пароль потребуется на другом устройстве.", fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(passphrase, { passphrase = it; localError = null }, singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text("Пароль") })
            localError?.let { Text(it, color = RtRed, fontSize = 10.sp) }
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            if (passphrase.length < 8) localError = "Введите не менее 8 символов"
            else {
                val value = passphrase; backupPassDialog = false; passphrase = ""
                viewModel.createEncryptedBackup(value) { bytes ->
                    pendingBackup = bytes
                    val name = "RunTrack_backup_${LocalDate.now()}.rtbk"
                    backupSaveLauncher.launch(name)
                }
            }
        }) { Text("Создать") } },
        dismissButton = { TextButton(onClick = { backupPassDialog = false; passphrase = "" }) { Text("Отмена") } },
    )
    if (restorePassDialog) AlertDialog(
        onDismissRequest = { restorePassDialog = false; passphrase = ""; pendingRestoreUri = null },
        title = { Text("Пароль резервной копии") },
        text = { Column {
            Text("Восстановление объединит данные. Повторяющиеся workout ID будут отклонены.", fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(passphrase, { passphrase = it; localError = null }, singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text("Пароль") })
            localError?.let { Text(it, color = RtRed, fontSize = 10.sp) }
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val uri = pendingRestoreUri
            if (passphrase.length < 8) localError = "Введите не менее 8 символов"
            else if (uri != null) {
                val value = passphrase; restorePassDialog = false; passphrase = ""; pendingRestoreUri = null
                viewModel.restoreEncryptedBackup(uri, value) { count -> Toast.makeText(context, "Восстановлено тренировок: $count", Toast.LENGTH_LONG).show() }
            }
        }) { Text("Восстановить") } },
        dismissButton = { TextButton(onClick = { restorePassDialog = false; passphrase = ""; pendingRestoreUri = null }) { Text("Отмена") } },
    )
    if (deleteConfirm) AlertDialog(
        onDismissRequest = { deleteConfirm = false },
        title = { Text("Удалить все данные?") },
        text = { Text("Будут остановлены активные процессы, очищены тренировки, маршруты, настройки и временные файлы экспорта. Действие необратимо.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { deleteConfirm = false; viewModel.deleteAllData { Toast.makeText(context, "Данные удалены", Toast.LENGTH_SHORT).show(); onNavigate(0) } }) { Text("Удалить", color = RtRed) } },
        dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Отмена") } },
    )
}

@Composable
private fun SettingSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RtSurface, RoundedCornerShape(13.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = RtText2, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = RtMuted, fontSize = 9.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RtBg,
                checkedTrackColor = RtGreen,
                uncheckedThumbColor = RtMuted,
                uncheckedTrackColor = RtSurface2,
            ),
        )
    }
}

@Composable
private fun ConnectionsScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val context = LocalContext.current
    val hrState by viewModel.heartRateState.collectAsStateWithLifecycle()
    val devices by viewModel.heartRateDevices.collectAsStateWithLifecycle()
    val appSettings by viewModel.settings.collectAsStateWithLifecycle()
    val gps by viewModel.gpsReadiness.collectAsStateWithLifecycle()
    val selectedWorkout by viewModel.selectedWorkout.collectAsStateWithLifecycle()
    val healthConnect by viewModel.healthConnectState.collectAsStateWithLifecycle()
    val bt = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    val btSupported = bt != null
    val scanPermission = if (android.os.Build.VERSION.SDK_INT >= 31) ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED else ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val connectPermission = android.os.Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.onPermissionStateChanged(); viewModel.refreshBluetoothState() }
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted -> viewModel.onHealthConnectPermissionsResult(granted) }
    LaunchedEffect(Unit) {
        viewModel.refreshBluetoothState()
        viewModel.onPermissionStateChanged()
        viewModel.refreshHealthConnect()
    }

    fun requestBlePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 31) permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }
    val hrSubtitle = when (val state = hrState) {
        BleHeartRateState.Unsupported -> "Bluetooth LE не поддерживается"
        BleHeartRateState.PermissionRequired -> "Нужно разрешение Bluetooth"
        BleHeartRateState.BluetoothOff -> "Bluetooth выключен"
        BleHeartRateState.Idle -> appSettings.heartRateDeviceName?.let { "Сохранён: $it" } ?: "Готов к поиску BLE Heart Rate"
        BleHeartRateState.Scanning -> "Поиск совместимых устройств…"
        is BleHeartRateState.Connecting -> "Подключение: ${state.name}"
        is BleHeartRateState.Subscribing -> "Подписка на Heart Rate: ${state.name}"
        is BleHeartRateState.Connected -> if (state.bpm != null) "${state.name} · ${state.bpm} уд/мин" else "${state.name} · подключён"
        is BleHeartRateState.Error -> state.message
    }
    val hrAction = when (hrState) {
        BleHeartRateState.Unsupported -> "Недоступно"
        BleHeartRateState.PermissionRequired -> "Разрешить"
        BleHeartRateState.BluetoothOff -> "Включить"
        BleHeartRateState.Idle -> if (appSettings.heartRateDeviceAddress != null) "Подключить" else "Поиск"
        BleHeartRateState.Scanning -> "Отмена"
        is BleHeartRateState.Connecting, is BleHeartRateState.Subscribing -> "Отмена"
        is BleHeartRateState.Connected -> "Отключить"
        is BleHeartRateState.Error -> "Повторить"
    }
    val hrConnected = hrState is BleHeartRateState.Connected

    NativePage(title = "Подключения", subtitle = "Устройства и сервисы", onBack = { onNavigate(17) }, bottomNavSelected = 3, onNavigate = onNavigate) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
            item {
                ConnectionCard(
                    Icons.Outlined.Bluetooth,
                    "Bluetooth-датчики",
                    when { !btSupported -> "Не поддерживается"; !scanPermission || !connectPermission -> "Нужно разрешение Bluetooth"; hrState == BleHeartRateState.BluetoothOff -> "Bluetooth выключен"; else -> "BLE доступен" },
                    RtBlue,
                    actionText = when { !btSupported -> "Недоступно"; !scanPermission || !connectPermission -> "Разрешить"; hrState == BleHeartRateState.BluetoothOff -> "Включить"; else -> "Обновить" },
                    positive = false,
                    onClick = if (!btSupported) null else { { when { !scanPermission || !connectPermission -> requestBlePermissions(); hrState == BleHeartRateState.BluetoothOff -> context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); else -> viewModel.refreshBluetoothState() } } },
                )
            }
            item { ConnectionCard(Icons.Outlined.FavoriteBorder, "Пульсометр", hrSubtitle, RtRed, hrAction, positive = hrConnected, onClick = if (hrState == BleHeartRateState.Unsupported) null else { {
                when (hrState) {
                    BleHeartRateState.PermissionRequired -> requestBlePermissions()
                    BleHeartRateState.BluetoothOff -> context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    BleHeartRateState.Unsupported -> Unit
                    BleHeartRateState.Scanning -> viewModel.cancelHeartRateScan()
                    is BleHeartRateState.Connected -> viewModel.disconnectHeartRateDevice()
                    is BleHeartRateState.Connecting, is BleHeartRateState.Subscribing -> viewModel.disconnectHeartRateDevice()
                    BleHeartRateState.Idle -> if (appSettings.heartRateDeviceAddress != null) viewModel.connectSavedHeartRateDevice() else viewModel.startHeartRateScan()
                    is BleHeartRateState.Error -> viewModel.startHeartRateScan()
                }
            } }) }
            if (appSettings.heartRateDeviceAddress != null && hrState == BleHeartRateState.Idle) item {
                TextButton(onClick = { viewModel.startHeartRateScan() }) { Text("Найти другой пульсометр", color = RtBlue) }
            }
            if (devices.isNotEmpty() && hrState == BleHeartRateState.Scanning) {
                item { SectionTitle("Найденные устройства") }
                items(devices, key = { it.address }) { device ->
                    ConnectionCard(Icons.Outlined.FavoriteBorder, device.name, "RSSI ${device.rssi}", RtRed, "Подключить") { viewModel.connectHeartRateDevice(device.address) }
                }
            }
            item {
                val selected = selectedWorkout?.workout
                val selectedId = selected?.id
                val selectedCompleted = selected?.status == WorkoutStatus.COMPLETED.name
                val currentMessage = healthConnect.message.takeUnless {
                    healthConnect.lastExportedWorkoutId != selectedId && it == "Тренировка записана в Health Connect"
                }
                val healthSubtitle = currentMessage ?: when (healthConnect.availability) {
                    HealthConnectAvailability.UNAVAILABLE -> "Недоступен на этом устройстве"
                    HealthConnectAvailability.UPDATE_REQUIRED -> "Нужно установить или обновить Health Connect"
                    HealthConnectAvailability.AVAILABLE -> when {
                        !healthConnect.permissionsGranted -> "Только запись по вашему выбору · без GPS-маршрута"
                        selectedId == null -> "Подключён · сначала откройте сохранённую тренировку"
                        !selectedCompleted -> "Можно записать только завершённую тренировку"
                        healthConnect.lastExportedWorkoutId == selectedId -> "Тренировка записана · GPS-маршрут не передан"
                        else -> "Готов записать выбранную тренировку · без GPS-маршрута"
                    }
                }
                val healthAction = when {
                    healthConnect.inProgress -> "Запись…"
                    healthConnect.availability == HealthConnectAvailability.UNAVAILABLE -> "Недоступно"
                    healthConnect.availability == HealthConnectAvailability.UPDATE_REQUIRED -> "Обновить"
                    !healthConnect.permissionsGranted -> "Разрешить"
                    !selectedCompleted -> "Выберите"
                    else -> "Экспорт"
                }
                val healthActionHandler: (() -> Unit)? = when {
                    healthConnect.inProgress -> null
                    healthConnect.availability == HealthConnectAvailability.UNAVAILABLE -> null
                    healthConnect.availability == HealthConnectAvailability.UPDATE_REQUIRED -> {
                        {
                            runCatching { context.startActivity(viewModel.healthConnectSettingsIntent()) }
                                .onFailure { Toast.makeText(context, "Не удалось открыть Health Connect", Toast.LENGTH_SHORT).show() }
                        }
                    }
                    !healthConnect.permissionsGranted -> {
                        { healthPermissionLauncher.launch(viewModel.healthConnectPermissions) }
                    }
                    !selectedCompleted -> null
                    else -> viewModel::exportSelectedWorkoutToHealthConnect
                }
                ConnectionCard(
                    Icons.Outlined.HealthAndSafety,
                    "Health Connect",
                    healthSubtitle,
                    RtGreen,
                    healthAction,
                    positive = healthConnect.permissionsGranted,
                    onClick = healthActionHandler,
                )
            }
            item { ConnectionCard(Icons.Outlined.Cloud, "Облачная синхронизация", "Через SAF / DocumentProvider · только по выбору пользователя", RtGreen, "Настроить") { onNavigate(17) } }
            item {
                val systemReady = gps.finePermissionGranted && gps.locationEnabled
                val systemText = when { !gps.finePermissionGranted -> "Нужно разрешение Location"; !gps.locationEnabled -> "Геолокация Android выключена"; else -> "Location разрешено · сервис включён" }
                ConnectionCard(Icons.Outlined.Smartphone, "Системные данные", systemText, RtYellow, if (systemReady) "Готово" else "Открыть", positive = systemReady) {
                    when {
                        !gps.finePermissionGranted -> permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                        !gps.locationEnabled -> context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        else -> context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    actionText: String,
    positive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(RtSurface, RoundedCornerShape(13.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).background(accent.copy(alpha = 0.14f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(23.dp)) }
        Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium); Text(subtitle, color = RtMuted, fontSize = 9.sp) }
        Text(actionText, color = if (positive) RtGreen else if (onClick != null) RtBlue else RtMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ExportScreen(viewModel: RunTrackViewModel, onNavigate: (Int) -> Unit) {
    val relation by viewModel.selectedWorkout.collectAsStateWithLifecycle(); val settings by viewModel.settings.collectAsStateWithLifecycle(); val context = LocalContext.current
    var format by remember { mutableStateOf(ExportFormat.IMAGE) }; var includeMap by remember { mutableStateOf(true) }; var includeSplits by remember { mutableStateOf(true) }; var pending by remember { mutableStateOf<ExportPayload?>(null) }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope(); val manager = remember { WorkoutExportManager(context.applicationContext) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val payload = pending; val uri = result.data?.data
        pending = null
        if (result.resultCode == Activity.RESULT_OK && payload != null && uri != null) {
            scope.launch {
                busy = true
                runCatching { withContext(Dispatchers.IO) { manager.writeToUri(uri, payload) } }
                    .onFailure { error = it.message ?: "Файл не сохранён" }
                busy = false
            }
        } else if (result.resultCode != Activity.RESULT_CANCELED) error = "Файл не сохранён"
    }
    val r = relation
    if (r == null) { MissingDataPage("Экспорт тренировки", onNavigate); return }
    fun buildThen(action: suspend (ExportPayload) -> Unit) { if (busy) return; busy = true; error = null; scope.launch {
        val result = runCatching { withContext(Dispatchers.Default) { manager.build(r, format, ExportOptions(includeMap, includeSplits, settings.units)) } }
        if (result.isSuccess) action(result.getOrThrow()) else error = result.exceptionOrNull()?.message ?: "Экспорт не выполнен"
        busy = false
    } }
    NativePage(title = "Экспорт тренировки", subtitle = r.workout.displayTitle(), onBack = { onNavigate(8) }, onNavigate = onNavigate) {
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            SectionTitle("Формат"); Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ExportFormat.entries.forEach { candidate -> Box(Modifier.weight(1f).height(42.dp).background(if (format == candidate) RtGreen else RtSurface2, RoundedCornerShape(11.dp)).clickable { format = candidate }, contentAlignment = Alignment.Center) { Text(when (candidate) { ExportFormat.IMAGE -> "Изображение"; ExportFormat.GPX -> "GPX"; ExportFormat.CSV -> "CSV" }, color = if (format == candidate) RtBg else Color.White, fontSize = 10.sp) } } }
            Spacer(Modifier.height(16.dp)); SettingSwitch(Icons.Outlined.Map, "Добавить маршрут", if (format == ExportFormat.IMAGE) "Маршрут в изображении" else "Для GPX/CSV не применяется", includeMap && format == ExportFormat.IMAGE) { if (format == ExportFormat.IMAGE) includeMap = it }
            Spacer(Modifier.height(8.dp)); SettingSwitch(Icons.Outlined.Timeline, "Добавить разбивку", if (format == ExportFormat.GPX) "GPX хранит трек без таблицы splits" else "Разбивка в поддерживаемом формате", includeSplits && format != ExportFormat.GPX) { if (format != ExportFormat.GPX) includeSplits = it }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(190.dp).background(RtSurface, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.Share, null, tint = RtGreen, modifier = Modifier.size(34.dp)); Spacer(Modifier.height(8.dp)); Text("${RunTrackFormatter.distance(r.workout.distanceMeters, settings.units)} · ${RunTrackFormatter.duration(r.workout.elapsedMillis)}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text("Предпросмотр из сохранённой тренировки", color = RtMuted, fontSize = 10.sp) } }
            error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = RtRed, fontSize = 10.sp) }
            Spacer(Modifier.weight(1f))
            PrimaryAction("Поделиться", { buildThen { payload -> val share = withContext(Dispatchers.IO) { manager.createShareIntent(payload) }; context.startActivity(Intent.createChooser(share, "Поделиться тренировкой")) } }, Modifier.fillMaxWidth(), Icons.Outlined.Share, loading = busy)
            Spacer(Modifier.height(9.dp))
            SecondaryAction("Сохранить файл", { buildThen { payload -> pending = payload; saveLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = payload.mimeType; putExtra(Intent.EXTRA_TITLE, payload.fileName) }) } }, Modifier.fillMaxWidth(), Icons.Outlined.Download, loading = busy)
            Spacer(Modifier.height(14.dp))
        }
    }
}

private fun openAppLanguageSettings(context: android.content.Context) {
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, "Настройки языка недоступны", Toast.LENGTH_SHORT).show() }
    } else {
        Toast.makeText(context, "На этой версии Android язык приложения следует системной локали", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun MissingDataPage(title: String, onNavigate: (Int) -> Unit) {
    NativePage(title = title, subtitle = "Нет сохранённых данных", onBack = { onNavigate(9) }, onNavigate = onNavigate) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Нет данных", color = RtMuted) }
    }
}

@Composable
private fun RadioButtonRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick); Spacer(Modifier.width(8.dp)); Text(title)
    }
}

private fun goalProgressText(snapshot: com.runtrack.app.tracking.TrackingSnapshot, units: UnitSystem): String = when (snapshot.goal.kind) {
    GoalKind.NONE -> ""
    GoalKind.DISTANCE -> {
        val target = snapshot.goal.distanceMeters ?: return "Цель недоступна"
        val progress = (snapshot.distanceMeters / target * 100.0).coerceIn(0.0, 999.0)
        if (snapshot.goalReached) "Цель достигнута · ${RunTrackFormatter.distance(target, units)}" else "Цель ${RunTrackFormatter.distance(target, units)} · ${progress.toInt()}%"
    }
    GoalKind.DURATION -> {
        val target = snapshot.goal.durationMillis ?: return "Цель недоступна"
        val progress = (snapshot.elapsedMillis.toDouble() / target * 100.0).coerceIn(0.0, 999.0)
        if (snapshot.goalReached) "Цель достигнута · ${RunTrackFormatter.duration(target)}" else "Цель ${RunTrackFormatter.duration(target)} · ${progress.toInt()}%"
    }
}

private fun WorkoutEntity.typeOrNull(): WorkoutType? = runCatching { WorkoutType.valueOf(type) }.getOrNull()
private fun WorkoutEntity.displayTitle(): String = title ?: when (typeOrNull()) { WorkoutType.RUN -> "Пробежка"; WorkoutType.WALK -> "Прогулка"; WorkoutType.BIKE -> "Велопоездка"; null -> "Тренировка" }
private fun WorkoutEntity.resultSubtitle(): String = "${Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMMM"))} · ${workoutTypeTitle(typeOrNull() ?: WorkoutType.RUN)}"
private fun workoutTypeTitle(type: WorkoutType): String = when (type) { WorkoutType.RUN -> "Бег"; WorkoutType.WALK -> "Ходьба"; WorkoutType.BIKE -> "Велосипед" }
private fun WorkoutType.icon(): ImageVector = when (this) { WorkoutType.RUN -> Icons.Outlined.DirectionsRun; WorkoutType.WALK -> Icons.Outlined.DirectionsWalk; WorkoutType.BIKE -> Icons.Outlined.DirectionsBike }
private fun WorkoutType.accent(): Color = when (this) { WorkoutType.RUN -> RtGreen; WorkoutType.WALK -> RtYellow; WorkoutType.BIKE -> RtBlue }
private fun MapLayer.toggle(): MapLayer = if (this == MapLayer.STANDARD) MapLayer.TERRAIN else MapLayer.STANDARD
private fun RoutePointEntity.asLocationSample(): LocationSample = LocationSample(timestampMillis, latitude, longitude, accuracyMeters, altitudeMeters, speedMps, bearingDegrees, provider, elapsedRealtimeMillis)
private fun elevationPairText(w: WorkoutEntity, units: UnitSystem): String = if (w.elevationGainMeters == null && w.elevationLossMeters == null) "Нет данных" else "+${RunTrackFormatter.elevation(w.elevationGainMeters ?: 0.0, units)} · −${RunTrackFormatter.elevation(w.elevationLossMeters ?: 0.0, units)}"

@Composable
private fun WeatherSummaryCard(
    snapshot: WeatherSnapshotEntity?,
    units: UnitSystem,
    emptyText: String,
) {
    val display = remember(snapshot, units) { snapshot?.let { WeatherFormatter.format(it, units) } }
    Row(
        modifier = Modifier.fillMaxWidth().background(RtSurface, RoundedCornerShape(13.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).background(RtBlue.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Cloud, null, tint = RtBlue, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(display?.headline ?: "Погода", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(display?.details ?: emptyText, color = RtMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun MissingScreen(index: Int, onNavigate: (Int) -> Unit) {
    NativePage(
        title = "Экран ${index + 1}",
        subtitle = "Экран не найден",
        onBack = { onNavigate(0) },
        onNavigate = onNavigate,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Нет содержимого", color = RtMuted)
        }
    }
}

@Composable
private fun LiveRouteMap(segments: List<List<LocationSample>>, layer: MapLayer, onLayer: () -> Unit, modifier: Modifier = Modifier) {
    RunTrackRouteMap(segments, layer, onLayer, modifier)
}

@Composable
private fun RouteCanvas(modifier: Modifier = Modifier, routes: List<List<LocationSample>>, layer: MapLayer, onLayer: () -> Unit) {
    RunTrackRouteMap(routes, layer, onLayer, modifier)
}

@Composable
private fun RouteArtCanvas(segments: List<List<LocationSample>>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val normalizedSegments = RouteGeometry.normalizeRoutes(segments.filter { it.isNotEmpty() }, size.width, size.height, 18.dp.toPx())
        normalizedSegments.forEach { normalized ->
            if (normalized.size == 1) drawCircle(RtGreen, 7.dp.toPx(), Offset(normalized[0].x, normalized[0].y))
            else if (normalized.size >= 2) {
                val path = Path().apply { moveTo(normalized.first().x, normalized.first().y); normalized.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(path, RtGreen, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        val first = normalizedSegments.firstOrNull { it.isNotEmpty() }?.firstOrNull()
        val last = normalizedSegments.lastOrNull { it.isNotEmpty() }?.lastOrNull()
        if (first != null) drawCircle(RtGreen, 7.dp.toPx(), Offset(first.x, first.y))
        if (last != null) drawCircle(RtGreen, 8.dp.toPx(), Offset(last.x, last.y), style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun ChartCard(modifier: Modifier = Modifier, bars: Boolean, values: List<Float> = emptyList()) {
    Box(modifier.background(RtSurface, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height; val grid = Color(0xFF22313A)
            for (i in 0..4) { val y = h * i / 4f; drawLine(grid, Offset(0f, y), Offset(w, y), 1.dp.toPx()) }
            val safe = values.map { it.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f }
            if (safe.isNotEmpty() && bars) {
                val gap = w / (safe.size * 2f + 1f); val bw = gap
                safe.forEachIndexed { i, v -> val x = gap + i * (bw + gap); val top = h * (1f - v); drawRoundRect(if (i == safe.lastIndex) RtGreen else RtBlue.copy(alpha = 0.75f), Offset(x, top), androidx.compose.ui.geometry.Size(bw, h - top), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())) }
            } else if (safe.size >= 2) {
                val path = Path(); safe.forEachIndexed { i, v -> val x = w * i / (safe.size - 1).toFloat(); val y = h * (1f - v); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }; drawPath(path, RtGreen, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)); safe.forEachIndexed { i, v -> drawCircle(RtGreen, 4.dp.toPx(), Offset(w * i / (safe.size - 1).toFloat(), h * (1f - v))) }
            }
        }
        if (values.isEmpty()) Text("Нет данных", color = RtMuted, fontSize = 10.sp, modifier = Modifier.align(Alignment.Center))
    }
}

