package com.example.phonecam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.phonecam.data.WebcamRepository
import com.example.phonecam.data.LogEntity
import com.example.phonecam.data.SettingsEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

// State Hoisting
data class WebcamUiState(
    val isStreaming: Boolean = false,
    val cameraSettings: CameraSettings = CameraSettings(1920, 1080, 30, "Back Camera", null),
    val currentProtocol: String = "RTSP",
    val currentBitrate: Int = 0,
    val currentFps: Int = 0,
    val connectionDuration: String = "00:00",
    val publicIp: String = "Завантаження...",
    val formattedBitrate: String = "0 Kbps",
    // Поле для результатів парсингу
    val controllerStatus: String = "Дані не завантажено"
)

class WebcamViewModel(private val repository: WebcamRepository) : ViewModel() {

    // Незмінний потік для UI
    private val _uiState = MutableStateFlow(WebcamUiState())
    val uiState: StateFlow<WebcamUiState> = _uiState.asStateFlow()

    // SharedFlow для подій, як Snackbar
    private val _eventFlow = MutableSharedFlow<String>()
    val eventFlow: SharedFlow<String> = _eventFlow.asSharedFlow()

    // Кешований потік сторінок
    val logsPagingFlow: Flow<PagingData<LogEntity>> = repository.getPagedLogs()
        .cachedIn(viewModelScope)

    private var simulationJob: Job? = null
    private var secondsCounter = 0

    init {
        loadSettings()
        fetchPublicIp()
        // Перевірка контролера при старті
        checkControllerStatus()
    }

    // Функція перевірки та парсингу
    fun checkControllerStatus() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(controllerStatus = "Завантаження даних...") }

                // 1. Отримуємо JSON дані
                val config = repository.fetchControllerConfig()

                // 2. Отримуємо XML дані
                val legacy = repository.fetchLegacyStatus()

                val statusReport = buildString {
                    appendLine("ID: ${config.controllerId} (v${config.firmwareVersion})")
                    appendLine("Сенсори:")
                    config.sensors.forEach { sensor ->
                        appendLine(" • ${sensor.type}: ${sensor.value} ${sensor.unit}")
                    }
                    appendLine("SCADA Status:")
                    appendLine(" • System: ${legacy["SystemState"]}")
                    appendLine(" • Uptime: ${legacy["UptimeSeconds"]}s")
                }

                _uiState.update { it.copy(controllerStatus = statusReport) }
                repository.addLog("Дані контролера оновлено (JSON/XML OK)")

            } catch (e: Exception) {
                _uiState.update { it.copy(controllerStatus = "Помилка парсингу: ${e.message}") }
                repository.addLog("Помилка парсингу даних", "ERROR")
            }
        }
    }

    // Робота з мережею (Retrofit)
    private fun fetchPublicIp() {
        viewModelScope.launch {
            val ip = repository.getPublicIp()
            _uiState.update { it.copy(publicIp = ip) }
            repository.addLog("Отримано IP: $ip")
        }
    }

    // Завантаження з Room Database через Repository
    private fun loadSettings() {
        viewModelScope.launch {
            repository.settingsFlow.collect { savedSettings ->
                if (savedSettings != null) {
                    _uiState.update { it.copy(
                        cameraSettings = it.cameraSettings.copy(
                            width = savedSettings.resolutionWidth,
                            height = savedSettings.resolutionHeight,
                            fps = savedSettings.fps,
                            cameraName = savedSettings.cameraName
                        ),
                        currentProtocol = savedSettings.protocol
                    )}
                } else {
                    saveCurrentSettings()
                }
            }
        }
    }

    private fun saveCurrentSettings() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val entity = SettingsEntity(
                id = 1,
                cameraName = currentState.cameraSettings.cameraName,
                resolutionWidth = currentState.cameraSettings.width,
                resolutionHeight = currentState.cameraSettings.height,
                fps = currentState.cameraSettings.fps,
                protocol = currentState.currentProtocol
            )
            repository.saveSettings(entity)
        }
    }

    fun updateProtocol(newProtocol: String) {
        _uiState.update { it.copy(currentProtocol = newProtocol) }
        saveCurrentSettings()
        viewModelScope.launch { repository.addLog("Протокол змінено на $newProtocol") }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearAllLogs()
            repository.addLog("Історію подій очищено")
        }
    }

    fun toggleStreaming() {
        if (!_uiState.value.isStreaming) startStreaming() else stopStreaming()
    }

    // Джерела даних
    private val fpsFlow: Flow<Int> = flow {
        while (true) {
            delay(1000)
            val fps = if (Random.nextBoolean()) Random.nextInt(25, 61) else 0
            emit(fps)
        }
    }

    private val bitrateFlow: Flow<Int> = flow {
        while (true) {
            delay(1000)
            emit(Random.nextInt(1500, 8000))
        }
    }

    private fun startStreaming() {
        _uiState.update { it.copy(
            isStreaming = true,
            cameraSettings = it.cameraSettings.copy(serverIp = "192.168.1.105")
        )}
        viewModelScope.launch { repository.addLog("Трансляцію розпочато") }

        simulationJob = viewModelScope.launch {
            // Оператори Flow (filter, map, combine)
            val cleanFpsFlow = fpsFlow.filter { it > 10 }

            val formattedBitrateFlow = bitrateFlow
                .onEach { bitrate ->
                    if (bitrate < 2000) {
                        _eventFlow.emit("Низька швидкість мережі: $bitrate Kbps")
                        repository.addLog("Низький бітрейт: $bitrate", "WARN")
                    }
                }
                .map { bitrate -> "$bitrate Kbps" }

            cleanFpsFlow.combine(formattedBitrateFlow) { fps, bitrateString ->
                secondsCounter++
                Triple(fps, bitrateString, formatDuration(secondsCounter))
            }.collect { (fps, bitrateStr, duration) ->
                _uiState.update { it.copy(
                    currentFps = fps,
                    formattedBitrate = bitrateStr,
                    currentBitrate = try { bitrateStr.split(" ")[0].toInt() } catch (e: Exception) { 0 },
                    connectionDuration = duration
                )}
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
            formattedBitrate = "0 Kbps",
            connectionDuration = "00:00",
            cameraSettings = it.cameraSettings.copy(serverIp = null)
        )}
        viewModelScope.launch { repository.addLog("Трансляцію зупинено") }
    }

    private fun formatDuration(seconds: Int): String {
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}

class WebcamViewModelFactory(private val repository: WebcamRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WebcamViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WebcamViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}