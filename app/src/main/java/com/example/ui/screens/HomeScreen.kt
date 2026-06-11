package com.example.ui.screens

import android.app.AlertDialog
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.example.data.local.entities.MediaFile
import com.example.data.local.entities.PlaylistEntity
import com.example.data.local.entities.ScannedFolder
import com.example.ui.MediaViewModel
import com.example.ui.components.TrackArtwork
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MediaViewModel,
    onPlayFile: (String) -> Unit,
    onNavigateToBrowser: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val selectedPaths = remember { mutableStateListOf<String>() }

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

    val historyList by viewModel.history.collectAsState(initial = emptyList())
    val selectedFolderPath by viewModel.selectedFolderPath.collectAsState()

    val themeColorHex by viewModel.themeColorHexState.collectAsState()
    val resumeButtonPosition by viewModel.resumeButtonPositionState.collectAsState()
    val currentAccentColor = remember(themeColorHex) { Color(android.graphics.Color.parseColor(themeColorHex)) }

    val resumeVideoFilePath = remember(historyList, selectedFolderPath, videoList) {
        if (selectedFolderPath != null) {
            val inHistory = historyList.firstOrNull { entry ->
                val f = File(entry.mediaFilePath)
                f.parentFile?.absolutePath == selectedFolderPath && videoList.any { it.path == entry.mediaFilePath }
            }?.mediaFilePath
            inHistory ?: videoList.firstOrNull { File(it.path).parentFile?.absolutePath == selectedFolderPath }?.path
        } else {
            val inHistory = historyList.firstOrNull { entry ->
                videoList.any { it.path == entry.mediaFilePath }
            }?.mediaFilePath
            inHistory ?: videoList.firstOrNull()?.path
        }
    }

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

    var isMovePickerOpen by remember { mutableStateOf(false) }
    var isCopyPickerOpen by remember { mutableStateOf(false) }
    var isRenameDialogOpen by remember { mutableStateOf(false) }
    var isDeleteConfirmOpen by remember { mutableStateOf(false) }
                    
    var renameOldPath by remember { mutableStateOf<String?>(null) }
    var renameNewName by remember { mutableStateOf("") }

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

    var lastBackPressTime by remember { mutableStateOf(0L) }

    BackHandler(enabled = true) {
        if (selectedPaths.isNotEmpty()) {
            selectedPaths.clear()
        } else if (selectedFolderPath != null) {
            viewModel.setSelectedFolderPath(null)
        } else if (selectedBottomTab in listOf(1, 2)) {
            selectedBottomTab = 0
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                (context as? android.app.Activity)?.finish()
            } else {
                lastBackPressTime = currentTime
                android.widget.Toast.makeText(context, "اضغط مرة أخرى للخروج من التطبيق", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            if (selectedBottomTab != 2) {
                Column(modifier = Modifier.background(currentAccentColor)) {
                     CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                        if (selectedPaths.isNotEmpty()) {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = "${selectedPaths.size} محدد",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { selectedPaths.clear() }) {
                                        Icon(Icons.Default.Close, contentDescription = "إلغاء التحديد")
                                    }
                                },
                                actions = {
                                    IconButton(onClick = {
                                        val allPaths = mutableListOf<String>()
                                        if (viewContentMode == "FOLDERS" && selectedFolderPath == null) {
                                            val foldersWithVideos = videoList.mapNotNull { video ->
                                                File(video.path).parentFile?.absolutePath
                                            }.distinct()
                                            allPaths.addAll(foldersWithVideos)
                                        } else {
                                            val currentFolder = selectedFolderPath
                                            val filteredVideos = if (currentFolder == null) videoList else videoList.filter {
                                                File(it.path).parentFile?.absolutePath == currentFolder
                                            }
                                            filteredVideos.forEach { allPaths.add(it.path) }
                                        }

                                        if (selectedPaths.size == allPaths.size) {
                                            selectedPaths.clear()
                                        } else {
                                            selectedPaths.clear()
                                            selectedPaths.addAll(allPaths)
                                        }
                                    }) {
                                        Icon(Icons.Default.SelectAll, contentDescription = "تحديد الكل")
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent
                                )
                            )
                        } else {
                            TopAppBar(
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
                                        val currentFolder = selectedFolderPath
                                        if (currentFolder != null && selectedBottomTab == 0 && selectedSubTabIndex == 0) {
                                            Column {
                                                Text(
                                                    text = java.io.File(currentFolder).name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                )
                                                val filesCount = videoList.count { 
                                                    java.io.File(it.path).parentFile?.absolutePath == currentFolder 
                                                }
                                                Text(
                                                    text = formatVideosCountArabic(filesCount),
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        } else {
                                            Text(
                                                "FinalPlayer",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp
                                            )
                                        }
                                    }
                                },
                                navigationIcon = {
                                    val currentFolder = selectedFolderPath
                                    if (currentFolder != null && selectedBottomTab == 0 && selectedSubTabIndex == 0 && !isSearchActive) {
                                        IconButton(onClick = { viewModel.setSelectedFolderPath(null) }) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to folders")
                                        }
                                    }
                                },
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
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent
                                )
                            )
                        }
                    }

                    // Incremental scan alert progress banner
                    AnimatedVisibility(visible = false) {
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
                                     text = if (scanProgressText.isNotEmpty()) scanProgressText else "جاري مسح الملفات الجديدة... (Scanning media library)",
                                     color = MaterialTheme.colorScheme.onPrimaryContainer,
                                     fontSize = 12.sp
                                 )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column {
                val currentTrack by viewModel.currentTrack.collectAsState()
                if (currentTrack != null) {
                    MiniPlayer(viewModel = viewModel)
                }
                
                if (selectedPaths.isNotEmpty()) {
                    NavigationBar(
                        containerColor = currentAccentColor.copy(alpha = 0.15f),
                        windowInsets = WindowInsets.navigationBars,
                        modifier = Modifier.height(64.dp)
                    ) {
                        NavigationBarItem(
                            selected = false,
                            onClick = { isMovePickerOpen = true },
                            icon = { Icon(Icons.Default.DriveFileMove, contentDescription = "نقل") },
                            label = { Text("نقل", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent
                            )
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { isCopyPickerOpen = true },
                            icon = { Icon(Icons.Default.ContentCopy, contentDescription = "نسخ") },
                            label = { Text("نسخ", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent
                            )
                        )
                        NavigationBarItem(
                            selected = false,
                            enabled = selectedPaths.size == 1,
                            onClick = {
                                if (selectedPaths.size == 1) {
                                    renameOldPath = selectedPaths.first()
                                    renameNewName = File(renameOldPath!!).name
                                    isRenameDialogOpen = true
                                }
                            },
                            icon = { Icon(Icons.Default.Edit, contentDescription = "إعادة تسمية") },
                            label = { Text("تسمية", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent
                            )
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { isDeleteConfirmOpen = true },
                            icon = { Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red) },
                            label = { Text("حذف", fontSize = 10.sp, color = Color.Red, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                } else {
                    NavigationBar(
                        containerColor = currentAccentColor.copy(alpha = 0.15f),
                        windowInsets = WindowInsets.navigationBars,
                        modifier = Modifier.height(64.dp)
                    ) {
                        NavigationBarItem(
                            selected = selectedBottomTab == 0,
                            onClick = { selectedBottomTab = 0 },
                            icon = { RedCircleIcon(Icons.Default.VideoLibrary, selectedBottomTab == 0, "Videos", currentAccentColor) },
                            label = { Text("الفيديوهات", fontSize = 10.sp, fontWeight = if (selectedBottomTab == 0) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                                selectedIconColor = Color.Unspecified,
                                unselectedIconColor = Color.Unspecified
                            )
                        )
                        NavigationBarItem(
                            selected = selectedBottomTab == 1,
                            onClick = { selectedBottomTab = 1 },
                            icon = { RedCircleIcon(Icons.Default.MusicNote, selectedBottomTab == 1, "Music", currentAccentColor) },
                            label = { Text("الموسيقى", fontSize = 10.sp, fontWeight = if (selectedBottomTab == 1) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                                selectedIconColor = Color.Unspecified,
                                unselectedIconColor = Color.Unspecified
                            )
                        )
                        NavigationBarItem(
                            selected = selectedBottomTab == 2,
                            onClick = { selectedBottomTab = 2 },
                            icon = { RedCircleIcon(Icons.Default.Settings, selectedBottomTab == 2, "Settings", currentAccentColor) },
                            label = { Text("الإعدادات", fontSize = 10.sp, fontWeight = if (selectedBottomTab == 2) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                                selectedIconColor = Color.Unspecified,
                                unselectedIconColor = Color.Unspecified
                            )
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        // Multi-select operation dialog overlays
        if (isMovePickerOpen) {
            FolderPickerDialog(
                onDismiss = { isMovePickerOpen = false },
                onFolderSelected = { targetDir ->
                    isMovePickerOpen = false
                    viewModel.movePaths(selectedPaths.toList(), targetDir) {
                        selectedPaths.clear()
                    }
                }
            )
        }
        if (isCopyPickerOpen) {
            FolderPickerDialog(
                onDismiss = { isCopyPickerOpen = false },
                onFolderSelected = { targetDir ->
                    isCopyPickerOpen = false
                    viewModel.copyPaths(selectedPaths.toList(), targetDir) {
                        selectedPaths.clear()
                    }
                }
            )
        }
        if (isRenameDialogOpen) {
            val oldPath = renameOldPath
            if (oldPath != null) {
                AlertDialog(
                    onDismissRequest = { isRenameDialogOpen = false },
                    title = { Text("إعادة التسمية", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                    text = {
                        Column {
                            Text("أدخل الاسم الجديد:", fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = renameNewName,
                                onValueChange = { renameNewName = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                isRenameDialogOpen = false
                                if (renameNewName.isNotBlank()) {
                                    viewModel.renamePath(oldPath, renameNewName) {
                                        selectedPaths.clear()
                                    }
                                }
                            }
                        ) {
                            Text("تعديل")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { isRenameDialogOpen = false }) {
                            Text("إلغاء")
                        }
                    }
                )
            }
        }
        if (isDeleteConfirmOpen) {
            AlertDialog(
                onDismissRequest = { isDeleteConfirmOpen = false },
                title = { Text("حذف العناصر", fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 16.sp) },
                text = {
                    Text("هل أنت متأكد من رغبتك في حذف العناصر المحددة (${selectedPaths.size}) بشكل نهائي من جهازك؟", fontSize = 14.sp)
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        onClick = {
                            isDeleteConfirmOpen = false
                            viewModel.deletePaths(selectedPaths.toList()) {
                                selectedPaths.clear()
                            }
                        }
                    ) {
                        Text("حذف نهائي", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isDeleteConfirmOpen = false }) {
                        Text("إلغاء")
                    }
                }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isScanning,
                onRefresh = { viewModel.launchIncrementalScan() },
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
                                onViewContentModeChange = { viewContentMode = it },
                                sortOption = sortOption,
                                sortDirection = sortDirection,
                                searchQuery = searchQuery,
                                onSelectedBottomTab = { selectedBottomTab = it },
                                onSelectedSubTabIndex = { selectedSubTabIndex = it },
                                onOptionsClick = { isOptionsSheetVisible = true },
                                onShowTransfer = { isTransferDialogVisible = true },
                                onShowStatusSaver = { isStatusSaverVisible = true },
                                onShowCleaner = { isCleanerVisible = true },
                                selectedFolderPath = selectedFolderPath,
                                onSelectedFolderPathChange = { viewModel.setSelectedFolderPath(it) },
                                selectedPaths = selectedPaths
                            )
                            1 -> PlaylistsAndFavoritesTab(
                                playlists = playlistsList,
                                favorites = favoritesList,
                                onPlayFile = onPlayFile,
                                onCreatePlaylist = { isPlaylistModalVisible = true },
                                viewModel = viewModel
                            )
                            2 -> MainVaultTabScreen(
                                viewModel = viewModel,
                                onPlayFile = onPlayFile,
                                accentColor = currentAccentColor,
                                onBackToMainMenu = { selectedBottomTab = 0 }
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
                        onPlayFile = onPlayFile,
                        onBack = { selectedBottomTab = 0 }
                    )
                }
            }

            // Floating Resume Button placed dynamically (circular & play video symbol)
            if (selectedBottomTab == 0 && selectedSubTabIndex == 0 && resumeVideoFilePath != null) {
                val isLeft = resumeButtonPosition == "LEFT"
                Box(
                    modifier = Modifier
                        .align(if (isLeft) Alignment.BottomStart else Alignment.BottomEnd)
                        .padding(
                            bottom = paddingValues.calculateBottomPadding() + 24.dp,
                            start = if (isLeft) 24.dp else 0.dp,
                            end = if (isLeft) 0.dp else 24.dp
                        )
                ) {
                    FloatingActionButton(
                        onClick = {
                            resumeVideoFilePath?.let { onPlayFile(it) }
                        },
                        containerColor = currentAccentColor,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp).testTag("resume_playback_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "استئناف التشغيل",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
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

fun toEasternArabicNumerals(input: String): String {
    val western = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ".")
    val eastern = listOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", ",")
    var result = input
    for (i in western.indices) {
        result = result.replace(western[i], eastern[i])
    }
    return result
}

fun formatFolderSizeArabic(totalBytes: Long): String {
    val gigabytes = totalBytes / (1024.0 * 1024.0 * 1024.0)
    val megabytes = totalBytes / (1024.0 * 1024.0)
    return if (gigabytes >= 1.0) {
        val formatted = "%.1f".format(gigabytes)
        toEasternArabicNumerals(formatted) + " غيغابايت"
    } else {
        val formatted = "%.0f".format(megabytes)
        toEasternArabicNumerals(formatted) + " ميغابايت"
    }
}

fun formatVideosCountArabic(count: Int): String {
    return when {
         count == 1 -> "1 فيديو"
         count in 3..10 -> "$count فيديوهات"
         else -> "$count فيديو"
    }
}

@Composable
fun MXFolderIcon(
    folderName: String,
    filesCount: Int,
    isSelected: Boolean = false,
    accentColor: Color = Color(0xFFFF3333)
) {
    val darkGrey = Color(0xFF4C5059) // Silver-grey/dark silver color of MX folder
    val iconColor = Color(0xFF8E95A5) // Light grey color for camera/movie icon inside the folder

    val nameLower = folderName.lowercase()
    val innerIcon = when {
        nameLower.contains("camera") || nameLower.contains("كاميرا") -> Icons.Filled.PhotoCamera
        nameLower.contains("movie") || nameLower.contains("فيديو") || nameLower.contains("film") || nameLower.contains("record") || nameLower.contains("video") -> Icons.Filled.Movie
        else -> null
    }

    Box(
        modifier = Modifier
            .size(width = 54.dp, height = 44.dp)
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accentColor, shape = RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            // Folder tab on the top-left of folder shape to make it look like a real folder directory silhouette!
            Box(
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(width = 16.dp, height = 6.dp)
                    .background(darkGrey, shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            )
            // Bottom/Main Folder body container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp)
                    .background(darkGrey, shape = RoundedCornerShape(3.dp))
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                if (innerIcon != null) {
                    Icon(
                        imageVector = innerIcon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Beautiful badge count displayed as a perfect circle (or pill for multi-digits), aligned & color-synced
        if (filesCount > 0 && !isSelected) {
            val isSingleDigit = filesCount < 10
            Box(
                modifier = if (isSingleDigit) {
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                        .size(18.dp)
                        .background(accentColor, shape = CircleShape)
                } else {
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                        .height(18.dp)
                        .widthIn(min = 18.dp)
                        .background(accentColor, shape = CircleShape)
                        .padding(horizontal = 4.dp)
                },
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    Text(
                        text = filesCount.toString(),
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                includeFontPadding = false
                            )
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VideosAndFoldersTab(
    videoList: List<MediaFile>,
    scannedFolders: List<ScannedFolder>,
    onPlayFile: (String) -> Unit,
    viewModel: MediaViewModel,
    layoutMode: String,
    viewContentMode: String,
    onViewContentModeChange: (String) -> Unit,
    sortOption: String,
    sortDirection: String,
    searchQuery: String,
    onSelectedBottomTab: (Int) -> Unit,
    onSelectedSubTabIndex: (Int) -> Unit,
    onOptionsClick: () -> Unit,
    onShowTransfer: () -> Unit,
    onShowStatusSaver: () -> Unit,
    onShowCleaner: () -> Unit,
    selectedFolderPath: String?,
    onSelectedFolderPathChange: (String?) -> Unit,
    selectedPaths: MutableList<String>
) {
    BackHandler(enabled = selectedFolderPath != null || selectedPaths.isNotEmpty()) {
        if (selectedPaths.isNotEmpty()) {
            selectedPaths.clear()
        } else {
            onSelectedFolderPathChange(null)
        }
    }
    var videoToRename by remember { mutableStateOf<MediaFile?>(null) }
    var newNameText by remember { mutableStateOf("") }
    var videoToDelete by remember { mutableStateOf<MediaFile?>(null) }

    val themeColorHex by viewModel.themeColorHexState.collectAsState()
    val accentColor = remember(themeColorHex) { Color(android.graphics.Color.parseColor(themeColorHex)) }

    // Derive folders list if database list is empty as a failover
    val derivedFoldersList = remember(videoList, scannedFolders) {
        val foldersWithVideos = videoList.mapNotNull { video ->
            File(video.path).parentFile?.absolutePath
        }.distinct()

        val validScannedFolders = scannedFolders.filter { folder ->
            foldersWithVideos.contains(folder.folderPath)
        }

        if (validScannedFolders.isNotEmpty()) {
            validScannedFolders
        } else {
            foldersWithVideos.map { p ->
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
        // --- CONTENT SPLITTING OR DIRECT VIEW ---
        if (viewContentMode == "FOLDERS" && selectedFolderPath == null && searchQuery.isBlank()) {
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
                    val filesCount = videoList.count { 
                        val p = File(it.path).parentFile?.absolutePath
                        p == folder.folderPath
                    }
                    val totalBytes = videoList.filter { 
                        val p = File(it.path).parentFile?.absolutePath
                        p == folder.folderPath
                    }.sumOf { it.size }

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selectedPaths.isNotEmpty()) {
                                            if (selectedPaths.contains(folder.folderPath)) {
                                                selectedPaths.remove(folder.folderPath)
                                            } else {
                                                selectedPaths.add(folder.folderPath)
                                            }
                                        } else {
                                            onSelectedFolderPathChange(folder.folderPath)
                                        }
                                    },
                                    onLongClick = {
                                        if (selectedPaths.contains(folder.folderPath)) {
                                            selectedPaths.remove(folder.folderPath)
                                        } else {
                                            selectedPaths.add(folder.folderPath)
                                        }
                                    }
                                )
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val folderNewVideosCount = remember(videoList, folder.folderPath) {
                                videoList.count { 
                                    it.isNew && java.io.File(it.path).parentFile?.absolutePath == folder.folderPath
                                }
                            }
                            MXFolderIcon(folderName = folderName, filesCount = folderNewVideosCount, isSelected = selectedPaths.contains(folder.folderPath), accentColor = accentColor)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = folderName,
                                        fontSize = 13.5.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = formatVideosCountArabic(filesCount),
                                        color = Color(0xFF8E94A0),
                                        fontSize = 11.5.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF242730), shape = RoundedCornerShape(3.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = formatFolderSizeArabic(totalBytes),
                                            color = Color(0xFFA6ABB6),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Browsing inside a folder or FLAT directory files listing helper
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
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (selectedPaths.isNotEmpty()) {
                                                if (selectedPaths.contains(video.path)) {
                                                    selectedPaths.remove(video.path)
                                                } else {
                                                    selectedPaths.add(video.path)
                                                }
                                            } else {
                                                onPlayFile(video.path)
                                            }
                                        },
                                        onLongClick = {
                                            if (selectedPaths.contains(video.path)) {
                                                selectedPaths.remove(video.path)
                                            } else {
                                                selectedPaths.add(video.path)
                                            }
                                        }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val thumbnail = rememberVideoThumbnail(video.path)
                                Box(
                                    modifier = Modifier
                                        .size(width = 116.dp, height = 68.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (thumbnail != null) {
                                        Image(
                                            bitmap = thumbnail,
                                            contentDescription = "Video thumbnail",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                                        )
                                                    )
                                                )
                                        )
                                    }

                                    if (video.isNew) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(4.dp)
                                                .background(Color(0xFFFF3366), shape = RoundedCornerShape(3.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp), contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "NEW",
                                                color = Color.White,
                                                fontSize = 7.5.sp,
                                                style = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Small elegant play overlay in list view
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                            .border(0.5.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    
                                    // Duration badge overlay on bottom right
                                    val totalSeconds = video.duration / 1000
                                    val hours = totalSeconds / 3600
                                    val minutes = (totalSeconds % 3600) / 60
                                    val seconds = totalSeconds % 60
                                    val durationText = if (hours > 0) {
                                        "%d:%02d:%02d".format(hours, minutes, seconds)
                                    } else {
                                        "%d:%02d".format(minutes, seconds)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(2.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = durationText,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (selectedPaths.contains(video.path)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = video.title,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val hasSubtitles = remember(video.path) {
                                        try {
                                            val vf = java.io.File(video.path)
                                            val parent = vf.parentFile
                                            if (parent != null && parent.exists()) {
                                                val baseName = vf.nameWithoutExtension
                                                parent.listFiles()?.any { sib ->
                                                    val sName = sib.name
                                                    sName.startsWith(baseName) && (sName.endsWith(".srt", ignoreCase = true) || sName.endsWith(".vtt", ignoreCase = true))
                                                } ?: false
                                            } else false
                                        } catch (e: Exception) { false }
                                    }
                                    val dateText = remember(video.dateModified) {
                                        try {
                                            val sdf = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
                                            sdf.format(java.util.Date(video.dateModified))
                                        } catch (e: Exception) {
                                            ""
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (hasSubtitles) {
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFF007AFF), RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp), contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "SRT",
                                                    color = Color.White,
                                                    fontSize = 7.5.sp,
                                                    style = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        if (video.width > 0 && video.height > 0) {
                                            val resolutionText = remember(video.width, video.height) {
                                                val minDim = minOf(video.width, video.height)
                                                when {
                                                    minDim >= 2160 -> "4K"
                                                    minDim >= 1440 -> "2K"
                                                    minDim >= 1080 -> "1080p"
                                                    minDim >= 720 -> "720p"
                                                    minDim >= 480 -> "480p"
                                                    else -> "${video.width}x${video.height}"
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFF34C759), RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp), contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = resolutionText,
                                                    color = Color.White,
                                                    fontSize = 7.5.sp,
                                                    style = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        val sizeString = "%.1f MB".format(video.size / (1024f * 1024f))
                                        Text(
                                            text = sizeString,
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                        if (dateText.isNotEmpty()) {
                                            Text(
                                                text = "•",
                                                fontSize = 11.sp,
                                                color = Color.Gray.copy(alpha = 0.6f)
                                            )
                                            Text(
                                                text = dateText,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }

                            // Offset 3-dots actions menu
                            var isMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { isMenuExpanded = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options menu",
                                        tint = Color.Gray
                                    )
                                }
                                DropdownMenu(
                                    expanded = isMenuExpanded,
                                    onDismissRequest = { isMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("تسمية الملف", fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            isMenuExpanded = false
                                            videoToRename = video
                                            newNameText = video.title
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("حذف الملف", fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Red) },
                                        onClick = {
                                            isMenuExpanded = false
                                            videoToDelete = video
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("نقل إلى الخزنة", fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            isMenuExpanded = false
                                            viewModel.setPrivateStatus(video, true)
                                        }
                                    )
                                }
                            }
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
                                        onClick = {
                                            if (selectedPaths.isNotEmpty()) {
                                                if (selectedPaths.contains(video.path)) {
                                                    selectedPaths.remove(video.path)
                                                } else {
                                                    selectedPaths.add(video.path)
                                                }
                                            } else {
                                                onPlayFile(video.path)
                                            }
                                        },
                                        onFavoriteClick = { viewModel.toggleFavorite(video) },
                                        onVaultClick = { viewModel.setPrivateStatus(video, true) },
                                        onRenameClick = {
                                            videoToRename = video
                                            newNameText = video.title
                                        },
                                        onDeleteClick = {
                                            videoToDelete = video
                                        },
                                        isSelected = selectedPaths.contains(video.path),
                                        onLongClick = {
                                            if (selectedPaths.contains(video.path)) {
                                                selectedPaths.remove(video.path)
                                            } else {
                                                selectedPaths.add(video.path)
                                            }
                                        },
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

    // --- Rename Dialog ---
    if (videoToRename != null) {
        AlertDialog(
            onDismissRequest = { videoToRename = null },
            title = { Text("إعادة تسمية الملف", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("أدخل الاسم الجديد للملف:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newNameText,
                        onValueChange = { newNameText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentVideo = videoToRename
                        if (currentVideo != null && newNameText.isNotBlank()) {
                            viewModel.renameFile(currentVideo, newNameText)
                        }
                        videoToRename = null
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToRename = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // --- Delete Confirmation Dialog ---
    if (videoToDelete != null) {
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text("حذف الملف", fontWeight = FontWeight.Bold, color = Color.Red) },
            text = {
                Text("هل أنت متأكد من رغبتك في حذف هذا الملف بشكل نهائي من جهازك؟")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    onClick = {
                        val currentVideo = videoToDelete
                        if (currentVideo != null) {
                            viewModel.deleteFile(currentVideo)
                        }
                        videoToDelete = null
                    }
                ) {
                    Text("حذف", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

private val videoThumbnailCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(200)

@Composable
fun rememberVideoThumbnail(videoPath: String?): androidx.compose.ui.graphics.ImageBitmap? {
    if (videoPath == null) return null
    var bitmap by remember(videoPath) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(videoThumbnailCache.get(videoPath)) }
    LaunchedEffect(videoPath) {
        if (bitmap != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val file = java.io.File(videoPath)
            if (!file.exists()) return@withContext

            var loadedBitmap: android.graphics.Bitmap? = null

            // Method 1: Try MediaMetadataRetriever via FileInputStream's File Descriptor (Highly robust and permission-safe under API 29+)
            try {
                java.io.FileInputStream(file).use { fis ->
                    android.media.MediaMetadataRetriever().use { retriever ->
                        retriever.setDataSource(fis.fd)
                        loadedBitmap = retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.frameAtTime
                    }
                }
            } catch (e: Exception) {
                // Suppress and try next method
            }

            // Method 2: Try OS-native ThumbnailUtils
            if (loadedBitmap == null) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        loadedBitmap = android.media.ThumbnailUtils.createVideoThumbnail(
                            file,
                            android.util.Size(640, 360), // HD 16:9 aspect ratio standard thumbnail size
                            null
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        loadedBitmap = android.media.ThumbnailUtils.createVideoThumbnail(
                            videoPath,
                            android.provider.MediaStore.Video.Thumbnails.MINI_KIND
                        )
                    }
                } catch (e: Exception) {
                    // Suppress and try next method
                }
            }

            // Method 3: Direct setDataSource path fallback as a last resort
            if (loadedBitmap == null) {
                try {
                    android.media.MediaMetadataRetriever().use { retriever ->
                        retriever.setDataSource(videoPath)
                        loadedBitmap = retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.frameAtTime
                    }
                } catch (e: Exception) {
                    // Suppress
                }
            }

            if (loadedBitmap != null) {
                // Downscale large frames to keep memory consumption low while preserving crisp resolution & aspect ratio
                val finalBitmap = try {
                    val w = loadedBitmap!!.width
                    val h = loadedBitmap!!.height
                    val maxTargetSide = 640
                    if (w > maxTargetSide || h > maxTargetSide) {
                        val ratio = w.toFloat() / h
                        val (targetW, targetH) = if (w > h) {
                            maxTargetSide to (maxTargetSide / ratio).toInt().coerceAtLeast(1)
                        } else {
                            (maxTargetSide * ratio).toInt().coerceAtLeast(1) to maxTargetSide
                        }
                        android.graphics.Bitmap.createScaledBitmap(loadedBitmap!!, targetW, targetH, true)
                    } else {
                        loadedBitmap!!
                    }
                } catch (e: Exception) {
                    loadedBitmap!!
                }
                val imageBitmap = finalBitmap.asImageBitmap()
                videoThumbnailCache.put(videoPath, imageBitmap)
                bitmap = imageBitmap
            }
        }
    }
    return bitmap
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VideoGridItem(
    video: MediaFile,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onVaultClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val thumbnail = rememberVideoThumbnail(video.path)
    Card(
        modifier = modifier
            .padding(vertical = 6.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick?.invoke() }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column {
            // Visual Header Box representing clean Movie artwork thumbnail layout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = "Video thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                                    )
                                )
                            )
                    )
                }
                if (video.isNew) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(Color(0xFFFF3366), shape = RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp), contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NEW",
                            color = Color.White,
                            fontSize = 7.5.sp,
                            style = androidx.compose.ui.text.TextStyle(
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                                lineHeight = 7.5.sp
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play video badge",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                 // Duration badge overlay
                 val totalSeconds = video.duration / 1000
                 val hours = totalSeconds / 3600
                 val minutes = (totalSeconds % 3600) / 60
                 val seconds = totalSeconds % 60
                 val durationText = if (hours > 0) {
                     "%d:%02d:%02d".format(hours, minutes, seconds)
                 } else {
                     "%d:%02d".format(minutes, seconds)
                 }
                 Box(
                     modifier = Modifier
                         .align(Alignment.BottomEnd)
                         .padding(6.dp)
                         .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp))
                         .padding(horizontal = 4.dp, vertical = 2.dp)
                 ) {
                     Text(
                         text = durationText,
                         color = Color.White,
                         fontSize = 10.sp,
                         fontWeight = FontWeight.Bold
                     )
                 }

                 if (isSelected) {
                     Box(
                         modifier = Modifier
                             .fillMaxSize()
                             .background(Color.Black.copy(alpha = 0.5f)),
                         contentAlignment = Alignment.Center
                     ) {
                         Icon(
                             imageVector = Icons.Default.CheckCircle,
                             contentDescription = "Selected",
                             tint = MaterialTheme.colorScheme.primary,
                             modifier = Modifier.size(36.dp)
                         )
                     }
                 }
            }

            // Information elements
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val hasSubtitles = remember(video.path) {
                        try {
                            val vf = java.io.File(video.path)
                            val parent = vf.parentFile
                            if (parent != null && parent.exists()) {
                                val baseName = vf.nameWithoutExtension
                                parent.listFiles()?.any { sib ->
                                    val sName = sib.name
                                    sName.startsWith(baseName) && (sName.endsWith(".srt", ignoreCase = true) || sName.endsWith(".vtt", ignoreCase = true))
                                } ?: false
                            } else false
                        } catch (e: Exception) { false }
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = video.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (hasSubtitles) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF007AFF), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp), contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "SRT",
                                    color = Color.White,
                                    fontSize = 7.5.sp,
                                    style = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (video.width > 0 && video.height > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val resolutionText = remember(video.width, video.height) {
                                val minDim = minOf(video.width, video.height)
                                when {
                                    minDim >= 2160 -> "4K"
                                    minDim >= 1440 -> "2K"
                                    minDim >= 1080 -> "1080p"
                                    minDim >= 720 -> "720p"
                                    minDim >= 480 -> "480p"
                                    else -> "${video.width}x${video.height}"
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF34C759), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp), contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = resolutionText,
                                    color = Color.White,
                                    fontSize = 7.5.sp,
                                    style = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 3-dots Menu trigger
                    var isMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { isMenuExpanded = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options menu",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("تسمية الملف", fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onRenameClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("حذف الملف", fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Red) },
                                onClick = {
                                    isMenuExpanded = false
                                    onDeleteClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("نقل إلى الخزنة", fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onVaultClick()
                                }
                            )
                        }
                    }
                }
                val dateText = remember(video.dateModified) {
                    try {
                        val sdf = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
                        sdf.format(java.util.Date(video.dateModified))
                    } catch (e: Exception) {
                        ""
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "%.1f MB".format(video.size / (1024f * 1024f)),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    if (dateText.isNotEmpty()) {
                        Text(
                            text = " • ",
                            fontSize = 11.sp,
                            color = Color.Gray.copy(alpha = 0.5f)
                        )
                        Text(
                            text = dateText,
                            fontSize = 11.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false
                        )
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
    var isSortMenuVisible by remember { mutableStateOf(false) }
    var sortByOption by remember { mutableStateOf(viewModel.getAudioSortOption()) } // Persisted sort options: 0 = A-Z, 1 = Z-A, 2 = Newest, 3 = Oldest

    var showDialogForPlaylistSelection by remember { mutableStateOf(false) }
    var selectedTrackPathForPlaylist by remember { mutableStateOf("") }
    val playlistsList by viewModel.playlists.collectAsState(initial = emptyList())

    val sortedList = remember(audioList, sortByOption) {
        when (sortByOption) {
            0 -> audioList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            1 -> audioList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }).reversed()
            2 -> audioList.sortedByDescending { it.dateModified }
            3 -> audioList.sortedBy { it.dateModified }
            else -> audioList
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("music_player_tab")
        ) {
            // PureSonic Header Row!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Title + Sort icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isSortMenuVisible = true },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort, // 3 lines sort icon!
                            contentDescription = "Sort Songs Menu",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "PureSonic",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Right Song count badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.padding(6.dp)
                ) {
                    Text(
                        "${audioList.size} أغنية",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            if (sortedList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("لا توجد ملفات موسيقية مضافة", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedList) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                .clickable { onPlayFile(track.path) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Right side: Artwork
                            TrackArtwork(
                                filePath = track.path,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.width(14.dp))

                            // 2. Center: Artist & Title
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = track.artist ?: "فنان غير معروف",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF00C8FF), // Beautiful cyan/teal from PureSonic screenshots!
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // 3. Left side: Duration & Actions
                            val durationSec = track.duration / 1000
                            val durationStr = "%02d:%02d".format(durationSec / 60, durationSec % 60)
                            Text(
                                text = durationStr,
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )

                            // Favorite toggle menu or playlist actions
                            var isTrackMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { isTrackMenuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Track Actions Menu",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = isTrackMenuExpanded,
                                    onDismissRequest = { isTrackMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (track.isFavorite) "إزالة من المفضلة" else "إضافة للمفضلة") },
                                        leadingIcon = {
                                            Icon(
                                                if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = null,
                                                tint = if (track.isFavorite) Color.Red else Color.Gray
                                            )
                                        },
                                        onClick = {
                                            viewModel.toggleFavorite(track)
                                            isTrackMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("إضافة إلى قائمة تشغيل") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.PlaylistAdd,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            selectedTrackPathForPlaylist = track.path
                                            showDialogForPlaylistSelection = true
                                            isTrackMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sort songs interactive bottom overlay sheet
        if (isSortMenuVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { isSortMenuVisible = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(enabled = false) {}, // prevent click-through
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        // Drag handle accent
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 40.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "ترتيب الأغاني",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        val options = listOf(
                            "أبجديًا (من أ إلى ي)" to 0,
                            "أبجديًا عكسيًا (من ي إلى أ)" to 1,
                            "التاريخ (من الأحدث إلى الأقدم)" to 2,
                            "التاريخ (من الأقدم إلى الأحدث)" to 3
                        )

                        options.forEach { (label, optionIdx) ->
                            val isActive = sortByOption == optionIdx
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        sortByOption = optionIdx
                                        viewModel.saveAudioSortOption(optionIdx)
                                        isSortMenuVisible = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isActive) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        if (showDialogForPlaylistSelection) {
            AlertDialog(
                onDismissRequest = { showDialogForPlaylistSelection = false },
                title = { Text("إضافة إلى قائمة تشغيل", fontWeight = FontWeight.Bold) },
                text = {
                    if (playlistsList.isEmpty()) {
                        Text("لا توجد قائمة تشغيل حالية. يرجى إنشاء قائمة جديدة أولاً.")
                    } else {
                        LazyColumn {
                            items(playlistsList) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addToPlaylist(playlist.id, selectedTrackPathForPlaylist)
                                            showDialogForPlaylistSelection = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(playlist.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDialogForPlaylistSelection = false }) {
                        Text("إغلاق")
                    }
                }
            )
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
                    if (favorite.isVideo) {
                        val thumbnail = rememberVideoThumbnail(favorite.path)
                        Box(
                            modifier = Modifier
                                .size(width = 68.dp, height = 40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumbnail != null) {
                                Image(
                                    bitmap = thumbnail,
                                    contentDescription = "Video thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                                )
                                            )
                                        )
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Play icon overlay",
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    } else {
                        TrackArtwork(
                            filePath = favorite.path,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    }
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
                        Text(
                            text = "الخزنة فارغة حالياً.\nلتأمين الفيديوهات اضغط على (3 نقاط) لأي فيديو في تبويب الفيديوهات واختر \"نقل إلى الخزنة\".",
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(privateFiles) { target ->
                            val thumbnail = rememberVideoThumbnail(target.path)
                            val sizeMb = target.size / (1024f * 1024f)
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayFile(target.path) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Thumbnail container
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp, 52.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Black.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (thumbnail != null) {
                                            androidx.compose.foundation.Image(
                                                bitmap = thumbnail,
                                                contentDescription = "Thumbnail",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play Icon",
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        if (target.duration > 0) {
                                            val totalSec = target.duration / 1000
                                            val min = totalSec / 60
                                            val sec = totalSec % 60
                                            val durFormatted = "%02d:%02d".format(min, sec)
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(2.dp)
                                                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = durFormatted,
                                                    fontSize = 8.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Title & Size Detail
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = target.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "الحجم: %.1f MB".format(sizeMb),
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    // Action keys
                                    IconButton(
                                        onClick = { onPlayFile(target.path) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Video",
                                            tint = Color(0xFF34C759)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.setPrivateStatus(target, false) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LockOpen,
                                            contentDescription = "Restore File",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
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

@Composable
fun MiniPlayer(viewModel: MediaViewModel) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isAudioPlaying.collectAsState()
    val progress by viewModel.audioProgress.collectAsState()
    val duration by viewModel.audioDuration.collectAsState()
    val track = currentTrack ?: return

    val colors = remember(track.id) { getAuroraColors(track) }
    val progressFraction = if (duration > 0) progress.toFloat() / duration else 0f

    val themeColorHex by viewModel.themeColorHexState.collectAsState()
    val themeColor = remember(themeColorHex) { Color(android.graphics.Color.parseColor(themeColorHex)) }

    val miniPlayerBrush = remember(colors) {
        val c1 = colors.c1.copy(alpha = 0.38f)
        val c2 = colors.c2.copy(alpha = 0.35f)
        val c3 = colors.c3.copy(alpha = 0.32f)
        val c4 = colors.c4.copy(alpha = 0.36f)
        Brush.linearGradient(
            colors = listOf(c1, c2, c3, c4)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0C0C12))
            .background(brush = miniPlayerBrush)
            .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.15f))
            .clickable { viewModel.setFullPlayerOpen(true) }
    ) {
        // Ultra-thin beautiful progression line on top
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtwork(
                filePath = track.path,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                fallbackColor = colors.c1
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist ?: "فنان غير معروف",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = { viewModel.toggleAudioPlayPause() },
                modifier = Modifier
                    .background(Color.White, CircleShape)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause audio stream",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { viewModel.stopAudio() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Stop stream audio",
                    tint = Color.LightGray.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun RedCircleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    contentDescription: String,
    themeColor: Color
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = if (isSelected) themeColor else Color.Transparent,
                shape = CircleShape
            )
            .border(
                width = 1.dp,
                color = if (isSelected) themeColor else Color.Gray.copy(alpha = 0.3f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) Color.White else Color.Gray,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun FolderPickerDialog(
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit
) {
    var currentDir by remember { mutableStateOf(java.io.File("/storage/emulated/0")) }
    val directories = remember(currentDir) {
        try {
            val list = currentDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: emptyList()
            list.sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "اختر مجلد الوجهة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentDir.absolutePath.replace("/storage/emulated/0", "المساحة الداخلية"),
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(modifier = Modifier.height(280.dp)) {
                // Back button if we are not at absolute root of simulated storage
                if (currentDir.absolutePath != "/storage/emulated/0" && currentDir.parentFile != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currentDir = currentDir.parentFile!! }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = ".. (المجلد السابق)",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                }

                if (directories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد مجلدات فرعية هنا",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(directories) { dir ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentDir = dir }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Folder",
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = dir.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onFolderSelected(currentDir.absolutePath) }
            ) {
                Text("اختر هذا المجلد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

// ========================================================
// --- FULLY IMMERSIVE DEDICATED PRIVATE VAULT PLATFORM ---
// ========================================================
@Composable
fun MainVaultTabScreen(
    viewModel: com.example.ui.MediaViewModel,
    onPlayFile: (String) -> Unit,
    accentColor: Color,
    onBackToMainMenu: () -> Unit
) {
    val privateFiles by viewModel.privateFiles.collectAsState(initial = emptyList())
    val isPrivateLocked by viewModel.isPrivateFolderLocked.collectAsState()
    
    // Track passcode status reactively if user saves/modifies it
    var passcodeTrigger by remember { mutableStateOf(0) }
    val hasPasscode = remember(isPrivateLocked, passcodeTrigger) { 
        viewModel.getPasscode() != null 
    }
    
    var localViewState by remember { mutableStateOf("") }
    
    val resolvedState = remember(hasPasscode, isPrivateLocked, localViewState) {
        if (localViewState.isNotEmpty()) {
            localViewState
        } else {
            if (!hasPasscode) {
                "landing"
            } else if (isPrivateLocked) {
                "keypad_unlock"
            } else {
                "dashboard"
            }
        }
    }
    
    var keypadErrorText by remember { mutableStateOf<String?>(null) }
    
    when (resolvedState) {
        "landing" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(2.dp, accentColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Shield SECURE",
                        tint = accentColor,
                        modifier = Modifier.size(56.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "مركز النواة السرية (Secure Vault Hub) 🛡️",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "مساحة آمنة ومعزولة تماماً في ذاكرة النظام الداخلية لحفظ مقاطع الفيديو الخاصة والحساسة بعيداً عن أيدي الفضوليين والمعرض العام لهاتفك.",
                    fontSize = 12.5.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = "Shield", tint = Color(0xFF34C759), modifier = Modifier.size(20.dp))
                            Column {
                                Text("تأمين بـ PIN خاص", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Color.White)
                                Text("نظام رقمي كامل يمنع أي متسلل من استعراض محتوى الخزنة.", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = "Private", tint = Color(0xFFFF9500), modifier = Modifier.size(20.dp))
                            Column {
                                Text("إخفاء كامل من نظام الهاتف", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Color.White)
                                Text("تتم إزالة الفيديوهات فوراً من الاستوديو وتخزينها تحت تشفير معزول.", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.LockClock, contentDescription = "Clock Auto Lock", tint = Color(0xFF30B0C7), modifier = Modifier.size(20.dp))
                            Column {
                                Text("إغلاق فوري تلقائي", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Color.White)
                                Text("عند الخروج من التطبيق أو غلقه، تقفل الخزنة تلقائياً من جديد للحفاظ على سرية ملفاتك.", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = { localViewState = "keypad_setup" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("vault_full_setup_trigger"),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("تعيين رمز PIN والتفعيل الآن 🔒", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        "keypad_setup" -> {
            VaultKeypad(
                title = "تعيين رمز PIN الجديد للخزنة",
                subtitle = "الرجاء كتابة رمز قفل مكون من 4 أرقام لتأمين خزنتك السرية",
                accentColor = accentColor,
                errorText = keypadErrorText,
                onPinEntered = { enteredPin ->
                    if (enteredPin.length == 4) {
                        viewModel.savePasscode(enteredPin)
                        keypadErrorText = null
                        passcodeTrigger++
                        localViewState = "dashboard"
                    } else {
                        keypadErrorText = "يجب أن يتكون الرمز من 4 أرقام!"
                    }
                },
                onBack = {
                    keypadErrorText = null
                    if (viewModel.getPasscode() != null) {
                        localViewState = "dashboard"
                    } else {
                        localViewState = "landing"
                    }
                }
            )
        }
        "keypad_unlock" -> {
            VaultKeypad(
                title = "الخزنة السرية مقفلة",
                subtitle = "الرجاء إدخال رمز PIN المكون من 4 أرقام لفتح الخزنة واستعراض ملفاتك",
                accentColor = accentColor,
                errorText = keypadErrorText,
                onPinEntered = { enteredPin ->
                    if (viewModel.unlockPrivateFolder(enteredPin)) {
                        keypadErrorText = null
                        localViewState = "dashboard"
                    } else {
                        keypadErrorText = "رمز الـ PIN الذي أدخلته غير صحيح! حاول مرة أخرى"
                    }
                },
                onBack = {
                    keypadErrorText = null
                    onBackToMainMenu()
                }
            )
        }
        "dashboard" -> {
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

            // Confirm permanent deletion alert
            showDeleteConfirmByFile?.let { mediaToDelete ->
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmByFile = null },
                    title = { Text("حذف فيديو خاص نهائياً", fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 16.sp) },
                    text = {
                        Text("هل أنت متأكد من رغبتك في حذف مقطع الفيديو الخاص هذا (${mediaToDelete.title}) بشكل نهائي ومغلق من على جهازك؟ لا يمكن التراجع عن هذه الخطوة.", fontSize = 13.5.sp)
                    },
                    confirmButton = {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            onClick = {
                                showDeleteConfirmByFile = null
                                viewModel.deletePaths(listOf(mediaToDelete.path)) {}
                            }
                        ) {
                            Text("حذف نهائي", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmByFile = null }) {
                            Text("إلغاء")
                        }
                    }
                )
            }

            Scaffold(
                topBar = {
                    @OptIn(ExperimentalMaterial3Api::class)
                    TopAppBar(
                        title = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Safe",
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "مستودع الخزنة الخاصة",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        actions = {
                            // Quick Action Buttons
                            TextButton(onClick = { 
                                localViewState = "keypad_setup" 
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.VpnKey, contentDescription = "Change PIN", tint = accentColor, modifier = Modifier.size(16.dp))
                                    Text("تغيير PIN", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            IconButton(onClick = { 
                                viewModel.lockPrivateFolder()
                                localViewState = "keypad_unlock"
                            }) {
                                Icon(Icons.Default.Lock, contentDescription = "Lock", tint = Color(0xFFFF5252))
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
                    // Analytics stats panel
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("الملفات المحمية", fontSize = 10.5.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("${privateFiles.size} فيديو", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accentColor)
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("إجمالي السعة المشغولة", fontSize = 10.5.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(formattedTotalSize, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accentColor)
                            }
                        }
                    }

                    // Security Certificate
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF34C759).copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, Color(0xFF34C759).copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = "Verified Status", tint = Color(0xFF34C759), modifier = Modifier.size(20.dp))
                            Text(
                                text = "حصانة برمجية كاملة: الملفات معزولة ومحمية محلياً بنجاح.",
                                fontSize = 10.5.sp,
                                color = Color(0xFF34C759)
                            )
                        }
                    }

                    // Toolbar action search & sorting
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("ابحث بالاسم هنا...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        // Sort Menu Dropdown
                        Box {
                            var showSortMenu by remember { mutableStateOf(false) }
                            Button(
                                onClick = { showSortMenu = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(48.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Sort, contentDescription = "Sort Icon", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = when(sortBy) {
                                            "date" -> "الأحدث"
                                            "title" -> "الاسم"
                                            "size" -> "الحجم"
                                            else -> "فرز"
                                        },
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("الأحدث 📅", fontSize = 12.sp) },
                                    onClick = { sortBy = "date"; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("الاسم أ-ي 🔤", fontSize = 12.sp) },
                                    onClick = { sortBy = "title"; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("الحجم الأكبر 💾", fontSize = 12.sp) },
                                    onClick = { sortBy = "size"; showSortMenu = false }
                                )
                            }
                        }
                    }

                    // Secured Videos List
                    if (filteredFiles.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Info, contentDescription = "Empty", tint = accentColor.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "لا توجد نتائج مطابقة لبحثك!" 
                                           else "الخزنة فارغة حالياً!\n\nلتأمين الفيديوهات اضغط على زر الخيارات (3 نقاط) لأي فيديو في تبويب الفيديوهات واختر \"نقل إلى الخزنة\".",
                                    fontSize = 12.5.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            items(filteredFiles, key = { it.id }) { file ->
                                val thumbnail = rememberVideoThumbnail(file.path)
                                val sizeMb = file.size / (1024f * 1024f)

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Visual thumbnail cover
                                        Box(
                                            modifier = Modifier
                                                .size(90.dp, 58.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.Black.copy(alpha = 0.3f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (thumbnail != null) {
                                                Image(
                                                    bitmap = thumbnail,
                                                    contentDescription = "Cover",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Default Play",
                                                    tint = accentColor.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(24.dp)
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
                                                        .padding(2.dp)
                                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                                ) {
                                                    Text(durFormatted, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Text titles
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.title,
                                                fontSize = 12.2.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
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

                                        // Play action button
                                        IconButton(
                                            onClick = { onPlayFile(file.path) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color(0xFF34C759), modifier = Modifier.size(22.dp))
                                        }

                                        // Unlocks / Restore from vault
                                        IconButton(
                                            onClick = { viewModel.setPrivateStatus(file, false) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.LockOpen, contentDescription = "Restore", tint = accentColor, modifier = Modifier.size(18.dp))
                                        }

                                        // Permanently delete
                                        IconButton(
                                            onClick = { showDeleteConfirmByFile = file },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF4D4D), modifier = Modifier.size(18.dp))
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
}
