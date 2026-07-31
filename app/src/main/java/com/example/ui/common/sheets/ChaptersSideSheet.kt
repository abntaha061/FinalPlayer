package com.example.ui.common.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.model.Chapter

@Composable
fun ChaptersSideSheet(
    onClose: () -> Unit,
    onSeekToChapter: (Long) -> Unit
) {
    val chapters = remember {
        listOf(
            Chapter("01. المقدمة والشارة (Intro)", 0L, 60000L),
            Chapter("02. بداية الأحداث (Part 1)", 60000L, 300000L),
            Chapter("03. العرض الرئيسي (Main Segment)", 300000L, 900000L),
            Chapter("04. خاتمة المقطع (Outro & Credits)", 900000L, 1200000L)
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
                    Icon(Icons.Default.ViewList, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("فصول الفيديو (Chapters) 📌", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                chapters.forEach { chapter ->
                    Surface(
                        onClick = {
                            onSeekToChapter(chapter.startMs)
                            onClose()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF262632),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(chapter.title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            val secs = chapter.startMs / 1000
                            Text("%02d:%02d".format(secs / 60, secs % 60), color = Color(0xFFFF2A4B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
