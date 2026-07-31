package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.player.model.AspectMode
import com.example.player.model.PlaybackSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "playback_settings")

class PlaybackSettingsRepository(private val context: Context) {

    private object Keys {
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val VOLUME = intPreferencesKey("volume")
        val SPEED = floatPreferencesKey("speed")
        val ASPECT_MODE = stringPreferencesKey("aspect_mode")
        val AUDIO_BOOST = floatPreferencesKey("audio_boost")
        val IS_HW_DECODER = booleanPreferencesKey("is_hw_decoder")
        val IS_NIGHT_MODE = booleanPreferencesKey("is_night_mode")
    }

    val settingsFlow: Flow<PlaybackSettings> = context.dataStore.data.map { prefs ->
        PlaybackSettings(
            brightness = prefs[Keys.BRIGHTNESS] ?: 0.8f,
            volume = prefs[Keys.VOLUME] ?: 100,
            speed = prefs[Keys.SPEED] ?: 1.0f,
            aspectMode = try {
                AspectMode.valueOf(prefs[Keys.ASPECT_MODE] ?: AspectMode.FIT.name)
            } catch (e: Exception) {
                AspectMode.FIT
            },
            audioBoostDb = prefs[Keys.AUDIO_BOOST] ?: 0f,
            isHwDecoder = prefs[Keys.IS_HW_DECODER] ?: true,
            isNightMode = prefs[Keys.IS_NIGHT_MODE] ?: false
        )
    }

    suspend fun updateBrightness(value: Float) {
        context.dataStore.edit { it[Keys.BRIGHTNESS] = value }
    }

    suspend fun updateVolume(value: Int) {
        context.dataStore.edit { it[Keys.VOLUME] = value }
    }

    suspend fun updateSpeed(value: Float) {
        context.dataStore.edit { it[Keys.SPEED] = value }
    }

    suspend fun updateAspectMode(mode: AspectMode) {
        context.dataStore.edit { it[Keys.ASPECT_MODE] = mode.name }
    }

    suspend fun updateAudioBoost(db: Float) {
        context.dataStore.edit { it[Keys.AUDIO_BOOST] = db }
    }

    suspend fun updateHwDecoder(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_HW_DECODER] = enabled }
    }

    suspend fun updateNightMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_NIGHT_MODE] = enabled }
    }
}
