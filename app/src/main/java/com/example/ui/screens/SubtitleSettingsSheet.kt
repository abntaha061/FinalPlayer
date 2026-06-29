package com.example.ui.screens

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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
                        .fillMaxWidth(0.5f)       // 50% of width
                        .align(Alignment.CenterEnd) // from RIGHT side
                        .background(Color(0xFF1A1A1A)) // sharp corners = NO shape
                } else {
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.5f)        // 50% of height
                        .align(Alignment.BottomCenter) // from BOTTOM
                        .background(Color(0xFF1A1A1A)) // sharp corners = NO shape
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
                            text = "إعدادات الترجمة",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // Appearance button
                        TextButton(onClick = onCustomizeAppearanceClick) {
                            Text("🎨 تخصيص المظهر", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(Modifier.height(12.dp))

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
                                containerColor = Color(0xFF6200EE)
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
