package com.example.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object SubtitleAutoFetchService {
    private const val TAG = "SubtitleAutoFetch"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Parse the video file name using Gemini to detect clean metadata.
     */
    suspend fun detectMetadata(fileName: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not configured")
            return@withContext fileName
        }

        val prompt = "Analyze this media file name: \"$fileName\". Extract the clean, friendly movie/show title, release year, season, and episode if applicable. Keep the output very concise, e.g. \"The Matrix (1999)\" or \"Breaking Bad - Season 2 Episode 5\". Return ONLY this clean title text, with no extra explanations or markdown."
        
        try {
            val response = makeGeminiCall(apiKey, prompt)
            response.trim().ifEmpty { fileName }
        } catch (e: Exception) {
            Log.e(TAG, "Error in detectMetadata: ${e.message}", e)
            fileName
        }
    }

    /**
     * Translates an existing subtitle file (SRT/VTT) into the target language using Gemini.
     */
    suspend fun translateSubtitleFile(
        context: Context,
        inputFile: File,
        targetLanguage: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not configured")
            return@withContext null
        }

        try {
            val lines = inputFile.readLines()
            if (lines.isEmpty()) return@withContext null

            // Group subtitle file into logical chunks of ~80 lines (~15-20 subtitle blocks)
            val chunks = mutableListOf<List<String>>()
            var currentChunk = mutableListOf<String>()
            
            for (line in lines) {
                currentChunk.add(line)
                if (currentChunk.size >= 80) {
                    chunks.add(currentChunk)
                    currentChunk = mutableListOf()
                }
            }
            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk)
            }

            val translatedChunks = mutableListOf<String>()
            val totalChunks = chunks.size

            for ((index, chunk) in chunks.withIndex()) {
                val chunkContent = chunk.joinToString("\n")
                val prompt = """
                    You are an expert subtitle translator. Translate the following subtitle block into $targetLanguage.
                    
                    CRITICAL RULES:
                    1. Keep the exact subtitle indices (e.g. '1', '2', '3') and timestamp lines (e.g. '00:01:20,000 --> 00:01:23,000') perfectly intact.
                    2. Only translate the dialogue text lines.
                    3. Preserve all line breaks, layouts, and italic tags (e.g. <i>...</i>) if present.
                    4. Return ONLY the valid translated subtitle content without any intro, outro, markdown block fences, or surrounding text.
                    
                    Subtitle Content:
                    $chunkContent
                """.trimIndent()

                val translatedChunk = makeGeminiCall(apiKey, prompt)
                translatedChunks.add(translatedChunk.trim())
                
                val progress = ((index + 1).toFloat() / totalChunks * 100).toInt()
                onProgress(progress)
            }

            val fullTranslatedContent = translatedChunks.joinToString("\n\n")

            // Determine output file path
            val baseName = inputFile.nameWithoutExtension
            val parentDir = inputFile.parentFile ?: context.cacheDir
            val extension = if (inputFile.name.endsWith(".vtt", ignoreCase = true)) ".vtt" else ".srt"
            
            // Clean suffix to avoid duplicates like .en.ar.srt
            val cleanBase = baseName.removeSuffix(".en").removeSuffix(".EN")
                .removeSuffix(".fr").removeSuffix(".FR")
                .removeSuffix(".es").removeSuffix(".ES")
            
            val outputFile = File(parentDir, "$cleanBase.ar$extension")
            outputFile.writeText(fullTranslatedContent)
            
            Log.d(TAG, "Successfully translated subtitle to: ${outputFile.absolutePath}")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error in translateSubtitleFile: ${e.message}", e)
            null
        }
    }

    /**
     * Automatically generates a beautiful, synchronized subtitle file containing
     * scene-by-scene highlights/narration in the target language based on metadata and duration.
     */
    suspend fun generateSubtitleFromMetadata(
        context: Context,
        videoFile: File,
        title: String,
        durationMs: Long,
        targetLanguage: String
    ): File? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not configured")
            return@withContext null
        }

        val durationSeconds = durationMs / 1000
        val prompt = """
            Generate a synchronized, beautiful SRT subtitle file in $targetLanguage for the media titled "$title" which has a duration of $durationSeconds seconds.
            
            Since this is an AI-generated descriptive/narrative subtitle, please divide the duration into logical chronological scenes or highlights (approx. every 15 to 30 seconds).
            For each scene:
            1. Write a beautiful, short descriptive/narrative highlight or context commentary in $targetLanguage (e.g. "تبدأ الإثارة مع تحرك الأحداث...", "مشهد رائع يستعرض ملامح الشخصية...").
            2. Format it as a strictly valid SRT subtitle block with proper sequential indices and timestamp lines.
            
            Example Format:
            1
            00:00:00,000 --> 00:00:15,000
            بداية أحداث العمل الفني المثير وسرد المقدمة.

            2
            00:00:15,000 --> 00:00:45,000
            تسلسل سريع للأحداث وبناء خلفية القصة الدرامية.
            
            Provide the subtitles spanning up to the full duration of $durationSeconds seconds.
            Return ONLY the valid SRT file contents. Do not include any intro, outro, explanations, or markdown code fences (like ```srt or ```).
        """.trimIndent()

        try {
            val srtContent = makeGeminiCall(apiKey, prompt)
            val parentDir = videoFile.parentFile ?: context.cacheDir
            val baseName = videoFile.nameWithoutExtension
            val outputFile = File(parentDir, "$baseName.ar.srt")
            outputFile.writeText(srtContent.trim())
            
            Log.d(TAG, "Successfully generated metadata subtitle to: ${outputFile.absolutePath}")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateSubtitleFromMetadata: ${e.message}", e)
            null
        }
    }

    /**
     * Executes the API call to the Gemini 3.5 Flash REST API endpoint.
     */
    private suspend fun makeGeminiCall(apiKey: String, prompt: String): String = withContext(Dispatchers.IO) {
        val url = "$BASE_URL?key=$apiKey"
        
        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                Log.e(TAG, "Gemini API failed with code ${response.code}: $errBody")
                throw Exception("API call failed with code ${response.code}")
            }

            val respBody = response.body?.string() ?: throw Exception("Empty response body")
            val jsonObject = JSONObject(respBody)
            val candidates = jsonObject.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                if (content != null) {
                    val parts = content.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text")
                    }
                }
            }
            throw Exception("Failed to parse Gemini response candidates")
        }
    }
}
