package com.example.phonecam.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.phonecam.network.RetrofitInstance

// Worker для виконання фонових завдань
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("SyncWorker", "Початок фонової синхронізації...")

            // Виконуємо запит до API у фоні
            val response = RetrofitInstance.api.getPublicIp()

            Log.d("SyncWorker", "IP оновлено успішно: ${response.ip}")

            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Помилка синхронізації", e)
            // Повертаємо retry(), для повторення спроби пізніше
            Result.retry()
        }
    }
}