package com.example.ui.common.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.model.AspectMode
import com.example.player.model.PlaybackSettings

@Composable
fun PlayerSettingsSideSheet(
    settings: PlaybackSettings,
    onClose: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onAspectModeChange: (AspectMode) -> Unit,
    onNightModeToggle: (Boolean) -> Unit
) {
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
                Text(
                    text = "إعدادات المشغل ⚙️",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Speed Selection
            Text("سرعة التشغيل", color = Color.Gray, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                    FilterChip(
                        selected = settings.speed == speed,
                        onClick = { onSpeedChange(speed) },
                        label = { Text("${speed}x", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFF2A4B),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF262632),
                            labelColor = Color.LightGray
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Aspect Ratio
            Text("أبعاد العرض (Aspect Ratio)", color = Color.Gray, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AspectMode.values().forEach { mode ->
                    Surface(
                        onClick = { onAspectModeChange(mode) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (settings.aspectMode == mode) Color(0xFFFF2A4B) else Color(0xFF262632),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = mode.label,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Night Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NightsStay, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("الوضع الليلي وحماية العين", color = Color.White, fontSize = 14.sp)
                }
                Switch(
                    checked = settings.isNightMode,
                    onCheckedChange = onNightModeToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF2A4B))
                )
            }
        }
    }
}
