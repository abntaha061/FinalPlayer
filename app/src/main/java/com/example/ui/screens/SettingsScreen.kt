package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MediaViewModel,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    var defaultSpeed by remember { mutableStateOf(viewModel.getPlaybackSpeed()) }
    var hideControlsDelay by remember { mutableStateOf(viewModel.getHideControlsDelay()) }
    var subtitleSize by remember { mutableStateOf(viewModel.getSubtitleSize()) }
    var subtitlesEnabled by remember { mutableStateOf(viewModel.getSubtitlesEnabled()) }
    var audioBoostEnabled by remember { mutableStateOf(viewModel.getAudioBoostEnabled()) }
    var defaultScaleMode by remember { mutableStateOf(viewModel.getDefaultScaleMode()) }

    var alertMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات والمحاذاة (Settings)", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // General Title Section
            Text(
                "خصائص التشغيل (Playback Configuration)",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Speed Control
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "السرعة الافتراضية (Default Playing Speed)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "حدد معدل السرعة كوضع أساسي عند فتح أي ملف",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { speed ->
                            FilterChip(
                                selected = defaultSpeed == speed,
                                onClick = {
                                    defaultSpeed = speed
                                    viewModel.savePlaybackSpeed(speed)
                                },
                                label = { Text("${speed}x") }
                            )
                        }
                    }
                }
            }

            // Aspect ratio scaling mode
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "تحجيم الشاشة الافتراضي (Default Aspect Ratio)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("FIT", "FILL", "STRETCH", "CROP").forEach { mode ->
                            FilterChip(
                                selected = defaultScaleMode == mode,
                                onClick = {
                                    defaultScaleMode = mode
                                    viewModel.saveDefaultScaleMode(mode)
                                },
                                label = { Text(mode) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- THEME & CUSTOMIZATION SECTION ---
            val themeColorHex by viewModel.themeColorHexState.collectAsState()
            val resumeButtonPosition by viewModel.resumeButtonPositionState.collectAsState()
            val currentAccentColor = remember(themeColorHex) { Color(android.graphics.Color.parseColor(themeColorHex)) }

            Text(
                "مظهر التطبيق وتخصيص الألوان (App Theme & Settings)",
                fontSize = 15.sp,
                color = currentAccentColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "اختر لون السمة الأساسي (Select Accent Color)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "سيغير هذا لون أشرطة العناوين، شريط التشغيل السفلي، وشريط تقدم الفيديوهات لمظهر متناسق",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val colorsList = listOf(
                        "#FFD500F9" to "أرجواني كودياك (Magenta)",
                        "#FFFF3366" to "وردي نيون (Pink)",
                        "#FF007AFF" to "أزرق ملكي (iOS Blue)",
                        "#FF00E5FF" to "سماوي نيون (Cyan)",
                        "#FF4CD964" to "أخضر عشبي (Green)",
                        "#FFFF9500" to "برتقالي ناري (Orange)",
                        "#FFFF5252" to "أحمر مرجاني (Coral)",
                        "#FF9C27B0" to "بنفسجي عميق (Violet)"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(end = 8.dp)
                        ) {
                            items(colorsList.size) { index ->
                                val (hex, name) = colorsList[index]
                                val colorItem = Color(android.graphics.Color.parseColor(hex))
                                val isSelected = themeColorHex.equals(hex, ignoreCase = true)

                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(colorItem)
                                        .clickable {
                                            viewModel.saveThemeColorHex(hex)
                                        }
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "موقع زر استئناف التشغيل (Resume Button Side)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "تحديد اتجاه زر استئناف تشغيل الفيديوهات في الواجهة (اليمين أو اليسار)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf("LEFT" to "اليسار (Left)", "RIGHT" to "اليمين (Right)").forEach { (side, label) ->
                            FilterChip(
                                selected = resumeButtonPosition == side,
                                onClick = {
                                    viewModel.saveResumeButtonPosition(side)
                                },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle Title Section
            Text(
                "الترجمة والخطوط (Subtitles Styling)",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("تمكين الترجمة (Enable Subtitles)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("تفعيل استيراد ملف ترجمة الفيديو بشكل دائم", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = subtitlesEnabled,
                            onCheckedChange = {
                                subtitlesEnabled = it
                                viewModel.saveSubtitlesEnabled(it)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("حجم خط الترجمة (Subtitle size): ${subtitleSize.toInt()}sp", fontSize = 14.sp)
                    Slider(
                        value = subtitleSize,
                        onValueChange = {
                            subtitleSize = it
                            viewModel.saveSubtitleSize(it)
                        },
                        valueRange = 12f..32f,
                        modifier = Modifier.testTag("subtitle_size_slider")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cleaner DB Systems
            Text(
                "الأداء والذاكرة والترقية (Performance & Maintenance)",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Manual refresh index scans
                    Button(
                        onClick = {
                            viewModel.launchIncrementalScan()
                            alertMessage = "تم إطلاق الفحص المتكامل للملفات بنجاح"
                        },
                        modifier = Modifier.fillMaxWidth().testTag("settings_scan_trigger"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تحديث مكتبة الوسائط يدوياً (Rescan Library)", color = Color.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Wipe history button
                    Button(
                        onClick = {
                            viewModel.clearHistory()
                            alertMessage = "تم تصفية وإلغاء أرشيف تاريخ المشاهدة بنجاح"
                        },
                        modifier = Modifier.fillMaxWidth().testTag("settings_clear_history"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تصفية سجل المشاهدة (Wipe Playback History)", color = Color.White)
                        }
                    }
                }
            }

            // Status feedback modal
            alertMessage?.let { msg ->
                AlertDialog(
                    onDismissRequest = { alertMessage = null },
                    title = { Text("إشعار (Notification)", fontWeight = FontWeight.Bold) },
                    text = { Text(msg) },
                    confirmButton = {
                        TextButton(onClick = { alertMessage = null }) {
                            Text("حسناً (Ok)")
                        }
                    }
                )
            }
        }
    }
}
