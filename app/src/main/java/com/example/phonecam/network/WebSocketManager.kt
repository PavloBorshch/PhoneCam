package com.example.phonecam.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlin.random.Random

// Модель даних, що приходить через WebSocket
@Serializable
data class RealtimeData(
    val timestamp: Long,
    val bitrate: Int,
    val fps: Int,
    val cpuTemp: Float,
    val voltage: Float,
    val isAlert: Boolean
)

// Менеджер WebSocket підключення
class WebSocketManager {
    // Емуляція вхідного потоку даних (Live Data Stream)
    fun connectToDataStream(): Flow<RealtimeData> = flow {
        while (true) {
            // Емулюємо затримку мережі
            delay(500)

            // Генерація випадкових даних
            val temp = Random.nextDouble(40.0, 90.0).toFloat() // Температура може бути високою
            val voltage = Random.nextDouble(4.5, 5.2).toFloat()
            val bitrate = Random.nextInt(2000, 9500)
            val fps = Random.nextInt(24, 60)

            // Логіка визначення тривоги на стороні "сервера"
            val isCritical = temp > 85.0f || voltage < 4.6f

            emit(
                RealtimeData(
                    timestamp = System.currentTimeMillis(),
                    bitrate = bitrate,
                    fps = fps,
                    cpuTemp = temp,
                    voltage = voltage,
                    isAlert = isCritical
                )
            )
        }
    }
}