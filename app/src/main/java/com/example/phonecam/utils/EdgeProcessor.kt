package com.example.phonecam.utils

import kotlin.math.abs

// Edge Computing
class EdgeProcessor {

    private var lastTemp: Float? = null
    private var lastLight: Float? = null

    // Поріг змін для відправки
    private val tempThreshold = 0.5f
    private val lightThreshold = 10.0f

    // Повертає JSON, якщо зміни суттєві, або null, якщо нічого не змінилось
    fun processAndFormat(currentTemp: Float, currentLight: Float): String? {
        val tempChanged = lastTemp == null || abs(currentTemp - lastTemp!!) > tempThreshold
        val lightChanged = lastLight == null || abs(currentLight - lastLight!!) > lightThreshold

        if (tempChanged || lightChanged) {
            lastTemp = currentTemp
            lastLight = currentLight

            // Формуємо JSON пакет для розумного будинку
            return """
                {
                    "device_id": "CAM-01",
                    "type": "sensor_update",
                    "data": {
                        "temperature": %.1f,
                        "light_level": %.1f,
                        "timestamp": %d
                    }
                }
            """.trimIndent().format(currentTemp, currentLight, System.currentTimeMillis())
        }

        return null
    }
}