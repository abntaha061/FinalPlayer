package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MediaViewModel
import com.example.ui.components.TrackArtwork
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: MediaViewModel,
    onPlayFile: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentDirectory by remember {
        mutableStateOf(context.getExternalFilesDir(null) ?: context.filesDir)
    }

    var filesList by remember { mutableStateOf(emptyList<File>()) }
    var searchQuery by remember { mutableStateOf("") }

    // Fetch files in directory
    LaunchedEffect(currentDirectory) {
        val files = currentDirectory.listFiles()?.toList() ?: emptyList()
        filesList = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("متصفح الملفات (File Browser)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            currentDirectory.absolutePath,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val parent = currentDirectory.parentFile
                        if (parent != null) {
                            currentDirectory = parent
                        }
                    }) {
                        Icon(Icons.Default.FolderZip, contentDescription = "Up Directory")
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
        ) {
            // Search Bar Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("بحث في المجلد الحالي (Search in directory)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .testTag("browser_search_input"),
                singleLine = true
            )

            val filteredList = filesList.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = "Empty",
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "المجلد فارغ أو لا توجد ملفات مطابقة",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    items(filteredList) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable {
                                    if (file.isDirectory) {
                                        currentDirectory = file
                                    } else {
                                        onPlayFile(file.absolutePath)
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (file.isDirectory) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Directory indicator",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                val isAudio = file.name.endsWith(".mp3", ignoreCase = true) || file.name.endsWith(".m4a", ignoreCase = true) || file.name.endsWith(".wav", ignoreCase = true) || file.name.endsWith(".ogg", ignoreCase = true)
                                if (isAudio) {
                                    TrackArtwork(
                                        filePath = file.absolutePath,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = "Video indicator",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (file.isDirectory) {
                                        "${file.listFiles()?.size ?: 0} elements"
                                    } else {
                                        "Size: %.2f KB".format(file.length() / 1024f)
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!file.isDirectory) {
                                var isMenuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { isMenuExpanded = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Media options", tint = Color.Gray)
                                    }
                                    DropdownMenu(
                                        expanded = isMenuExpanded,
                                        onDismissRequest = { isMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("مشاركة (Share)") },
                                            onClick = {
                                                isMenuExpanded = false
                                                shareFile(context, file)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("حذف (Delete)") },
                                            onClick = {
                                                isMenuExpanded = false
                                                try {
                                                    file.delete()
                                                    val updated = currentDirectory.listFiles()?.toList() ?: emptyList()
                                                    filesList = updated.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
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

private fun shareFile(context: Context, file: File) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share media file with"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
