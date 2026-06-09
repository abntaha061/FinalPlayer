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
    onPlayFile: ((String) -> Unit)? = null,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    val isPrivateLocked by viewModel.isPrivateFolderLocked.collectAsState()
    val privateFiles by viewModel.privateFiles.collectAsState(initial = emptyList())
    var hasPasscodeState by remember { mutableStateOf(viewModel.getPasscode() != null) }

    var isShowSetupDialog by remember { mutableStateOf(false) }
    var isShowUnlockDialog by remember { mutableStateOf(false) }
    var pinSetupInput by remember { mutableStateOf("") }
    var pinUnlockInput by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf(false) }

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

            // --- SECURE VAULT ("الخزنة الخاصة") SECTION ---
            Text(
                "الخزنة الخاصة وتأمين الفيديوهات (Secure Vault)",
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Vault Safe",
                            tint = currentAccentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "خزنة حماية الفيديوهات الخاصة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "هذا القسم يتيح لك حظر الفيديوهات عبر نقلها وإخفائها مادياً من مساحات التخزين العامة بالجهاز إلى مساحة مخصصة ومعزولة تماماً داخل التطبيق كملف سري خاص لا يمكن تصفحه أو كشفه من أي تطبيق خارجي كالمعرض أو الاستوديو أو مشغلات الفيديو الأخرى.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Status of the vault
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (!hasPasscodeState) {
                                "حالة الخزنة: غير مفعّلة ⚠️"
                            } else if (isPrivateLocked) {
                                "حالة الخزنة: مقفلة ومؤمّنة 🔒"
                            } else {
                                "حالة الخزنة: مفتوحة ومتاحة ✅"
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (!hasPasscodeState) Color(0xFFFF9500) else if (isPrivateLocked) Color(0xFFFF5252) else Color(0xFF34C759)
                        )

                        Text(
                            text = "عدد الفيديوهات المخفية: ${privateFiles.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!hasPasscodeState) {
                            Button(
                                onClick = {
                                    pinSetupInput = ""
                                    isShowSetupDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = currentAccentColor)
                            ) {
                                Text("تفعيل الخزنة (رمز PIN)", color = Color.White, fontSize = 12.sp)
                            }
                        } else {
                            if (isPrivateLocked) {
                                Button(
                                    onClick = {
                                        pinUnlockInput = ""
                                        unlockError = false
                                        isShowUnlockDialog = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                                ) {
                                    Text("فتح قفل الخزنة 🔓", color = Color.White, fontSize = 12.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        viewModel.lockPrivateFolder()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                                ) {
                                    Text("قفل الخزنة الآن 🔒", color = Color.White, fontSize = 12.sp)
                                }
                            }

                            // Option to reset or change PIN code anyway
                            OutlinedButton(
                                onClick = {
                                    pinSetupInput = ""
                                    isShowSetupDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("تغيير رمز PIN 🔑", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    // Render files inside the Vault if unlocked
                    if (hasPasscodeState && !isPrivateLocked) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "الملفات السرية بالخزنة (Files in Vault):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = currentAccentColor,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        if (privateFiles.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "الخزنة فارغة حالياً.\nأضف فيديوهات من صفحة الفيديوهات بالنقر على القائمة (3 نقاط) واختيار \"نقل إلى الخزنة\".",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    lineHeight = 16.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                privateFiles.forEach { file ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Video",
                                            tint = currentAccentColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.title,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            val sizeMb = file.size / (1024f * 1024f)
                                            Text(
                                                text = "الموقع الحالي: مساحة آمنة ومخفية • %.1f MB".format(sizeMb),
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }

                                        // Play directly
                                        IconButton(
                                            onClick = { onPlayFile?.invoke(file.path) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play Video",
                                                tint = Color(0xFF34C759)
                                            )
                                        }

                                        // Move out of vault (Restore/Unhide)
                                        IconButton(
                                            onClick = { viewModel.setPrivateStatus(file, false) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LockOpen,
                                                contentDescription = "Restore File",
                                                tint = currentAccentColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- PASSCODE DIALOGS ---
            if (isShowSetupDialog) {
                AlertDialog(
                    onDismissRequest = { isShowSetupDialog = false },
                    title = { Text("إعداد الرقم السري 🔑", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("أدخل رمزاً سرياً مكوناً من أرقام لحماية وإخفاء فيديوهاتك:", fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            TextField(
                                value = pinSetupInput,
                                onValueChange = { pinSetupInput = it },
                                placeholder = { Text("أدخل الكود الرقمي") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (pinSetupInput.isNotEmpty()) {
                                    viewModel.savePasscode(pinSetupInput)
                                    hasPasscodeState = true
                                    isShowSetupDialog = false
                                }
                            }
                        ) {
                            Text("حفظ الكود والتمكين")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { isShowSetupDialog = false }) {
                            Text("إلغاء")
                        }
                    }
                )
            }

            if (isShowUnlockDialog) {
                AlertDialog(
                    onDismissRequest = { isShowUnlockDialog = false },
                    title = { Text("فك قفل الخزنة الآمنة 🔓", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("يرجى كتابة رمز PIN السري المصرح لفك القفل والاستعراض:", fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            TextField(
                                value = pinUnlockInput,
                                onValueChange = {
                                    pinUnlockInput = it
                                    unlockError = false
                                },
                                placeholder = { Text("الرمز السري") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (unlockError) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("كود PIN خاطئ! يرجى التحقق وإعادة المحاولة", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (viewModel.unlockPrivateFolder(pinUnlockInput)) {
                                    isShowUnlockDialog = false
                                    unlockError = false
                                } else {
                                    unlockError = true
                                }
                            }
                        ) {
                            Text("تأكيد الفك")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { isShowUnlockDialog = false }) {
                            Text("إلغاء")
                        }
                    }
                )
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
