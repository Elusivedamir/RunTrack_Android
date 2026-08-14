package com.runtrack.app.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HealthConnectRationale(onClose = ::finish)
            }
        }
    }
}

@Composable
private fun HealthConnectRationale(onClose: () -> Unit) {
    Surface(color = Color(0xFF07131B), modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(Color(0xFF101C25), RoundedCornerShape(18.dp))
                    .padding(20.dp),
            ) {
                Text("RunTrack и Health Connect", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Text(
                    "RunTrack записывает тренировку в Health Connect только после вашего нажатия. " +
                        "Передаются тип тренировки, время, дистанция и оценка калорий, если в профиле указан вес.",
                    color = Color(0xFFB8C2C9),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "GPS-маршрут, точные координаты и погода не передаются. Разрешения можно отозвать в Health Connect в любой момент.",
                    color = Color(0xFFB8C2C9),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF62D72F),
                        contentColor = Color(0xFF07131B),
                    ),
                ) {
                    Text("Понятно", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
