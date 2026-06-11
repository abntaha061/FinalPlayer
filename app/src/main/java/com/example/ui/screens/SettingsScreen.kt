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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.layout.ContentScale
import com.example.data.local.entities.MediaFile

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

    // Vault View State Machine: "settings", "keypad_unlock", "keypad_setup", "dashboard"
    var activeVaultViewState by remember { mutableStateOf("settings") }

    // Setup Passcode parameters
    var setupStepState by remember { mutableStateOf(1) } // 1 = enter code, 2 = confirm code
    var setupFirstPinState by remember { mutableStateOf("") }
    var setupErrorTextState by remember { mutableStateOf<String?>(null) }

    // Unlock parameters
    var unlockErrorTextState by remember { mutableStateOf<String?>(null) }

    // Playback settings
    var defaultSpeed by remember { mutableStateOf(viewModel.getPlaybackSpeed()) }
    var hideControlsDelay by remember { mutableStateOf(viewModel.getHideControlsDelay()) }
    var subtitleSize by remember { mutableStateOf(viewModel.getSubtitleSize()) }
    var subtitlesEnabled by remember { mutableStateOf(viewModel.getSubtitlesEnabled()) }
    var audioBoostEnabled by remember { mutableStateOf(viewModel.getAudioBoostEnabled()) }
    var defaultScaleMode by remember { mutableStateOf(viewModel.getDefaultScaleMode()) }

    var alertMessage by remember { mutableStateOf<String?>(null) }

    val themeColorHex by viewModel.themeColorHexState.collectAsState()
    val resumeButtonPosition by viewModel.resumeButtonPositionState.collectAsState()
    val currentAccentColor = remember(themeColorHex) { Color(android.graphics.Color.parseColor(themeColorHex)) }

    // Auto Lock synchronization: if files locked & we are on dashboard, swap back
    LaunchedEffect(isPrivateLocked) {
        if (isPrivateLocked && activeVaultViewState == "dashboard") {
            activeVaultViewState = "keypad_unlock"
        }
    }

    // --- RENDER DYNAMIC ACTIVE VIEW ---
    when (activeVaultViewState) {
        "keypad_unlock" -> {
            VaultKeypad(
                title = "فتح قفل الخزنة الآمنة 🔓",
                subtitle = "الرجاء كتابة رمز PIN السري لفك التشفير والاستعراض",
                accentColor = currentAccentColor,
                errorText = unlockErrorTextState,
                onPinEntered = { pin ->
                    if (viewModel.unlockPrivateFolder(pin)) {
                        unlockErrorTextState = null
                        activeVaultViewState = "dashboard"
                    } else {
                        unlockErrorTextState = "رمز PIN خاطئ! يرجى التحقق المحاولة مرة أخرى."
                    }
                },
                onBack = {
                    activeVaultViewState = "settings"
                }
            )
        }

        "keypad_setup" -> {
            val setupTitle = if (setupStepState == 1) "إعداد الخزنة السرية 🔐" else "تأكيد الرمز السري 🔒"
            val setupSubtitle = if (setupStepState == 1) 
                "أدخل رمز الـ PIN المكون من 4 أرقام لتعيينه ككود قفل أساسي" 
            else 
                "أعد كتابة الرمز السري المكون من 4 أرقام لتأكيد التفعيل"

            VaultKeypad(
                title = setupTitle,
                subtitle = setupSubtitle,
                accentColor = currentAccentColor,
                errorText = setupErrorTextState,
                onPinEntered = { pin ->
                    if (setupStepState == 1) {
                        setupFirstPinState = pin
                        setupStepState = 2
                        setupErrorTextState = null
                    } else {
                        if (pin == setupFirstPinState) {
                            viewModel.savePasscode(pin)
                            hasPasscodeState = true
                            setupStepState = 1
                            setupFirstPinState = ""
                            setupErrorTextState = null
                            activeVaultViewState = "dashboard"
                        } else {
                            setupStepState = 1
                            setupFirstPinState = ""
                            setupErrorTextState = "الرموز غير متطابقة! يرجى إعادة تعيين الكود مجدداً."
                        }
                    }
                },
                onBack = {
                    if (setupStepState == 2) {
                        setupStepState = 1
                        setupFirstPinState = ""
                        setupErrorTextState = null
                    } else {
                        activeVaultViewState = "settings"
                    }
                }
            )
        }

        "dashboard" -> {
            VaultDashboard(
                viewModel = viewModel,
                privateFiles = privateFiles,
                accentColor = currentAccentColor,
                onPlayFile = onPlayFile,
                onBackToSettings = {
                    activeVaultViewState = "settings"
                },
                onChangePin = {
                    setupStepState = 1
                    setupFirstPinState = ""
                    setupErrorTextState = null
                    activeVaultViewState = "keypad_setup"
                }
            )
        }

        else -> {
            // "settings" view
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("الإعدادات والمحاذاة (Settings)", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                                                .clip(CircleShape)
                                                .background(colorItem)
                                                .clickable {
                                                    viewModel.saveThemeColorHex(hex)
                                                }
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                                    shape = CircleShape
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

                    // --- NEW DEDICATED VAULT PORT-CARD ---
                    Text(
                        "الخزنة السرية المحمية (Secure Vault Center)",
                        fontSize = 15.sp,
                        color = currentAccentColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable {
                                if (!hasPasscodeState) {
                                    activeVaultViewState = "keypad_setup"
                                } else if (isPrivateLocked) {
                                    activeVaultViewState = "keypad_unlock"
                                } else {
                                    activeVaultViewState = "dashboard"
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, currentAccentColor.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(currentAccentColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Safe Folder",
                                    tint = currentAccentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "الخزنة الخاصة (Private Folder)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (!hasPasscodeState) "غير نشط. اضغط لتهيئة الرمز السري وحماية ملفاتك"
                                    else if (isPrivateLocked) "مغلق. انقر لإدخال الرمز السري واستعراض المحتوى"
                                    else "مفتوح. انقر لتصفح الملفات والتحكم بها",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Visual badge with count
                            if (privateFiles.isNotEmpty()) {
                                Badge(
                                    containerColor = currentAccentColor,
                                    contentColor = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Text("${privateFiles.size}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
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
    }
}

// ==========================================
// --- PREMIUM KEYPAD COMPOSABLE LOCK SCREEN ---
// ==========================================
@Composable
fun VaultKeypad(
    title: String,
    subtitle: String,
    accentColor: Color,
    errorText: String? = null,
    onPinEntered: (String) -> Unit,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Upper section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            if (errorText != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.08f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = errorText,
                        fontSize = 12.sp,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Active visual PIN dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val isFilled = pin.length > i
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .border(
                                width = 2.dp,
                                color = if (isFilled) accentColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .background(
                                color = if (isFilled) accentColor else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        // Numerical keypad layout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(bottom = 30.dp)
        ) {
            val keyRows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "back")
            )

            keyRows.forEach { rowKeys ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    rowKeys.forEach { key ->
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(
                                    if (key == "C" || key == "back") Color.Transparent 
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                                .clickable {
                                    if (key == "back") {
                                        if (pin.isNotEmpty()) {
                                            pin = pin.dropLast(1)
                                        }
                                    } else if (key == "C") {
                                        pin = ""
                                    } else {
                                        if (pin.length < 4) {
                                            pin += key
                                            if (pin.length == 4) {
                                                val savedPin = pin
                                                pin = "" // clear for next try
                                                onPinEntered(savedPin)
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (key == "back") {
                                Icon(
                                    imageVector = Icons.Default.Backspace,
                                    contentDescription = "Backspace",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            } else {
                                Text(
                                    text = key,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (key == "C") Color.Red.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ==========================================
// --- PREMIUM VAULT DEDICATED DASHBOARD ---
// ==========================================
@Composable
fun VaultDashboard(
    viewModel: MediaViewModel,
    privateFiles: List<MediaFile>,
    accentColor: Color,
    onPlayFile: ((String) -> Unit)? = null,
    onBackToSettings: () -> Unit,
    onChangePin: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("date") } // "date", "title", "size"
    var showDeleteConfirmByFile by remember { mutableStateOf<MediaFile?>(null) }

    val filteredFiles = remember(privateFiles, searchQuery, sortBy) {
        var list = privateFiles.filter { 
            it.title.contains(searchQuery, ignoreCase = true)
        }
        when (sortBy) {
            "title" -> list = list.sortedBy { it.title }
            "size" -> list = list.sortedByDescending { it.size }
            "date" -> list = list.sortedByDescending { it.dateModified }
        }
        list
    }

    val totalSize = remember(privateFiles) {
        privateFiles.sumOf { it.size }
    }

    val formattedTotalSize = remember(totalSize) {
        val mb = totalSize / (1024f * 1024f)
        if (mb > 1024) {
            "%.2f GB".format(mb / 1024f)
        } else {
            "%.1f MB".format(mb)
        }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { 
                    Text(
                        "الخزنة الخاصة (Private Folder)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackToSettings) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Quick Lock Button
                    Button(
                        onClick = { 
                            viewModel.lockPrivateFolder()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("قفل المجلد", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Stats Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text("إجمالي الفيديوهات", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${privateFiles.size} فيديو",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text("المساحة المستخدمة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            formattedTotalSize,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar & Filter Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث عن فيديو خاص بالاسم...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp)) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Sort Dropdown
                Box {
                    var expandedSortShow by remember { mutableStateOf(false) }
                    Button(
                        onClick = { expandedSortShow = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                            Text(
                                when (sortBy) {
                                    "date" -> "الأحدث"
                                    "title" -> "الاسم"
                                    "size" -> "الحجم"
                                    else -> "افتراضي"
                                },
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expandedSortShow,
                        onDismissRequest = { expandedSortShow = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("ترتيب حسب الأحدث 📅") },
                            onClick = { 
                                sortBy = "date"
                                expandedSortShow = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("ترتيب حسب الاسم أ-ي 🔤") },
                            onClick = { 
                                sortBy = "title"
                                expandedSortShow = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("ترتيب حسب الحجم الأكبر 💾") },
                            onClick = { 
                                sortBy = "size"
                                expandedSortShow = false
                            }
                        )
                    }
                }
            }

            // Media Files List view
            if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Empty",
                            tint = accentColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (searchQuery.isNotEmpty()) "لا توجد نتائج مطابقة لبحثك!" 
                            else "الخزنة فارغة حالياً!\n\nلتأمين الفيديوهات اضغط على زر الخيارات (3 نقاط) لأي فيديو في الصفحة الرئيسية واختر \"نقل إلى الخزنة\".",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredFiles, key = { it.id }) { file ->
                        val thumbnail = rememberVideoThumbnail(file.path)
                        val sizeMb = file.size / (1024f * 1024f)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail
                                Box(
                                    modifier = Modifier
                                        .size(100.dp, 64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (thumbnail != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = thumbnail,
                                            contentDescription = "Thumbnail",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Icon",
                                            tint = accentColor.copy(alpha = 0.6f),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }

                                    if (file.duration > 0) {
                                        val totalSec = file.duration / 1000
                                        val min = totalSec / 60
                                        val sec = totalSec % 60
                                        val durFormatted = "%02d:%02d".format(min, sec)
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = durFormatted,
                                                fontSize = 9.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Details
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "الحجم: %.1f MB".format(sizeMb),
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }

                                // Play button
                                IconButton(
                                    onClick = { onPlayFile?.invoke(file.path) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Video",
                                        tint = Color(0xFF34C759),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                // Restore button
                                IconButton(
                                    onClick = { 
                                        viewModel.setPrivateStatus(file, false) 
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = "Restore File",
                                        tint = accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Permanent Delete button
                                IconButton(
                                    onClick = { showDeleteConfirmByFile = file },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Permanent",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Secondary option: Customize PIN
            OutlinedButton(
                onClick = onChangePin,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = "Change Pin", modifier = Modifier.size(16.dp))
                    Text("تغيير أو إعداد الرقم السري (PIN) الخاص بالخزنة 🔑", fontSize = 12.sp)
                }
            }
        }
    }

    // Double Delete confirmation
    showDeleteConfirmByFile?.let { fileToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmByFile = null },
            title = { Text("حذف سري نهائي ⚠️", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد تماماً أنك تريد حذف الفيديو \"${fileToDelete.title}\" بشكل دائم ونهائي من جهازك؟ لن تتمكن من استرجاع هذا الملف أبداً بعد الحذف!") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFile(fileToDelete)
                        showDeleteConfirmByFile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("نعم، احذف نهائياً", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmByFile = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
