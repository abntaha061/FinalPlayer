package com.example.ui.common.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DecoderSideSheet(
    isHwDecoder: Boolean,
    onClose: () -> Unit,
    onToggleDecoder: (Boolean) -> Unit
) {
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
                    Icon(Icons.Default.Memory, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("مفكك الترميز (HW/SW) ⚡", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = { onToggleDecoder(true) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isHwDecoder) Color(0xFFFF2A4B) else Color(0xFF262632),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("HW (فك الترميز العتادي - Hardware)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("يستخدم شريحة المعالج لتوفير الطاقة وتقليل الحرارة", color = Color.LightGray, fontSize = 12.sp)
                    }
                }

                Surface(
                    onClick = { onToggleDecoder(false) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (!isHwDecoder) Color(0xFFFF2A4B) else Color(0xFF262632),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("SW (فك الترميز البرمجي - Software)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("يدعم كافة الصيغ النادرة والمعقدة باستخدام البرمجيات", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
