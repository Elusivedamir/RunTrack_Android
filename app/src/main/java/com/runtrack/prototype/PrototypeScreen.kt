package com.runtrack.prototype

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.abs

private val PrototypeBackground = Color(0xFF07131B)
private val PrototypeGreen = Color(0xFF63D331)

@Composable
fun RunTrackPrototypeApp() {
    MaterialTheme {
        var currentIndex by remember { mutableIntStateOf(0) }
        var showScreenPicker by remember { mutableStateOf(false) }

        val screen = prototypeScreens[currentIndex]

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(PrototypeBackground)
        ) {
            val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

            Image(
                painter = painterResource(screen.drawableRes),
                contentDescription = screen.title,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentIndex) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                totalDrag += dragAmount
                            },
                            onDragEnd = {
                                if (abs(totalDrag) > widthPx * 0.16f) {
                                    currentIndex = if (totalDrag < 0f) {
                                        (currentIndex + 1).coerceAtMost(prototypeScreens.lastIndex)
                                    } else {
                                        (currentIndex - 1).coerceAtLeast(0)
                                    }
                                }
                                totalDrag = 0f
                            },
                            onDragCancel = { totalDrag = 0f }
                        )
                    }
                    .pointerInput(currentIndex) {
                        detectTapGestures(
                            onLongPress = { showScreenPicker = true },
                            onTap = { offset ->
                                val nx = (offset.x / widthPx).coerceIn(0f, 1f)
                                val ny = (offset.y / heightPx).coerceIn(0f, 1f)
                                routeTap(currentIndex, nx, ny)?.let { currentIndex = it }
                            }
                        )
                    }
            )
        }

        if (showScreenPicker) {
            ScreenPickerDialog(
                currentIndex = currentIndex,
                onDismiss = { showScreenPicker = false },
                onSelect = {
                    currentIndex = it
                    showScreenPicker = false
                }
            )
        }
    }
}

/**
 * Invisible clickable routing layer for the visual prototype.
 *
 * Indices are zero-based:
 * 0 main, 1 setup, 2 active, 3 pause, 4 finish,
 * 5 overview, 6 map, 7 route art, 8 details, 9 history,
 * 10 stats overview, 11 charts, 12 records, 13 comparison, 14 profile,
 * 15 calendar, 16 all-routes map, 17 settings, 18 connections, 19 export.
 */
private fun routeTap(current: Int, x: Float, y: Float): Int? {
    // Bottom navigation on screens that contain it.
    if (y >= 0.90f && current in setOf(0, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)) {
        return when {
            x < 0.25f -> 0      // Главная
            x < 0.50f -> 9      // История
            x < 0.75f -> 10     // Статистика
            else -> 14          // Профиль
        }
    }

    return when (current) {
        0 -> { // Main: three quick-start activity cards.
            if (y in 0.47f..0.72f) 1 else null
        }
        1 -> { // Setup: start workout.
            if (y > 0.82f) 2 else null
        }
        2 -> { // Active: pause / stop.
            if (y > 0.80f) {
                if (x < 0.72f) 3 else 4
            } else null
        }
        3 -> { // Pause: resume / stop.
            if (y > 0.78f) {
                if (x < 0.72f) 2 else 4
            } else null
        }
        4 -> { // Finish: view result.
            if (y > 0.68f) 5 else null
        }
        5 -> resultTabs(x, y, current)
        6 -> resultTabs(x, y, current)
        7 -> resultTabs(x, y, current)
        8 -> null
        9 -> null
        10 -> { // Stats overview: tapping cards/charts region opens charts.
            if (y in 0.18f..0.86f) 11 else null
        }
        11 -> null
        12 -> null
        13 -> null
        14 -> { // Profile menu.
            when {
                y in 0.48f..0.60f -> 17
                y in 0.60f..0.72f -> 18
                else -> null
            }
        }
        15 -> null
        16 -> null
        17 -> { // Settings: connections.
            if (y in 0.60f..0.78f) 18 else null
        }
        18 -> null
        19 -> null
        else -> null
    }
}

private fun resultTabs(x: Float, y: Float, current: Int): Int? {
    if (y !in 0.10f..0.23f) return null
    return when {
        x < 0.34f -> 5
        x < 0.67f -> 6
        else -> 7
    }
}

@Composable
private fun ScreenPickerDialog(
    currentIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF101B24),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Text(
                    text = "Экраны прототипа",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Свайп влево/вправо — соседний экран. Долгое нажатие — этот список.",
                    color = Color(0xFF9AA5AE),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    itemsIndexed(prototypeScreens) { index, item ->
                        Button(
                            onClick = { onSelect(index) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (index == currentIndex) {
                                    PrototypeGreen
                                } else {
                                    Color(0xFF172732)
                                },
                                contentColor = if (index == currentIndex) {
                                    Color.Black
                                } else {
                                    Color.White
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${index + 1}. ${item.title}")
                        }
                    }
                }
            }
        }
    }
}
