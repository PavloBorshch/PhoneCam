package com.example.phonecam.data

import com.example.phonecam.database.SettingsDao
import com.example.phonecam.database.SettingsEntity
import com.example.phonecam.network.IpApiService
import kotlinx.coroutines.flow.Flow

// ЗАНЯТТЯ 10: Repository Pattern
// Клас, що виступає посередником між даними (DB, API) та UI (ViewModel).
// Реалізує принцип Single Source of Truth.
class WebcamRepository(
    private val settingsDao: SettingsDao,
    private val apiService: IpApiService
) {

    // Offline-first: Ми завжди повертаємо дані з локальної БД (Flow).
    // UI підписується на це джерело і отримує оновлення миттєво.
    val settingsFlow: Flow<SettingsEntity?> = settingsDao.getSettings()

    // Метод для збереження налаштувань
    suspend fun saveSettings(settings: SettingsEntity) {
        settingsDao.saveSettings(settings)
    }

    // Метод для отримання IP (Спроба мережі -> Обробка помилки)
    // У реальному Offline-first додатку ми б зберігали цей IP в БД,
    // але для спрощення просто повертаємо результат.
    suspend fun getPublicIp(): String {
        return try {
            val response = apiService.getPublicIp()
            response.ip
        } catch (e: Exception) {
            // Якщо мережа недоступна, повертаємо повідомлення або кешоване значення
            "Офлайн режим"
        }
    }
}