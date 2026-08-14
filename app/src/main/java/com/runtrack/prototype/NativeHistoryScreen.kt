package com.runtrack.prototype

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runtrack.prototype.data.WorkoutEntity
import com.runtrack.prototype.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HistoryBg = Color(0xFF07131B)
private val HistoryCard = Color(0xFF101C25)
private val HistoryChip = Color(0xFF14222C)
private val HistoryGreen = Color(0xFF62D72F)
private val HistoryMuted = Color(0xFF8E9AA3)
private val HistoryYellow = Color(0xFFF4B400)
private val HistoryBlue = Color(0xFF42A5F5)

private enum class HistoryFilter(val label: String, val type: WorkoutType?) {
    ALL("Все", null), RUN("Бег", WorkoutType.RUN), WALK("Ходьба", WorkoutType.WALK), BIKE("Велосипед", WorkoutType.BIKE),
}

@Composable
fun NativeHistoryScreen(
    viewModel: RunTrackViewModel,
    onHome: () -> Unit,
    onStats: () -> Unit,
    onProfile: () -> Unit,
    onOpenWorkout: (String) -> Unit,
) {
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var pendingDelete by remember { mutableStateOf<WorkoutEntity?>(null) }
    val history by viewModel.history.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val visible = remember(history, selectedFilter) {
        history.filter { workout -> selectedFilter.type == null || workout.type == selectedFilter.type?.name }
    }
    val zone = remember { ZoneId.systemDefault() }
    val grouped = remember(visible, zone) { visible.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() } }

    Column(Modifier.fillMaxSize().background(HistoryBg)) {
        Text("История", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp))
        FilterRow(selectedFilter, { selectedFilter = it }, Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(12.dp))
        if (visible.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (history.isEmpty()) "Завершённых тренировок пока нет" else "Нет тренировок этого типа", color = HistoryMuted, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                grouped.forEach { (date, workouts) ->
                    item(key = "header_$date") {
                        Text(groupLabel(date), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                    }
                    items(workouts, key = { it.id }) { workout ->
                        WorkoutHistoryCard(
                            workout = workout,
                            units = settings.units,
                            onClick = { viewModel.selectWorkout(workout.id); onOpenWorkout(workout.id) },
                            onLongClick = { pendingDelete = workout },
                        )
                    }
                }
            }
        }
        HistoryBottomBar(1, onHome, null, onStats, onProfile)
    }

    pendingDelete?.let { workout ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить тренировку?") },
            text = { Text("Маршрут и связанные локальные данные этой тренировки будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWorkout(workout.id) { pendingDelete = null }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun FilterRow(selected: HistoryFilter, onSelected: (HistoryFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HistoryFilter.entries.forEach { filter ->
            val active = filter == selected
            Box(
                Modifier.weight(1f).height(40.dp).background(if (active) HistoryGreen else HistoryChip, RoundedCornerShape(11.dp)).clickable { onSelected(filter) },
                contentAlignment = Alignment.Center,
            ) {
                Text(filter.label, color = if (active) HistoryBg else Color.White, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkoutHistoryCard(workout: WorkoutEntity, units: UnitSystem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val type = runCatching { WorkoutType.valueOf(workout.type) }.getOrDefault(WorkoutType.RUN)
    val accent = when (type) { WorkoutType.RUN -> HistoryGreen; WorkoutType.WALK -> HistoryYellow; WorkoutType.BIKE -> HistoryBlue }
    val icon = when (type) { WorkoutType.RUN -> Icons.Outlined.DirectionsRun; WorkoutType.WALK -> Icons.Outlined.DirectionsWalk; WorkoutType.BIKE -> Icons.Outlined.DirectionsBike }
    val metrics = WorkoutMath.metrics(workout.distanceMeters, workout.elapsedMillis, workout.movingMillis)
    val performance = if (type == WorkoutType.BIKE) RunTrackFormatter.speed(workout.averageSpeedMps, units) else RunTrackFormatter.pace(metrics.paceSecondsPerKm, units)
    val time = Instant.ofEpochMilli(workout.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).background(HistoryCard, RoundedCornerShape(13.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(accent.copy(alpha = 0.14f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(workout.title ?: defaultTitle(type), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text("${RunTrackFormatter.distance(workout.distanceMeters, units)}  •  ${RunTrackFormatter.duration(workout.elapsedMillis)}  •  $performance", color = HistoryMuted, fontSize = 10.sp, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp)); Text(time, color = Color(0xFFB8C2C9), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private fun groupLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> date.format(DateTimeFormatter.ofPattern("d MMMM"))
    }
}

private fun defaultTitle(type: WorkoutType) = when (type) { WorkoutType.RUN -> "Пробежка"; WorkoutType.WALK -> "Прогулка"; WorkoutType.BIKE -> "Велопоездка" }

@Composable
private fun HistoryBottomBar(selected: Int, onHome: (() -> Unit)?, onHistory: (() -> Unit)?, onStats: (() -> Unit)?, onProfile: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(70.dp).background(Color(0xFF0B151C)).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        HistoryNavItem("Главная", Icons.Outlined.Home, selected == 0, onHome)
        HistoryNavItem("История", Icons.Outlined.History, selected == 1, onHistory)
        HistoryNavItem("Статистика", Icons.Outlined.BarChart, selected == 2, onStats)
        HistoryNavItem("Профиль", Icons.Outlined.Person, selected == 3, onProfile)
    }
}

@Composable
private fun RowScope.HistoryNavItem(title: String, icon: ImageVector, selected: Boolean, onClick: (() -> Unit)?) {
    Column(Modifier.weight(1f).fillMaxHeight().then(if (!selected && onClick != null) Modifier.clickable(onClick = onClick) else Modifier), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, title, tint = if (selected) HistoryGreen else HistoryMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp)); Text(title, color = if (selected) HistoryGreen else HistoryMuted, fontSize = 9.sp)
    }
}
