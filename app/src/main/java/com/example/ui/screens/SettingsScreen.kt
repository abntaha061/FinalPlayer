// app/src/main/java/com/example/ui/screens/SettingsScreen.kt
package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.MediaFile
import com.example.ui.MediaViewModel
import com.example.ui.components.SettingsNavigationRow
import com.example.ui.components.SettingsSectionHeader
import com.example.ui.components.SettingsSwitchRow

/**
 * Modern Settings Screen integrated with Private Vault management.
 *
 * `activeVaultViewState` navigation states:
 * - "settings": Standard sectioned settings list
 * - "keypad_unlock": Prompts for PIN to unlock private vault
 * - "keypad_unlock_for_change": Prompts for existing PIN before changing PIN
 * - "keypad_setup": Setup flow for entering and confirming new PIN
 * - "dashboard": Private Vault file list view
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MediaViewModel? = null,
    onPlayFile: ((String) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Vault ViewModel states
    val isPrivateLocked by viewModel?.isPrivateFolderLocked?.collectAsState(initial = true) ?: remember { mutableStateOf(true) }
    val privateFiles by viewModel?.privateFiles?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val savedPasscode = viewModel?.getPasscode()
    val hasPasscode = !savedPasscode.isNullOrEmpty()

    val appSettings by viewModel?.appSettings?.collectAsState(initial = com.example.data.repository.AppSettings()) ?: remember { mutableStateOf(com.example.data.repository.AppSettings()) }

    // Active screen navigation inside Settings
    var activeVaultViewState by remember { mutableStateOf("settings") }

    // Keypad & setup states
    var setupStep by remember { mutableStateOf(1) } // 1 = enter new PIN, 2 = confirm
    var tempPin by remember { mutableStateOf("") }
    var keypadError by remember { mutableStateOf<String?>(null) }

    // Dialog state for choices (resume_mode, seek_increment, default_orientation)
    var selectionDialogType by remember { mutableStateOf<String?>(null) }

    // Vault click handler
    val handleVaultClick = {
        if (!hasPasscode) {
            setupStep = 1
            tempPin = ""
            keypadError = null
            activeVaultViewState = "keypad_setup"
        } else if (isPrivateLocked) {
            keypadError = null
            activeVaultViewState = "keypad_unlock"
        } else {
            activeVaultViewState = "dashboard"
        }
    }

    // PIN change click handler
    val handlePinChangeClick = {
        if (!hasPasscode) {
            setupStep = 1
            tempPin = ""
            keypadError = null
            activeVaultViewState = "keypad_setup"
        } else {
            keypadError = null
            activeVaultViewState = "keypad_unlock_for_change"
        }
    }

    // Status text for vault row
    val vaultStatusText = when {
        !hasPasscode -> "غير نشط (بدون رمز)"
        isPrivateLocked -> "مغلق 🔒"
        else -> "مفتوح 🔓"
    }

    // Controlled navigation routing
    when (activeVaultViewState) {
        "keypad_unlock" -> {
            VaultKeypad(
                title = "فتح الخزنة السرية",
                subtitle = "أدخل الرقم السري المكون من 4 أرقام للوصول إلى الملفات المحمية",
                accentColor = MaterialTheme.colorScheme.primary,
                errorText = keypadError,
                onPinEntered = { pin: String ->
                    val success = viewModel?.unlockPrivateFolder(pin) ?: false
                    if (success) {
                        keypadError = null
                        activeVaultViewState = "dashboard"
                    } else {
                        keypadError = "الرقم السري غير صحيح، حاول مجدداً"
                    }
                },
                onBack = { activeVaultViewState = "settings" }
            )
        }

        "keypad_unlock_for_change" -> {
            VaultKeypad(
                title = "التحقق من الرقم السري",
                subtitle = "أدخل الرقم السري الحالي للمتابعة إلى تغيير الرمز",
                accentColor = MaterialTheme.colorScheme.secondary,
                errorText = keypadError,
                onPinEntered = { pin: String ->
                    val success = (savedPasscode == pin)
                    if (success) {
                        setupStep = 1
                        tempPin = ""
                        keypadError = null
                        activeVaultViewState = "keypad_setup"
                    } else {
                        keypadError = "الرقم السري الحالي غير صحيح"
                    }
                },
                onBack = { activeVaultViewState = "settings" }
            )
        }

        "keypad_setup" -> {
            val titleText = if (setupStep == 1) "تعيين رقم سري جديد" else "تأكيد الرقم السري"
            val subtitleText = if (setupStep == 1) "أدخل 4 أرقام لحماية ملفاتك الخاصة" else "أعد إدخال نفس الأرقام للتأكيد"

            VaultKeypad(
                title = titleText,
                subtitle = subtitleText,
                accentColor = MaterialTheme.colorScheme.primary,
                errorText = keypadError,
                onPinEntered = { pin: String ->
                    if (setupStep == 1) {
                        tempPin = pin
                        setupStep = 2
                        keypadError = null
                    } else {
                        if (pin == tempPin) {
                            viewModel?.savePasscode(pin)
                            viewModel?.unlockPrivateFolder(pin)
                            Toast.makeText(context, "تم حفظ الرقم السري بنجاح", Toast.LENGTH_SHORT).show()
                            activeVaultViewState = "dashboard"
                        } else {
                            keypadError = "الرموز غير متطابقة، حاول مجدداً"
                            setupStep = 1
                            tempPin = ""
                        }
                    }
                },
                onBack = { activeVaultViewState = "settings" }
            )
        }

        "dashboard" -> {
            VaultDashboard(
                privateFiles = privateFiles,
                onPlayFile = { path ->
                    onPlayFile?.invoke(path)
                },
                onRemoveFromVault = { file ->
                    viewModel?.setPrivateStatus(file, false)
                    Toast.makeText(context, "تمت إزالة الملف من الخزنة", Toast.LENGTH_SHORT).show()
                },
                onLockVault = {
                    viewModel?.lockPrivateFolder()
                    activeVaultViewState = "settings"
                },
                onBack = { activeVaultViewState = "settings" }
            )
        }

        else -> {
            // "settings" main view
            val scrollState = rememberScrollState()

            Scaffold(
                modifier = modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "الإعدادات والمحاذاة (Settings)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            if (onBack != null) {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "رجوع"
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(scrollState)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // =========================================================
                    // 1. قسم "خصائص المشغل والتشغيل"
                    // =========================================================
                    SettingsSectionHeader(title = "خصائص المشغل والتشغيل")

                    val resumeLabel = when (appSettings.resumePlaybackMode) {
                        "AUTO" -> "استئناف تلقائي (AUTO)"
                        "START" -> "البدء من البداية (START)"
                        else -> "سؤال دائماً (ASK)"
                    }
                    SettingsNavigationRow(
                        icon = Icons.Default.Replay,
                        title = "نمط استئناف التشغيل",
                        currentValue = resumeLabel,
                        onClick = { selectionDialogType = "resume_mode" }
                    )

                    SettingsNavigationRow(
                        icon = Icons.Default.Speed,
                        title = "مدة التقديم والترجيع",
                        currentValue = "${appSettings.seekIncrementSeconds} ثوانٍ",
                        onClick = { selectionDialogType = "seek_increment" }
                    )

                    val orientationLabel = when (appSettings.defaultOrientation) {
                        "PORTRAIT" -> "عمودي (PORTRAIT)"
                        "LANDSCAPE" -> "أفقي (LANDSCAPE)"
                        "AUTO" -> "تلقائي مع المستشعر (AUTO)"
                        else -> "افتراضي النظام (SYSTEM)"
                    }
                    SettingsNavigationRow(
                        icon = Icons.Default.AspectRatio,
                        title = "اتجاه الشاشة الافتراضي",
                        currentValue = orientationLabel,
                        onClick = { selectionDialogType = "default_orientation" }
                    )

                    SettingsSwitchRow(
                        icon = Icons.Default.PlayArrow,
                        title = "التشغيل التلقائي للتالي",
                        description = "تشغيل الفيديو التالي تلقائياً عند انتهاء الحالي",
                        checked = appSettings.autoPlayNext,
                        onCheckedChange = { viewModel?.setAutoPlayNext(it) }
                    )

                    SettingsSwitchRow(
                        icon = Icons.Default.PlayArrow,
                        title = "صورة داخل صورة تلقائية (Auto-PiP)",
                        description = "تفعيل وضع PiP تلقائياً عند الخروج للتطبيق الآخر",
                        checked = appSettings.autoPip,
                        onCheckedChange = { viewModel?.setAutoPip(it) }
                    )

                    SettingsSwitchRow(
                        icon = Icons.Default.PlayArrow,
                        title = "التشغيل في الخلفية",
                        description = "استمرار تشغيل الصوت عند تصغير التطبيق",
                        checked = appSettings.backgroundPlayback,
                        onCheckedChange = { viewModel?.setBackgroundPlayback(it) }
                    )

                    SettingsSwitchRow(
                        icon = Icons.Default.ColorLens,
                        title = "تذكر السطوع",
                        description = "حفظ مستوى السطوع واستعادته عند فتح المشغل",
                        checked = appSettings.rememberBrightness,
                        onCheckedChange = { viewModel?.setRememberBrightness(it) }
                    )

                    SettingsSwitchRow(
                        icon = Icons.Default.Speed,
                        title = "تذكر السرعة",
                        description = "تذكر سرعة التشغيل المختارة بين الفيديوهات",
                        checked = appSettings.rememberSpeed,
                        onCheckedChange = { viewModel?.setRememberSpeed(it) }
                    )

                    SettingsSwitchRow(
                        icon = Icons.Default.AspectRatio,
                        title = "تذكر أبعاد الشاشة",
                        description = "تذكر نسبة أبعاد الفيديو واستعادتها تلقائياً",
                        checked = appSettings.rememberAspectRatio,
                        onCheckedChange = { viewModel?.setRememberAspectRatio(it) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // =========================================================
                    // 2. قسم "إدارة الوسائط والمكتبة"
                    // =========================================================
                    SettingsSectionHeader(title = "إدارة الوسائط والمكتبة")

                    SettingsSwitchRow(
                        icon = Icons.Default.Info,
                        title = "إظهار الملفات المخفية",
                        description = "عرض الملفات والمجلدات التي تبدأ بنقطة (.)",
                        checked = appSettings.showHiddenFiles,
                        onCheckedChange = { viewModel?.setShowHiddenFiles(it) }
                    )

                    SettingsSwitchRow(
                        icon = Icons.Default.Info,
                        title = "إظهار مجلدات .nomedia",
                        description = "إظهار مجلدات وسائط تحتوي على ملفات .nomedia",
                        checked = appSettings.showNoMediaFiles,
                        onCheckedChange = { viewModel?.setShowNoMediaFiles(it) }
                    )

                    SettingsSwitchRow(
                        icon = Icons.Default.Replay,
                        title = "إظهار مشغل مؤخراً",
                        description = "عرض قسم آخر الفيديوهات المشغلة في الصفحة الرئيسية",
                        checked = appSettings.showRecentlyPlayed,
                        onCheckedChange = { viewModel?.setShowRecentlyPlayed(it) }
                    )

                    SettingsSwitchRow(
                        icon = Icons.Default.Palette,
                        title = "إظهار الزر العائم (FAB)",
                        description = "إظهار زر التشغيل العائم في الواجهة الرئيسية",
                        checked = appSettings.showFab,
                        onCheckedChange = { viewModel?.setShowFab(it) }
                    )

                    SettingsNavigationRow(
                        icon = Icons.Default.Refresh,
                        title = "تحديث المكتبة يدوياً",
                        currentValue = "إعادة فحص",
                        onClick = {
                            viewModel?.launchIncrementalScan()
                            Toast.makeText(context, "جاري تحديث ملفات الوسائط...", Toast.LENGTH_SHORT).show()
                        }
                    )

                    SettingsNavigationRow(
                        icon = Icons.Default.CleaningServices,
                        title = "تصفية سجل المشاهدة",
                        currentValue = "مسح",
                        onClick = {
                            viewModel?.clearHistory()
                            Toast.makeText(context, "تم تنظيف سجل المشاهدة", Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // =========================================================
                    // 3. قسم "الخزنة السرية المحمية"
                    // =========================================================
                    SettingsSectionHeader(title = "الخزنة السرية المحمية")

                    SettingsNavigationRow(
                        icon = if (isPrivateLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        title = "الخزنة الخاصة",
                        currentValue = vaultStatusText,
                        onClick = handleVaultClick
                    )

                    SettingsNavigationRow(
                        icon = Icons.Default.VpnKey,
                        title = "تغيير الرقم السري (PIN)",
                        currentValue = if (hasPasscode) "تعيين رمز جديد" else "لم يتم الضبط",
                        onClick = handlePinChangeClick
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Choice Dialogs
    when (selectionDialogType) {
        "resume_mode" -> {
            AlertDialog(
                onDismissRequest = { selectionDialogType = null },
                title = { Text("نمط استئناف التشغيل", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf(
                            "ASK" to "سؤال دائماً (ASK)",
                            "AUTO" to "استئناف تلقائي (AUTO)",
                            "START" to "البدء من البداية (START)"
                        ).forEach { (modeKey, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel?.setResumePlaybackMode(modeKey)
                                        selectionDialogType = null
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = (appSettings.resumePlaybackMode == modeKey),
                                    onClick = {
                                        viewModel?.setResumePlaybackMode(modeKey)
                                        selectionDialogType = null
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectionDialogType = null }) {
                        Text("إلغاء")
                    }
                }
            )
        }

        "seek_increment" -> {
            AlertDialog(
                onDismissRequest = { selectionDialogType = null },
                title = { Text("مدة التقديم والترجيع", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf(5, 10, 15, 30).forEach { seconds ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel?.setSeekIncrementSeconds(seconds)
                                        selectionDialogType = null
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = (appSettings.seekIncrementSeconds == seconds),
                                    onClick = {
                                        viewModel?.setSeekIncrementSeconds(seconds)
                                        selectionDialogType = null
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "$seconds ثوانٍ", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectionDialogType = null }) {
                        Text("إلغاء")
                    }
                }
            )
        }

        "default_orientation" -> {
            AlertDialog(
                onDismissRequest = { selectionDialogType = null },
                title = { Text("اتجاه الشاشة الافتراضي", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf(
                            "SYSTEM" to "افتراضي النظام (SYSTEM)",
                            "PORTRAIT" to "عمودي (PORTRAIT)",
                            "LANDSCAPE" to "أفقي (LANDSCAPE)",
                            "AUTO" to "تلقائي مع المستشعر (AUTO)"
                        ).forEach { (orientKey, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel?.setDefaultOrientation(orientKey)
                                        selectionDialogType = null
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = (appSettings.defaultOrientation == orientKey),
                                    onClick = {
                                        viewModel?.setDefaultOrientation(orientKey)
                                        selectionDialogType = null
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectionDialogType = null }) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }
}

// =============================================================================
// AUXILIARY COMPOSABLE FUNCTIONS (VAULT KEYPAD, DASHBOARD & DIALOGS)
// =============================================================================

/**
 * PIN Keypad composable for unlocking or setting up the Private Vault.
 */
