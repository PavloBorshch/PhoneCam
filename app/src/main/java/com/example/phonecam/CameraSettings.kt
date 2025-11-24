package com.example.phonecam

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

// Опис пареметрів веб-камери
data class CameraSettings(
    val width: Int,
    val height: Int,
    val fps: Int,
    val cameraName: String,
    // IP адреса може бути відсутня, якщо ми ще не підключились
    var serverIp: String? = null
)

// Визначає якість відео на основі ширини
fun CameraSettings.getVideoQualityTag(): String {
    return when {
        this.width >= 3840 -> "4K UHD"
        this.width >= 1920 -> "Full HD"
        this.width >= 1280 -> "HD"
        else -> "SD"
    }
}

// Функція для тестування
fun testCameraLogic() {
    // Створення об'єкта
    val mySettings = CameraSettings(
        width = 1920,
        height = 1080,
        fps = 30,
        cameraName = "Back Camera",
        serverIp = null
    )

    println("Камера: ${mySettings.cameraName}")

    // Виклик Extension функції
    println("Якість: ${mySettings.getVideoQualityTag()}")

    val currentIp = mySettings.serverIp ?: "Не підключено"
    println("IP адреса: $currentIp")

    // Імітуємо процес підключення, який займає час
    runBlocking {
        connectToPc(mySettings)
    }
}

// Імітація довготривалої операції
suspend fun connectToPc(settings: CameraSettings) {
    println("Спроба підключення...")
    delay(1500) // Затримка 1.5 секунди (імітація роботи мережі)
    println("Підключено! Трансляція ${settings.width}x${settings.height} почалася.")
}