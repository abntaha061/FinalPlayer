package com.example.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SubtitlePreferences(
    val textSize: Float = 18f,
    val verticalOffset: Float = 0.95f, // Position vertical offset ratio from top (0.0 to 1.0)
    val textColorArgb: Int = Color.White.toArgb(),
    val backgroundColorArgb: Int = Color.Black.copy(alpha = 0.6f).toArgb()
) {
    val textColor: Color get() = Color(textColorArgb)
    val backgroundColor: Color get() = Color(backgroundColorArgb)
}

private val Context.subtitleDataStore: DataStore<Preferences> by preferencesDataStore(name = "subtitle_preferences")

class SubtitlePrefsManager(private val context: Context) {

    companion object {
        val TEXT_SIZE = floatPreferencesKey("sub_text_size")
        val VERTICAL_OFFSET = floatPreferencesKey("sub_vertical_offset")
        val TEXT_COLOR_ARGB = intPreferencesKey("sub_text_color_argb")
        val BACKGROUND_COLOR_ARGB = intPreferencesKey("sub_background_color_argb")
    }

    val subtitlePreferencesFlow: Flow<SubtitlePreferences> = context.subtitleDataStore.data.map { preferences ->
        SubtitlePreferences(
            textSize = preferences[TEXT_SIZE] ?: 18f,
            verticalOffset = preferences[VERTICAL_OFFSET] ?: 0.95f,
            textColorArgb = preferences[TEXT_COLOR_ARGB] ?: Color.White.toArgb(),
            backgroundColorArgb = preferences[BACKGROUND_COLOR_ARGB] ?: Color.Black.copy(alpha = 0.6f).toArgb()
        )
    }

    suspend fun savePreferences(prefs: SubtitlePreferences) {
        context.subtitleDataStore.edit { preferences ->
            preferences[TEXT_SIZE] = prefs.textSize
            preferences[VERTICAL_OFFSET] = prefs.verticalOffset
            preferences[TEXT_COLOR_ARGB] = prefs.textColorArgb
            preferences[BACKGROUND_COLOR_ARGB] = prefs.backgroundColorArgb
        }
    }
}
