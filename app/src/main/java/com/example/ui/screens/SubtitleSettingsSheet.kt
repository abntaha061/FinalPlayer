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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.CaptionStyleCompat
import com.example.ui.components.AppSlider
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
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
    val accentCyan = Color(0xFF00C8FF)
    val cardBg = Color(0xFF222228)
    val containerBg = Color(0xFF16161A)

    AnimatedVisibility(
        visible = isVisible,
        enter = if (isLandscape) slideInHorizontally { it } else slideInVertically { it },
        exit = if (isLandscape) slideOutHorizontally { it } else slideOutVertically { it }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Dark Backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { onDismiss() }
            )

            // Sheet Container
            Box(
                modifier = (if (isLandscape) {
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.58f)
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                        .background(containerBg)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(containerBg)
                }).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
            ) {
                var isCustomizationExpanded by remember { mutableStateOf(true) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── TOP HEADER ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.LightGray)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "إعدادات الترجمة 💬",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Enable Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSubtitleEnabled) accentCyan.copy(alpha = 0.18f) else Color.Red.copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isSubtitleEnabled) "نشطة" else "معطلة",
                                color = if (isSubtitleEnabled) accentCyan else Color(0xFFFF5252),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // ── 1. تعطيل وتفعيل الترجمة (DISABLE/ENABLE SWITCH) ──
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(16.dp),
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
                                Text(
                                    text = "تفعيل الترجمة",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isSubtitleEnabled) "عرض النصوص والترجمات على الشاشة" else "إخفاء الترجمة حالياً",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = isSubtitleEnabled,
                                onCheckedChange = { enabled ->
                                    onSubtitleEnabledChange(enabled)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = accentCyan,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color(0xFF33333D)
                                )
                            )
                        }
                    }

                    // ── 2. الملفات الموجودة التي ظهرت (AVAILABLE SUBTITLE FILES & TRACKS) ──
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onAddSubtitleClick,
                                colors = ButtonDefaults.buttonColors(containerColor = accentCyan),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("إضافة ملف +", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Text(
                                text = "ملفات الترجمة المتاحة 📂",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            item {
                                val isOff = !isSubtitleEnabled
                                FilterChip(
                                    selected = isOff,
                                    onClick = { onSubtitleEnabledChange(false) },
                                    label = { Text("إيقاف الترجمة 🚫", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color.Red.copy(alpha = 0.25f),
                                        selectedLabelColor = Color(0xFFFF5252),
                                        containerColor = Color(0xFF2C2C34),
                                        labelColor = Color.White
                                    )
                                )
                            }

                            items(subtitleLanguages.indices.toList()) { idx ->
                                val lang = subtitleLanguages[idx]
                                val subFile = detectedSubtitles.getOrNull(idx)
                                val displayName = subFile?.name ?: lang
                                val isSelected = isSubtitleEnabled && selectedSubtitleLang == lang
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        onSubtitleEnabledChange(true)
                                        onSelectedSubtitleLangChange(lang)
                                    },
                                    label = { Text(displayName, fontSize = 11.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, null, tint = accentCyan, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentCyan.copy(alpha = 0.25f),
                                        selectedLabelColor = accentCyan,
                                        containerColor = Color(0xFF2C2C34),
                                        labelColor = Color.White
                                    )
                                )
                            }

                            items(manualSubs.indices.toList()) { idx ->
                                val pair = manualSubs[idx]
                                val lang = "manual_${idx}_${pair.first}"
                                val isSelected = isSubtitleEnabled && selectedSubtitleLang == lang
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        onSubtitleEnabledChange(true)
                                        onSelectedSubtitleLangChange(lang)
                                    },
                                    label = { Text(pair.first, fontSize = 11.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, null, tint = accentCyan, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentCyan.copy(alpha = 0.25f),
                                        selectedLabelColor = accentCyan,
                                        containerColor = Color(0xFF2C2C34),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // ── 3. تقديم وتأخير النص (SUBTITLE OFFSET / SYNC SLIDER) ──
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                            .padding(14.dp)
                    ) {
                        var tempDelay by remember { mutableStateOf(subtitleDelayMs.toFloat()) }
                        LaunchedEffect(subtitleDelayMs) { tempDelay = subtitleDelayMs.toFloat() }

                        val delaySeconds = tempDelay / 1000f
                        val delayLabel = when {
                            tempDelay == 0f -> "0.0 ثانية (مضبوطة ✅)"
                            tempDelay > 0 -> "+${"%.1f".format(delaySeconds)} ثانية (تأخير ⏩)"
                            else -> "${"%.1f".format(delaySeconds)} ثانية (تقديم ⏪)"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = delayLabel,
                                color = if (tempDelay == 0f) accentCyan else Color(0xFFFFD700),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "تقديم وتأخير النص (المزامنة) ⏱️",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Material 3 Thin Rounded Slider
                        AppSlider(
                            value = tempDelay,
                            onValueChange = {
                                tempDelay = it
                                onSubtitleDelayMsChange(it.toLong())
                            },
                            valueRange = -10000f..10000f,
                            steps = 100,
                            activeColor = accentCyan,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        // Quick step fine-tuning buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val newDelay = (tempDelay - 500f).coerceIn(-10000f, 10000f)
                                    tempDelay = newDelay
                                    onSubtitleDelayMsChange(newDelay.toLong())
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("-0.5ث", color = Color.White, fontSize = 10.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val newDelay = (tempDelay - 100f).coerceIn(-10000f, 10000f)
                                    tempDelay = newDelay
                                    onSubtitleDelayMsChange(newDelay.toLong())
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("-0.1ث", color = Color.White, fontSize = 10.sp)
                            }

                            TextButton(
                                onClick = {
                                    tempDelay = 0f
                                    onSubtitleDelayMsChange(0L)
                                },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("إعادة ضبط (0s)", color = accentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val newDelay = (tempDelay + 100f).coerceIn(-10000f, 10000f)
                                    tempDelay = newDelay
                                    onSubtitleDelayMsChange(newDelay.toLong())
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("+0.1ث", color = Color.White, fontSize = 10.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val newDelay = (tempDelay + 500f).coerceIn(-10000f, 10000f)
                                    tempDelay = newDelay
                                    onSubtitleDelayMsChange(newDelay.toLong())
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("+0.5ث", color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }

                    // ── 4. تخصيص الترجمة (CUSTOMIZE SUBTITLES SECTION) ──
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isCustomizationExpanded = !isCustomizationExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isCustomizationExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = accentCyan
                                )
                                Text(
                                    text = "تخصيص الترجمة والمظهر 🎨",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isCustomizationExpanded) {
                                Spacer(Modifier.height(14.dp))
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                Spacer(Modifier.height(14.dp))

                                // A) خط عريض وخط مائل (BOLD & ITALIC)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilterChip(
                                        selected = subtitleStyle.bold,
                                        onClick = { onSubtitleStyleChange(subtitleStyle.copy(bold = !subtitleStyle.bold)) },
                                        label = { Text("خط عريض B", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = accentCyan.copy(alpha = 0.25f),
                                            selectedLabelColor = accentCyan,
                                            containerColor = Color(0xFF2C2C34),
                                            labelColor = Color.White
                                        )
                                    )

                                    FilterChip(
                                        selected = subtitleStyle.italic,
                                        onClick = { onSubtitleStyleChange(subtitleStyle.copy(italic = !subtitleStyle.italic)) },
                                        label = { Text("خط مائل I", fontWeight = FontWeight.Medium, fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = accentCyan.copy(alpha = 0.25f),
                                            selectedLabelColor = accentCyan,
                                            containerColor = Color(0xFF2C2C34),
                                            labelColor = Color.White
                                        )
                                    )
                                }

                                Spacer(Modifier.height(14.dp))

                                // B) اختيار عده خطوط على الاقل 4 خطوط (AT LEAST 4 FONT CHOICES)
                                Text(
                                    text = "نوع الخط (اختر الخط):",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))

                                val fontOptions = listOf(
                                    "default" to "افتراضي (Sans)",
                                    "serif" to "خط شريفي (Serif)",
                                    "monospace" to "عرض ثابت (Mono)",
                                    "cursive" to "خط مزخرف (Cursive)",
                                    "sans-serif" to "خط حديث (Modern)"
                                )

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    fontOptions.forEach { (fontKey, fontLabel) ->
                                        val isSelected = subtitleStyle.fontFamily == fontKey
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { onSubtitleStyleChange(subtitleStyle.copy(fontFamily = fontKey)) },
                                            label = { Text(fontLabel, fontSize = 11.sp) },
                                            leadingIcon = if (isSelected) {
                                                { Icon(Icons.Default.Check, null, tint = accentCyan, modifier = Modifier.size(14.dp)) }
                                            } else null,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = accentCyan.copy(alpha = 0.25f),
                                                selectedLabelColor = accentCyan,
                                                containerColor = Color(0xFF2C2C34),
                                                labelColor = Color.White
                                            )
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // C) شريط تمرير Material 3 الدائري الرفيع لحجم الترجمة (SUBTITLE SIZE SLIDER)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${(subtitleStyle.textSize * 100).toInt()}%",
                                        color = accentCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "حجم خط الترجمة:",
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                AppSlider(
                                    value = subtitleStyle.textSize,
                                    onValueChange = { onSubtitleStyleChange(subtitleStyle.copy(textSize = it)) },
                                    valueRange = 0.5f..2.5f,
                                    steps = 20,
                                    activeColor = accentCyan,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(22.dp)
                                )

                                Spacer(Modifier.height(16.dp))

                                // D) لون الخط واختيار من متعدد ل 7 ألوان على الأقل (TEXT COLOR MULTI-CHOICE AT LEAST 7 COLORS)
                                Text(
                                    text = "لون الخط (اختيار من متعدد):",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))

                                val colorChoices = listOf(
                                    Color.White to "أبيض",
                                    Color(0xFFFFD700) to "أصفر",
                                    Color(0xFF4CAF50) to "أخضر",
                                    Color(0xFF00C8FF) to "سماوي",
                                    Color(0xFFFF5252) to "أحمر",
                                    Color(0xFFFF4081) to "وردي",
                                    Color(0xFFFF9800) to "برتقالي",
                                    Color.Black to "أسود",
                                    Color(0xFFB0BEC5) to "رمادي"
                                )

                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    items(colorChoices) { (colorVal, colorName) ->
                                        val isSelected = subtitleStyle.textColor.toArgb() == colorVal.toArgb()
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable {
                                                onSubtitleStyleChange(subtitleStyle.copy(textColor = colorVal))
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(colorVal)
                                                    .border(
                                                        width = if (isSelected) 3.dp else 1.dp,
                                                        color = if (isSelected) accentCyan else Color.White.copy(alpha = 0.3f),
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        null,
                                                        tint = if (colorVal == Color.White || colorVal == Color(0xFFFFD700)) Color.Black else Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(colorName, fontSize = 9.sp, color = if (isSelected) accentCyan else Color.Gray)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // E) لون الحواف الخاص بالخط واختيار من متعدد لعدة ألوان (EDGE TYPE & EDGE COLORS)
                                Text(
                                    text = "نمط ولون الحواف (Outline Edge):",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))

                                val edgeTypeOptions = listOf(
                                    CaptionStyleCompat.EDGE_TYPE_NONE to "بدون",
                                    CaptionStyleCompat.EDGE_TYPE_OUTLINE to "إطار خارجي",
                                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW to "ظل ساقط",
                                    CaptionStyleCompat.EDGE_TYPE_RAISED to "بارز"
                                )

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    edgeTypeOptions.forEach { (edgeTypeVal, edgeTypeName) ->
                                        val isSelected = subtitleStyle.edgeType == edgeTypeVal
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { onSubtitleStyleChange(subtitleStyle.copy(edgeType = edgeTypeVal)) },
                                            label = { Text(edgeTypeName, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = accentCyan.copy(alpha = 0.25f),
                                                selectedLabelColor = accentCyan,
                                                containerColor = Color(0xFF2C2C34),
                                                labelColor = Color.White
                                            )
                                        )
                                    }
                                }

                                if (subtitleStyle.edgeType != CaptionStyleCompat.EDGE_TYPE_NONE) {
                                    Spacer(Modifier.height(10.dp))
                                    Text("لون الحواف:", color = Color.Gray, fontSize = 11.sp)
                                    Spacer(Modifier.height(6.dp))

                                    val edgeColors = listOf(
                                        Color.Black to "أسود",
                                        Color.White to "أبيض",
                                        Color.Red to "أحمر",
                                        Color.Blue to "أزرق",
                                        Color(0xFFFFD700) to "أصفر",
                                        Color(0xFF4CAF50) to "أخضر",
                                        Color(0xFF00C8FF) to "سماوي",
                                        Color(0xFF757575) to "رمادي"
                                    )

                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(edgeColors) { (col, name) ->
                                            val isSelected = subtitleStyle.edgeColor.toArgb() == col.toArgb()
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(col)
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) accentCyan else Color.White.copy(alpha = 0.2f),
                                                        shape = CircleShape
                                                    )
                                                    .clickable { onSubtitleStyleChange(subtitleStyle.copy(edgeColor = col)) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        null,
                                                        tint = if (col == Color.White || col == Color(0xFFFFD700)) Color.Black else Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // F) اختيار تفعيل خاصية خلفية النص وتكون عبارة عن اختيار من متعدد لعدة ألوان (TEXT BACKGROUND FEATURE & MULTI-CHOICE COLORS)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "تفعيل خاصية خلفية النص",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Switch(
                                        checked = subtitleStyle.backgroundEnabled,
                                        onCheckedChange = { onSubtitleStyleChange(subtitleStyle.copy(backgroundEnabled = it)) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = accentCyan
                                        )
                                    )
                                }

                                if (subtitleStyle.backgroundEnabled) {
                                    Spacer(Modifier.height(10.dp))
                                    Text("لون خلفية النص (اختيار من متعدد):", color = Color.LightGray, fontSize = 11.sp)
                                    Spacer(Modifier.height(6.dp))

                                    val bgColors = listOf(
                                        Color.Black.copy(alpha = 0.6f) to "أسود شفاف",
                                        Color.Black to "أسود كامل",
                                        Color(0xFF0D47A1).copy(alpha = 0.7f) to "أزرق غامق",
                                        Color(0xFFB71C1C).copy(alpha = 0.7f) to "أحمر غامق",
                                        Color(0xFF212121).copy(alpha = 0.8f) to "رمادي داكن",
                                        Color(0xFF1B5E20).copy(alpha = 0.7f) to "أخضر غامق",
                                        Color(0xFFF57F17).copy(alpha = 0.7f) to "أصفر داكن"
                                    )

                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(bgColors) { (col, name) ->
                                            val isSelected = subtitleStyle.backgroundColor.toArgb() == col.toArgb()
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.clickable {
                                                    onSubtitleStyleChange(subtitleStyle.copy(backgroundColor = col))
                                                }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(col)
                                                        .border(
                                                            width = if (isSelected) 2.5.dp else 1.dp,
                                                            color = if (isSelected) accentCyan else Color.White.copy(alpha = 0.2f),
                                                            shape = RoundedCornerShape(8.dp)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(name, fontSize = 9.sp, color = if (isSelected) accentCyan else Color.Gray)
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // G) المحاذاة والارتفاع عن الأسفل (ALIGNMENT & BOTTOM MARGIN)
                                Text(
                                    text = "موقع المحاذاة والمسافة من الأسفل:",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))

                                Box(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .height(140.dp)
                                        .align(Alignment.CenterHorizontally)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1A1A20))
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
                                            ).padding(4.dp)
                                        ) {
                                            IconButton(
                                                onClick = { onSubtitleStyleChange(subtitleStyle.copy(alignment = grav)) },
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    containerColor = if (isSelected) accentCyan else Color.Transparent,
                                                    contentColor = if (isSelected) Color.Black else Color.LightGray
                                                ),
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(icon, null, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "المسافة من الأسفل: ${(subtitleStyle.bottomPadding * 1000).toInt()}dp",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                                AppSlider(
                                    value = subtitleStyle.bottomPadding,
                                    onValueChange = { onSubtitleStyleChange(subtitleStyle.copy(bottomPadding = it)) },
                                    valueRange = -0.03f..0.30f,
                                    steps = 33,
                                    activeColor = accentCyan,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}
