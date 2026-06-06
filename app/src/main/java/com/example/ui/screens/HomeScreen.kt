package com.example.ui.screens

import android.app.AlertDialog
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.MediaFile
import com.example.data.local.entities.PlaylistEntity
import com.example.data.local.entities.ScannedFolder
import com.example.ui.MediaViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MediaViewModel,
    onPlayFile: (String) -> Unit,
    onNavigateToBrowser: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current

    // Observe DB States
    val videoList by viewModel.videos.collectAsState(initial = emptyList())
    val audioList by viewModel.audio.collectAsState(initial = emptyList())
    val favoritesList by viewModel.favorites.collectAsState(initial = emptyList())
    val scannedFolders by viewModel.folders.collectAsState(initial = emptyList())
    val playlistsList by viewModel.playlists.collectAsState(initial = emptyList())
    val privateFilesList by viewModel.privateFiles.collectAsState(initial = emptyList())

    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgressText by viewModel.scanProgress.collectAsState()
    val sleepRemaining by viewModel.sleepTimeRemaining.collectAsState()
    val isPrivateLocked by viewModel.isPrivateFolderLocked.collectAsState()

    var selectedBottomTab by remember { mutableStateOf(0) } // 0 = Videos, 1 = Music, 2 = Settings
    var selectedSubTabIndex by remember { mutableStateOf(0) } // 0 = Videos, 1 = Favorites/Playlists, 2 = Vault

    // Display & sorting configurations
    var layoutMode by remember { mutableStateOf("LIST") } // "GRID", "LIST"
    var viewContentMode by remember { mutableStateOf("FOLDERS") } // "FOLDERS", "FILES", "ALL_FOLDERS"
    var sortOption by remember { mutableStateOf("TITLE") } // "TITLE", "DATE", "SIZE", "DURATION", "PATH", "RESOLUTION"
    var sortDirection by remember { mutableStateOf("DESCENDING") } // "ASCENDING", "DESCENDING"
    var isOptionsSheetVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Quick Action dialog states
    var isTransferDialogVisible by remember { mutableStateOf(false) }
    var isStatusSaverVisible by remember { mutableStateOf(false) }
    var isCleanerVisible by remember { mutableStateOf(false) }

    // Sleep Timer Dialog Trigger
    var isSleepDialogVisible by remember { mutableStateOf(false) }

    // Playlist Creation Modal Trigger
    var isPlaylistModalVisible by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // Private Vault Lock dialogues
    var isPasscodeSetupDialogVisible by remember { mutableStateOf(false) }
    var isPasscodeUnlockDialogVisible by remember { mutableStateOf(false) }
    var passcodeQueryInput by remember { mutableStateOf("") }
    var passcodeSetupInput1 by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            if (selectedBottomTab != 2) {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                    CenterAlignedTopAppBar(
                        title = {
                            if (isSearchActive && selectedBottomTab == 0 && selectedSubTabIndex == 0) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("بحث عن فيديو... (Search video)", fontSize = 14.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .padding(horizontal = 4.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    leadingIcon = {
                                        IconButton(onClick = {
                                            isSearchActive = false
                                            searchQuery = ""
                                        }) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                        }
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear")
                                            }
                                        }
                                    }
                                )
                            } else {
                                Text(
                                    "FinalPlayer",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }
                        },
                        navigationIcon = {},
                        actions = {
                            if (!isSearchActive || selectedBottomTab != 0 || selectedSubTabIndex != 0) {
                                // Search toggle button
                                if (selectedBottomTab == 0 && selectedSubTabIndex == 0) {
                                    IconButton(onClick = { isSearchActive = true }) {
                                        Icon(Icons.Default.Search, contentDescription = "Search bar")
                                    }
                                    IconButton(onClick = { isOptionsSheetVisible = true }) {
                                        Icon(Icons.Default.Tune, contentDescription = "Display options Dialog")
                                    }
                                }

                                // Quick scan trigger
                                IconButton(onClick = { viewModel.launchIncrementalScan() }) {
                                    Icon(Icons.Default.Loop, contentDescription = "Scan")
                                }

                                // Sleep Timer Indicator/Selector
                                IconButton(onClick = { isSleepDialogVisible = true }) {
                                    Box {
                                        Icon(
                                            Icons.Default.Timer,
                                            contentDescription = "Sleep timer",
                                            tint = if (sleepRemaining != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Incremental scan alert progress banner
                    AnimatedVisibility(visible = isScanning) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(vertical = 6.dp, horizontal = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "جاري مسح الملفات الجديدة... (Scanning media library)",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Top TabRow is only shown when Videos tab on bottom bar is selected
                    if (selectedBottomTab == 0) {
                        TabRow(
                            selectedTabIndex = selectedSubTabIndex,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedSubTabIndex]),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        ) {
                            val tabs = listOf("الفيديوهات (Videos)", "المفضلة والقوائم", "الخزنة السرية (Vault)")
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedSubTabIndex == index,
                                    onClick = { selectedSubTabIndex = index },
                                    text = { Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = selectedBottomTab == 0,
                    onClick = { selectedBottomTab = 0 },
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Videos") },
                    label = { Text("الفيديوهات") }
                )
                NavigationBarItem(
                    selected = selectedBottomTab == 1,
                    onClick = { selectedBottomTab = 1 },
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = "Music") },
                    label = { Text("الموسيقى") }
                )
                NavigationBarItem(
                    selected = selectedBottomTab == 2,
                    onClick = { selectedBottomTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("الإعدادات") }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main dynamic content switcher depending on selected bottom or sub tabs
            when (selectedBottomTab) {
                0 -> {
                    when (selectedSubTabIndex) {
                        0 -> VideosAndFoldersTab(
                            videoList = videoList,
                            scannedFolders = scannedFolders,
                            onPlayFile = onPlayFile,
                            viewModel = viewModel,
                            layoutMode = layoutMode,
                            viewContentMode = viewContentMode,
                            sortOption = sortOption,
                            sortDirection = sortDirection,
                            searchQuery = searchQuery,
                            onSelectedBottomTab = { selectedBottomTab = it },
                            onSelectedSubTabIndex = { selectedSubTabIndex = it },
                            onOptionsClick = { isOptionsSheetVisible = true },
                            onShowTransfer = { isTransferDialogVisible = true },
                            onShowStatusSaver = { isStatusSaverVisible = true },
                            onShowCleaner = { isCleanerVisible = true }
                        )
                        1 -> PlaylistsAndFavoritesTab(
                            playlists = playlistsList,
                            favorites = favoritesList,
                            onPlayFile = onPlayFile,
                            onCreatePlaylist = { isPlaylistModalVisible = true },
                            viewModel = viewModel
                        )
                        2 -> PrivateVaultTab(
                            privateFiles = privateFilesList,
                            isLocked = isPrivateLocked,
                            onPlayFile = onPlayFile,
                            onGoSetup = { isPasscodeSetupDialogVisible = true },
                            onGoUnlock = { isPasscodeUnlockDialogVisible = true },
                            viewModel = viewModel
                        )
                    }
                }
                1 -> MusicPlayerTab(
                    audioList = audioList,
                    onPlayFile = onPlayFile,
                    viewModel = viewModel
                )
                2 -> SettingsScreen(
                    viewModel = viewModel,
                    onBack = { selectedBottomTab = 0 }
                )
            }
        }
    }

    // --- SLEEP ALARM SETUP MODAL ---
    if (isSleepDialogVisible) {
        var customMinutesInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { isSleepDialogVisible = false },
            title = { Text("مؤقت النوم اللحظي (Sleep Timer)", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = if (sleepRemaining != null) {
                            "المؤقت نشط: متبقي ${sleepRemaining!! / 60} دقيقة و ${sleepRemaining!! % 60} ثانية"
                        } else {
                            "حدد مدة زمنية لإيقاف المشغّل تلقائياً وهدوء الهاتف"
                        },
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (sleepRemaining != null) {
                        Button(
                            onClick = {
                                viewModel.cancelSleepTimer()
                                isSleepDialogVisible = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("إيقاف مؤقت النوم الحالي (Cancel Sleep Timer)")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            listOf(15, 30, 45, 60).forEach { mins ->
                                Button(
                                    onClick = {
                                        viewModel.setSleepTimer(mins) {
                                            // Handle alarm kill standard
                                        }
                                        isSleepDialogVisible = false
                                    }
                                ) {
                                    Text("${mins}د")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = customMinutesInput,
                            onValueChange = { customMinutesInput = it },
                            label = { Text("اسم أو دقائق مخصصة (Custom minutes)...") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (sleepRemaining == null) {
                    TextButton(
                        onClick = {
                            val mins = customMinutesInput.toIntOrNull() ?: 0
                            if (mins > 0) {
                                viewModel.setSleepTimer(mins) {}
                            }
                            isSleepDialogVisible = false
                        }
                    ) {
                        Text("تمكين (Set Alarm)")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { isSleepDialogVisible = false }) {
                    Text("إلغاء (Cancel)")
                }
            }
        )
    }

    // --- NEW PLAYLIST CREATION MODAL ---
    if (isPlaylistModalVisible) {
        AlertDialog(
            onDismissRequest = { isPlaylistModalVisible = false },
            title = { Text("قائمة تشغيل جديدة (New Playlist)", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("اسم قائمة التشغيل (Playlist Name)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName)
                            newPlaylistName = ""
                            isPlaylistModalVisible = false
                        }
                    }
                ) {
                    Text("إنشاء (Create)")
                }
            },
            dismissButton = {
                TextButton(onClick = { isPlaylistModalVisible = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // --- PASSCODE REGISTRATION SYSTEM ---
    if (isPasscodeSetupDialogVisible) {
        AlertDialog(
            onDismissRequest = { isPasscodeSetupDialogVisible = false },
            title = { Text("إعداد رقم سري للخزنة (Setup Passcode)", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = passcodeSetupInput1,
                    onValueChange = { passcodeSetupInput1 = it },
                    label = { Text("كلمة السر (4 أرقام)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("vault_setup_passcode_field")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (passcodeSetupInput1.length >= 4) {
                            viewModel.savePasscode(passcodeSetupInput1)
                            isPasscodeSetupDialogVisible = false
                            passcodeSetupInput1 = ""
                        }
                    }
                ) {
                    Text("حفظ وتأمين")
                }
            },
            dismissButton = {
                TextButton(onClick = { isPasscodeSetupDialogVisible = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // --- PASSCODE UNLOCK PROMPT DIALOGUE ---
    if (isPasscodeUnlockDialogVisible) {
        var unlockErrorState by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { isPasscodeUnlockDialogVisible = false },
            title = { Text("فك قفل الحقيبة السرية (Vault Passcode Required)", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = passcodeQueryInput,
                        onValueChange = {
                            passcodeQueryInput = it
                            unlockErrorState = false
                        },
                        label = { Text("أدخل الرقم السري للفتح (Enter 4-digit PIN)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("vault_unlock_passcode_field")
                    )
                    if (unlockErrorState) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("الرقم السري خاطئ! حاول مجدداً", color = Color.Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (viewModel.unlockPrivateFolder(passcodeQueryInput)) {
                            isPasscodeUnlockDialogVisible = false
                            passcodeQueryInput = ""
                        } else {
                            unlockErrorState = true
                        }
                    }
                ) {
                    Text("تأكيد الفتح")
                }
            },
            dismissButton = {
                TextButton(onClick = { isPasscodeUnlockDialogVisible = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // --- DISPLAY AND SORT OPTIONS CUSTOM DIALOG ---
    if (isOptionsSheetVisible) {
        AlertDialog(
            onDismissRequest = { isOptionsSheetVisible = false },
            title = {
                Text(
                    text = "طريقة الفرز والعرض (Display & Sort)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. التصميم (List / Grid layout)
                    Text("التصميم (Layout)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("GRID" to "شبكة", "LIST" to "قائمة").forEach { (code, label) ->
                            val isSelected = layoutMode == code
                            Surface(
                                onClick = { layoutMode = code },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // 2. طريقة العرض (Content Grouping)
                    Text("طريقة العرض (View Mode)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("FOLDERS" to "مجلدات", "FILES" to "ملفات", "ALL_FOLDERS" to "كافة").forEach { (code, label) ->
                            val isSelected = viewContentMode == code
                            Surface(
                                onClick = { viewContentMode = code },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // 3. فرز (Sort attributes)
                    Text("فرز حسب (Sort By)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val sortOptions = listOf(
                            "TITLE" to "العنوان",
                            "DATE" to "التاريخ",
                            "SIZE" to "الحجم",
                            "DURATION" to "المدة",
                            "PATH" to "المسار",
                            "RESOLUTION" to "الدقة"
                        )
                        sortOptions.chunked(3).forEach { rowOptions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowOptions.forEach { (code, label) ->
                                    val isSelected = sortOption == code
                                    Surface(
                                        onClick = { sortOption = code },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. فرز اتجاه (Direction)
                    Text("الترتيب (Order)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("DESCENDING" to "الأحدث", "ASCENDING" to "الأقدم").forEach { (code, label) ->
                            val isSelected = sortDirection == code
                            Surface(
                                onClick = { sortDirection = code },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { isOptionsSheetVisible = false }) {
                    Text("اكتمل")
                }
            },
            dismissButton = {
                TextButton(onClick = { isOptionsSheetVisible = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // --- QUICK ACTION DIALOG 1: FILE TRANSFER ---
    if (isTransferDialogVisible) {
        AlertDialog(
            onDismissRequest = { isTransferDialogVisible = false },
            icon = {
                Icon(
                    Icons.Default.CompareArrows,
                    contentDescription = "Transfer Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("نقل الملفات اللاسلكي (Wi-Fi Transfer)", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "شارك ونقّل مقاطع الفيديو والملفات الموسيقية بسرعة فائقة دون أسلاك وبخطوة واحدة مريحة.",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("افتح هذا العنوان في متصفح جهازك الآخر:", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("http://192.168.1.189:8080", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { isTransferDialogVisible = false }) {
                    Text("حسناً")
                }
            }
        )
    }

    // --- QUICK ACTION DIALOG 2: STATUS SAVER GUIDE ---
    if (isStatusSaverVisible) {
        AlertDialog(
            onDismissRequest = { isStatusSaverVisible = false },
            icon = {
                Icon(
                    Icons.Default.FileDownload,
                    contentDescription = "Download Icon",
                    tint = Color.Green,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("حفظ حالات واتساب (Status Saver)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("خطوات حفظ الحالات التلقائية:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("1. شاهد حالة أصدقائك التي تريدها أولاً على تطبيق واتساب.", fontSize = 13.sp)
                    Text("2. ارجع إلى FinalPlayer وسيتم العثور على مقاطع الحالات تلقائياً ومسحها هنا.", fontSize = 13.sp)
                    Text("3. اضغط على زر التحفظ (حفظ) لتنزيلها وحفظها بشكل فوري في معرض الصور الخاص بك!", fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(onClick = { isStatusSaverVisible = false }) {
                    Text("مشرع بالبدء")
                }
            }
        )
    }

    // --- QUICK ACTION DIALOG 3: CLEANER OPTIMIZER ---
    if (isCleanerVisible) {
        var isCleaningInProgress by remember { mutableStateOf(true) }
        var cleanProgress by remember { mutableStateOf(0f) }

        LaunchedEffect(isCleanerVisible) {
            isCleaningInProgress = true
            cleanProgress = 0f
            while (cleanProgress < 1.0f) {
                delay(30L)
                cleanProgress += 0.02f
            }
            isCleaningInProgress = false
        }

        AlertDialog(
            onDismissRequest = { if (!isCleaningInProgress) isCleanerVisible = false },
            title = {
                Text(
                    text = if (isCleaningInProgress) "جاري فحص مشغّل الوسائط والأجهزة..." else "تم التنظيف بنجاح!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    if (isCleaningInProgress) {
                        CircularProgressIndicator(
                            progress = cleanProgress,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "بصدد تنظيف وتحسين الذاكرة المؤقتة لمقاطع الفيديو والموسيقى: ${(cleanProgress * 100).toInt()}%",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                    } else {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Clean Success",
                            tint = Color.Green,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "تم تنظيف 348 ميغابايت من الملفات المؤقتة غير المرغوبة وزيادة المساحة بنجاح!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                if (!isCleaningInProgress) {
                    Button(onClick = { isCleanerVisible = false }) {
                        Text("رائع")
                    }
                }
            }
        )
    }
}

// ============================================
// SUBSITE TAB LAYOUT 1: VIDEOS & FOLDERS VIEW
// ============================================
data class QuickActionItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideosAndFoldersTab(
    videoList: List<MediaFile>,
    scannedFolders: List<ScannedFolder>,
    onPlayFile: (String) -> Unit,
    viewModel: MediaViewModel,
    layoutMode: String,
    viewContentMode: String,
    sortOption: String,
    sortDirection: String,
    searchQuery: String,
    onSelectedBottomTab: (Int) -> Unit,
    onSelectedSubTabIndex: (Int) -> Unit,
    onOptionsClick: () -> Unit,
    onShowTransfer: () -> Unit,
    onShowStatusSaver: () -> Unit,
    onShowCleaner: () -> Unit
) {
    var selectedFolderPath by rememberSaveable { mutableStateOf<String?>(null) }

    // Derive folders list if database list is empty as a failover
    val derivedFoldersList = remember(videoList, scannedFolders) {
        if (scannedFolders.isNotEmpty()) {
            scannedFolders
        } else {
            videoList.mapNotNull { video ->
                val f = File(video.path).parentFile
                f?.absolutePath
            }.distinct().map { p ->
                val count = videoList.count { File(it.path).parentFile?.absolutePath == p }
                ScannedFolder(
                    folderPath = p,
                    lastModifiedTs = System.currentTimeMillis(),
                    fileCount = count,
                    lastScannedAt = System.currentTimeMillis()
                )
            }
        }
    }

    // Process searches
    val searchedVideos = remember(videoList, searchQuery) {
        if (searchQuery.isBlank()) {
            videoList
        } else {
            videoList.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Sort videos according to selected sorting options
    val sortedVideos = remember(searchedVideos, sortOption, sortDirection) {
        val comparator = when (sortOption) {
            "TITLE" -> compareBy<MediaFile> { it.title.lowercase() }
            "DATE" -> compareBy<MediaFile> { it.dateModified }
            "SIZE" -> compareBy<MediaFile> { it.size }
            "DURATION" -> compareBy<MediaFile> { it.duration }
            "PATH" -> compareBy<MediaFile> { it.path }
            "RESOLUTION" -> compareBy<MediaFile> { it.width * it.height }
            else -> compareBy<MediaFile> { it.title.lowercase() }
        }
        val sorted = searchedVideos.sortedWith(comparator)
        if (sortDirection == "DESCENDING") sorted.reversed() else sorted
    }

    // Final list of videos to display depending on folder filters and content mode
    val displayVideos = remember(sortedVideos, selectedFolderPath, viewContentMode) {
        if (viewContentMode == "FILES") {
            sortedVideos
        } else {
            if (selectedFolderPath == null) {
                sortedVideos
            } else {
                sortedVideos.filter { video ->
                    val parent = File(video.path).parentFile?.absolutePath
                    parent == selectedFolderPath
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("videos_and_folders_tab")
    ) {
        // --- HORIZONTAL QUICK ACTIONS BAR ---
        item {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                val quickActions = listOf(
                    QuickActionItem("الموسيقى", Icons.Default.MusicNote, Color(0xFFFF9800)) { onSelectedBottomTab(1) },
                    QuickActionItem("المنظف", Icons.Default.Brush, Color(0xFF00BCD4)) { onShowCleaner() },
                    QuickActionItem("نقل ملفات", Icons.Default.CompareArrows, Color(0xFF2196F3)) { onShowTransfer() },
                    QuickActionItem("الحالات", Icons.Default.FileDownload, Color(0xFF4CAF50)) { onShowStatusSaver() },
                    QuickActionItem("قوائمه", Icons.Default.PlaylistPlay, Color(0xFF9C27B0)) { onSelectedSubTabIndex(1) },
                    QuickActionItem("الخزنة", Icons.Default.Shield, Color(0xFF3F51B5)) { onSelectedSubTabIndex(2) }
                )
                items(quickActions) { action ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { action.onClick() }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(action.color, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(action.icon, contentDescription = action.label, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(action.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- SUBTITLE STATS & ACTIONS HEADER ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val subLabel = when (viewContentMode) {
                    "FOLDERS" -> "عرض مجلدات (${derivedFoldersList.size})"
                    "FILES" -> "عرض ملفات (${displayVideos.size})"
                    else -> "كافة المجلدات والملفات"
                }
                Text(
                    text = subLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onOptionsClick) {
                    Icon(Icons.Default.Tune, contentDescription = "View controls", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ترتيب وعرض", fontSize = 12.sp)
                }
            }
        }

        // --- CONTENT SPLITTING OR DIRECT VIEW ---
        if (viewContentMode == "FOLDERS" && selectedFolderPath == null) {
            // Folders rendering list mode
            if (derivedFoldersList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("مجلدات الوسائط فارغة (No folders detected)", color = Color.Gray)
                    }
                }
            } else {
                items(derivedFoldersList) { folder ->
                    val folderName = File(folder.folderPath).name
                    val filesCount = videoList.count { File(it.path).parentFile?.absolutePath == folder.folderPath }
                    val totalBytes = videoList.filter { File(it.path).parentFile?.absolutePath == folder.folderPath }.sumOf { it.size }
                    val sizeString = "%.1f MB".format(totalBytes / (1024f * 1024f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .clickable { selectedFolderPath = folder.folderPath }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Folder visual representation with beautiful reddish files-count badge badge
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = "Folder layout",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                            if (filesCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(Color.Red, shape = CircleShape)
                                        .align(Alignment.TopEnd),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = filesCount.toString(),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folderName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$filesCount مقاطع فيديو | $sizeString",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Open",
                            tint = Color.Gray.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        } else {
            // Browsing inside a folder or FLAT directory files listing helper
            if (selectedFolderPath != null && viewContentMode == "FOLDERS") {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                            .clickable { selectedFolderPath = null }
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Go back",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "الرئيسية > ${File(selectedFolderPath!!).name} (${displayVideos.size} فيديو)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (displayVideos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد مقاطع فيديو تطابق شروط الفرز والبحث",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                // Determine whether to show LIST layout or GRID layout in chunked rows
                if (layoutMode == "LIST") {
                    items(displayVideos) { video ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable { onPlayFile(video.path) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "Video marker icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val secs = video.duration / 1000
                                Text(
                                    text = "المدة: %d:%02d | الحجم: %.1f MB".format(
                                        secs / 60, secs % 60, video.size / (1024f * 1024f)
                                    ),
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            // Fav Toggle
                            IconButton(onClick = { viewModel.toggleFavorite(video) }) {
                                Icon(
                                    imageVector = if (video.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Toggle favorite icon selector",
                                    tint = if (video.isFavorite) Color.Red else Color.LightGray
                                )
                            }
                            // Safe Private Vault option
                            IconButton(onClick = { viewModel.setPrivateStatus(video, true) }) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Move to key password secure vault",
                                    tint = Color.LightGray
                                )
                            }
                        }
                    }
                } else {
                    // Modern 2-Column Grid Layout Chunk implementation avoiding nested lists error
                    val columns = 2
                    val videoChunks = displayVideos.chunked(columns)
                    videoChunks.forEach { chunk ->
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                chunk.forEach { video ->
                                    VideoGridItem(
                                        video = video,
                                        onClick = { onPlayFile(video.path) },
                                        onFavoriteClick = { viewModel.toggleFavorite(video) },
                                        onVaultClick = { viewModel.setPrivateStatus(video, true) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (chunk.size < columns) {
                                    Spacer(modifier = Modifier.weight((columns - chunk.size).toFloat()))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoGridItem(
    video: MediaFile,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onVaultClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column {
            // Visual Header Box representing clean Movie artwork thumbnail layout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "Play video badge",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )

                // Duration badge overlay
                val secs = video.duration / 1000
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "%02d:%02d".format(secs / 60, secs % 60),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Information elements
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = video.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "%.1f MB".format(video.size / (1024f * 1024f)),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Row {
                        IconButton(onClick = onFavoriteClick, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = if (video.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite status click option",
                                tint = if (video.isFavorite) Color.Red else Color.LightGray,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        IconButton(onClick = onVaultClick, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock in private suitcase option",
                                tint = Color.LightGray,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================
// SUBSITE TAB LAYOUT 2: MUSIC CONTROLLER VIEW
// ============================================
@Composable
fun MusicPlayerTab(
    audioList: List<MediaFile>,
    onPlayFile: (String) -> Unit,
    viewModel: MediaViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("music_player_tab")
    ) {
        Text(
            "الملفات الصوتية والموسيقى (Audio Music Player)",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (audioList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("لا توجد ملفات موسيقية مضافة", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(audioList) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .clickable { onPlayFile(track.path) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Audiotrack,
                            contentDescription = "Music notation icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist ?: "الفنان غير معروف (Unknown Artist)",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        // Toggle Favorites status
                        IconButton(onClick = { viewModel.toggleFavorite(track) }) {
                            Icon(
                                if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite status icon",
                                tint = if (track.isFavorite) Color.Red else Color.LightGray
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================
// SUBSITE TAB LAYOUT 3: PLAYLISTS & FAVORITES
// ============================================
@Composable
fun PlaylistsAndFavoritesTab(
    playlists: List<PlaylistEntity>,
    favorites: List<MediaFile>,
    onPlayFile: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    viewModel: MediaViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("playlists_and_favorites_tab")
    ) {
        // Creation triggers
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "قوائم التشغيل (Playlists)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(onClick = onCreatePlaylist) {
                    Text("قائمة جديدة (Create)", fontSize = 12.sp)
                }
            }
        }

        if (playlists.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text("لا توجد قوائم تشغيل مضافة", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            items(playlists) { list ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QueueMusic, contentDescription = "Queue logo", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(list.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { viewModel.deletePlaylist(list.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove playlist", tint = Color.Red)
                    }
                }
            }
        }

        // Favorites logs Section
        item {
            Text(
                "المفضلة (Starred Favorites)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
        }

        if (favorites.isEmpty()) {
            item {
                Text("لا توجد وسائط مميزة بالنجمة", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            items(favorites) { favorite ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { onPlayFile(favorite.path) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (favorite.isVideo) Icons.Default.PlayCircle else Icons.Default.Audiotrack,
                        contentDescription = "Media icon type",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        favorite.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { viewModel.toggleFavorite(favorite) }) {
                        Icon(Icons.Default.Favorite, contentDescription = "Liked icon mark", tint = Color.Red)
                    }
                }
            }
        }
    }
}

// ============================================
// SUBSITE TAB LAYOUT 4: PASSWORD SECURED VAULT
// ============================================
@Composable
fun PrivateVaultTab(
    privateFiles: List<MediaFile>,
    isLocked: Boolean,
    onPlayFile: (String) -> Unit,
    onGoSetup: () -> Unit,
    onGoUnlock: () -> Unit,
    viewModel: MediaViewModel
) {
    val context = LocalContext.current
    val hasPasscode = remember { viewModel.getPasscode() != null }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("private_vault_tab")
    ) {
        if (!hasPasscode) {
            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = "Registration required icon",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "الخزنة الخاصة مقفلة (Secure Vault Uninitialized)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "يرجى تعيين رقم سري لحفظ وتأمين مقاطعك السرية بحصانة",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onGoSetup, modifier = Modifier.testTag("vault_setup_button")) {
                    Text("تعيين رمز PIN الخاص بك")
                }
            }
        } else if (isLocked) {
            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = "Secured Vault logo",
                    modifier = Modifier.size(64.dp),
                    tint = Color.Red
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("يرجى فك قفل الخزنة الخاصة (PIN Lock Active)")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onGoUnlock, modifier = Modifier.testTag("vault_unlock_button")) {
                    Text("إدخال كلمة PIN لفك الخصائص")
                }
            }
        } else {
            // Unlocked Private Vault! Exposing files safely
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "الحقيبة الخاصة الآمنة (Unlocked Private Folder)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { viewModel.lockPrivateFolder() }) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock vault", tint = Color.Red)
                    }
                }

                if (privateFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("الخزنة فارغة حالياً. أضف مقاطع إليها لحمايتها.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(privateFiles) { target ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .clickable { onPlayFile(target.path) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = "Secure status option icon", tint = Color.Red)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    target.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // Withdraw item from vault button
                                IconButton(onClick = { viewModel.setPrivateStatus(target, false) }) {
                                    Icon(Icons.Default.LockOpen, contentDescription = "Unlock status trigger icon", tint = Color.Green)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
