package com.example.ui.screens

import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    manualSubs: List<Pair<String, Uri>>,
    onAddSubtitleClick: () -> Unit,
    onCustomizeAppearanceClick: () -> Unit,
    subtitleDelaySeconds: Float,
    onSubtitleDelaySecondsChange: (Float) -> Unit,
    isSubBgTransparent: Boolean,
    onSubBgTransparentChange: (Boolean) -> Unit,
    filePath: String,
    videoDurationMs: Long,
    onSubtitleFileGenerated: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()

    // AI subtitle operation states
    var isAIOperating by remember { mutableStateOf(false) }
    var aiOperationText by remember { mutableStateOf("") }
    var aiProgress by remember { mutableStateOf(0) }

    // Animate slide in/out
    AnimatedVisibility(
        visible = isVisible,
        enter = if (isLandscape)
            slideInHorizontally { it }   // slides in from RIGHT
        else
            slideInVertically { it },    // slides in from BOTTOM
        exit = if (isLandscape)
            slideOutHorizontally { it }  // slides out to RIGHT
        else
            slideOutVertically { it }    // slides out to BOTTOM
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Dim background - tap to dismiss
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { onDismiss() }
            )

            // ── PANEL ──
            Box(
                modifier = if (isLandscape) {
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.52f)       // 52% of width
                        .align(Alignment.CenterEnd) // from RIGHT side
                        .background(Color(0xFF141419)) // sharp corners = NO shape
                } else {
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.65f)        // 65% of height to fit AI section
                        .align(Alignment.BottomCenter) // from BOTTOM
                        .background(Color(0xFF141419)) // sharp corners = NO shape
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {

                    // ── HEADER ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = "إعدادات الترجمة بالذكاء الاصطناعي",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // Appearance button
                        TextButton(onClick = onCustomizeAppearanceClick) {
                            Text("🎨 المظهر", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(Modifier.height(10.dp))

                    // ── AI ASSISTANT PANEL (CRITICAL FEATURE) ──
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(
                                width = 1.dp,
                                color = Color(0xFF00C8FF).copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1F1F27)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "مساعد الترجمة التلقائي الذكي",
                                    color = Color(0xFF00C8FF),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI",
                                    tint = Color(0xFF00C8FF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "يمكنك جلب ترجمة عربية ومزامنتها تلقائياً بالاعتماد على بيانات الفيديو وسياقه، أو ترجمة ملف ترجمة موجود.",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (isAIOperating) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF00C8FF),
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = aiOperationText,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                    if (aiProgress > 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { aiProgress.toFloat() / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth(0.7f)
                                                .height(4.dp),
                                            color = Color(0xFF00C8FF),
                                            trackColor = Color.White.copy(alpha = 0.1f)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$aiProgress%",
                                            color = Color(0xFF00C8FF),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val nonArabicFile = detectedSubtitles.firstOrNull { 
                                        !it.name.contains(".ar", ignoreCase = true) 
                                    }
                                    
                                    if (nonArabicFile != null) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    isAIOperating = true
                                                    aiProgress = 0
                                                    aiOperationText = "جاري ترجمة ملف: ${nonArabicFile.name}..."
                                                    val translatedFile = com.example.service.SubtitleAutoFetchService.translateSubtitleFile(
                                                        context = context,
                                                        inputFile = nonArabicFile,
                                                        targetLanguage = "العربية (Arabic)",
                                                        onProgress = { progress ->
                                                            aiProgress = progress
                                                        }
                                                    )
                                                    if (translatedFile != null) {
                                                        onSubtitleFileGenerated(translatedFile)
                                                        aiOperationText = "تمت الترجمة وحفظ الملف!"
                                                        Toast.makeText(context, "تمت ترجمة الملف للعربية بنجاح!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        aiOperationText = "فشلت ترجمة الملف"
                                                    }
                                                    delay(1200)
                                                    isAIOperating = false
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF2E7D32)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Translate, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("ترجمة الملف للعربية", color = Color.White, fontSize = 11.sp)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isAIOperating = true
                                                aiProgress = 0
                                                aiOperationText = "جاري تحديد تفاصيل الفيديو وسياقه..."
                                                val videoFile = File(filePath)
                                                val cleanTitle = com.example.service.SubtitleAutoFetchService.detectMetadata(videoFile.name)
                                                
                                                aiOperationText = "جاري توليد ملف الترجمة ومزامنة العرض..."
                                                val generatedFile = com.example.service.SubtitleAutoFetchService.generateSubtitleFromMetadata(
                                                    context = context,
                                                    videoFile = videoFile,
                                                    title = cleanTitle,
                                                    durationMs = videoDurationMs,
                                                    targetLanguage = "اللغة العربية"
                                                )
                                                
                                                if (generatedFile != null) {
                                                    onSubtitleFileGenerated(generatedFile)
                                                    aiOperationText = "تم التوليد بنجاح ومزامنته!"
                                                    Toast.makeText(context, "تم توليد ومزامنة الترجمة التلقائية!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    aiOperationText = "فشل توليد الترجمة"
                                                }
                                                delay(1200)
                                                isAIOperating = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF6200EE)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Sync, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("جاري توليد ترجمة AI", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── SUBTITLE FILES ──
                    Text(
                        "ملفات الترجمة المتوفرة:",
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onSubtitleEnabledChange(false)
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Color.Gray)
                        ) {
                            Icon(Icons.Default.Check, null, tint = Color.White,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إيقاف الترجمة", color = Color.White, fontSize = 13.sp)
                        }
                        Button(
                            onClick = onAddSubtitleClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF222228)
                            )
                        ) {
                            Icon(Icons.Default.Add, null, tint = Color.White,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إضافة ترجمة +", color = Color.White, fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Arabic builtin
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSubtitleEnabledChange(!isSubtitleEnabled)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text("ترجمة عربية مدمجة (Automatic Arabic)", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.End)
                        Spacer(modifier = Modifier.width(8.dp))
                        Checkbox(
                            checked = isSubtitleEnabled && selectedSubtitleLang == "",
                            onCheckedChange = {
                                onSubtitleEnabledChange(it)
                            }
                        )
                    }

                    // Detected subtitles
                    detectedSubtitles.forEachIndexed { index, file ->
                        val lang = subtitleLanguages.getOrNull(index) ?: "Default"
                        val isChecked = isSubtitleEnabled && selectedSubtitleLang == lang
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectedSubtitleLangChange(lang)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(file.name, color = Color.White, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    if (it) {
                                        onSelectedSubtitleLangChange(lang)
                                    } else {
                                        onSubtitleEnabledChange(false)
                                    }
                                }
                            )
                        }
                    }

                    // Manual subtitles
                    manualSubs.forEachIndexed { index, pair ->
                        val lang = "manual_${index}_${pair.first}"
                        val isChecked = isSubtitleEnabled && selectedSubtitleLang == lang
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectedSubtitleLangChange(lang)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text("📁 ${pair.first}", color = Color(0xFF00C8FF), fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    if (it) {
                                        onSelectedSubtitleLangChange(lang)
                                    } else {
                                        onSubtitleEnabledChange(false)
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.1f)
                    )

                    // ── SYNC SECTION ──
                    Text(
                        "مزامنة الترجمة",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${String.format("%.2f", subtitleDelaySeconds)} ثانية",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                        Text(
                            "تأخير/تقديم النص (Delay/Advance):",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    Slider(
                        value = subtitleDelaySeconds,
                        onValueChange = onSubtitleDelaySecondsChange,
                        valueRange = -5f..5f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("خلفية سوداء خلف لوحة الترجمة:", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.End)
                        Spacer(modifier = Modifier.width(8.dp))
                        Checkbox(
                            checked = !isSubBgTransparent,
                            onCheckedChange = { onSubBgTransparentChange(!it) }
                        )
                    }
                }
            }
        }
    }
}
