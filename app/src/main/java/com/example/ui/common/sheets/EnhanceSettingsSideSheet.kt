package com.example.ui.common.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.model.PlaybackSettings

@Composable
fun EnhanceSettingsSideSheet(
    settings: PlaybackSettings,
    onClose: () -> Unit,
    onAudioBoostChange: (Float) -> Unit
) {
    var contrast by remember { mutableStateOf(settings.contrast) }
    var saturation by remember { mutableStateOf(settings.saturation) }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(Color(0xFF181820).copy(alpha = 0.95f))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("المعادل والتحسين 🎨", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("مُضخم الصوت (Audio Booster)", color = Color.Gray, fontSize = 13.sp)
            Text("${settings.audioBoostDb.toInt()} dB", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Slider(
                value = settings.audioBoostDb,
                onValueChange = onAudioBoostChange,
                valueRange = 0f..15f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFF2A4B),
                    activeTrackColor = Color(0xFFFF2A4B)
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text("التباين (Contrast)", color = Color.Gray, fontSize = 13.sp)
            Slider(
                value = contrast,
                onValueChange = { contrast = it },
                valueRange = 0.5f..1.5f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFF2A4B),
                    activeTrackColor = Color(0xFFFF2A4B)
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text("تشبع الألوان (Saturation)", color = Color.Gray, fontSize = 13.sp)
            Slider(
                value = saturation,
                onValueChange = { saturation = it },
                valueRange = 0.0f..2.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFF2A4B),
                    activeTrackColor = Color(0xFFFF2A4B)
                )
            )
        }
    }
}
