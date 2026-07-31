package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.local.entities.MediaFile
import com.example.data.local.entities.PlaylistEntity
import com.example.ui.MediaViewModel
import com.example.util.FileSizeFormatter
import com.example.util.VideoProcessor
import com.example.ui.screens.rememberVideoThumbnail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

sealed class VideoOptionAction {
    object AddToPlaylist : VideoOptionAction()
    object ConvertToMp3 : VideoOptionAction()
    object LockVault : VideoOptionAction()
    object TrimVideo : VideoOptionAction()
    object Edit : VideoOptionAction()
    object Share : VideoOptionAction()
    object Rename : VideoOptionAction()
    object Details : VideoOptionAction()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoOptionsBottomSheet(
    video: MediaFile,
    onDismiss: () -> Unit,
    onActionSelected: (VideoOptionAction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1B1B22),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.5f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header Info Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Video Thumbnail
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2B2B36)),
                    contentAlignment = Alignment.Center
                ) {
                    val thumbnail = rememberVideoThumbnail(video.path, video.id)
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${FileSizeFormatter.formatSize(video.size)} • ${formatDurationMs(video.duration)}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color.White.copy(alpha = 0.1f)
            )

            // 8 Options List
            val optionsList = listOf(
                Triple("أضف لقائمة التشغيل", Icons.Default.PlaylistAdd, VideoOptionAction.AddToPlaylist),
                Triple("تحويل لـ MP3 (استخراج الصوت)", Icons.Default.Audiotrack, VideoOptionAction.ConvertToMp3),
                Triple("قفل / نقل للخزنة المشفرة", Icons.Default.Lock, VideoOptionAction.LockVault),
                Triple("قص وتعديل الفيديو (Video Trimmer)", Icons.Default.ContentCut, VideoOptionAction.TrimVideo),
                Triple("تعديل وتحرير (Edit)", Icons.Default.EditNote, VideoOptionAction.Edit),
                Triple("مشاركة الفيديو", Icons.Default.Share, VideoOptionAction.Share),
                Triple("إعادة تسمية الملف", Icons.Default.DriveFileRenameOutline, VideoOptionAction.Rename),
                Triple("تفاصيل الفيديو المعمقة", Icons.Default.Info, VideoOptionAction.Details)
            )

            optionsList.forEach { (label, icon, action) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onActionSelected(action)
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// 1. Extract Audio Dialog
@Composable
fun ExtractAudioDialog(
    video: MediaFile,
    viewModel: MediaViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExtracting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isExtracting) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Audiotrack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "تحويل الفيديو لـ MP3",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (isExtracting) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "جاري استخراج المسار الصوتي وحفظه...",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            } else {
                Text(
                    text = "هل ترغب في استخراج الصوت من فيديو \"${video.title}\" وحفظه كملف صوتي مستقل في مجلد الموسيقى بدون التعديل على الفيديو الأصلي؟",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            if (!isExtracting) {
                Button(
                    onClick = {
                        isExtracting = true
                        scope.launch {
                            val audioFile = VideoProcessor.extractAudioTrack(context, video.path)
                            isExtracting = false
                            if (audioFile != null) {
                                Toast.makeText(context, "تم استخراج ملف الصوت بنجاح: ${audioFile.name}", Toast.LENGTH_LONG).show()
                                viewModel.launchIncrementalScan()
                            } else {
                                Toast.makeText(context, "حدث خطأ أثناء استخراج الصوت", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("استخراج الآن")
                }
            }
        },
        dismissButton = {
            if (!isExtracting) {
                TextButton(onClick = onDismiss) {
                    Text("إلغاء")
                }
            }
        },
        containerColor = Color(0xFF22222A),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

// 2. Details Dialog
@Composable
fun VideoDetailsDialog(
    video: MediaFile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var detailsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(video.path) {
        val res = VideoProcessor.getVideoDetails(video.path)
        detailsMap = res
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "تفاصيل الفيديو والمعلومات",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(detailsMap.toList()) { (key, value) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = key,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = value,
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("إغلاق")
            }
        },
        containerColor = Color(0xFF22222A),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

// 3. Rename File Dialog
@Composable
fun RenameFileDialog(
    video: MediaFile,
    viewModel: MediaViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var newName by remember { mutableStateOf(File(video.path).name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إعادة تسمية الفيديو",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "أدخل الاسم الجديد للملف:",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank() && newName != File(video.path).name) {
                        viewModel.renamePath(video.path, newName.trim()) {
                            Toast.makeText(context, "تم إعادة تسمية الفيديو بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    }
                    onDismiss()
                },
                enabled = newName.isNotBlank()
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        },
        containerColor = Color(0xFF22222A),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

// 4. Add to Playlist Dialog
@Composable
fun AddToPlaylistDialog(
    video: MediaFile,
    viewModel: MediaViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    var showNewPlaylistInput by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إضافة لقائمة التشغيل",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (showNewPlaylistInput) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("اسم القائمة الجديدة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    OutlinedButton(
                        onClick = { showNewPlaylistInput = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إنشاء قائمة تشغيل جديدة")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (playlists.isEmpty()) {
                        Text(
                            text = "لا توجد قوائم تشغيل سابقة",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(playlists) { playlist ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addToPlaylist(playlist.id, video.path)
                                            Toast.makeText(context, "تمت الإضافة إلى \"${playlist.name}\"", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        },
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.PlaylistPlay,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = playlist.name,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showNewPlaylistInput) {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName.trim())
                            Toast.makeText(context, "تم إنشاء القائمة بنجاح", Toast.LENGTH_SHORT).show()
                            showNewPlaylistInput = false
                            newPlaylistName = ""
                        }
                    }
                ) {
                    Text("إنشاء")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (showNewPlaylistInput) {
                        showNewPlaylistInput = false
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text("إلغاء")
            }
        },
        containerColor = Color(0xFF22222A),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

// Helper function to share video via Intent
fun shareVideoFile(context: Context, filePath: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "الملف غير موجود!", Toast.LENGTH_SHORT).show()
            return
        }

        val uri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(shareIntent, "مشاركة الفيديو عبر")
        )
    } catch (e: Exception) {
        Toast.makeText(context, "حدث خطأ أثناء تنفيذ المشاركة", Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}

private fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
