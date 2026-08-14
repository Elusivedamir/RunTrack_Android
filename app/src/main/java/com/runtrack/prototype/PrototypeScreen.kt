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
import androidx.compose.ui.Modifier
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

        when (currentIndex) {
            0 -> {
                NativeMainScreen(
                    onQuickStart = { currentIndex = 1 },
                    onHistory = { currentIndex = 9 },
                    onStats = { currentIndex = 10 },
                    onProfile = { currentIndex = 14 },
                    onSettings = { currentIndex = 17 },
                )
            }

            9 -> {
                NativeHistoryScreen(
                    onHome = { currentIndex = 0 },
                    onStats = { currentIndex = 10 },
                    onProfile = { currentIndex = 14 },
                )
            }

            else -> {
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
                                                (currentIndex + 1)
                                                    .coerceAtMost(prototypeScreens.lastIndex)
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
                                        routeTap(currentIndex, nx, ny)
                                            ?.let { currentIndex = it }
                                    }
                                )
                            }
                    )
                }
            }
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

private fun routeTap(current: Int, x: Float, y: Float): Int? {
    if (y >= 0.90f && current in setOf(9, 10, 11, 12, 13, 14, 15, 16, 17, 18)) {
        return when {
            x < 0.25f -> 0
            x < 0.50f -> 9
            x < 0.75f -> 10
            else -> 14
        }
    }

    return when (current) {
        1 -> if (y > 0.82f) 2 else null
        2 -> if (y > 0.80f) {
            if (x < 0.72f) 3 else 4
        } else null
        3 -> if (y > 0.78f) {
            if (x < 0.72f) 2 else 4
        } else null
        4 -> if (y > 0.68f) 5 else null
        5 -> resultTabs(x, y)
        6 -> resultTabs(x, y)
        7 -> resultTabs(x, y)
        10 -> if (y in 0.18f..0.86f) 11 else null
        14 -> when {
            y in 0.48f..0.60f -> 17
            y in 0.60f..0.72f -> 18
            else -> null
        }
        17 -> if (y in 0.60f..0.78f) 18 else null
        else -> null
    }
}

private fun resultTabs(x: Float, y: Float): Int? {
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
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Экраны прототипа",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Свайп влево/вправо — соседний экран.",
                    color = Color(0xFF9AA5AE),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
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
