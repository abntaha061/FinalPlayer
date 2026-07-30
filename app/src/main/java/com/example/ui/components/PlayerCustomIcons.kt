package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
 * Two distinct 'C's inside a rounded box.
 */
@Composable
fun CcSubtitleIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = (h * 0.08f).coerceAtLeast(1.5f)

        // Draw outer rounded rectangle border
        drawRoundRect(
            color = tint,
            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
            size = Size(w - strokeWidth, h - strokeWidth),
            cornerRadius = CornerRadius(h * 0.2f, h * 0.2f),
            style = Stroke(width = strokeWidth)
        )

        // Draw first 'C' arc
        val cRadius = h * 0.20f
        val c1Center = Offset(w * 0.36f, h * 0.5f)
        drawArc(
            color = tint,
            startAngle = 45f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(c1Center.x - cRadius, c1Center.y - cRadius),
            size = Size(cRadius * 2, cRadius * 2),
            style = Stroke(width = strokeWidth * 1.3f, cap = StrokeCap.Round)
        )

        // Draw second 'C' arc
        val c2Center = Offset(w * 0.64f, h * 0.5f)
        drawArc(
            color = tint,
            startAngle = 45f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(c2Center.x - cRadius, c2Center.y - cRadius),
            size = Size(cRadius * 2, cRadius * 2),
            style = Stroke(width = strokeWidth * 1.3f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Headphones Icon:
 * Headband arc at top with two rounded ear cups on the sides.
 */
@Composable
fun HeadphonesCustomIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.8.dp.toPx()

        // Headband arc
        val arcRect = Rect(strokeWidth * 1.2f, strokeWidth, w - strokeWidth * 1.2f, h * 0.85f)
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = strokeWidth * 1.1f, cap = StrokeCap.Round)
        )

        // Left ear cup
        val cupW = w * 0.22f
        val cupH = h * 0.42f
        drawRoundRect(
            color = tint,
            topLeft = Offset(strokeWidth, h * 0.48f),
            size = Size(cupW, cupH),
            cornerRadius = CornerRadius(cupW * 0.4f, cupW * 0.4f)
        )

        // Right ear cup
        drawRoundRect(
            color = tint,
            topLeft = Offset(w - strokeWidth - cupW, h * 0.48f),
            size = Size(cupW, cupH),
            cornerRadius = CornerRadius(cupW * 0.4f, cupW * 0.4f)
        )
    }
}

/**
 * Square with Play Triangle inside Icon:
 * Bordered square with a small solid play triangle inside.
 */
@Composable
fun SquareWithTriangleIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.8.dp.toPx()

        // Outer square
        drawRoundRect(
            color = tint,
            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
            size = Size(w - strokeWidth, h - strokeWidth),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = strokeWidth)
        )

        // Inside filled play triangle
        val path = Path().apply {
            moveTo(w * 0.38f, h * 0.28f)
            lineTo(w * 0.72f, h * 0.5f)
            lineTo(w * 0.38f, h * 0.72f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}

/**
 * Custom Lock Icon matching Photo 1:
 * Solid rounded padlock with top shackle arch and central keyhole dot.
 */
@Composable
fun CustomLockIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    holeColor: Color = Color.Black
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()

        // Shackle arch at top
        val shackleWidth = w * 0.52f
        val shackleHeight = h * 0.42f
        val shackleLeft = (w - shackleWidth) / 2
        val shackleTop = strokeWidth

        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(shackleLeft, shackleTop),
            size = Size(shackleWidth, shackleHeight),
            style = Stroke(width = strokeWidth * 1.2f, cap = StrokeCap.Round)
        )

        // Lock Body (solid rounded rectangle)
        val bodyWidth = w * 0.82f
        val bodyHeight = h * 0.58f
        val bodyLeft = (w - bodyWidth) / 2
        val bodyTop = h * 0.38f

        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Fill
        )

        // Center keyhole dot
        val keyholeCenter = Offset(w / 2, bodyTop + bodyHeight * 0.48f)
        drawCircle(
            color = holeColor,
            radius = w * 0.12f,
            center = keyholeCenter
        )
    }
}

/**
 * Custom Play / Pause Button matching Photo 3 & Photo 4:
 * White ring outline with solid white pause bars or play triangle inside.
 */
@Composable
fun CustomPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .border(2.5.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
    ) {
        if (isPlaying) {
            // Two vertical pause bars
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.5.dp)
                        .height(18.dp)
                        .background(Color.White, RoundedCornerShape(2.dp))
                )
                Box(
                    modifier = Modifier
                        .width(4.5.dp)
                        .height(18.dp)
                        .background(Color.White, RoundedCornerShape(2.dp))
                )
            }
        } else {
            // Solid play triangle
            Canvas(modifier = Modifier.size(20.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.15f)
                    lineTo(size.width * 0.88f, size.height * 0.5f)
                    lineTo(size.width * 0.28f, size.height * 0.85f)
                    close()
                }
                drawPath(path = path, color = Color.White)
            }
        }
    }
}

/**
 * Custom 10s Seek Icon matching Photo 3 & Photo 4:
 * Circular arrow with "10" clearly displayed in the center.
 */
@Composable
fun CustomSeek10Icon(
    isForward: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val strokeWidth = 2.dp.toPx()
            val arcInset = strokeWidth * 1.5f
            val arcRect = Rect(arcInset, arcInset, w - arcInset, h - arcInset)

            val startAngle = if (isForward) -60f else -120f
            val sweepAngle = if (isForward) 280f else -280f

            drawArc(
                color = tint,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = arcRect.topLeft,
                size = arcRect.size,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Arrowhead tip
            if (isForward) {
                val tipX = w * 0.85f
                val tipY = h * 0.35f
                drawLine(tint, Offset(tipX - 5.dp.toPx(), tipY - 2.dp.toPx()), Offset(tipX, tipY), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(tipX - 2.dp.toPx(), tipY + 5.dp.toPx()), Offset(tipX, tipY), strokeWidth, StrokeCap.Round)
            } else {
                val tipX = w * 0.15f
                val tipY = h * 0.35f
                drawLine(tint, Offset(tipX + 5.dp.toPx(), tipY - 2.dp.toPx()), Offset(tipX, tipY), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(tipX + 2.dp.toPx(), tipY + 5.dp.toPx()), Offset(tipX, tipY), strokeWidth, StrokeCap.Round)
            }
        }
        Text(
            text = "10",
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
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
