package com.example.phonecam.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.phonecam.WebcamUiState

// KPI DASHBOARD CONTAINER

@Composable
fun DashboardSection(state: WebcamUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "KPI Дашборд пристрою",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Візуальний алерт
            val tempBorderColor = if (state.isCriticalAlert) Color.Red else Color.Transparent

            DashboardCard(
                title = "CPU Temp",
                modifier = Modifier.weight(1f).border(2.dp, tempBorderColor, RoundedCornerShape(12.dp))
            ) {
                CircularGauge(
                    value = state.cpuTemp,
                    min = 0f,
                    max = 100f,
                    unit = "°C",
                    warningThreshold = 60f,
                    criticalThreshold = 80f
                )
            }

            // Картка Вольтажу
            DashboardCard(
                title = "Input Voltage",
                modifier = Modifier.weight(1f)
            ) {
                LinearKpi(
                    value = state.inputVoltage,
                    max = 12f,
                    unit = "V",
                    targetValue = 5.0f // Цільове значення (наприклад, USB 5V)
                )
            }
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Card(
        modifier = modifier.height(180.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}

@Composable
fun CircularGauge(
    value: Float,
    min: Float = 0f,
    max: Float = 100f,
    unit: String,
    warningThreshold: Float,
    criticalThreshold: Float,
    size: Dp = 120.dp,
    strokeWidth: Dp = 12.dp
) {
    // Анімація значення для плавності стрілки/дуги
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 500), // Пришвидшена анімація для реалтайму
        label = "GaugeAnimation"
    )

    // Визначення кольору залежно від стану
    val colorState = remember(animatedValue) {
        when {
            animatedValue >= criticalThreshold -> Color(0xFFD32F2F) // Red
            animatedValue >= warningThreshold -> Color(0xFFFBC02D) // Yellow
            else -> Color(0xFF388E3C) // Green
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.toPx()
            val canvasHeight = size.toPx()
            val center = Offset(canvasWidth / 2, canvasHeight / 2)

            val startAngle = 135f
            val sweepAngle = 270f

            // Фон шкали (сірий)
            drawArc(
                color = Color.LightGray.copy(alpha = 0.3f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(strokeWidth.toPx() / 2, strokeWidth.toPx() / 2),
                size = Size(canvasWidth - strokeWidth.toPx(), canvasHeight - strokeWidth.toPx()),
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )

            // Активна дуга
            val percent = ((animatedValue - min) / (max - min)).coerceIn(0f, 1f)
            val currentSweep = sweepAngle * percent

            drawArc(
                color = colorState,
                startAngle = startAngle,
                sweepAngle = currentSweep,
                useCenter = false,
                topLeft = Offset(strokeWidth.toPx() / 2, strokeWidth.toPx() / 2),
                size = Size(canvasWidth - strokeWidth.toPx(), canvasHeight - strokeWidth.toPx()),
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }

        // Текстове значення по центру
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%.1f".format(animatedValue),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colorState
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun LinearKpi(
    value: Float,
    max: Float,
    unit: String,
    targetValue: Float
) {
    val animatedValue by animateFloatAsState(targetValue = value, animationSpec = tween(500), label = "LinearAnim")
    val percent = (animatedValue / max).coerceIn(0f, 1f)

    // Колір: Зелений, якщо близько до цілі (+- 10%), інакше червоний
    val isOptimal = kotlin.math.abs(animatedValue - targetValue) < (targetValue * 0.1)
    val barColor = if (isOptimal) Color(0xFF388E3C) else Color(0xFFD32F2F)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Значення
        Text(
            text = "%.1f %s".format(animatedValue, unit),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = barColor
        )
        Text(
            text = "Target: $targetValue $unit",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Прогрес бар
        Box(
            modifier = Modifier
                .height(24.dp)
                .fillMaxWidth()
                .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent)
                    .background(barColor, RoundedCornerShape(12.dp))
            )
        }
    }
}