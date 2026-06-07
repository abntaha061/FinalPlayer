package com.example.data.local

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.MediaStore
import android.util.Log
import com.example.data.local.dao.MediaDao
import com.example.data.local.entities.MediaFile
import com.example.data.local.entities.ScannedFolder
import kotlinx.coroutines.*
import java.io.File

class MediaScanner(private val mediaDao: MediaDao) {

    // Main scanning orchestrator
    suspend fun scanMedia(context: Context, onProgress: (String) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        val sharedPrefs = context.getSharedPreferences("finalplayer_preferences", Context.MODE_PRIVATE)
        val isFirstLaunch = !sharedPrefs.getBoolean("first_launch_complete", false)
        val lastScanTime = sharedPrefs.getLong("last_scan_timestamp", 0L)

        Log.d("MediaScanner", "Initiating scan. FirstLaunch: $isFirstLaunch, LastScan: $lastScanTime")

        // -----------------------------------------------------------------
        // COLD SYSTEM HEALTH CHECK (Low Battery or Low Memory exits)
        // -----------------------------------------------------------------
        try {
            // Check battery level
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, batteryFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100f / scale) else 100f
            if (batteryPct < 15.0f) {
                onProgress("تم تجاهل المسح: البطارية منخفضة (${batteryPct.toInt()}%)")
                Log.w("MediaScanner", "Scan skipped: Battery level is too low ($batteryPct%)")
                return@withContext 0
            }

            // Check low memory state
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)
            if (memoryInfo.lowMemory) {
                onProgress("تم تجاهل المسح: الذاكرة العشوائية ممتلئة")
                Log.w("MediaScanner", "Scan skipped: Low system memory condition detected.")
                return@withContext 0
            }
        } catch (e: Exception) {
            Log.w("MediaScanner", "Resource metrics check failed, proceeding safely", e)
        }

        // -----------------------------------------------------------------
        // FOLDER TIMESTAMP CHECK (<100ms Fast Check for Submissions)
        // -----------------------------------------------------------------
        if (!isFirstLaunch && lastScanTime > 0) {
            try {
                val registeredFolders = mediaDao.getAllFolders()
                if (registeredFolders.isNotEmpty()) {
                    var directoriesChanged = false
                    for (folder in registeredFolders) {
                        val localDir = File(folder.folderPath)
                        if (localDir.exists() && localDir.isDirectory) {
                            if (localDir.lastModified() > folder.lastModifiedTs) {
                                directoriesChanged = true
                                break
                            }
                        }
                    }
                    if (!directoriesChanged) {
                        Log.d("MediaScanner", "Folders timestamps identical. Skipping full scans (<10ms).")
                        // Run a swift mini delta query on MediaStore for changes to be absolutely sure
                        return@withContext runDeltaScanner(context, lastScanTime, onProgress)
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaScanner", "Folder fast-check failed, fallback to delta scanning", e)
            }
        }

        // -----------------------------------------------------------------
        // ROUTE SPECIFICATION: First Launch vs Delta Scan
        // -----------------------------------------------------------------
        try {
            withTimeout(30_000) { // Safety timeout limit 30 seconds
                val insertedCount = if (isFirstLaunch) {
                    val count = runFirstLaunchScan(context, onProgress)
                    sharedPrefs.edit().putBoolean("first_launch_complete", true).apply()
                    count
                } else {
                    runDeltaScanner(context, lastScanTime, onProgress)
                }
                
                // Track current scan time
                sharedPrefs.edit().putLong("last_scan_timestamp", System.currentTimeMillis()).apply()
                insertedCount
            }
        } catch (e: TimeoutCancellationException) {
            Log.e("MediaScanner", "Scan process timed out after 30s", e)
            onProgress("توقف المسح بسبب المهلة الزمنية")
            0
        } catch (e: Exception) {
            Log.e("MediaScanner", "General scanning failure", e)
            0
        }
    }

    // PHASED FIRST LAUNCH SCAN (High Speed, Pager-like immediate response)
    private suspend fun runFirstLaunchScan(context: Context, onProgress: (String) -> Unit): Int = supervisorScope {
        Log.d("MediaScanner", "First launch! Scanning crucial directories first.")
        
        // Phase 1: High priority folders first (DCIM, Movies, Download)
        onProgress("جاري مسح المجلدات الرئيسية (DCIM/Movies) 📂...")
        val priorityFiles = scanMediaStoreDirectories(context, listOf("/DCIM", "/Movies", "/Download", "/DCIM/Camera"))
        insertBatchChunked(priorityFiles)

        // Phase 2: Secondary folders and files in the background
        onProgress("جاري مسح باقي ملفات الوسائط في الخلفية 🔍...")
        val remainingFiles = scanMediaStoreDirectories(context, emptyList(), excludePaths = listOf("/DCIM", "/Movies", "/Download"))
        insertBatchChunked(remainingFiles)

        onProgress("اكتمل المسح التلقائي الأول بنجاح 🎉")
        
        // Start helper to clean up nonexistent files
        deleteOrphanedFiles()
        priorityFiles.size + remainingFiles.size
    }

    // COLD DELTA SCANNER: Query MediaStore for modern creations since last scan
    private suspend fun runDeltaScanner(context: Context, lastScanTime: Long, onProgress: (String) -> Unit): Int {
        Log.d("MediaScanner", "Running Delta Scanner since: $lastScanTime")
        onProgress("جاري فحص الملفات الجديدة 🧭...")
        val newAndUpdated = queryNewMediaStoreItems(context, lastScanTime)
        
        if (newAndUpdated.isNotEmpty()) {
            insertBatchChunked(newAndUpdated)
            onProgress("تمت إضافة وتحديث ${newAndUpdated.size} ملفات جديدة ✅")
        } else {
            onProgress("")
        }

        // Run orphaned file cleanup occasionally (every 24 hrs or simply during scan if not frequent)
        deleteOrphanedFiles()
        return newAndUpdated.size
    }

    // Helper: Scans MediaStore with folder filtering
    private suspend fun scanMediaStoreDirectories(
        context: Context,
        matchingPaths: List<String>,
        excludePaths: List<String> = emptyList()
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val foundFiles = mutableListOf<MediaFile>()

        // 1. VIDEOS CURSOR
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    try {
                        val path = cursor.getString(dataCol) ?: continue
                        val file = File(path)
                        val folderPath = file.parent ?: "/"

                        if (matchingPaths.isNotEmpty() && !matchingPaths.any { folderPath.contains(it, ignoreCase = true) }) {
                            continue
                        }
                        if (excludePaths.isNotEmpty() && excludePaths.any { folderPath.contains(it, ignoreCase = true) }) {
                            continue
                        }

                        val dateMod = cursor.getLong(dateModCol) * 1000L
                        foundFiles.add(
                            MediaFile(
                                path = path,
                                title = cursor.getString(nameCol) ?: file.name,
                                duration = cursor.getLong(durationCol),
                                size = cursor.getLong(sizeCol),
                                dateModified = dateMod,
                                isVideo = true,
                                width = cursor.getInt(widthCol),
                                height = cursor.getInt(heightCol)
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("MediaScanner", "Error analyzing video index row", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MediaScanner", "Error querying videos cursor", e)
        }

        // 2. AUDIOS CURSOR
        val audioProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM
        )

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioProjection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)

                while (cursor.moveToNext()) {
                    try {
                        val path = cursor.getString(dataCol) ?: continue
                        val file = File(path)
                        val folderPath = file.parent ?: "/"

                        if (matchingPaths.isNotEmpty() && !matchingPaths.any { folderPath.contains(it, ignoreCase = true) }) {
                            continue
                        }
                        if (excludePaths.isNotEmpty() && excludePaths.any { folderPath.contains(it, ignoreCase = true) }) {
                            continue
                        }

                        val dateMod = cursor.getLong(dateModCol) * 1000L
                        foundFiles.add(
                            MediaFile(
                                path = path,
                                title = cursor.getString(nameCol) ?: file.name,
                                duration = cursor.getLong(durationCol),
                                size = cursor.getLong(sizeCol),
                                dateModified = dateMod,
                                isVideo = false,
                                artist = cursor.getString(artistCol),
                                album = cursor.getString(albumCol)
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("MediaScanner", "Error analyzing audio cursor row", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MediaScanner", "Error querying audios cursor", e)
        }

        foundFiles
    }

    // Query elements added or modified since lastScanTime
    private suspend fun queryNewMediaStoreItems(context: Context, lastScanTime: Long): List<MediaFile> = withContext(Dispatchers.IO) {
        val deltaFiles = mutableListOf<MediaFile>()
        val lastScanTimeSec = lastScanTime / 1000L

        // VIDEOS
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        val videoSelection = "${MediaStore.Video.Media.DATE_MODIFIED} > ?"
        val selectionArgs = arrayOf(lastScanTimeSec.toString())

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                videoSelection,
                selectionArgs,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    try {
                        val path = cursor.getString(dataCol) ?: continue
                        val file = File(path)
                        val dateMod = cursor.getLong(dateModCol) * 1000L
                        deltaFiles.add(
                            MediaFile(
                                path = path,
                                title = cursor.getString(nameCol) ?: file.name,
                                duration = cursor.getLong(durationCol),
                                size = cursor.getLong(sizeCol),
                                dateModified = dateMod,
                                isVideo = true,
                                width = cursor.getInt(widthCol),
                                height = cursor.getInt(heightCol)
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("MediaScanner", "Error compiling delta video row", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MediaScanner", "Delta video query failed", e)
        }

        // AUDIOS
        val audioProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM
        )
        val audioSelection = "${MediaStore.Audio.Media.DATE_MODIFIED} > ?"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioProjection,
                audioSelection,
                selectionArgs,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)

                while (cursor.moveToNext()) {
                    try {
                        val path = cursor.getString(dataCol) ?: continue
                        val file = File(path)
                        val dateMod = cursor.getLong(dateModCol) * 1000L
                        deltaFiles.add(
                            MediaFile(
                                path = path,
                                title = cursor.getString(nameCol) ?: file.name,
                                duration = cursor.getLong(durationCol),
                                size = cursor.getLong(sizeCol),
                                dateModified = dateMod,
                                isVideo = false,
                                artist = cursor.getString(artistCol),
                                album = cursor.getString(albumCol)
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("MediaScanner", "Error compiling delta audio row", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MediaScanner", "Delta audio query failed", e)
        }

        deltaFiles
    }

    // CHUNKED PROCESSING: Saves 50 items with 10ms delays to prevent UI locks
    private suspend fun insertBatchChunked(mediaList: List<MediaFile>) = supervisorScope {
        if (mediaList.isEmpty()) return@supervisorScope
        
        mediaList.chunked(50).forEach { chunk ->
            try {
                val processedChunk = chunk.map { original ->
                    // Protect custom states fields of existing objects
                    val existing = mediaDao.getMediaFileByPath(original.path)
                    if (existing != null) {
                        original.copy(
                            id = existing.id,
                            isFavorite = existing.isFavorite,
                            isPrivate = existing.isPrivate,
                            lastPlayPosition = existing.lastPlayPosition,
                            thumbnailPath = existing.thumbnailPath
                        )
                    } else {
                        original
                    }
                }
                mediaDao.insertMediaFiles(processedChunk)
                
                // Keep Scanned Folder structures updated as folder elements are stored
                val folderGroups = processedChunk.groupBy { File(it.path).parent ?: "/" }
                folderGroups.forEach { (dirPath, files) ->
                    val maxModified = files.maxOfOrNull { it.dateModified } ?: System.currentTimeMillis()
                    mediaDao.insertFolder(
                        ScannedFolder(
                            folderPath = dirPath,
                            lastModifiedTs = maxModified,
                            fileCount = files.size,
                            lastScannedAt = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e // Propagate coroutines cancellation cleanly
            } catch (e: Exception) {
                Log.e("MediaScanner", "Error storing chunk into database", e)
            }
            delay(10) // 10ms respite delay
        }
    }

    // Periodic cleanup of physically deleted files from the room database
    suspend fun deleteOrphanedFiles() = withContext(Dispatchers.IO) {
        try {
            // Note: Safely perform locally to clear records that don't match existing documents
            val databaseFolders = mediaDao.getAllFolders()
            for (folder in databaseFolders) {
                val dir = File(folder.folderPath)
                if (!dir.exists()) {
                    mediaDao.deleteFolder(folder.folderPath)
                }
            }
        } catch (e: Exception) {
            Log.e("MediaScanner", "Orphaned files cleanup error", e)
        }
    }
}
