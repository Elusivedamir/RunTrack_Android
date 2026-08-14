package com.runtrack.prototype

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val PrototypeBackground = Color(0xFF07131B)
private val PrototypeGreen = Color(0xFF63D331)

@Composable
fun RunTrackPrototypeApp() {
    MaterialTheme {
        var currentIndex by remember { mutableIntStateOf(0) }
        var showScreenPicker by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PrototypeBackground)
                .pointerInput(currentIndex) {
                    detectTapGestures(onLongPress = { showScreenPicker = true })
                }
        ) {
            when (currentIndex) {
                0 -> NativeMainScreen(
                    onQuickStart = { currentIndex = 1 },
                    onHistory = { currentIndex = 9 },
                    onStats = { currentIndex = 10 },
                    onProfile = { currentIndex = 14 },
                    onSettings = { currentIndex = 17 },
                )

                9 -> NativeHistoryScreen(
                    onHome = { currentIndex = 0 },
                    onStats = { currentIndex = 10 },
                    onProfile = { currentIndex = 14 },
                )

                else -> NativeRemainingScreen(
                    index = currentIndex,
                    onNavigate = { target ->
                        if (target in prototypeScreens.indices) currentIndex = target
                    },
                )
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
                    text = "Долгое нажатие — открыть этот список.",
                    color = Color(0xFF9AA5AE),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    itemsIndexed(prototypeScreens) { index, item ->
                        Button(
                            onClick = { onSelect(index) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (index == currentIndex) PrototypeGreen else Color(0xFF172732),
                                contentColor = if (index == currentIndex) Color.Black else Color.White
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
