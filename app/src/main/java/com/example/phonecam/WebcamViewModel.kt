package com.example.phonecam

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.phonecam.data.WebcamRepository
import com.example.phonecam.data.LogEntity
import com.example.phonecam.data.SettingsEntity
import com.example.phonecam.network.BleDevice
import com.example.phonecam.network.BleManager
import com.example.phonecam.network.MqttHelper
import com.example.phonecam.utils.EdgeProcessor
import com.example.phonecam.utils.NotificationHelper
import com.example.phonecam.utils.SensorHelper
import com.example.phonecam.utils.VibrationHelper
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.Dispatchers
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
    val isCriticalAlert: Boolean = false,
    val scannedDevices: List<BleDevice> = emptyList(),
    val bleConnectionState: String = "Disconnected",
    val lightLevel: Float = 0f,
    val movementAlert: String = "Спокійно",
    // ЗАВДАННЯ 20: Статус підключення до розумного будинку
    val mqttStatus: String = "Smart Home: Connecting..."
)

// Змінено на AndroidViewModel для доступу до Context
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
    private val bleManager = BleManager(application)
    private val sensorHelper = SensorHelper(application)
    private val vibrationHelper = VibrationHelper(application)

    // Ініціалізація MQTT та Edge процесора
    private val edgeProcessor = EdgeProcessor()
    private val mqttHelper = MqttHelper(onMessageReceived = { command ->
        handleMqttCommand(command)
    })

    private var simulationJob: Job? = null
    private var scanJob: Job? = null
    private var alertResetJob: Job? = null

    private var secondsCounter = 0
    private var lastAlertTime = 0L

    init {
        loadSettings()
        fetchPublicIp()
        checkControllerStatus()
        loadMapLocations()
        observeBleConnection()
        startSensorMonitoring()
        connectToSmartHome() // Підключення до MQTT
    }

    // Підключення до брокера
    private fun connectToSmartHome() {
        viewModelScope.launch(Dispatchers.IO) {
            val connected = mqttHelper.connect()
            val status = if (connected) "Smart Home: Connected (MQTT)" else "Smart Home: Error"
            _uiState.update { it.copy(mqttStatus = status) }

            if (connected) {
                repository.addLog("Підключено до Розумного будинку", "INFO")
            }
        }
    }

    // Обробка команд від розумного будинку
    private fun handleMqttCommand(command: String) {
        viewModelScope.launch {
            repository.addLog("MQTT Команда: $command", "CMD")
            if (command.contains("START_STREAM")) {
                if (!_uiState.value.isStreaming) startStreaming()
            } else if (command.contains("STOP_STREAM")) {
                if (_uiState.value.isStreaming) stopStreaming()
            }
        }
    }

    fun onDeviceIdentified(code: String) {
        viewModelScope.launch {
            val deviceName = when {
                code.contains("CAM-01") -> "Камера Цех №1"
                code.contains("CAM-02") -> "Камера Склад"
                else -> "Невідомий пристрій ($code)"
            }

            _uiState.update {
                it.copy(
                    cameraSettings = it.cameraSettings.copy(cameraName = deviceName)
                )
            }

            repository.addLog("Ідентифіковано: $deviceName", "INFO")
            _eventFlow.emit("Підключено: $deviceName")
            vibrationHelper.vibrateWarning()
        }
    }

    private fun getBatteryTemperature(): Float {
        val intent = getApplication<Application>().registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val tempInt = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return tempInt / 10f
    }

    private fun startSensorMonitoring() {
        viewModelScope.launch {
            sensorHelper.observeSensors().collect { data ->
                _uiState.update {
                    it.copy(
                        lightLevel = data.lightLux,
                        movementAlert = if (data.isShaking) "УДАР/ПАДІННЯ! (${"%.1f".format(data.gForce)}G)" else it.movementAlert
                    )
                }

                // ЗАВДАННЯ 20: Edge Computing - Відправка даних тільки при змінах
                val payload = edgeProcessor.processAndFormat(getBatteryTemperature(), data.lightLux)
                if (payload != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        mqttHelper.publishTelemetry(payload)
                    }
                }

                if (data.isShaking) {
                    handleMovementAlert(data.gForce)
                }
            }
        }
    }

    private fun handleMovementAlert(gForce: Float) {
        alertResetJob?.cancel()

        val message = "Детектовано удар! Перевантаження: %.1f G".format(gForce)

        viewModelScope.launch {
            repository.addLog(message, "WARN")
            _eventFlow.emit(message)
            // Також можна відправити SOS по MQTT
            launch(Dispatchers.IO) { mqttHelper.publishTelemetry("""{"alert": "CRITICAL_SHOCK", "g_force": $gForce}""") }
        }

        vibrationHelper.vibrateWarning()
        notificationHelper.showCriticalAlert("Аварійна ситуація", message)

        alertResetJob = viewModelScope.launch {
            delay(5000)
            _uiState.update { it.copy(movementAlert = "Спокійно") }
        }
    }

    private fun observeBleConnection() {
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                _uiState.update { it.copy(bleConnectionState = state) }
            }
        }
    }

    fun startBleScan() {
        _uiState.update { it.copy(scannedDevices = emptyList()) }

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            try {
                bleManager.scanBleDevices().collect { device ->
                    _uiState.update { currentState ->
                        val currentList = currentState.scannedDevices.toMutableList()
                        if (currentList.none { it.address == device.address }) {
                            currentList.add(device)
                        }
                        currentState.copy(scannedDevices = currentList)
                    }
                }
            } catch (e: Exception) {
                Log.e("WebcamViewModel", "Scan error: ${e.message}")
            }
        }

        viewModelScope.launch {
            delay(10000)
            scanJob?.cancel()
        }
    }

    fun connectToBleDevice(device: BleDevice) {
        scanJob?.cancel()
        bleManager.connectToDevice(device.device)
    }

    fun disconnectBle() {
        bleManager.disconnect()
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
                    appendLine("Sensors OK")
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

        // Підключення до WebSocket потоку
        simulationJob = viewModelScope.launch {
            repository.observeRealtimeData().collectLatest { data ->
                secondsCounter++

                val realBatteryTemp = getBatteryTemperature()

                _uiState.update { currentState ->
                    val newHistory = currentState.bitrateHistory.toMutableList().apply {
                        add(data.bitrate)
                        if (size > 50) removeAt(0)
                    }

                    val isTempCritical = realBatteryTemp > 45.0f
                    val isVoltageCritical = data.voltage < 4.0f
                    val isCritical = isTempCritical || isVoltageCritical

                    if (isTempCritical) {
                        handleCriticalAlert(realBatteryTemp)
                    }

                    currentState.copy(
                        currentFps = data.fps,
                        formattedBitrate = "${data.bitrate} Kbps",
                        currentBitrate = data.bitrate,
                        connectionDuration = formatDuration(secondsCounter / 2),
                        bitrateHistory = newHistory,
                        cpuTemp = realBatteryTemp,
                        inputVoltage = data.voltage,
                        isCriticalAlert = isCritical
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
                _eventFlow.emit("УВАГА! Перегрів пристрою!")
            }
            // Системна нотифікація
            notificationHelper.showCriticalAlert(
                "Критична помилка",
                "Температура пристрою перевищила норму: %.1f°C".format(temp)
            )
            vibrationHelper.vibrateWarning()
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
    private val application: Application,
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