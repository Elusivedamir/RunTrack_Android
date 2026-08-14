package com.runtrack.prototype

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RtBg = Color(0xFF07131B)
private val RtSurface = Color(0xFF101C25)
private val RtSurface2 = Color(0xFF14222C)
private val RtGreen = Color(0xFF62D72F)
private val RtMuted = Color(0xFF8E9AA3)
private val RtText2 = Color(0xFFB8C2C9)
private val RtYellow = Color(0xFFF4B400)
private val RtBlue = Color(0xFF42A5F5)
private val RtRed = Color(0xFFFF6B6B)

@Composable
fun NativeRemainingScreen(
    index: Int,
    onNavigate: (Int) -> Unit,
) {
    when (index) {
        1 -> WorkoutSetupScreen(onNavigate)
        2 -> ActiveWorkoutScreen(onNavigate)
        3 -> PausedWorkoutScreen(onNavigate)
        4 -> FinishWorkoutScreen(onNavigate)
        5 -> ResultOverviewScreen(onNavigate)
        6 -> ResultMapScreen(onNavigate)
        7 -> ResultRouteScreen(onNavigate)
        8 -> WorkoutDetailsScreen(onNavigate)
        10 -> StatsOverviewScreen(onNavigate)
        11 -> StatsChartsScreen(onNavigate)
        12 -> RecordsScreen(onNavigate)
        13 -> ComparisonScreen(onNavigate)
        14 -> ProfileScreen(onNavigate)
        15 -> CalendarScreen(onNavigate)
        16 -> AllRoutesScreen(onNavigate)
        17 -> SettingsScreen(onNavigate)
        18 -> ConnectionsScreen(onNavigate)
        19 -> ExportScreen(onNavigate)
        else -> MissingScreen(index, onNavigate)
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
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RtGreen,
            contentColor = RtBg,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    danger: Boolean = false,
) {
    val accent = if (danger) RtRed else RtText2
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
private fun WorkoutSetupScreen(onNavigate: (Int) -> Unit) {
    var type by remember { mutableIntStateOf(0) }
    var goal by remember { mutableIntStateOf(0) }

    NativePage(
        title = "Новая тренировка",
        subtitle = "Настрой параметры перед стартом",
        onBack = { onNavigate(0) },
        onNavigate = onNavigate,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            SectionTitle("Тип тренировки")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ActivityChoice("Бег", Icons.Outlined.DirectionsRun, RtGreen, type == 0, Modifier.weight(1f)) { type = 0 }
                ActivityChoice("Ходьба", Icons.Outlined.DirectionsWalk, RtYellow, type == 1, Modifier.weight(1f)) { type = 1 }
                ActivityChoice("Велосипед", Icons.Outlined.DirectionsBike, RtBlue, type == 2, Modifier.weight(1f)) { type = 2 }
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Цель")
            Spacer(Modifier.height(9.dp))
            GoalChoice("Без цели", "Свободная тренировка", goal == 0) { goal = 0 }
            Spacer(Modifier.height(8.dp))
            GoalChoice("5,00 км", "Дистанция", goal == 1) { goal = 1 }
            Spacer(Modifier.height(8.dp))
            GoalChoice("45:00", "Время", goal == 2) { goal = 2 }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RtSurface, RoundedCornerShape(13.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .background(RtGreen.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.MyLocation, null, tint = RtGreen, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("GPS готов", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Высокая точность определения позиции", color = RtMuted, fontSize = 10.sp)
                }
                Icon(Icons.Outlined.CheckCircle, null, tint = RtGreen, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.weight(1f))
            PrimaryAction(
                text = "Начать тренировку",
                icon = Icons.Outlined.PlayArrow,
                onClick = { onNavigate(2) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}

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
private fun ActiveWorkoutScreen(onNavigate: (Int) -> Unit) {
    NativePage(
        title = "Бег",
        subtitle = "Тренировка идёт",
        onNavigate = onNavigate,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LiveRouteMap(Modifier.fillMaxWidth().height(245.dp))
            Spacer(Modifier.height(18.dp))

            Text("3,84", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Bold)
            Text("км", color = RtMuted, fontSize = 14.sp)

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricBox("Время", "21:34", Modifier.weight(1f))
                MetricBox("Темп", "5:37 /км", Modifier.weight(1f))
                MetricBox("Ккал", "264", Modifier.weight(1f))
            }

            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryAction(
                    text = "Пауза",
                    icon = Icons.Outlined.Pause,
                    onClick = { onNavigate(3) },
                    modifier = Modifier.weight(1f),
                )
                SecondaryAction(
                    text = "Финиш",
                    icon = Icons.Outlined.Stop,
                    danger = true,
                    onClick = { onNavigate(4) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun PausedWorkoutScreen(onNavigate: (Int) -> Unit) {
    NativePage(
        title = "Пауза",
        subtitle = "Тренировка приостановлена",
        onNavigate = onNavigate,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .background(RtYellow.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Pause, null, tint = RtYellow, modifier = Modifier.size(45.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("3,84 км", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("21:34", color = RtMuted, fontSize = 18.sp)

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricBox("Средний темп", "5:37 /км", Modifier.weight(1f))
                MetricBox("Калории", "264 ккал", Modifier.weight(1f))
            }

            Spacer(Modifier.weight(1f))
            PrimaryAction(
                text = "Продолжить",
                icon = Icons.Outlined.PlayArrow,
                onClick = { onNavigate(2) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(9.dp))
            SecondaryAction(
                text = "Завершить тренировку",
                icon = Icons.Outlined.Stop,
                danger = true,
                onClick = { onNavigate(4) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun FinishWorkoutScreen(onNavigate: (Int) -> Unit) {
    NativePage(
        title = "Тренировка завершена",
        subtitle = "Отличная работа",
        onNavigate = onNavigate,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(RtGreen.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CheckCircle, null, tint = RtGreen, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("7,42 км", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Text("42:18", color = RtMuted, fontSize = 17.sp)

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricBox("Темп", "5:42 /км", Modifier.weight(1f))
                MetricBox("Калории", "512", Modifier.weight(1f))
                MetricBox("Набор", "68 м", Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))
            InfoRow(Icons.Outlined.FavoriteBorder, "Средний пульс", "148 уд/мин", RtRed)

            Spacer(Modifier.weight(1f))
            PrimaryAction(
                text = "Сохранить результат",
                icon = Icons.Outlined.Check,
                onClick = { onNavigate(5) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ResultOverviewScreen(onNavigate: (Int) -> Unit) {
    ResultPage(
        title = "Утренняя пробежка",
        activeTab = 0,
        onNavigate = onNavigate,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricBox("Дистанция", "7,42 км", Modifier.weight(1f), RtGreen)
            MetricBox("Время", "42:18", Modifier.weight(1f))
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricBox("Средний темп", "5:42 /км", Modifier.weight(1f))
            MetricBox("Калории", "512 ккал", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("Показатели")
        Spacer(Modifier.height(8.dp))
        InfoRow(Icons.Outlined.Speed, "Лучший темп", "4:58 /км", RtGreen)
        Spacer(Modifier.height(8.dp))
        InfoRow(Icons.Outlined.FavoriteBorder, "Пульс", "148 средний · 172 максимум", RtRed)
        Spacer(Modifier.height(8.dp))
        InfoRow(Icons.Outlined.Terrain, "Высота", "+68 м · −63 м", RtBlue)
        Spacer(Modifier.height(12.dp))
        SecondaryAction(
            text = "Все детали",
            icon = Icons.Outlined.Tune,
            onClick = { onNavigate(8) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ResultMapScreen(onNavigate: (Int) -> Unit) {
    ResultPage(
        title = "Утренняя пробежка",
        activeTab = 1,
        onNavigate = onNavigate,
    ) {
        RouteCanvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(355.dp),
            multiple = false,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricBox("Дистанция", "7,42 км", Modifier.weight(1f), RtGreen)
            MetricBox("Набор высоты", "68 м", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        InfoRow(Icons.Outlined.LocationOn, "Маршрут", "Старт и финиш сохранены", RtBlue)
    }
}

@Composable
private fun ResultRouteScreen(onNavigate: (Int) -> Unit) {
    ResultPage(
        title = "Утренняя пробежка",
        activeTab = 2,
        onNavigate = onNavigate,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp)
                .background(RtSurface, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            RouteArtCanvas(Modifier.fillMaxSize().padding(20.dp))
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("Разбивка по километрам")
        Spacer(Modifier.height(8.dp))
        PaceRow("1 км", "5:51", 0.72f)
        PaceRow("2 км", "5:39", 0.82f)
        PaceRow("3 км", "5:31", 0.91f)
        PaceRow("4 км", "5:46", 0.76f)
        PaceRow("5 км", "5:27", 0.95f)
    }
}

@Composable
private fun WorkoutDetailsScreen(onNavigate: (Int) -> Unit) {
    NativePage(
        title = "Детали тренировки",
        subtitle = "Утренняя пробежка · 14 августа",
        onBack = { onNavigate(5) },
        onNavigate = onNavigate,
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 14.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    MetricBox("Дистанция", "7,42 км", Modifier.weight(1f), RtGreen)
                    MetricBox("Время", "42:18", Modifier.weight(1f))
                }
            }
            item { InfoRow(Icons.Outlined.Speed, "Средний темп", "5:42 /км", RtGreen) }
            item { InfoRow(Icons.Outlined.Timer, "В движении", "41:56", RtBlue) }
            item { InfoRow(Icons.Outlined.FavoriteBorder, "Пульс", "148 / 172 уд/мин", RtRed) }
            item { InfoRow(Icons.Outlined.LocalFireDepartment, "Калории", "512 ккал", RtYellow) }
            item { InfoRow(Icons.Outlined.Terrain, "Высота", "+68 м / −63 м", RtBlue) }
            item { InfoRow(Icons.Outlined.DirectionsRun, "Каденс", "168 шаг/мин", RtGreen) }
            item { InfoRow(Icons.Outlined.Straighten, "Длина шага", "1,04 м", RtText2) }
            item {
                SecondaryAction(
                    text = "Экспорт тренировки",
                    icon = Icons.Outlined.Share,
                    onClick = { onNavigate(19) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ResultPage(
    title: String,
    activeTab: Int,
    onNavigate: (Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    NativePage(
        title = title,
        subtitle = "14 августа · Бег",
        onBack = { onNavigate(9) },
        onNavigate = onNavigate,
    ) {
        ResultTabs(activeTab = activeTab, onNavigate = onNavigate)
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
private fun StatsOverviewScreen(onNavigate: (Int) -> Unit) {
    StatsPage(title = "Статистика", active = 0, onNavigate = onNavigate) {
        SectionTitle("Эта неделя")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricBox("Тренировок", "4", Modifier.weight(1f), RtGreen)
            MetricBox("Дистанция", "32,7 км", Modifier.weight(1f))
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricBox("Время", "3:18:42", Modifier.weight(1f))
            MetricBox("Калории", "1 842", Modifier.weight(1f))
        }
        Spacer(Modifier.height(15.dp))
        SectionTitle("По активности")
        Spacer(Modifier.height(8.dp))
        ActivityProgress("Бег", "17,6 км", RtGreen, 0.78f, Icons.Outlined.DirectionsRun)
        ActivityProgress("Ходьба", "6,1 км", RtYellow, 0.42f, Icons.Outlined.DirectionsWalk)
        ActivityProgress("Велосипед", "26,3 км", RtBlue, 0.92f, Icons.Outlined.DirectionsBike)
        Spacer(Modifier.height(14.dp))
        InfoRow(Icons.Outlined.TrendingUp, "Прогресс", "+12% к прошлой неделе", RtGreen) { onNavigate(11) }
    }
}

@Composable
private fun StatsChartsScreen(onNavigate: (Int) -> Unit) {
    StatsPage(title = "Статистика", active = 1, onNavigate = onNavigate) {
        SectionTitle("Дистанция за 7 дней")
        Spacer(Modifier.height(8.dp))
        ChartCard(Modifier.fillMaxWidth().height(210.dp), bars = false)
        Spacer(Modifier.height(14.dp))
        SectionTitle("Время тренировок")
        Spacer(Modifier.height(8.dp))
        ChartCard(Modifier.fillMaxWidth().height(190.dp), bars = true)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricBox("Средняя", "7,8 км", Modifier.weight(1f))
            MetricBox("Максимум", "12,4 км", Modifier.weight(1f), RtGreen)
        }
    }
}

@Composable
private fun RecordsScreen(onNavigate: (Int) -> Unit) {
    StatsPage(title = "Рекорды", active = 2, onNavigate = onNavigate) {
        RecordCard("Самые быстрые 5 км", "24:51", "4:58 /км", RtGreen)
        Spacer(Modifier.height(9.dp))
        RecordCard("Самая длинная пробежка", "14,82 км", "1:27:16", RtBlue)
        Spacer(Modifier.height(9.dp))
        RecordCard("Лучший километровый темп", "4:21 /км", "1,00 км", RtYellow)
        Spacer(Modifier.height(9.dp))
        RecordCard("Максимальный набор", "284 м", "9,63 км", Color(0xFFAB7CFF))
    }
}

@Composable
private fun ComparisonScreen(onNavigate: (Int) -> Unit) {
    StatsPage(title = "Сравнение", active = 3, onNavigate = onNavigate) {
        Text("Эта неделя vs прошлая", color = RtMuted, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        ComparisonRow("Дистанция", "32,7 км", "29,1 км", "+12%")
        ComparisonRow("Тренировки", "4", "3", "+33%")
        ComparisonRow("Время", "3:18", "3:04", "+8%")
        ComparisonRow("Средний темп", "5:39", "5:46", "−7 сек")
        Spacer(Modifier.height(14.dp))
        SectionTitle("Тренд")
        Spacer(Modifier.height(8.dp))
        ChartCard(Modifier.fillMaxWidth().height(220.dp), bars = false)
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
private fun ComparisonRow(label: String, current: String, previous: String, delta: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(RtSurface, RoundedCornerShape(13.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(current, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("было $previous", color = RtMuted, fontSize = 9.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(delta, color = RtGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProfileScreen(onNavigate: (Int) -> Unit) {
    NativePage(
        title = "Профиль",
        bottomNavSelected = 3,
        onNavigate = onNavigate,
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RtSurface, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(62.dp)
                            .background(RtGreen.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Person, null, tint = RtGreen, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Пользователь", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("RunTrack", color = RtMuted, fontSize = 11.sp)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    MetricBox("Всего", "48", Modifier.weight(1f), RtGreen)
                    MetricBox("Дистанция", "286 км", Modifier.weight(1f))
                    MetricBox("Время", "31 ч", Modifier.weight(1f))
                }
            }
            item { InfoRow(Icons.Outlined.CalendarMonth, "Календарь", "Тренировки по дням", RtGreen) { onNavigate(15) } }
            item { InfoRow(Icons.Outlined.Map, "Все маршруты", "Карта сохранённых тренировок", RtBlue) { onNavigate(16) } }
            item { InfoRow(Icons.Outlined.Settings, "Настройки", "Единицы, уведомления, приватность", RtText2) { onNavigate(17) } }
            item { InfoRow(Icons.Outlined.Link, "Подключения", "Устройства и сервисы", RtYellow) { onNavigate(18) } }
        }
    }
}

@Composable
private fun CalendarScreen(onNavigate: (Int) -> Unit) {
    NativePage(
        title = "Календарь",
        subtitle = "Август 2026",
        onBack = { onNavigate(14) },
        bottomNavSelected = 3,
        onNavigate = onNavigate,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            CalendarGrid()
            Spacer(Modifier.height(16.dp))
            SectionTitle("14 августа")
            Spacer(Modifier.height(8.dp))
            InfoRow(Icons.Outlined.DirectionsRun, "Утренняя пробежка", "7,42 км · 42:18", RtGreen) { onNavigate(5) }
            Spacer(Modifier.height(8.dp))
            Text("Зелёная точка — день с тренировкой", color = RtMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun CalendarGrid() {
    val days = (1..31).toList()
    val active = setOf(2, 5, 8, 10, 11, 13, 14)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RtSurface, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                Text(it, color = RtMuted, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        val padded = listOf<Int?>(null, null, null, null, null) + days
        padded.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    day.toString(),
                                    color = if (day == 14) RtBg else Color.White,
                                    fontSize = 11.sp,
                                    modifier = if (day == 14) {
                                        Modifier.background(RtGreen, CircleShape).padding(horizontal = 7.dp, vertical = 4.dp)
                                    } else Modifier,
                                )
                                if (day in active && day != 14) {
                                    Spacer(Modifier.height(2.dp))
                                    Box(Modifier.size(4.dp).background(RtGreen, CircleShape))
                                }
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

@Composable
private fun AllRoutesScreen(onNavigate: (Int) -> Unit) {
    NativePage(
        title = "Все маршруты",
        subtitle = "Последние тренировки на карте",
        onBack = { onNavigate(14) },
        bottomNavSelected = 3,
        onNavigate = onNavigate,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            RouteCanvas(Modifier.fillMaxWidth().weight(1f), multiple = true)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricBox("Маршрутов", "18", Modifier.weight(1f), RtGreen)
                MetricBox("Всего", "164 км", Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun SettingsScreen(onNavigate: (Int) -> Unit) {
    var notifications by remember { mutableStateOf(true) }
    var autoPause by remember { mutableStateOf(true) }
    var keepScreen by remember { mutableStateOf(false) }

    NativePage(
        title = "Настройки",
        onBack = { onNavigate(14) },
        bottomNavSelected = 3,
        onNavigate = onNavigate,
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 14.dp),
        ) {
            item { SettingSwitch(Icons.Outlined.Notifications, "Уведомления", "Итоги и напоминания", notifications) { notifications = it } }
            item { SettingSwitch(Icons.Outlined.Pause, "Автопауза", "При остановке движения", autoPause) { autoPause = it } }
            item { SettingSwitch(Icons.Outlined.Visibility, "Не выключать экран", "Во время активной тренировки", keepScreen) { keepScreen = it } }
            item { InfoRow(Icons.Outlined.Straighten, "Единицы измерения", "Километры · кг · °C", RtBlue) }
            item { InfoRow(Icons.Outlined.Language, "Язык", "Русский", RtText2) }
            item { InfoRow(Icons.Outlined.Lock, "Приватность", "Данные хранятся на устройстве", RtGreen) }
            item { InfoRow(Icons.Outlined.Link, "Подключения", "Устройства и сервисы", RtYellow) { onNavigate(18) } }
        }
    }
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
private fun ConnectionsScreen(onNavigate: (Int) -> Unit) {
    NativePage(
        title = "Подключения",
        subtitle = "Устройства и сервисы",
        onBack = { onNavigate(17) },
        bottomNavSelected = 3,
        onNavigate = onNavigate,
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 14.dp),
        ) {
            item { ConnectionCard(Icons.Outlined.Bluetooth, "Bluetooth-датчики", "Нет подключённых устройств", RtBlue, false) }
            item { ConnectionCard(Icons.Outlined.FavoriteBorder, "Пульсометр", "Поиск совместимого устройства", RtRed, false) }
            item { ConnectionCard(Icons.Outlined.Cloud, "Облачная синхронизация", "Не подключено", RtGreen, false) }
            item { ConnectionCard(Icons.Outlined.Smartphone, "Системные данные", "Разрешения Android", RtYellow, true) }
        }
    }
}

@Composable
private fun ConnectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    connected: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RtSurface, RoundedCornerShape(13.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(accent.copy(alpha = 0.14f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = RtMuted, fontSize = 9.sp)
        }
        Text(
            if (connected) "Готово" else "Подключить",
            color = if (connected) RtGreen else RtBlue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ExportScreen(onNavigate: (Int) -> Unit) {
    var format by remember { mutableIntStateOf(0) }
    var includeMap by remember { mutableStateOf(true) }
    var includeSplits by remember { mutableStateOf(true) }

    NativePage(
        title = "Экспорт тренировки",
        subtitle = "Утренняя пробежка",
        onBack = { onNavigate(8) },
        onNavigate = onNavigate,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            SectionTitle("Формат")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Изображение", "GPX", "CSV").forEachIndexed { i, label ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .background(if (format == i) RtGreen else RtSurface2, RoundedCornerShape(11.dp))
                            .clickable { format = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = if (format == i) RtBg else Color.White, fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SettingSwitch(Icons.Outlined.Map, "Добавить карту", "Маршрут в итоговом файле", includeMap) { includeMap = it }
            Spacer(Modifier.height(8.dp))
            SettingSwitch(Icons.Outlined.Timeline, "Добавить разбивку", "Темп по километрам", includeSplits) { includeSplits = it }

            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(RtSurface, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Share, null, tint = RtGreen, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("7,42 км · 42:18", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Предпросмотр экспорта", color = RtMuted, fontSize = 10.sp)
                }
            }

            Spacer(Modifier.weight(1f))
            PrimaryAction(
                text = "Поделиться",
                icon = Icons.Outlined.Share,
                onClick = { },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(9.dp))
            SecondaryAction(
                text = "Сохранить файл",
                icon = Icons.Outlined.Download,
                onClick = { },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
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
private fun LiveRouteMap(modifier: Modifier = Modifier) {
    RouteCanvas(modifier = modifier, multiple = false)
}

@Composable
private fun RouteCanvas(modifier: Modifier = Modifier, multiple: Boolean) {
    Box(
        modifier = modifier.background(Color(0xFF0C1821), RoundedCornerShape(16.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val road = Color(0xFF20313C)
            val minor = Color(0xFF172630)

            for (i in 0..8) {
                val y = h * (0.08f + i * 0.12f)
                drawLine(
                    color = if (i % 2 == 0) road else minor,
                    start = Offset(0f, y),
                    end = Offset(w, y - h * 0.16f),
                    strokeWidth = if (i % 2 == 0) 1.2.dp.toPx() else 0.7.dp.toPx(),
                )
            }
            for (i in 0..8) {
                val x = w * (0.02f + i * 0.13f)
                drawLine(
                    color = minor,
                    start = Offset(x, 0f),
                    end = Offset(x + w * 0.17f, h),
                    strokeWidth = 0.7.dp.toPx(),
                )
            }

            fun route(pathColor: Color, offsetX: Float, offsetY: Float, scale: Float) {
                val path = Path().apply {
                    moveTo(w * (0.72f * scale + offsetX), h * (0.84f * scale + offsetY))
                    cubicTo(
                        w * (0.66f * scale + offsetX), h * (0.70f * scale + offsetY),
                        w * (0.56f * scale + offsetX), h * (0.62f * scale + offsetY),
                        w * (0.50f * scale + offsetX), h * (0.54f * scale + offsetY),
                    )
                    cubicTo(
                        w * (0.42f * scale + offsetX), h * (0.44f * scale + offsetY),
                        w * (0.38f * scale + offsetX), h * (0.32f * scale + offsetY),
                        w * (0.28f * scale + offsetX), h * (0.26f * scale + offsetY),
                    )
                }
                drawPath(path, pathColor, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            }

            route(RtGreen, 0f, 0f, 1f)
            if (multiple) {
                route(RtBlue, 0.07f, 0.06f, 0.78f)
                route(RtYellow, -0.03f, 0.20f, 0.65f)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapIcon(Icons.Outlined.MyLocation)
            MapIcon(Icons.Outlined.Layers)
        }
    }
}

@Composable
private fun MapIcon(icon: ImageVector) {
    Box(
        Modifier
            .size(32.dp)
            .background(Color(0xAA101D26), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = RtText2, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun RouteArtCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.78f)
            cubicTo(w * 0.26f, h * 0.28f, w * 0.45f, h * 0.42f, w * 0.53f, h * 0.18f)
            cubicTo(w * 0.62f, h * 0.02f, w * 0.69f, h * 0.44f, w * 0.82f, h * 0.36f)
            cubicTo(w * 0.92f, h * 0.30f, w * 0.85f, h * 0.72f, w * 0.63f, h * 0.76f)
            cubicTo(w * 0.48f, h * 0.78f, w * 0.38f, h * 0.63f, w * 0.15f, h * 0.78f)
        }
        drawPath(path, RtGreen, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(RtGreen, radius = 7.dp.toPx(), center = Offset(w * 0.15f, h * 0.78f))
        drawCircle(RtBg, radius = 3.dp.toPx(), center = Offset(w * 0.15f, h * 0.78f))
    }
}

@Composable
private fun ChartCard(modifier: Modifier = Modifier, bars: Boolean) {
    Box(modifier.background(RtSurface, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val grid = Color(0xFF22313A)
            for (i in 0..4) {
                val y = h * i / 4f
                drawLine(grid, Offset(0f, y), Offset(w, y), 1.dp.toPx())
            }

            val values = listOf(0.28f, 0.46f, 0.33f, 0.72f, 0.58f, 0.86f, 0.69f)
            if (bars) {
                val gap = w / 14f
                val bw = w / 12f
                values.forEachIndexed { i, v ->
                    val x = gap + i * (bw + gap)
                    val top = h * (1f - v)
                    drawRoundRect(
                        color = if (i == values.lastIndex) RtGreen else RtBlue.copy(alpha = 0.75f),
                        topLeft = Offset(x, top),
                        size = androidx.compose.ui.geometry.Size(bw, h - top),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                    )
                }
            } else {
                val path = Path()
                values.forEachIndexed { i, v ->
                    val x = w * i / (values.size - 1).toFloat()
                    val y = h * (1f - v)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, RtGreen, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                values.forEachIndexed { i, v ->
                    val x = w * i / (values.size - 1).toFloat()
                    val y = h * (1f - v)
                    drawCircle(RtGreen, radius = 4.dp.toPx(), center = Offset(x, y))
                }
            }
        }
    }
}
