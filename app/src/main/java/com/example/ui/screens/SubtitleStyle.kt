package com.example.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.media3.ui.CaptionStyleCompat
import android.view.Gravity

data class SubtitleStyle(
    val textSize: Float = 1.0f,
    val textColor: Color = Color.White,
    val backgroundColor: Color = Color.Black.copy(alpha = 0.5f),
    val backgroundEnabled: Boolean = false,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val fontFamily: String = "default",
    val alignment: Int = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
    val bottomPadding: Float = 0.05f,
    val edgeType: Int = CaptionStyleCompat.EDGE_TYPE_OUTLINE,
    val edgeColor: Color = Color.Black,
    val fitToVideo: Boolean = true
)
