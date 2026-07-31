package com.example.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object VideoProcessor {

    /**
     * Extracts audio track from video file and saves it as an MP3 or M4A file in Music folder.
     * Operates in background without modifying original video.
     */
    suspend fun extractAudioTrack(
        context: Context,
        inputVideoPath: String,
        customOutputName: String? = null
    ): File? = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            val inputFile = File(inputVideoPath)
            if (!inputFile.exists()) return@withContext null

            val baseName = if (!customOutputName.isNullOrEmpty()) {
                customOutputName.removeSuffix(".mp3").removeSuffix(".m4a")
            } else {
                "${inputFile.nameWithoutExtension}_audio"
            }

            val musicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "FinalPlayer"
            )
            if (!musicDir.exists()) musicDir.mkdirs()

            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            var mimeType = "audio/mp4a-latm"

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    mimeType = mime
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                Log.e("VideoProcessor", "No audio track found in $inputVideoPath")
                return@withContext null
            }

            val ext = if (mimeType.contains("mp3") || mimeType.contains("mpeg")) ".mp3" else ".m4a"
            val outputFile = File(musicDir, "$baseName$ext")

            extractor.selectTrack(audioTrackIndex)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerAudioTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val maxBufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(1024 * 1024)
            } else {
                1024 * 1024
            }

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerAudioTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null
            extractor.release()
            extractor = null

            MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), null, null)

            outputFile
        } catch (e: Exception) {
            Log.e("VideoProcessor", "Error extracting audio", e)
            try { muxer?.stop() } catch (ignored: Exception) {}
            try { muxer?.release() } catch (ignored: Exception) {}
            try { extractor?.release() } catch (ignored: Exception) {}
            null
        }
    }

    /**
     * Fast video trimming without re-encoding (copy codec).
     */
    suspend fun trimVideo(
        context: Context,
        inputVideoPath: String,
        outputFileName: String,
        startMs: Long,
        endMs: Long
    ): File? = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            val inputFile = File(inputVideoPath)
            if (!inputFile.exists()) return@withContext null

            val ext = inputFile.extension.ifEmpty { "mp4" }
            val cleanName = if (outputFileName.contains(".")) outputFileName else "$outputFileName.$ext"
            
            val moviesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "FinalPlayer"
            )
            if (!moviesDir.exists()) moviesDir.mkdirs()

            val outputFile = File(moviesDir, cleanName)

            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            val trackCount = extractor.trackCount
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val indexMap = HashMap<Int, Int>()
            val startUs = startMs * 1000L
            val endUs = endMs * 1000L

            var maxBufferSize = 1024 * 1024

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    if (size > maxBufferSize) maxBufferSize = size
                }
                val muxerTrackIndex = muxer.addTrack(format)
                indexMap[i] = muxerTrackIndex
            }

            muxer.start()

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            for (i in 0 until trackCount) {
                extractor.unselectTrack(i)
            }

            for (i in 0 until trackCount) {
                extractor.selectTrack(i)
                val muxerTrackIndex = indexMap[i] ?: continue

                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                var basePresentationTimeUs: Long = -1

                while (true) {
                    val sampleTime = extractor.sampleTime
                    if (sampleTime < 0 || sampleTime > endUs) {
                        break
                    }

                    if (sampleTime < startUs) {
                        extractor.advance()
                        continue
                    }

                    if (basePresentationTimeUs == -1L) {
                        basePresentationTimeUs = sampleTime
                    }

                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) break

                    bufferInfo.presentationTimeUs = (sampleTime - basePresentationTimeUs).coerceAtLeast(0L)
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                    extractor.advance()
                }

                extractor.unselectTrack(i)
            }

            muxer.stop()
            muxer.release()
            muxer = null
            extractor.release()
            extractor = null

            MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), null, null)

            outputFile
        } catch (e: Exception) {
            Log.e("VideoProcessor", "Error trimming video", e)
            try { muxer?.stop() } catch (ignored: Exception) {}
            try { muxer?.release() } catch (ignored: Exception) {}
            try { extractor?.release() } catch (ignored: Exception) {}
            null
        }
    }

    /**
     * Retrieves video metadata details.
     */
    suspend fun getVideoDetails(filePath: String): Map<String, String> = withContext(Dispatchers.IO) {
        val details = mutableMapOf<String, String>()
        val file = File(filePath)
        if (!file.exists()) return@withContext details

        details["اسم الملف"] = file.name
        details["المسار الكامل"] = file.absolutePath
        details["امتداد الملف"] = file.extension.uppercase()
        details["حجم الملف"] = FileSizeFormatter.formatSize(file.length())

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            
            val durationMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationMsStr?.toLongOrNull() ?: 0L
            details["المدة الزمنية"] = formatDurationMs(durationMs)

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "0"
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "0"
            details["أبعاد الفيديو"] = "$width × $height px"

            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/mp4"
            details["نوع ترميز الـ Mime"] = mime

            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            if (bitrateStr != null) {
                val kbps = (bitrateStr.toLongOrNull() ?: 0L) / 1000
                details["معدل البت (Bitrate)"] = "$kbps Kbps"
            }

            val dateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            if (!dateStr.isNullOrEmpty()) {
                details["تاريخ الإنشاء"] = dateStr
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
        details
    }

    private fun CharSequence?.isNull_or_empty(): Boolean = this == null || this.isEmpty()

    private fun formatDurationMs(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
}
