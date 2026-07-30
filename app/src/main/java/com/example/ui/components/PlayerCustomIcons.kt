package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Picture-in-Picture Icon matching Photo 1:
 * Outer rounded square, arrow pointing down-right at top-left, small rounded rectangle at bottom-right.
 */
@Composable
fun PipCustomIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()
        val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        // Outer frame
        drawRoundRect(
            color = tint,
            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
            size = Size(w - strokeWidth, h - strokeWidth),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth)
        )

        // Arrow top-left pointing down-right
        val startX = w * 0.22f
        val startY = h * 0.22f
        val endX = w * 0.44f
        val endY = h * 0.44f

        drawLine(
            color = tint,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(endX - w * 0.12f, endY),
            end = Offset(endX, endY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(endX, endY - h * 0.12f),
            end = Offset(endX, endY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Small inner filled window in bottom-right corner
        val innerW = w * 0.40f
        val innerH = h * 0.30f
        val innerLeft = w - innerW - strokeWidth * 1.5f
        val innerTop = h - innerH - strokeWidth * 1.5f

        drawRoundRect(
            color = tint,
            topLeft = Offset(innerLeft, innerTop),
            size = Size(innerW, innerH),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
            style = Fill
        )
    }
}

/**
 * Orientation Rotation Icon matching Photo 2:
 * Tilted phone in center surrounded by two curved rotation arrows.
 */
@Composable
fun OrientationCustomIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()

        // Tilted phone in center (-35 degrees)
        val phoneWidth = w * 0.32f
        val phoneHeight = h * 0.52f

        withTransform({
            rotate(degrees = -35f, pivot = Offset(w / 2, h / 2))
        }) {
            drawRoundRect(
                color = tint,
                topLeft = Offset((w - phoneWidth) / 2, (h - phoneHeight) / 2),
                size = Size(phoneWidth, phoneHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Fill
            )
        }

        // Curved rotation arrows
        val arcInset = strokeWidth * 1.5f
        val arcRect = Rect(
            left = arcInset,
            top = arcInset,
            right = w - arcInset,
            bottom = h - arcInset
        )

        // Top-right curved arc
        drawArc(
            color = tint,
            startAngle = -70f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        // Top-right arrow tip
        val p1x = w * 0.88f
        val p1y = h * 0.42f
        drawLine(color = tint, start = Offset(p1x - 3.dp.toPx(), p1y - 6.dp.toPx()), end = Offset(p1x, p1y), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(p1x + 5.dp.toPx(), p1y - 2.dp.toPx()), end = Offset(p1x, p1y), strokeWidth = strokeWidth, cap = StrokeCap.Round)

        // Bottom-left curved arc
        drawArc(
            color = tint,
            startAngle = 110f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        // Bottom-left arrow tip
        val p2x = w * 0.12f
        val p2y = h * 0.58f
        drawLine(color = tint, start = Offset(p2x + 3.dp.toPx(), p2y + 6.dp.toPx()), end = Offset(p2x, p2y), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(p2x - 5.dp.toPx(), p2y + 2.dp.toPx()), end = Offset(p2x, p2y), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
}

/**
 * Subtitles CC Icon:
 * Bold 'CC' inside a rounded box.
 */
@Composable
fun CcSubtitleIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Box(
        modifier = modifier
            .border(1.5.dp, tint, RoundedCornerShape(4.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "CC",
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Speedometer Gauge Icon matching Photo 4:
 * Tachometer dial gauge with pivot needle pointing upper-right.
 */
@Composable
fun SpeedometerCustomIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.2.dp.toPx()
        val center = Offset(w / 2, h / 2)

        // Arc gauge ring (270 deg sweep)
        val inset = strokeWidth
        drawArc(
            color = tint,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(w - inset * 2, h - inset * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Center pivot dot
        drawCircle(
            color = tint,
            radius = 2.5.dp.toPx(),
            center = center
        )

        // Needle pointing to -45 deg (up-right)
        val needleLength = w * 0.32f
        val angleRad = Math.toRadians(-45.0)
        val needleEnd = Offset(
            x = center.x + (needleLength * Math.cos(angleRad)).toFloat(),
            y = center.y + (needleLength * Math.sin(angleRad)).toFloat()
        )

        drawLine(
            color = tint,
            start = center,
            end = needleEnd,
            strokeWidth = strokeWidth * 0.9f,
            cap = StrokeCap.Round
        )
    }
}
