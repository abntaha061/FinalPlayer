package com.example.ui.screens

import android.content.res.Configuration
import android.view.Gravity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
    val accentRed = Color(0xFFFF2C2C)
    val cardBg = Color(0xFF1B1B22)
    val containerBg = Color(0xFF0F0F13)

    if (!isVisible) return

    Surface(
        color = containerBg,
        shape = if (isLandscape) {
            RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
        } else {
            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        },
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = if (isLandscape) {
            Modifier
                .fillMaxHeight()
                .width(380.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
        }
    ) {
        var isCustomizationExpanded by remember { mutableStateOf(true) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── TOP HEADER ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.LightGray)
                }

                Text(
                    text = "إعدادات الترجمة",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                // Enable Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSubtitleEnabled) accentRed.copy(alpha = 0.22f) else Color.Gray.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isSubtitleEnabled) "نشطة" else "معطلة",
                        color = if (isSubtitleEnabled) accentRed else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // ── 1. تعطيل وتفعيل الترجمة (DISABLE/ENABLE SWITCH) ──
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "تفعيل الترجمة",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (isSubtitleEnabled) "عرض النصوص والترجمات على الشاشة" else "إخفاء الترجمة حالياً",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = isSubtitleEnabled,
                        onCheckedChange = { enabled ->
                            onSubtitleEnabledChange(enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accentRed,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF2C2C34)
                        )
                    )
                }
            }

            // ── 2. الملفات الموجودة التي ظهرت (AVAILABLE SUBTITLE FILES & TRACKS) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardBg)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onAddSubtitleClick,
                        colors = ButtonDefaults.buttonColors(containerColor = accentRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة ملف +", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "ملفات الترجمة المتاحة",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(8.dp))

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
                            label = { Text("إيقاف الترجمة", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.Red.copy(alpha = 0.3f),
                                selectedLabelColor = Color(0xFFFF5252),
                                containerColor = Color(0xFF282832),
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
                                { Icon(Icons.Default.Check, null, tint = accentRed, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentRed.copy(alpha = 0.25f),
                                selectedLabelColor = accentRed,
                                containerColor = Color(0xFF282832),
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
                                { Icon(Icons.Default.Check, null, tint = accentRed, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentRed.copy(alpha = 0.25f),
                                selectedLabelColor = accentRed,
                                containerColor = Color(0xFF282832),
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }

            // ── 3. تقديم وتأخير النص (SUBTITLE OFFSET / SYNC SLIDER WITH RED TRACK) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardBg)
                    .padding(12.dp)
            ) {
                var tempDelay by remember { mutableStateOf(subtitleDelayMs.toFloat()) }
                LaunchedEffect(subtitleDelayMs) { tempDelay = subtitleDelayMs.toFloat() }

                val delaySeconds = tempDelay / 1000f
                val formattedDelay = String.format(java.util.Locale.US, "%.1f", delaySeconds)
                val delayLabel = when {
                    tempDelay == 0f -> "0.0 ثانية (مضبوطة)"
                    tempDelay > 0 -> "+$formattedDelay ثانية (تأخير)"
                    else -> "$formattedDelay ثانية (تقديم)"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = delayLabel,
                        color = if (tempDelay == 0f) accentRed else Color(0xFFFFD700),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "تقديم وتأخير النص (المزامنة)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Material 3 Thin Rounded Slider with RED accent
                AppSlider(
                    value = tempDelay,
                    onValueChange = {
                        tempDelay = it
                        onSubtitleDelayMsChange(it.toLong())
                    },
                    valueRange = -10000f..10000f,
                    steps = 100,
                    activeColor = accentRed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                )

                Spacer(Modifier.height(6.dp))

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
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("-0.5ث", color = Color.White, fontSize = 10.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val newDelay = (tempDelay - 100f).coerceIn(-10000f, 10000f)
                            tempDelay = newDelay
                            onSubtitleDelayMsChange(newDelay.toLong())
                        },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("-0.1ث", color = Color.White, fontSize = 10.sp)
                    }

                    TextButton(
                        onClick = {
                            tempDelay = 0f
                            onSubtitleDelayMsChange(0L)
                        },
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("إعادة ضبط", color = accentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val newDelay = (tempDelay + 100f).coerceIn(-10000f, 10000f)
                            tempDelay = newDelay
                            onSubtitleDelayMsChange(newDelay.toLong())
                        },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("+0.1ث", color = Color.White, fontSize = 10.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val newDelay = (tempDelay + 500f).coerceIn(-10000f, 10000f)
                            tempDelay = newDelay
                            onSubtitleDelayMsChange(newDelay.toLong())
                        },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("+0.5ث", color = Color.White, fontSize = 10.sp)
                    }
                }
            }

            // ── 4. تخصيص الترجمة (CUSTOMIZE SUBTITLES SECTION) ──
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
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
                            tint = accentRed
                        )
                        Text(
                            text = "تخصيص الترجمة والمظهر",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isCustomizationExpanded) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(Modifier.height(10.dp))

                        // A) خط عريض وخط مائل (BOLD & ITALIC)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = subtitleStyle.bold,
                                onClick = { onSubtitleStyleChange(subtitleStyle.copy(bold = !subtitleStyle.bold)) },
                                label = { Text("خط عريض B", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentRed.copy(alpha = 0.25f),
                                    selectedLabelColor = accentRed,
                                    containerColor = Color(0xFF282832),
                                    labelColor = Color.White
                                )
                            )

                            FilterChip(
                                selected = subtitleStyle.italic,
                                onClick = { onSubtitleStyleChange(subtitleStyle.copy(italic = !subtitleStyle.italic)) },
                                label = { Text("خط مائل I", fontWeight = FontWeight.Medium, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentRed.copy(alpha = 0.25f),
                                    selectedLabelColor = accentRed,
                                    containerColor = Color(0xFF282832),
                                    labelColor = Color.White
                                )
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // B) اختيار عده خطوط على الاقل 4 خطوط (AT LEAST 4 FONT CHOICES)
                        Text(
                            text = "نوع الخط (اختر الخط):",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))

                        val fontOptions = listOf(
                            "default" to "افتراضي (Sans)",
                            "serif" to "خط شريفي (Serif)",
                            "monospace" to "عرض ثابت (Mono)",
                            "cursive" to "خط مزخرف (Cursive)",
                            "sans-serif" to "خط حديث (Modern)"
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            fontOptions.forEach { (fontKey, fontLabel) ->
                                val isSelected = subtitleStyle.fontFamily == fontKey
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSubtitleStyleChange(subtitleStyle.copy(fontFamily = fontKey)) },
                                    label = { Text(fontLabel, fontSize = 10.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, null, tint = accentRed, modifier = Modifier.size(13.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentRed.copy(alpha = 0.25f),
                                        selectedLabelColor = accentRed,
                                        containerColor = Color(0xFF282832),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // C) شريط تمرير Material 3 الدائري الرفيع لحجم الترجمة (SUBTITLE SIZE SLIDER WITH RED TRACK)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${(subtitleStyle.textSize * 100).toInt()}%",
                                color = accentRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "حجم خط الترجمة:",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        AppSlider(
                            value = subtitleStyle.textSize,
                            onValueChange = { onSubtitleStyleChange(subtitleStyle.copy(textSize = it)) },
                            valueRange = 0.5f..2.5f,
                            steps = 20,
                            activeColor = accentRed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                        )

                        Spacer(Modifier.height(14.dp))

                        // D) لون الخط واختيار من متعدد ل 7 ألوان على الأقل (TEXT COLOR MULTI-CHOICE AT LEAST 7 COLORS)
                        Text(
                            text = "لون الخط (اختيار من متعدد):",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))

                        val colorChoices = listOf(
                            Color.White to "أبيض",
                            Color(0xFFFFD700) to "أصفر",
                            Color(0xFF4CAF50) to "أخضر",
                            Color(0xFF00C8FF) to "سماوي",
                            Color(0xFFFF2C2C) to "أحمر",
                            Color(0xFFFF4081) to "وردي",
                            Color(0xFFFF9800) to "برتقالي",
                            Color.Black to "أسود",
                            Color(0xFFB0BEC5) to "رمادي"
                        )

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
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
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(colorVal)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) accentRed else Color.White.copy(alpha = 0.3f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                null,
                                                tint = if (colorVal == Color.White || colorVal == Color(0xFFFFD700)) Color.Black else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(colorName, fontSize = 9.sp, color = if (isSelected) accentRed else Color.Gray)
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // E) لون الحواف الخاص بالخط واختيار من متعدد لعدة ألوان (EDGE TYPE & EDGE COLORS)
                        Text(
                            text = "نمط ولون الحواف (Outline Edge):",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))

                        val edgeTypeOptions = listOf(
                            CaptionStyleCompat.EDGE_TYPE_NONE to "بدون",
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE to "إطار خارجي",
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW to "ظل ساقط",
                            CaptionStyleCompat.EDGE_TYPE_RAISED to "بارز"
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            edgeTypeOptions.forEach { (edgeTypeVal, edgeTypeName) ->
                                val isSelected = subtitleStyle.edgeType == edgeTypeVal
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSubtitleStyleChange(subtitleStyle.copy(edgeType = edgeTypeVal)) },
                                    label = { Text(edgeTypeName, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentRed.copy(alpha = 0.25f),
                                        selectedLabelColor = accentRed,
                                        containerColor = Color(0xFF282832),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }

                        if (subtitleStyle.edgeType != CaptionStyleCompat.EDGE_TYPE_NONE) {
                            Spacer(Modifier.height(8.dp))
                            Text("لون الحواف:", color = Color.Gray, fontSize = 10.sp)
                            Spacer(Modifier.height(4.dp))

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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(edgeColors) { (col, name) ->
                                    val isSelected = subtitleStyle.edgeColor.toArgb() == col.toArgb()
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(col)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) accentRed else Color.White.copy(alpha = 0.2f),
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
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // F) اختيار تفعيل خاصية خلفية النص وتكون عبارة عن اختيار من متعدد لعدة ألوان (TEXT BACKGROUND FEATURE & MULTI-CHOICE COLORS)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "تفعيل خاصية خلفية النص",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Switch(
                                checked = subtitleStyle.backgroundEnabled,
                                onCheckedChange = { onSubtitleStyleChange(subtitleStyle.copy(backgroundEnabled = it)) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = accentRed
                                )
                            )
                        }

                        if (subtitleStyle.backgroundEnabled) {
                            Spacer(Modifier.height(8.dp))
                            Text("لون خلفية النص (اختيار من متعدد):", color = Color.LightGray, fontSize = 10.sp)
                            Spacer(Modifier.height(4.dp))

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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                                .size(30.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(col)
                                                .border(
                                                    width = if (isSelected) 2.5.dp else 1.dp,
                                                    color = if (isSelected) accentRed else Color.White.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(name, fontSize = 8.sp, color = if (isSelected) accentRed else Color.Gray)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // G) المحاذاة والارتفاع عن الأسفل (ALIGNMENT GRID)
                        Text(
                            text = "موقع المحاذاة والمسافة من الأسفل:",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .width(150.dp)
                                .height(130.dp)
                                .align(Alignment.CenterHorizontally)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF141418))
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
                                    ).padding(2.dp)
                                ) {
                                    IconButton(
                                        onClick = { onSubtitleStyleChange(subtitleStyle.copy(alignment = grav)) },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (isSelected) accentRed else Color.Transparent,
                                            contentColor = if (isSelected) Color.White else Color.LightGray
                                        ),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(icon, null, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