@Composable
fun VaultKeypad(
    title: String,
    subtitle: String,
    accentColor: Color,
    errorText: String?,
    onPinEntered: (String) -> Unit,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "رجوع",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(4) { idx ->
                val isFilled = idx < pin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isFilled) accentColor else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, accentColor, CircleShape)
                )
            }
        }

        if (errorText != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )

        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (key.isNotEmpty()) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .clickable(enabled = key.isNotEmpty()) {
                                when (key) {
                                    "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    "" -> {}
                                    else -> {
                                        if (pin.length < 4) {
                                            pin += key
                                            if (pin.length == 4) {
                                                val fullPin = pin
                                                pin = ""
                                                onPinEntered(fullPin)
                                            }
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Private Vault Dashboard for viewing and managing secure media files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDashboard(
    privateFiles: List<MediaFile>,
    onPlayFile: (String) -> Unit,
    onRemoveFromVault: (MediaFile) -> Unit,
    onLockVault: () -> Unit,
    onBack: () -> Unit
) {
    var fileDetailsToDisplay by remember { mutableStateOf<MediaFile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "الخزنة السرية (${privateFiles.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = onLockVault,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("قفل الخزنة", fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        if (privateFiles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "الخزنة السرية فارغة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "يمكنك نقل أي فيديو إلى الخزنة من قائمة الخيارات في الشاشة الرئيسية لحمايته",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(privateFiles, key = { it.id }) { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        onClick = { onPlayFile(file.path) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = file.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(onClick = { fileDetailsToDisplay = file }) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "التفاصيل",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = { onRemoveFromVault(file) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "إزالة من الخزنة",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fileDetailsToDisplay?.let { file ->
        VaultFileDetailsDialog(
            file = file,
            onDismiss = { fileDetailsToDisplay = null }
        )
    }
}

/**
 * File details popup dialog inside the Private Vault.
 */
@Composable
fun VaultFileDetailsDialog(
    file: MediaFile,
    onDismiss: () -> Unit
) {
    val sizeMb = file.size / (1024 * 1024)
    val durationSec = file.duration / 1000
    val min = durationSec / 60
    val sec = durationSec % 60

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "تفاصيل الملف المحمي",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("اسم الملف: ${file.title}")
                Text("المسار: ${file.path}")
                Text("الحجم: $sizeMb ميجابايت")
                Text("المدة: $min د $sec ث")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("حسناً")
            }
        }
    )
}
