package com.runtrack.prototype

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF07131B)
private val Card = Color(0xFF101C25)
private val Card2 = Color(0xFF14222C)
private val Green = Color(0xFF62D72F)
private val Muted = Color(0xFF8E9AA3)
private val Yellow = Color(0xFFF4B400)
private val Blue = Color(0xFF42A5F5)

@Composable
fun NativeMainScreen(
    onQuickStart: () -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Header(onSettings)

        RouteMap(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(205.dp)
        )

        Spacer(Modifier.height(12.dp))

        QuickStart(
            onQuickStart = onQuickStart,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(Modifier.height(12.dp))

        RecentWorkout(
            onHistory = onHistory,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(Modifier.weight(1f))

        BottomBar(
            selected = 0,
            onHome = {},
            onHistory = onHistory,
            onStats = onStats,
            onProfile = onProfile,
        )
    }
}

@Composable
private fun Header(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Доброе утро!",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Готов к новой тренировке?",
                color = Muted,
                fontSize = 12.sp
            )
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clickable { },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Tune,
                contentDescription = "Фильтры",
                tint = Color(0xFFB8C2C9),
                modifier = Modifier.size(20.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = "Настройки",
                tint = Color(0xFFB8C2C9),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RouteMap(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF0C1821), RoundedCornerShape(16.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val road = Color(0xFF20313C)
            val minor = Color(0xFF172630)

            for (i in 0..8) {
                val y = h * (0.10f + i * 0.11f)
                drawLine(
                    color = if (i % 2 == 0) road else minor,
                    start = Offset(0f, y),
                    end = Offset(w, y - h * 0.18f),
                    strokeWidth = if (i % 2 == 0) 1.2.dp.toPx() else 0.7.dp.toPx()
                )
            }

            for (i in 0..8) {
                val x = w * (0.02f + i * 0.13f)
                drawLine(
                    color = minor,
                    start = Offset(x, 0f),
                    end = Offset(x + w * 0.15f, h),
                    strokeWidth = 0.7.dp.toPx()
                )
            }

            drawLine(
                color = road,
                start = Offset(w * 0.02f, h * 0.74f),
                end = Offset(w * 0.94f, h * 0.20f),
                strokeWidth = 1.5.dp.toPx()
            )
            drawLine(
                color = road,
                start = Offset(w * 0.10f, h * 0.18f),
                end = Offset(w * 0.84f, h * 0.92f),
                strokeWidth = 1.3.dp.toPx()
            )

            val path = Path().apply {
                moveTo(w * 0.66f, h * 0.88f)
                cubicTo(
                    w * 0.62f, h * 0.76f,
                    w * 0.55f, h * 0.68f,
                    w * 0.49f, h * 0.60f
                )
                cubicTo(
                    w * 0.42f, h * 0.50f,
                    w * 0.39f, h * 0.40f,
                    w * 0.32f, h * 0.33f
                )
            }

            drawPath(
                path = path,
                color = Green,
                style = Stroke(
                    width = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )

            fun marker(cx: Float, cy: Float) {
                drawCircle(
                    color = Color(0xFF07131B),
                    radius = 10.dp.toPx(),
                    center = Offset(w * cx, h * cy)
                )
                drawCircle(
                    color = Green,
                    radius = 8.dp.toPx(),
                    center = Offset(w * cx, h * cy),
                    style = Stroke(3.dp.toPx())
                )
                drawCircle(
                    color = Green,
                    radius = 2.6.dp.toPx(),
                    center = Offset(w * cx, h * cy)
                )
            }

            marker(0.32f, 0.33f)
            marker(0.66f, 0.88f)
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallMapButton(Icons.Outlined.MyLocation)
            SmallMapButton(Icons.Outlined.Layers)
        }
    }
}

@Composable
private fun SmallMapButton(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(Color(0xAA101D26), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFFAEB8BE),
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun QuickStart(
    onQuickStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Быстрый старт",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                )
                Text(
                    "Выбери тип тренировки",
                    color = Muted,
                    fontSize = 11.sp
                )
            }

            Icon(
                Icons.Outlined.Close,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActivityCard(
                title = "Бег",
                icon = Icons.Outlined.DirectionsRun,
                accent = Green,
                onClick = onQuickStart,
                modifier = Modifier.weight(1f)
            )
            ActivityCard(
                title = "Ходьба",
                icon = Icons.Outlined.DirectionsWalk,
                accent = Yellow,
                onClick = onQuickStart,
                modifier = Modifier.weight(1f)
            )
            ActivityCard(
                title = "Велосипед",
                icon = Icons.Outlined.DirectionsBike,
                accent = Blue,
                onClick = onQuickStart,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActivityCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(92.dp)
            .background(Card2, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(accent.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(25.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun RecentWorkout(
    onHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Недавние тренировки",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )

            Text(
                "Смотреть все",
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = onHistory)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Card, RoundedCornerShape(13.dp))
                .clickable(onClick = onHistory)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Green.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.DirectionsRun,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Утренняя пробежка",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "7,42 км  •  42:18  •  5:42 /км",
                    color = Muted,
                    fontSize = 10.sp
                )
            }

            Text(
                "12 авг 2024",
                color = Color(0xFF73818B),
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun BottomBar(
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem("Главная", Icons.Outlined.Home, selected == 0, onHome)
        NavItem("История", Icons.Outlined.History, selected == 1, onHistory)
        NavItem("Статистика", Icons.Outlined.BarChart, selected == 2, onStats)
        NavItem("Профиль", Icons.Outlined.Person, selected == 3, onProfile)
    }
}

@Composable
private fun RowScope.NavItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = title,
            tint = if (selected) Green else Muted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            title,
            color = if (selected) Green else Muted,
            fontSize = 9.sp
        )
    }
}
