package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("MediaScanWorker", "Lightweight periodic scan worker triggered.")
            val repository = MediaRepository(applicationContext)
            
            // Fires the highly optimized, non-laggy delta scan
            repository.triggerScan(applicationContext) { progress ->
                Log.d("MediaScanWorker", "Worker scan report: $progress")
            }
            
            Log.d("MediaScanWorker", "Lightweight periodic scan worker completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e("MediaScanWorker", "Lightweight scan periodic worker error context", e)
            Result.retry()
        }
    }
}
