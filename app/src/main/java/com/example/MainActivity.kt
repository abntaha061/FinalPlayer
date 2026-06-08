package com.example

// PureSonic UI & Video Thumbnail Quality Improvements applied successfully.
// This activates standard Git tracking for staging and committing project progress.

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.MediaViewModel
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.MusicLyricsPlayerScreen
import com.example.data.local.entities.MediaFile
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainNavigationRoot()
            }
        }
    }
}

@Composable
fun MainNavigationRoot() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val viewModel: MediaViewModel = viewModel()

    // 1. Declare Permission requirements
    val requiredPermissions = remember {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.READ_MEDIA_VIDEO)
            list.add(Manifest.permission.READ_MEDIA_AUDIO)
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        list
    }

    // Helper functions for checking permissions status
    val checkAllFilesAccess = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    val checkStandardPermissions = {
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    val checkAllGranted = {
        checkStandardPermissions() && checkAllFilesAccess()
    }

    // Permission Verification status flow
    var hasGrantedPermissions by remember {
        mutableStateOf(checkAllGranted())
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        val standardGranted = resultMap.values.all { it }
        val allFilesGranted = checkAllFilesAccess()
        if (standardGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !allFilesGranted) {
                // Request All Files Access
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                }
            } else {
                hasGrantedPermissions = true
                viewModel.launchIncrementalScan()
            }
        }
    }

    // ON_RESUME lifecycle observer to automatically re-evaluate status
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val granted = checkAllGranted()
                if (granted != hasGrantedPermissions) {
                    hasGrantedPermissions = granted
                    if (granted) {
                        viewModel.launchIncrementalScan()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var activePlayingFilePath by rememberSaveable { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (!hasGrantedPermissions) {
            // High fidelity permission setup onboarding view
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                        .testTag("permissions_request_view"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Circular Glowing Icon Background Container
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(32.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FolderSpecial,
                            contentDescription = "Storage permissions setup logo",
                            modifier = Modifier.size(54.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "صلاحيات الوصول والتشغيل الكامل",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "يحتاج تطبيق FinalPlayer إلى منح هذه الصلاحيات لتقديم تجربة تشغيل مثالية للفيديوهات والمقاطع الصوتية وقراءة الترجمات المصاحبة تلقائياً",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(28.dp))
                    
                    // Direct interactive permissions status overview items
                    val allFilesGranted = checkAllFilesAccess()
                    val videoPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                    } else {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    }
                    val audioPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                    } else {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    }
                    val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Card 1: All Files Access
                        PermissionStatusCard(
                            icon = Icons.Default.Folder,
                            title = "الوصول إلى كل الملفات (All Files)",
                            description = "لتصفح الذاكرة وعرض ملفات الترجمة والوسائط في جميع المجلدات",
                            isGranted = allFilesGranted
                        )

                        // Card 2: Video Library
                        PermissionStatusCard(
                            icon = Icons.Default.VideoLibrary,
                            title = "مقاطع الفيديو والصور (Video Library)",
                            description = "لقراءة وتنسيق وتحسين عرض الفيديوهات داخل التطبيق",
                            isGranted = videoPermissionGranted
                        )

                        // Card 3: Audio Tracks
                        PermissionStatusCard(
                            icon = Icons.Default.Audiotrack,
                            title = "الموسيقى والملفات الصوتية (Audio Tracks)",
                            description = "لتشغيل الأغاني والملفات الصوتية وعرض كلمات الأغاني المدمجة",
                            isGranted = audioPermissionGranted
                        )

                        // Card 4: Notifications (for 13+ only)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            PermissionStatusCard(
                                icon = Icons.Default.Notifications,
                                title = "التحكم وشريط الإشعارات (Notifications)",
                                description = "لعرض أداة التحكم بالتشغيل في الخلفية والمشغل العائم بسلاسة",
                                isGranted = notificationsGranted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    Button(
                        onClick = {
                            val standardGranted = checkStandardPermissions()
                            if (!standardGranted) {
                                launcher.launch(requiredPermissions.toTypedArray())
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !allFilesGranted) {
                                    try {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = android.net.Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        context.startActivity(intent)
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("grant_permissions_button")
                    ) {
                        val buttonText = if (!checkStandardPermissions()) {
                            "منح الصلاحيات الآن (Grant Access)"
                        } else {
                            "السماح بالوصول لكافة الملفات (Allow All Files Access)"
                        }
                        Text(
                            text = buttonText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        } else {
            val isFullPlayerOpen by viewModel.isFullPlayerOpen.collectAsState()
            val scope = rememberCoroutineScope()

            val handlePlayFile: (String) -> Unit = { path ->
                val file = java.io.File(path)
                val ext = file.extension.lowercase()
                val isAudio = ext in listOf("mp3", "wav", "flac", "ogg", "m4a", "aac", "wma", "opus") || path.startsWith("http")
                if (isAudio) {
                    scope.launch {
                        val dbTrack = viewModel.getMediaByPath(path)
                        val track = dbTrack ?: MediaFile(
                            path = path,
                            title = file.nameWithoutExtension,
                            duration = 300000L,
                            size = file.length(),
                            dateModified = file.lastModified(),
                            isVideo = false
                        )
                        viewModel.playAudio(track)
                        viewModel.setFullPlayerOpen(true)
                    }
                } else {
                    activePlayingFilePath = path
                }
            }

            // Full screen overlay player is active
            if (activePlayingFilePath != null) {
                PlayerScreen(
                    filePath = activePlayingFilePath!!,
                    viewModel = viewModel,
                    onBack = { activePlayingFilePath = null },
                    onNavigateToVideo = { activePlayingFilePath = it }
                )
            } else if (isFullPlayerOpen) {
                MusicLyricsPlayerScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.setFullPlayerOpen(false) }
                )
            } else {
                // Navigation components layout
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onPlayFile = handlePlayFile,
                            onNavigateToBrowser = { navController.navigate("browser") },
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("browser") {
                        BrowserScreen(
                            viewModel = viewModel,
                            onPlayFile = handlePlayFile,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionStatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isGranted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = if (isGranted) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            Color.Gray.copy(alpha = 0.15f)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    fontSize = 10.5.sp,
                    color = Color.Gray,
                    lineHeight = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.width(10.dp))
            
            // Status Indicator Badge
            if (isGranted) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "مفعل ✅",
                        color = Color(0xFF4CAF50),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "مطلوب 🔑",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
