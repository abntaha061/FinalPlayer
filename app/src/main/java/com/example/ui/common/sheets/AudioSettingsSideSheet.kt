package com.example.ui.common.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.model.AudioTrack

@Composable
fun AudioSettingsSideSheet(
    onClose: () -> Unit,
    currentTrackId: String? = "1",
    onSelectTrack: (String) -> Unit,
    audioBoostDb: Float,
    onAudioBoostChange: (Float) -> Unit
) {
    val audioTracks = remember {
        listOf(
            AudioTrack("1", "المسار الأصلي (Original Track)", "5.1 Surround"),
            AudioTrack("2", "الدبلجة العربية (Arabic Dubbed)", "Stereo"),
            AudioTrack("3", "الوصف الصوتي (Audio Description)", "Stereo")
        )
    }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(Color(0xFF181820).copy(alpha = 0.95f))
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إعدادات المسار الصوتي 🔊", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("المسارات الصوتية المتاحة", color = Color.Gray, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                audioTracks.forEach { track ->
                    val isSelected = currentTrackId == track.id
                    Surface(
                        onClick = { onSelectTrack(track.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFFFF2A4B) else Color(0xFF262632),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(track.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(track.channels, color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Audio Boost Slider (dB)
            Text("مُضخم الصوت الإضافي (Audio Boost): ${audioBoostDb.toInt()} dB", color = Color.White, fontSize = 14.sp)
            Slider(
                value = audioBoostDb,
                onValueChange = onAudioBoostChange,
                valueRange = 0f..12f,
                steps = 12,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFF2A4B),
                    activeTrackColor = Color(0xFFFF2A4B)
                )
            )
        }
    }
}
