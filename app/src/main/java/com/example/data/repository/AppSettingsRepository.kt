// app/src/main/java/com/example/data/repository/AppSettingsRepository.kt
package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val showNoMediaFiles: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val resumePlaybackMode: String = "ASK", // "ASK", "AUTO", "START"
    val rememberBrightness: Boolean = false,
    val backgroundPlayback: Boolean = false,
    val rememberAspectRatio: Boolean = false,
    val rememberSpeed: Boolean = false,
    val defaultOrientation: String = "SYSTEM", // "SYSTEM", "PORTRAIT", "LANDSCAPE", "AUTO"
    val seekIncrementSeconds: Int = 10,
    val autoPlayNext: Boolean = false,
    val autoPip: Boolean = false,
    val showRecentlyPlayed: Boolean = true,
    val showFab: Boolean = true
)

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings_preferences")

class AppSettingsRepository(private val context: Context) {

    companion object {
        val SHOW_NOMEDIA_FILES = booleanPreferencesKey("show_nomedia_files")
        val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        val RESUME_PLAYBACK_MODE = stringPreferencesKey("resume_playback_mode")
        val REMEMBER_BRIGHTNESS = booleanPreferencesKey("remember_brightness")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val REMEMBER_ASPECT_RATIO = booleanPreferencesKey("remember_aspect_ratio")
        val REMEMBER_SPEED = booleanPreferencesKey("remember_speed")
        val DEFAULT_ORIENTATION = stringPreferencesKey("default_orientation")
        val SEEK_INCREMENT_SECONDS = intPreferencesKey("seek_increment_seconds")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val AUTO_PIP = booleanPreferencesKey("auto_pip")
        val SHOW_RECENTLY_PLAYED = booleanPreferencesKey("show_recently_played")
        val SHOW_FAB = booleanPreferencesKey("show_fab")
    }

    val appSettingsFlow: Flow<AppSettings> = context.appSettingsDataStore.data.map { preferences ->
        AppSettings(
            showNoMediaFiles = preferences[SHOW_NOMEDIA_FILES] ?: false,
            showHiddenFiles = preferences[SHOW_HIDDEN_FILES] ?: false,
            resumePlaybackMode = preferences[RESUME_PLAYBACK_MODE] ?: "ASK",
            rememberBrightness = preferences[REMEMBER_BRIGHTNESS] ?: false,
            backgroundPlayback = preferences[BACKGROUND_PLAYBACK] ?: false,
            rememberAspectRatio = preferences[REMEMBER_ASPECT_RATIO] ?: false,
            rememberSpeed = preferences[REMEMBER_SPEED] ?: false,
            defaultOrientation = preferences[DEFAULT_ORIENTATION] ?: "SYSTEM",
            seekIncrementSeconds = preferences[SEEK_INCREMENT_SECONDS] ?: 10,
            autoPlayNext = preferences[AUTO_PLAY_NEXT] ?: false,
            autoPip = preferences[AUTO_PIP] ?: false,
            showRecentlyPlayed = preferences[SHOW_RECENTLY_PLAYED] ?: true,
            showFab = preferences[SHOW_FAB] ?: true
        )
    }

    suspend fun updateShowNoMediaFiles(value: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[SHOW_NOMEDIA_FILES] = value
        }
    }

    suspend fun updateShowHiddenFiles(value: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[SHOW_HIDDEN_FILES] = value
        }
    }

    suspend fun updateResumePlaybackMode(value: String) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[RESUME_PLAYBACK_MODE] = value
        }
    }

    suspend fun updateRememberBrightness(value: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[REMEMBER_BRIGHTNESS] = value
        }
    }

    suspend fun updateBackgroundPlayback(value: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[BACKGROUND_PLAYBACK] = value
        }
    }

    suspend fun updateRememberAspectRatio(value: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[REMEMBER_ASPECT_RATIO] = value
        }
    }

    suspend fun updateRememberSpeed(value: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[REMEMBER_SPEED] = value
        }
    }

    suspend fun updateDefaultOrientation(value: String) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[DEFAULT_ORIENTATION] = value
        }
    }

    suspend fun updateSeekIncrementSeconds(value: Int) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[SEEK_INCREMENT_SECONDS] = value
        }
    }

    suspend fun updateAutoPlayNext(value: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[AUTO_PLAY_NEXT] = value
        }
    }

    suspend fun updateAutoPip(value: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[AUTO_PIP] = value
        }
    }

    suspend fun updateShowRecentlyPlayed(value: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[SHOW_RECENTLY_PLAYED] = value
        }
    }

    suspend fun updateShowFab(value: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[SHOW_FAB] = value
        }
    }
}
