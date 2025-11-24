package com.example.phonecam.network

import kotlinx.serialization.Serializable

// Структура даних для парсингу конфігурації контролера

@Serializable
data class ControllerConfig(
    val controllerId: String,
    val firmwareVersion: String,
    val sensors: List<SensorData>, // Вкладений список об'єктів
    val maintenanceRequired: Boolean
)

@Serializable
data class SensorData(
    val type: String,   // "TEMP_CPU", "VOLTAGE_IN"
    val value: Double,
    val unit: String
)