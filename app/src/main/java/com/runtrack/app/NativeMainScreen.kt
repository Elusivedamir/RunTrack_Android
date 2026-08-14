package com.runtrack.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runtrack.app.data.WorkoutWithRoute
import com.runtrack.app.domain.*
import com.runtrack.app.maps.RunTrackRouteMap
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Bg = Color(0xFF07131B)
private val Card = Color(0xFF101C25)
private val Card2 = Color(0xFF14222C)
private val Green = Color(0xFF62D72F)
private val Muted = Color(0xFF8E9AA3)
private val Yellow = Color(0xFFF4B400)
private val Blue = Color(0xFF42A5F5)

private enum class HomeTypeFilter { ALL, RUN, WALK, BIKE }

@Composable
fun NativeMainScreen(
    viewModel: RunTrackViewModel,
    onQuickStart: (WorkoutType) -> Unit,
    onHistory: () -> Unit,
    onStats: (() -> Unit)?,
    onProfile: (() -> Unit)?,
    onSettings: () -> Unit,
    onOpenWorkout: (String) -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allRoutes by viewModel.allRoutes.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    var showQuickStart by rememberSaveable { mutableStateOf(true) }
    var typeFilter by remember { mutableStateOf(HomeTypeFilter.ALL) }
    var periodDays by remember { mutableIntStateOf(30) }

    val cutoff = remember(periodDays) { System.currentTimeMillis() - periodDays * 86_400_000L }
    val visible = remember(allRoutes, typeFilter, cutoff) {
        allRoutes.filter { relation ->
            relation.workout.startedAt >= cutoff && when (typeFilter) {
                HomeTypeFilter.ALL -> true
                HomeTypeFilter.RUN -> relation.workout.type == WorkoutType.RUN.name
                HomeTypeFilter.WALK -> relation.workout.type == WorkoutType.WALK.name
                HomeTypeFilter.BIKE -> relation.workout.type == WorkoutType.BIKE.name
            }
        }
    }
    val latest = visible.maxByOrNull { it.workout.startedAt }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Header(onFilters = { showFilters = true }, onSettings = onSettings)
        RouteMap(
            relation = latest,
            layer = settings.mapLayer,
            onLayer = { viewModel.setMapLayer(if (settings.mapLayer == MapLayer.STANDARD) MapLayer.TERRAIN else MapLayer.STANDARD) },
            modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(205.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (showQuickStart) {
            QuickStart(onQuickStart = onQuickStart, onDismiss = { showQuickStart = false }, modifier = Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(12.dp))
        }
        RecentWorkout(latest, settings.units, onHistory, onOpenWorkout, Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.weight(1f))
        BottomBar(0, null, onHistory, onStats, onProfile)
    }

    if (showFilters) {
        AlertDialog(
            onDismissRequest = { showFilters = false },
            confirmButton = { TextButton(onClick = { showFilters = false }) { Text("Готово") } },
            title = { Text("Фильтры") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Тип активности")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(HomeTypeFilter.ALL to "Все", HomeTypeFilter.RUN to "Бег", HomeTypeFilter.WALK to "Ходьба", HomeTypeFilter.BIKE to "Вело").forEach { (filter, label) ->
                            FilterChip(selected = typeFilter == filter, onClick = { typeFilter = filter }, label = { Text(label) })
                        }
                    }
                    Text("Период")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(7 to "7 дней", 30 to "30 дней", 365 to "Год").forEach { (days, label) ->
                            FilterChip(selected = periodDays == days, onClick = { periodDays = days }, label = { Text(label) })
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun Header(onFilters: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Доброе утро!", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Готов к новой тренировке?", color = Muted, fontSize = 12.sp)
        }
        Box(Modifier.size(38.dp).clickable(onClick = onFilters), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Tune, "Фильтры", tint = Color(0xFFB8C2C9), modifier = Modifier.size(20.dp))
        }
        Box(Modifier.size(38.dp).clickable(onClick = onSettings), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Settings, "Настройки", tint = Color(0xFFB8C2C9), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RouteMap(relation: WorkoutWithRoute?, layer: MapLayer, onLayer: () -> Unit, modifier: Modifier = Modifier) {
    val segments = remember(relation) {
        relation?.route?.groupBy { it.segmentIndex }?.toSortedMap()?.values
            ?.map { segment -> RouteGeometry.downsampleForRender(segment.sortedBy { it.movingElapsedMillis }.map { it.toSample() }) }
            ?.filter { it.isNotEmpty() }.orEmpty()
    }
    RunTrackRouteMap(segments, layer, onLayer, modifier)
}

@Composable
private fun QuickStart(onQuickStart: (WorkoutType) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Быстрый старт", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text("Выбери тип тренировки", color = Muted, fontSize = 11.sp)
            }
            Box(Modifier.size(40.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "Скрыть быстрый старт", tint = Muted, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActivityCard("Бег", Icons.Outlined.DirectionsRun, Green, { onQuickStart(WorkoutType.RUN) }, Modifier.weight(1f))
            ActivityCard("Ходьба", Icons.Outlined.DirectionsWalk, Yellow, { onQuickStart(WorkoutType.WALK) }, Modifier.weight(1f))
            ActivityCard("Велосипед", Icons.Outlined.DirectionsBike, Blue, { onQuickStart(WorkoutType.BIKE) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActivityCard(title: String, icon: ImageVector, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.height(92.dp).background(Card2, RoundedCornerShape(13.dp)).clickable(onClick = onClick).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(40.dp).background(accent.copy(alpha = 0.18f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.height(6.dp)); Text(title, color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun RecentWorkout(relation: WorkoutWithRoute?, units: UnitSystem, onHistory: () -> Unit, onOpenWorkout: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Недавние тренировки", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("Смотреть все", color = Muted, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onHistory))
        }
        Spacer(Modifier.height(8.dp))
        if (relation == null) {
            Box(Modifier.fillMaxWidth().heightIn(min = 72.dp).background(Card, RoundedCornerShape(13.dp)).padding(12.dp), contentAlignment = Alignment.CenterStart) {
                Text("Завершённых тренировок пока нет", color = Muted, fontSize = 11.sp)
            }
        } else {
            val workout = relation.workout
            val type = runCatching { WorkoutType.valueOf(workout.type) }.getOrDefault(WorkoutType.RUN)
            val accent = when (type) { WorkoutType.RUN -> Green; WorkoutType.WALK -> Yellow; WorkoutType.BIKE -> Blue }
            val icon = when (type) { WorkoutType.RUN -> Icons.Outlined.DirectionsRun; WorkoutType.WALK -> Icons.Outlined.DirectionsWalk; WorkoutType.BIKE -> Icons.Outlined.DirectionsBike }
            val metrics = WorkoutMath.metrics(workout.distanceMeters, workout.elapsedMillis, workout.movingMillis)
            val performance = if (type == WorkoutType.BIKE) RunTrackFormatter.speed(workout.averageSpeedMps, units) else RunTrackFormatter.pace(metrics.paceSecondsPerKm, units)
            Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(13.dp)).clickable { onOpenWorkout(workout.id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(accent.copy(alpha = 0.14f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(workout.title ?: typeLabel(type), color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("${RunTrackFormatter.distance(workout.distanceMeters, units)}  •  ${RunTrackFormatter.duration(workout.elapsedMillis)}  •  $performance", color = Muted, fontSize = 10.sp)
                }
                Text(Instant.ofEpochMilli(workout.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy")), color = Color(0xFF73818B), fontSize = 9.sp)
            }
        }
    }
}

private fun typeLabel(type: WorkoutType) = when (type) { WorkoutType.RUN -> "Пробежка"; WorkoutType.WALK -> "Прогулка"; WorkoutType.BIKE -> "Велопоездка" }

private fun com.runtrack.app.data.RoutePointEntity.toSample() = LocationSample(timestampMillis, latitude, longitude, accuracyMeters, altitudeMeters, speedMps, bearingDegrees, provider, elapsedRealtimeMillis)

@Composable
private fun BottomBar(selected: Int, onHome: (() -> Unit)?, onHistory: (() -> Unit)?, onStats: (() -> Unit)?, onProfile: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(70.dp).background(Color(0xFF0B151C)).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        NavItem("Главная", Icons.Outlined.Home, selected == 0, onHome)
        NavItem("История", Icons.Outlined.History, selected == 1, onHistory)
        NavItem("Статистика", Icons.Outlined.BarChart, selected == 2, onStats)
        NavItem("Профиль", Icons.Outlined.Person, selected == 3, onProfile)
    }
}

@Composable
private fun RowScope.NavItem(title: String, icon: ImageVector, selected: Boolean, onClick: (() -> Unit)?) {
    Column(Modifier.weight(1f).fillMaxHeight().then(if (!selected && onClick != null) Modifier.clickable(onClick = onClick) else Modifier), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, title, tint = if (selected) Green else Muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp)); Text(title, color = if (selected) Green else Muted, fontSize = 9.sp)
    }
}
