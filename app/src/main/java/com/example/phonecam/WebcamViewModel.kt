package com.example.phonecam

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.phonecam.data.WebcamRepository
import com.example.phonecam.data.LogEntity
import com.example.phonecam.data.SettingsEntity
import com.example.phonecam.utils.NotificationHelper
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ControllerLocation(
    val id: String,
    val name: String,
    val position: GeoPoint,
    val isActive: Boolean
)

data class WebcamUiState(
    val isStreaming: Boolean = false,
    val cameraSettings: CameraSettings = CameraSettings(1920, 1080, 30, "Back Camera", null),
    val currentProtocol: String = "RTSP",
    val currentBitrate: Int = 0,
    val currentFps: Int = 0,
    val connectionDuration: String = "00:00",
    val publicIp: String = "Завантаження...",
    val formattedBitrate: String = "0 Kbps",
    val controllerStatus: String = "Дані не завантажено",
    val bitrateHistory: List<Int> = emptyList(),
    val cpuTemp: Float = 0f,
    val inputVoltage: Float = 0f,
    val mapLocations: List<ControllerLocation> = emptyList(),
    // ЗАВДАННЯ 16: Стан тривоги
    val isCriticalAlert: Boolean = false
)

// Змінено на AndroidViewModel для доступу до Context (для нотифікацій)
class WebcamViewModel(
    application: Application,
    private val repository: WebcamRepository
) : AndroidViewModel(application) {

    // Незмінний потік для UI
    private val _uiState = MutableStateFlow(WebcamUiState())
    val uiState: StateFlow<WebcamUiState> = _uiState.asStateFlow()

    // SharedFlow для подій, як Snackbar
    private val _eventFlow = MutableSharedFlow<String>()
    val eventFlow: SharedFlow<String> = _eventFlow.asSharedFlow()

    // Кешований потік сторінок
    val logsPagingFlow: Flow<PagingData<LogEntity>> = repository.getPagedLogs()
        .cachedIn(viewModelScope)

    private val notificationHelper = NotificationHelper(application)
    private var simulationJob: Job? = null
    private var secondsCounter = 0
    private var lastAlertTime = 0L

    init {
        loadSettings()
        fetchPublicIp()
        // Перевірка контролера при старті
        checkControllerStatus()
        loadMapLocations()
    }

    private fun loadMapLocations() {
        val locations = listOf(
            ControllerLocation("1", "Camera 1", GeoPoint(50.4501, 30.5234), true),
            ControllerLocation("2", "Camera 2", GeoPoint(50.4650, 30.5150), true),
            ControllerLocation("3", "Camera 3", GeoPoint(50.4350, 30.5500), false)
        )
        _uiState.update { it.copy(mapLocations = locations) }
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
                    appendLine("Sensors OK") // Скорочено для демо
                }

                _uiState.update { it.copy(controllerStatus = statusReport) }
            } catch (e: Exception) {
                _uiState.update { it.copy(controllerStatus = "Error: ${e.message}") }
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
        viewModelScope.launch { repository.clearAllLogs() }
    }

    fun toggleStreaming() {
        if (!_uiState.value.isStreaming) startStreaming() else stopStreaming()
    }

    private fun startStreaming() {
        _uiState.update { it.copy(
            isStreaming = true,
            cameraSettings = it.cameraSettings.copy(serverIp = "192.168.1.105"),
            bitrateHistory = emptyList()
        )}
        viewModelScope.launch { repository.addLog("Трансляцію розпочато (WS Connected)") }

        // ЗАВДАННЯ 16: Підключення до WebSocket потоку
        simulationJob = viewModelScope.launch {
            repository.observeRealtimeData().collectLatest { data ->
                secondsCounter++ // Просто для демо таймера, хоча потік швидший

                // Оновлення історії графіка
                _uiState.update { currentState ->
                    val newHistory = currentState.bitrateHistory.toMutableList().apply {
                        add(data.bitrate)
                        if (size > 50) removeAt(0)
                    }

                    // Логіка алертів
                    if (data.isAlert) {
                        handleCriticalAlert(data.cpuTemp)
                    }

                    currentState.copy(
                        currentFps = data.fps,
                        formattedBitrate = "${data.bitrate} Kbps",
                        currentBitrate = data.bitrate,
                        connectionDuration = formatDuration(secondsCounter / 2), // Корекція для таймера
                        bitrateHistory = newHistory,
                        cpuTemp = data.cpuTemp,
                        inputVoltage = data.voltage,
                        isCriticalAlert = data.isAlert
                    )
                }
            }
        }
    }

    private fun handleCriticalAlert(temp: Float) {
        val currentTime = System.currentTimeMillis()
        // Показуємо нотифікацію не частіше ніж раз на 10 секунд
        if (currentTime - lastAlertTime > 10000) {
            lastAlertTime = currentTime
            viewModelScope.launch {
                repository.addLog("КРИТИЧНА ТЕМПЕРАТУРА: %.1f°C".format(temp), "ERROR")
                _eventFlow.emit("УВАГА! Перегрів процесора!")
            }
            // Системна нотифікація
            notificationHelper.showCriticalAlert(
                "Критична помилка",
                "Температура CPU перевищила норму: %.1f°C".format(temp)
            )
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
            cameraSettings = it.cameraSettings.copy(serverIp = null),
            isCriticalAlert = false
        )}
        viewModelScope.launch { repository.addLog("Трансляцію зупинено") }
    }

    private fun formatDuration(seconds: Int): String {
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}

class WebcamViewModelFactory(
    private val application: Application, // Factory тепер потребує Application context
    private val repository: WebcamRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WebcamViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WebcamViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}