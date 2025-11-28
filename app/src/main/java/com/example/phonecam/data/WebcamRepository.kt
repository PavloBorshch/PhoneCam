package com.example.phonecam.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.phonecam.network.ControllerConfig
import com.example.phonecam.network.IpApiService
import com.example.phonecam.network.RealtimeData
import com.example.phonecam.network.WebSocketManager
import com.example.phonecam.utils.XmlParser
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

// Клас, що виступає посередником між даними
class WebcamRepository(
    private val settingsDao: SettingsDao,
    private val logDao: LogDao,
    private val apiService: IpApiService
) {

    val settingsFlow: Flow<SettingsEntity?> = settingsDao.getSettings()

    // Ініціалізація WebSocket менеджера
    private val webSocketManager = WebSocketManager()
    private val jsonParser = Json { ignoreUnknownKeys = true }

    // Функція для підписки на реалтайм потік
    fun observeRealtimeData(): Flow<RealtimeData> {
        return webSocketManager.connectToDataStream()
    }

    fun getPagedLogs(): Flow<PagingData<LogEntity>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false, initialLoadSize = 20),
            pagingSourceFactory = { logDao.getAllLogs() }
        ).flow
    }

    suspend fun addLog(message: String, type: String = "INFO") {
        logDao.insertLog(LogEntity(message = message, type = type))
    }

    suspend fun clearAllLogs() {
        logDao.clearLogs()
    }

    suspend fun saveSettings(settings: SettingsEntity) {
        settingsDao.saveSettings(settings)
    }

    suspend fun getPublicIp(): String {
        return try {
            val response = apiService.getPublicIp()
            response.ip
        } catch (e: Exception) {
            "Офлайн режим"
        }
    }

    // Емуляція отримання JSON
    suspend fun fetchControllerConfig(): ControllerConfig {
        // Симулюємо сиру відповідь від пристрою
        val rawJson = """
            {
                "controllerId": "CAM-CTRL-V3",
                "firmwareVersion": "2.5.4-stable",
                "maintenanceRequired": false,
                "sensors": [
                    {"type": "CPU_TEMP", "value": 45.2, "unit": "C"},
                    {"type": "INPUT_VOLTAGE", "value": 5.1, "unit": "V"}
                ]
            }
        """.trimIndent()

        // Парсинг JSON у об'єкт
        return jsonParser.decodeFromString<ControllerConfig>(rawJson)
    }

    // Емуляція отримання XML
    suspend fun fetchLegacyStatus(): Map<String, String> {
        val rawXml = """
            <StatusReport>
                <SystemState>ACTIVE</SystemState>
                <UptimeSeconds>86400</UptimeSeconds>
                <ErrorCode>0</ErrorCode>
            </StatusReport>
        """.trimIndent()

        // Парсинг XML
        return XmlParser.parseLegacyStatus(rawXml)
    }
}