package com.example.ui.screens

import android.app.AlertDialog
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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

    var searchQuery by remember { mutableStateOf("") }
    var selectedTabIndex by remember { mutableStateOf(0) }

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
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "مشغل الوسائط المحترف (FinalPlayer)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateToBrowser) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Device Storage Browser")
                        }
                    },
                    actions = {
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

                        // Settings navigation target
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.testTag("settings_button"))
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

                // Global search input field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("ابحث عن الفيديوهات أو المقاطع الصوتية (Search media files)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search bar") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("home_search_input"),
                    singleLine = true
                )

                // Master horizontal tabs selection
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 12.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    val tabs = listOf("الفيديوهات (Videos)", "الموسيقى (Music)", "المفضلة والقوائم", "الخزنة السرية (Vault)")
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main dynamic tab matching routes switcher
            when (selectedTabIndex) {
                0 -> VideosAndFoldersTab(
                    videoList = videoList.filter { it.title.contains(searchQuery, ignoreCase = true) },
                    scannedFolders = scannedFolders,
                    onPlayFile = onPlayFile,
                    viewModel = viewModel
                )
                1 -> MusicPlayerTab(
                    audioList = audioList.filter { it.title.contains(searchQuery, ignoreCase = true) },
                    onPlayFile = onPlayFile,
                    viewModel = viewModel
                )
                2 -> PlaylistsAndFavoritesTab(
                    playlists = playlistsList,
                    favorites = favoritesList,
                    onPlayFile = onPlayFile,
                    onCreatePlaylist = { isPlaylistModalVisible = true },
                    viewModel = viewModel
                )
                3 -> PrivateVaultTab(
                    privateFiles = privateFilesList,
                    isLocked = isPrivateLocked,
                    onPlayFile = onPlayFile,
                    onGoSetup = { isPasscodeSetupDialogVisible = true },
                    onGoUnlock = { isPasscodeUnlockDialogVisible = true },
                    viewModel = viewModel
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
}

// ============================================
// SUBSITE TAB LAYOUT 1: VIDEOS & FOLDERS VIEW
// ============================================
@Composable
fun VideosAndFoldersTab(
    videoList: List<MediaFile>,
    scannedFolders: List<ScannedFolder>,
    onPlayFile: (String) -> Unit,
    viewModel: MediaViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("videos_and_folders_tab")
    ) {
        // Folders Section Title If Present
        if (scannedFolders.isNotEmpty()) {
            item {
                Text(
                    "المجلدات المكتشفة (Discovered Folders)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(scannedFolders) { folder ->
                        val folderFile = File(folder.folderPath)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = "Folder logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = folderFile.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "${folder.fileCount} ملفات",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }

        // Standard Videos List Title
        item {
            Text(
                "جميع مقاطع الفيديو (Scanned Video Library)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (videoList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("لم يتم العثور على مقاطع فيديو في التخزين", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            items(videoList) { video ->
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
                        contentDescription = "Video icon indicator",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(40.dp)
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
                            text = "Duration: %02d:%02d | Size: %.1f MB".format(
                                secs / 60, secs % 60, video.size / (1024f * 1024f)
                            ),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }

                    // Favorite Toggler
                    IconButton(onClick = { viewModel.toggleFavorite(video) }) {
                        Icon(
                            imageVector = if (video.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Toggle favorite",
                            tint = if (video.isFavorite) Color.Red else Color.LightGray
                        )
                    }

                    // Move to Safe vault option
                    IconButton(onClick = { viewModel.setPrivateStatus(video, true) }) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock to secure vault",
                            tint = Color.LightGray
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
