package com.example.phonecam

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// Створення графіків
@Composable
fun BitrateChart(
    dataPoints: List<Int>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    if (dataPoints.isEmpty()) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp) // Фіксована висота графіка
    ) {
        val width = size.width
        val height = size.height

        // Визначаємо масштаб
        val maxVal = (dataPoints.maxOrNull() ?: 1).coerceAtLeast(1) * 1.1f

        // Відстань між точками по горизонталі
        val stepX = width / (dataPoints.size - 1).coerceAtLeast(1)

        val path = Path()

        // 1. Формування шляху лінії (Path)
        dataPoints.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height - (value / maxVal * height)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // Gradient Fill
        val fillPath = Path()
        fillPath.addPath(path)
        fillPath.lineTo(width, height)
        fillPath.lineTo(0f, height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.3f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = height
            )
        )

        // Малювання самої лінії
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx())
        )

        // Малювання сітки
        drawLine(
            color = Color.Gray.copy(alpha = 0.5f),
            start = Offset(0f, height / 2),
            end = Offset(width, height / 2),
            strokeWidth = 1.dp.toPx()
        )
    }
}