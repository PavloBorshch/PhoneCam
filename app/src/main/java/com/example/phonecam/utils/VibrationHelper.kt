package com.example.phonecam.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

// Хелпер для вібрації
class VibrationHelper(context: Context) {

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // Коротка подвійна вібрація для попередження
    fun vibrateWarning() {
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timing = longArrayOf(0, 100, 100, 200)
                val amplitudes = intArrayOf(0, 255, 0, 255)

                try {
                    vibrator.vibrate(VibrationEffect.createWaveform(timing, amplitudes, -1))
                } catch (e: Exception) {
                    vibrator.vibrate(VibrationEffect.createWaveform(timing, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 100, 100, 200), -1)
            }
        }
    }
}