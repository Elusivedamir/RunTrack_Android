package com.runtrack.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HistoryBg = Color(0xFF07131B)
private val HistoryCard = Color(0xFF101C25)
private val HistoryChip = Color(0xFF14222C)
private val HistoryGreen = Color(0xFF62D72F)
private val HistoryMuted = Color(0xFF8E9AA3)
private val HistoryYellow = Color(0xFFF4B400)
private val HistoryBlue = Color(0xFF42A5F5)

private enum class HistoryFilter(val label: String) {
    ALL("Все"),
    RUN("Бег"),
    WALK("Ходьба"),
    BIKE("Велосипед"),
}

private data class HistoryWorkout(
    val group: String,
    val title: String,
    val details: String,
    val time: String,
    val type: HistoryFilter,
    val icon: ImageVector,
    val accent: Color,
)

private val historyWorkouts = listOf(
    HistoryWorkout(
        group = "Сегодня",
        title = "Утренняя пробежка",
        details = "7,42 км  •  42:18  •  5:42 /км",
        time = "07:12",
        type = HistoryFilter.RUN,
        icon = Icons.Outlined.DirectionsRun,
        accent = HistoryGreen,
    ),
    HistoryWorkout(
        group = "Вчера",
        title = "Вечерняя прогулка",
        details = "4,83 км  •  1:03:15  •  13:06 /км",
        time = "19:45",
        type = HistoryFilter.WALK,
        icon = Icons.Outlined.DirectionsWalk,
        accent = HistoryYellow,
    ),
    HistoryWorkout(
        group = "11 августа",
        title = "Велопоездка",
        details = "26,31 км  •  1:14:42  •  21,1 км/ч",
        time = "17:08",
        type = HistoryFilter.BIKE,
        icon = Icons.Outlined.DirectionsBike,
        accent = HistoryBlue,
    ),
    HistoryWorkout(
        group = "10 августа",
        title = "Пробежка",
        details = "5,21 км  •  28:21  •  5:26 /км",
        time = "08:11",
        type = HistoryFilter.RUN,
        icon = Icons.Outlined.DirectionsRun,
        accent = HistoryGreen,
    ),
)

@Composable
fun NativeHistoryScreen(
    onHome: () -> Unit,
    onStats: () -> Unit,
    onProfile: () -> Unit,
) {
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }

    val visibleWorkouts = if (selectedFilter == HistoryFilter.ALL) {
        historyWorkouts
    } else {
        historyWorkouts.filter { it.type == selectedFilter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HistoryBg)
    ) {
        Text(
            text = "История",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = 14.dp
            )
        )

        FilterRow(
            selected = selectedFilter,
            onSelected = { selectedFilter = it },
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                bottom = 14.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val grouped = visibleWorkouts.groupBy { it.group }
            grouped.forEach { (group, workouts) ->
                item(key = "header_$group") {
                    Text(
                        text = group,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }

                items(
                    items = workouts,
                    key = { "${it.group}_${it.title}_${it.time}" }
                ) { workout ->
                    WorkoutHistoryCard(workout)
                }
            }
        }

        HistoryBottomBar(
            selected = 1,
            onHome = onHome,
            onHistory = {},
            onStats = onStats,
            onProfile = onProfile,
        )
    }
}

@Composable
private fun FilterRow(
    selected: HistoryFilter,
    onSelected: (HistoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HistoryFilter.entries.forEach { filter ->
            val active = filter == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(
                        color = if (active) HistoryGreen else HistoryChip,
                        shape = RoundedCornerShape(11.dp)
                    )
                    .clickable { onSelected(filter) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = filter.label,
                    color = if (active) HistoryBg else Color.White,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WorkoutHistoryCard(workout: HistoryWorkout) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(HistoryCard, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    workout.accent.copy(alpha = 0.14f),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = workout.icon,
                contentDescription = null,
                tint = workout.accent,
                modifier = Modifier.size(23.dp),
            )
        }

        Spacer(Modifier.width(11.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = workout.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = workout.details,
                color = HistoryMuted,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = workout.time,
            color = Color(0xFFB8C2C9),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun HistoryBottomBar(
    selected: Int,
    onHome: () -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
    onProfile: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(Color(0xFF0B151C))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HistoryNavItem("Главная", Icons.Outlined.Home, selected == 0, onHome)
        HistoryNavItem("История", Icons.Outlined.History, selected == 1, onHistory)
        HistoryNavItem("Статистика", Icons.Outlined.BarChart, selected == 2, onStats)
        HistoryNavItem("Профиль", Icons.Outlined.Person, selected == 3, onProfile)
    }
}

@Composable
private fun RowScope.HistoryNavItem(
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
            tint = if (selected) HistoryGreen else HistoryMuted,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = title,
            color = if (selected) HistoryGreen else HistoryMuted,
            fontSize = 9.sp,
        )
    }
}
