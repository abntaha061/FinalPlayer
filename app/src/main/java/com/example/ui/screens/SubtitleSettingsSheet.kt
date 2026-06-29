package com.example.ui.screens

import android.content.res.Configuration
import android.view.Gravity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.CaptionStyleCompat
import java.io.File

@Composable
fun SubtitleSettingsPanel(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    isSubtitleEnabled: Boolean,
    onSubtitleEnabledChange: (Boolean) -> Unit,
    detectedSubtitles: List<File>,
    subtitleLanguages: List<String>,
    selectedSubtitleLang: String?,
    onSelectedSubtitleLangChange: (String) -> Unit,
    manualSubs: List<Pair<String, android.net.Uri>>,
    onAddSubtitleClick: () -> Unit,
    onCustomizeAppearanceClick: () -> Unit,
    subtitleDelayMs: Long,
    onSubtitleDelayMsChange: (Long) -> Unit,
    subtitleSpeed: Float,
    onSubtitleSpeedChange: (Float) -> Unit,
    subtitleStyle: SubtitleStyle,
    onSubtitleStyleChange: (SubtitleStyle) -> Unit,
    filePath: String,
    videoDurationMs: Long,
    onSubtitleFileGenerated: (File) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    AnimatedVisibility(
        visible = isVisible,
        enter = if (isLandscape) slideInHorizontally { it } else slideInVertically { it },
        exit = if (isLandscape) slideOutHorizontally { it } else slideOutVertically { it }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { onDismiss() }
            )
            Box(
                modifier = if (isLandscape) {
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.5f)
                        .align(Alignment.CenterEnd)
                        .background(Color(0xFF1A1A1A))
                } else {
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.55f)
                        .align(Alignment.BottomCenter)
                        .background(Color(0xFF1A1A1A))
                }
            ) {
                var sheetPage by remember { mutableStateOf(0) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (sheetPage == 0) {
                        // ── صفحة 0: الاختيار والمزامنة ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                            }
                            Text(
                                text = "إعدادات الترجمة",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { sheetPage = 1 }) {
                                Text("🎨 تخصيص المظهر", fontSize = 12.sp)
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(Modifier.height(12.dp))

                        Text(
                            "ملفات الترجمة المتوفرة:",
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                        Spacer(Modifier.height(8.dp))

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            item {
                                Button(
                                    onClick = onAddSubtitleClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
                                ) {
                                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("إضافة ترجمة +", color = Color.White, fontSize = 13.sp)
                                }
                            }
                            item {
                                val isOff = !isSubtitleEnabled
                                OutlinedButton(
                                    onClick = { onSubtitleEnabledChange(false) },
                                    border = BorderStroke(1.dp, if (isOff) MaterialTheme.colorScheme.primary else Color.Gray)
                                ) {
                                    if (isOff) {
                                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text("إيقاف", color = if (isOff) MaterialTheme.colorScheme.primary else Color.White, fontSize = 13.sp)
                                }
                            }
                            items(subtitleLanguages.indices.toList()) { idx ->
                                val lang = subtitleLanguages[idx]
                                val subFile = detectedSubtitles.getOrNull(idx)
                                val displayName = subFile?.nameWithoutExtension ?: lang
                                val isSelected = isSubtitleEnabled && selectedSubtitleLang == lang
                                OutlinedButton(
                                    onClick = {
                                        onSubtitleEnabledChange(true)
                                        onSelectedSubtitleLangChange(lang)
                                    },
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray)
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(displayName, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White, fontSize = 13.sp)
                                }
                            }
                            items(manualSubs.indices.toList()) { idx ->
                                val pair = manualSubs[idx]
                                val lang = "manual_${idx}_${pair.first}"
                                val isSelected = isSubtitleEnabled && selectedSubtitleLang == lang
                                OutlinedButton(
                                    onClick = {
                                        onSubtitleEnabledChange(true)
                                        onSelectedSubtitleLangChange(lang)
                                    },
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray)
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(pair.first, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White, fontSize = 13.sp)
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))

                        Text("مزامنة الترجمة", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                        Spacer(Modifier.height(10.dp))

                        var tempDelay by remember { mutableStateOf(subtitleDelayMs.toFloat()) }
                        LaunchedEffect(subtitleDelayMs) { tempDelay = subtitleDelayMs.toFloat() }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val delayText = when {
                                tempDelay == 0f -> "مضبوطة"
                                tempDelay > 0 -> "+${"%.2f".format(tempDelay / 1000f)} ثانية"
                                else -> "${"%.2f".format(tempDelay / 1000f)} ثانية"
                            }
                            Text(delayText, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("تأخير/تقديم النص:", color = Color.Gray, fontSize = 12.sp)
                        }
                        Slider(
                            value = tempDelay,
                            onValueChange = {
                                tempDelay = it
                                onSubtitleDelayMsChange(it.toLong())
                            },
                            valueRange = -5000f..5000f,
                            steps = 100,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        var tempSpeed by remember { mutableStateOf(subtitleSpeed) }
                        LaunchedEffect(subtitleSpeed) { tempSpeed = subtitleSpeed }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${"%.2f".format(tempSpeed)}x", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("سرعة عرض الترجمة:", color = Color.Gray, fontSize = 12.sp)
                        }
                        Slider(
                            value = tempSpeed,
                            onValueChange = {
                                tempSpeed = it
                                onSubtitleSpeedChange(it)
                            },
                            valueRange = 0.5f..2.0f,
                            steps = 15,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )

                    } else {
                        // ── صفحة 1: تخصيص المظهر ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { sheetPage = 0 }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "عودة", tint = Color.White)
                            }
                            Text("تخصيص مظهر النص", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(48.dp))
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).clickable { onSubtitleStyleChange(subtitleStyle.copy(bold = !subtitleStyle.bold)) }.padding(4.dp)
                            ) {
                                Checkbox(checked = subtitleStyle.bold, onCheckedChange = { onSubtitleStyleChange(subtitleStyle.copy(bold = it)) })
                                Text("خط عريض", fontSize = 12.sp, color = Color.White)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).clickable { onSubtitleStyleChange(subtitleStyle.copy(italic = !subtitleStyle.italic)) }.padding(4.dp)
                            ) {
                                Checkbox(checked = subtitleStyle.italic, onCheckedChange = { onSubtitleStyleChange(subtitleStyle.copy(italic = it)) })
                                Text("خط مائل", fontSize = 12.sp, color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("الحجم: ${(subtitleStyle.textSize * 100).toInt()}%", fontSize = 12.sp, color = Color.Gray)
                        Slider(
                            value = subtitleStyle.textSize,
                            onValueChange = { onSubtitleStyleChange(subtitleStyle.copy(textSize = it)) },
                            valueRange = 0.5f..2.0f,
                            steps = 15
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("اللون:", fontSize = 12.sp, color = Color.Gray)
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(Color.White, Color.Yellow, Color(0xFF4CAF50), Color(0xFF00BCD4), Color(0xFFF44336)).forEach { col ->
                                Box(
                                    modifier = Modifier.size(36.dp).background(col, CircleShape)
                                        .clickable { onSubtitleStyleChange(subtitleStyle.copy(textColor = col)) }
                                        .border(2.dp, if (subtitleStyle.textColor.toArgb() == col.toArgb()) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (subtitleStyle.textColor.toArgb() == col.toArgb()) {
                                        Icon(Icons.Default.Check, null, tint = if (col == Color.White || col == Color.Yellow) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("نمط الحواف:", fontSize = 12.sp, color = Color.Gray)
                        var showEdgeMenu by remember { mutableStateOf(false) }
                        val edgeOptions = listOf(
                            CaptionStyleCompat.EDGE_TYPE_NONE to "بدون",
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE to "إطار خارجي",
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW to "ظل ساقط",
                            CaptionStyleCompat.EDGE_TYPE_RAISED to "بارز",
                            CaptionStyleCompat.EDGE_TYPE_DEPRESSED to "منخفض"
                        )
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            OutlinedButton(onClick = { showEdgeMenu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                                Text(edgeOptions.find { it.first == subtitleStyle.edgeType }?.second ?: "بدون", color = Color.White)
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null, tint = Color.White)
                            }
                            DropdownMenu(expanded = showEdgeMenu, onDismissRequest = { showEdgeMenu = false }) {
                                edgeOptions.forEach { opt ->
                                    DropdownMenuItem(text = { Text(opt.second) }, onClick = { onSubtitleStyleChange(subtitleStyle.copy(edgeType = opt.first)); showEdgeMenu = false })
                                }
                            }
                        }

                        if (subtitleStyle.edgeType != CaptionStyleCompat.EDGE_TYPE_NONE) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("لون الحواف:", fontSize = 12.sp, color = Color.Gray)
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(Color.Black, Color.White, Color.Red, Color.Blue, Color.Yellow).forEach { col ->
                                    Box(
                                        modifier = Modifier.size(36.dp).background(col, CircleShape)
                                            .clickable { onSubtitleStyleChange(subtitleStyle.copy(edgeColor = col)) }
                                            .border(2.dp, if (subtitleStyle.edgeColor.toArgb() == col.toArgb()) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (subtitleStyle.edgeColor.toArgb() == col.toArgb()) {
                                            Icon(Icons.Default.Check, null, tint = if (col == Color.White || col == Color.Yellow) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("تفعيل خلفية النص:", fontSize = 12.sp, color = Color.White)
                            Switch(checked = subtitleStyle.backgroundEnabled, onCheckedChange = { onSubtitleStyleChange(subtitleStyle.copy(backgroundEnabled = it)) })
                        }

                        if (subtitleStyle.backgroundEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("لون الخلفية:", fontSize = 12.sp, color = Color.Gray)
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    Color.Black.copy(alpha = 0.5f) to "50%",
                                    Color.Black to "أسود",
                                    Color(0xFF1C1B1F) to "داكن",
                                    Color(0xFF1A237E).copy(alpha = 0.6f) to "أزرق",
                                    Color(0xFF3E2723).copy(alpha = 0.6f) to "بني"
                                ).forEach { (col, name) ->
                                    Box(
                                        modifier = Modifier.size(36.dp).background(col, RoundedCornerShape(4.dp))
                                            .clickable { onSubtitleStyleChange(subtitleStyle.copy(backgroundColor = col)) }
                                            .border(2.dp, if (subtitleStyle.backgroundColor.toArgb() == col.toArgb()) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (subtitleStyle.backgroundColor.toArgb() == col.toArgb()) {
                                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("المحاذاة:", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.width(180.dp).height(180.dp)
                                .align(Alignment.CenterHorizontally)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        ) {
                            val gridAlignments = listOf(
                                (Gravity.TOP or Gravity.LEFT) to Icons.Default.NorthWest,
                                (Gravity.TOP or Gravity.CENTER_HORIZONTAL) to Icons.Default.North,
                                (Gravity.TOP or Gravity.RIGHT) to Icons.Default.NorthEast,
                                (Gravity.CENTER_VERTICAL or Gravity.LEFT) to Icons.Default.West,
                                Gravity.CENTER to Icons.Default.Adjust,
                                (Gravity.CENTER_VERTICAL or Gravity.RIGHT) to Icons.Default.East,
                                (Gravity.BOTTOM or Gravity.LEFT) to Icons.Default.SouthWest,
                                (Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL) to Icons.Default.South,
                                (Gravity.BOTTOM or Gravity.RIGHT) to Icons.Default.SouthEast
                            )
                            gridAlignments.forEachIndexed { index, (grav, icon) ->
                                val isSelected = subtitleStyle.alignment == grav
                                Box(
                                    modifier = Modifier.align(
                                        when (index) {
                                            0 -> Alignment.TopStart; 1 -> Alignment.TopCenter; 2 -> Alignment.TopEnd
                                            3 -> Alignment.CenterStart; 4 -> Alignment.Center; 5 -> Alignment.CenterEnd
                                            6 -> Alignment.BottomStart; 7 -> Alignment.BottomCenter; else -> Alignment.BottomEnd
                                        }
                                    ).padding(6.dp)
                                ) {
                                    IconButton(
                                        onClick = { onSubtitleStyleChange(subtitleStyle.copy(alignment = grav)) },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(icon, null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("المسافة من الأسفل: ${(subtitleStyle.bottomPadding * 1000).toInt()}dp", fontSize = 12.sp, color = Color.Gray)
                        Slider(
                            value = subtitleStyle.bottomPadding,
                            onValueChange = { onSubtitleStyleChange(subtitleStyle.copy(bottomPadding = it)) },
                            valueRange = 0.0f..0.15f,
                            steps = 15
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
