package com.example.ui.common.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.model.SubtitleTrack

@Composable
fun SubtitleSettingsSideSheet(
    onClose: () -> Unit,
    currentTrackId: String? = "1",
    onSelectTrack: (String) -> Unit
) {
    val subtitleTracks = remember {
        listOf(
            SubtitleTrack("0", "إيقاف الترجمة (Off)"),
            SubtitleTrack("1", "العربية (مدمجة)"),
            SubtitleTrack("2", "English (Subtitles)"),
            SubtitleTrack("3", "Français (Sous-titres)")
        )
    }

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
                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إعدادات الترجمة 💬", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("ملفات الترجمة المتاحة", color = Color.Gray, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                subtitleTracks.forEach { track ->
                    val isSelected = currentTrackId == track.id
                    Surface(
                        onClick = { onSelectTrack(track.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFFFF2A4B) else Color(0xFF262632),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = track.name,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}
