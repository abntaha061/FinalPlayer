package com.example.ui.screens

import androidx.compose.foundation.background
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
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
