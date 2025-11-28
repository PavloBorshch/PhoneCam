package com.example.phonecam.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

data class SensorData(
    val lightLux: Float = 0f,
    val isShaking: Boolean = false, // Детекція удару
    val gForce: Float = 0f
)

// Хелпер для роботи з сенсорами
class SensorHelper(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    fun observeSensors(): Flow<SensorData> = callbackFlow {
        var currentLight = 0f
        var lastShakeTime = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return

                when (event.sensor.type) {
                    Sensor.TYPE_LIGHT -> {
                        currentLight = event.values[0]
                        // Відправляємо оновлення лише при зміні освітлення, скидаючи статус струсу
                        trySend(SensorData(lightLux = currentLight, isShaking = false))
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]

                        // Розрахунок перевантаження
                        val gX = x / SensorManager.GRAVITY_EARTH
                        val gY = y / SensorManager.GRAVITY_EARTH
                        val gZ = z / SensorManager.GRAVITY_EARTH

                        // Формула модуля вектора
                        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

                        // Поріг для детекції "Аварії" > 2.5G
                        if (gForce > 2.5f) {
                            val now = System.currentTimeMillis()
                            // Дебаунс
                            if (now - lastShakeTime > 2000) {
                                lastShakeTime = now
                                trySend(SensorData(
                                    lightLux = currentLight,
                                    isShaking = true,
                                    gForce = gForce
                                ))
                            }
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            }
        }

        // Реєстрація слухачів
        accelerometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        lightSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}