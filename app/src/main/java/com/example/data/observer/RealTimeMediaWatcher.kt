package com.example.data.observer

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class RealTimeMediaWatcher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: MediaRepository,
    private val onTriggerScan: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null
    private var isRegistered = false

    private var contentObserverVideo: ContentObserver? = null
    private var contentObserverAudio: ContentObserver? = null
    private val fileObservers = mutableListOf<FileObserver>()

    init {
        scanRunnable = Runnable {
            Log.d("RealTimeMediaWatcher", "Debounced delta scanner trigger.")
            onTriggerScan()
        }
    }

    // Debounce triggers helper
    fun triggerDebouncedScan() {
        scanRunnable?.let {
            handler.removeCallbacks(it)
            handler.postDelayed(it, 2000) // 2 seconds debounce
        }
    }

    fun registerObservers() {
        if (isRegistered) return
        isRegistered = true

        Log.d("RealTimeMediaWatcher", "Registering ContentObserver and FileObservers")

        // 1. ContentObserver on MediaStore
        try {
            contentObserverVideo = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    Log.d("RealTimeMediaWatcher", "MediaStore video change detected.")
                    triggerDebouncedScan()
                }
            }

            contentObserverAudio = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    Log.d("RealTimeMediaWatcher", "MediaStore audio change detected.")
                    triggerDebouncedScan()
                }
            }

            context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserverVideo!!
            )

            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserverAudio!!
            )
        } catch (e: Exception) {
            Log.e("RealTimeMediaWatcher", "Failed to register MediaStore content observers", e)
        }

        // 2. FileObserver on target folders only (avoid full device lagging)
        val defaultDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File("/storage/emulated/0/DCIM/Camera")
        )

        val uniquePaths = defaultDirs.mapNotNull { if (it.exists() && it.isDirectory) it.absolutePath else null }.toSet()

        val mask = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE

        for (dirPath in uniquePaths) {
            try {
                val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    object : FileObserver(File(dirPath), mask) {
                        override fun onEvent(event: Int, path: String?) {
                            handleFileEvent(event, dirPath, path)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    object : FileObserver(dirPath, mask) {
                        override fun onEvent(event: Int, path: String?) {
                            handleFileEvent(event, dirPath, path)
                        }
                    }
                }
                observer.startWatching()
                fileObservers.add(observer)
                Log.d("RealTimeMediaWatcher", "Watching path: $dirPath")
            } catch (e: Exception) {
                Log.e("RealTimeMediaWatcher", "Could not track path file changes: $dirPath", e)
            }
        }
    }

    private fun handleFileEvent(event: Int, dirPath: String, path: String?) {
        if (path == null) return
        val fullPath = File(dirPath, path).absolutePath

        when (event) {
            FileObserver.CREATE -> {
                Log.d("RealTimeMediaWatcher", "File CREATE detected: $fullPath")
                triggerDebouncedScan()
            }
            FileObserver.CLOSE_WRITE -> {
                Log.d("RealTimeMediaWatcher", "File finished CLOSE_WRITE: $fullPath")
                triggerDebouncedScan()
            }
            FileObserver.DELETE -> {
                Log.d("RealTimeMediaWatcher", "File DELETE detected: $fullPath")
                scope.launch(Dispatchers.IO) {
                    try {
                        repository.getMediaByPath(fullPath)?.let {
                            repository.deleteFile(fullPath)
                        }
                    } catch (e: Exception) {
                        Log.e("RealTimeMediaWatcher", "Failed to delete from db on observation", e)
                    }
                }
                triggerDebouncedScan()
            }
            FileObserver.MOVED_FROM -> {
                Log.d("RealTimeMediaWatcher", "File MOVED_FROM detected context: $fullPath")
                scope.launch(Dispatchers.IO) {
                    try {
                        repository.deleteFile(fullPath)
                    } catch (e: Exception) {
                        Log.e("RealTimeMediaWatcher", "Failed to delete file on moved_from", e)
                    }
                }
                triggerDebouncedScan()
            }
            FileObserver.MOVED_TO -> {
                Log.d("RealTimeMediaWatcher", "File MOVED_TO detected: $fullPath")
                triggerDebouncedScan()
            }
        }
    }

    fun unregisterObservers() {
        isRegistered = false
        contentObserverVideo?.let { context.contentResolver.unregisterContentObserver(it) }
        contentObserverAudio?.let { context.contentResolver.unregisterContentObserver(it) }

        for (fo in fileObservers) {
            fo.stopWatching()
        }
        fileObservers.clear()
        scanRunnable?.let { handler.removeCallbacks(it) }
    }
}
