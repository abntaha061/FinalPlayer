package com.example.ui.common.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.model.VideoQuality

@Composable
fun QualitySettingsSideSheet(
    onClose: () -> Unit,
    currentQuality: String = "1080p",
    onQualitySelected: (String) -> Unit
) {
    val qualities = remember {
        listOf(
            VideoQuality("auto", "تلقائي (Auto)", "حسب سرعة الاتصال"),
            VideoQuality("4k", "4K Ultra HD", "3840 x 2160"),
            VideoQuality("1080p", "Full HD", "1920 x 1080"),
            VideoQuality("720p", "HD High", "1280 x 720"),
            VideoQuality("480p", "SD Medium", "854 x 480")
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
                    Icon(Icons.Default.HighQuality, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جودة الفيديو 🎬", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                qualities.forEach { quality ->
                    val isSelected = currentQuality == quality.id
                    Surface(
                        onClick = { onQualitySelected(quality.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFFFF2A4B) else Color(0xFF262632),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(quality.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(quality.resolution, color = Color.LightGray, fontSize = 12.sp)
                            }
                            if (isSelected) {
                                Text("مُفعل ✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
