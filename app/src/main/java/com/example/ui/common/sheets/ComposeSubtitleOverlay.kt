package com.example.ui.common.sheets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.player.model.SubtitleFont
import com.example.ui.common.ComposeSubtitleOverlay as BaseOverlay

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
    BaseOverlay(
        subtitleText = subtitleText,
        text = text,
        modifier = modifier,
        textSizeScale = textSizeScale,
        bgStyle = bgStyle,
        subtitleFont = subtitleFont,
        isSubtitleBold = isSubtitleBold,
        isSubtitleGestureEnabled = isSubtitleGestureEnabled,
        verticalOffsetFraction = verticalOffsetFraction,
        onVerticalOffsetFractionChanged = onVerticalOffsetFractionChanged,
        onSeekNext = onSeekNext,
        onSeekPrev = onSeekPrev
    )
}
