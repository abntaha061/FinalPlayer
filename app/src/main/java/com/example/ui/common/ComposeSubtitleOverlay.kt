package com.example.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.model.SubtitleFont

@Composable
fun ComposeSubtitleOverlay(
    subtitleText: String? = null,
    text: String? = subtitleText,
    modifier: Modifier = Modifier,
    textSizeScale: Float = 1.0f,
    bgStyle: Int = 0,
    subtitleFont: SubtitleFont = SubtitleFont.DEFAULT,
    isSubtitleBold: Boolean = true,
    isSubtitleGestureEnabled: Boolean = false,
    verticalOffsetFraction: Float = 0.05f,
    onVerticalOffsetFractionChanged: (Float) -> Unit = {},
    onSeekNext: () -> Unit = {},
    onSeekPrev: () -> Unit = {}
) {
    val displayText = subtitleText ?: text
    if (!displayText.isNullOrBlank()) {
        val fontFamily = when (subtitleFont) {
            SubtitleFont.SANS_SERIF -> FontFamily.SansSerif
            SubtitleFont.SERIF -> FontFamily.Serif
            SubtitleFont.MONOSPACE -> FontFamily.Monospace
            else -> FontFamily.Default
        }

        val bgColor = when (bgStyle) {
            1 -> Color.Black.copy(alpha = 0.9f)
            2 -> Color.Transparent
            else -> Color.Black.copy(alpha = 0.75f)
        }

        Box(
            modifier = modifier
                .padding(bottom = (60 * (1f + verticalOffsetFraction)).dp, start = 32.dp, end = 32.dp)
                .background(bgColor, shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayText,
                color = Color.White,
                fontSize = (18 * textSizeScale).sp,
                fontWeight = if (isSubtitleBold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center
            )
        }
    }
}
