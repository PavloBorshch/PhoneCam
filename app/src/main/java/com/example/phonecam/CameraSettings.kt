package com.example.phonecam

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

// Data Class
data class CameraSettings(
    val width: Int,
    val height: Int,
    val fps: Int,
    val cameraName: String,
    // Null Safety
    var serverIp: String? = null
)

// Extension функція
fun CameraSettings.getVideoQualityTag(): String {
    return when {
        this.width >= 3840 -> "4K UHD"
        this.width >= 1920 -> "Full HD"
        this.width >= 1280 -> "HD"
        else -> "SD"
    }
}

fun testCameraLogic() {
    val mySettings = CameraSettings(
        width = 1920,
        height = 1080,
        fps = 30,
        cameraName = "Back Camera",
        serverIp = null
    )

    // Корутини
    runBlocking {
        connectToPc(mySettings)
    }
}

suspend fun connectToPc(settings: CameraSettings) {
    delay(1500)
    println("Підключено!")
}