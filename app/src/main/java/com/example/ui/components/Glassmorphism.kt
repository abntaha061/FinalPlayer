package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.frostedGlass(
    isDark: Boolean,
    shape: Shape = RoundedCornerShape(16.dp),
    opacity: Float = if (isDark) 0.08f else 0.45f,
    drawBorder: Boolean = true
): Modifier {
    val brush = remember(isDark, opacity) {
        Brush.verticalGradient(
            colors = if (isDark) {
                listOf(
                    Color.White.copy(alpha = opacity + 0.05f),
                    Color.White.copy(alpha = opacity)
                )
            } else {
                listOf(
                    Color.White.copy(alpha = opacity + 0.15f),
                    Color.White.copy(alpha = opacity)
                )
            }
        )
    }
    val borderBrush = remember(isDark) {
        Brush.verticalGradient(
            colors = if (isDark) {
                listOf(
                    Color.White.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.05f)
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.5f),
                    Color.White.copy(alpha = 0.15f)
                )
            }
        )
    }
    return if (drawBorder) {
        this
            .background(brush = brush, shape = shape)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
    } else {
        this.background(brush = brush, shape = shape)
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    isDark: Boolean,
    shape: Shape = RoundedCornerShape(16.dp),
    opacity: Float = if (isDark) 0.08f else 0.45f,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(shape)
            .frostedGlass(isDark = isDark, shape = shape, opacity = opacity)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean,
    shape: Shape = CircleShape,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .frostedGlass(isDark = isDark, shape = shape, opacity = if (isDark) 0.12f else 0.55f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}
