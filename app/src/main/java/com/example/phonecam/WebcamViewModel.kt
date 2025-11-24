package com.example.phonecam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

// Data class, що описує повний стан UI (State Hoisting)
// Замість розрізнених змінних ми маємо один об'єкт стану
data class WebcamUiState(
    val isStreaming: Boolean = false,
    val cameraSettings: CameraSettings = CameraSettings(1920, 1080, 30, "Back Camera", null),
    val currentBitrate: Int = 0, // Аналог "споживання енергії" - навантаження на мережу
    val currentFps: Int = 0,
    val connectionDuration: String = "00:00"
)

class WebcamViewModel : ViewModel() {

    // Internal mutable state
    private val _uiState = MutableStateFlow(WebcamUiState())

    // External immutable state (StateFlow)
    val uiState: StateFlow<WebcamUiState> = _uiState.asStateFlow()

    private var simulationJob: Job? = null
    private var secondsCounter = 0

    // Функція для зміни стану трансляції (Event)
    fun toggleStreaming() {
        val currentStatus = _uiState.value.isStreaming

        if (!currentStatus) {
            startStreaming()
        } else {
            stopStreaming()
        }
    }

    private fun startStreaming() {
        // Оновлюємо стан: трансляція почалась
        _uiState.update { currentState ->
            currentState.copy(
                isStreaming = true,
                cameraSettings = currentState.cameraSettings.copy(serverIp = "192.168.1.105")
            )
        }

        // Запускаємо корутину в viewModelScope для симуляції даних
        simulationJob = viewModelScope.launch {
            while (true) {
                delay(1000) // Оновлення кожну секунду
                secondsCounter++

                // Симуляція зміни параметрів (FPS та Бітрейт "стрибають")
                val randomFps = Random.nextInt(28, 61)
                val randomBitrate = Random.nextInt(3500, 5000)

                _uiState.update { it.copy(
                    currentFps = randomFps,
                    currentBitrate = randomBitrate,
                    connectionDuration = formatDuration(secondsCounter)
                ) }
            }
        }
    }

    private fun stopStreaming() {
        simulationJob?.cancel()
        secondsCounter = 0

        _uiState.update { it.copy(
            isStreaming = false,
            currentFps = 0,
            currentBitrate = 0,
            connectionDuration = "00:00",
            cameraSettings = it.cameraSettings.copy(serverIp = null)
        ) }
    }

    private fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }
}