package com.example.phonecam.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Модель для відображення знайденого пристрою
data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice
)

// Менеджер Bluetooth Low Energy
class BleManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private var gatt: BluetoothGatt? = null

    // Потік для статусу підключення
    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState = _connectionState.asStateFlow()

    // Сканування пристроїв
    @SuppressLint("MissingPermission") // Дозволи перевіряються в UI
    fun scanBleDevices(): Flow<BleDevice> = callbackFlow {
        if (adapter == null || !adapter.isEnabled) {
            close()
            return@callbackFlow
        }

        val scanner = adapter.bluetoothLeScanner
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val deviceName = result.device.name ?: "Unknown Device"
                // Фільтруємо пристрої, щоб не засмічувати список (для демо)
                // Наприклад, показуємо тільки пристрої з ім'ям або сильним сигналом
                if (deviceName != "Unknown Device" || result.rssi > -60) {
                    trySend(
                        BleDevice(
                            name = deviceName,
                            address = result.device.address,
                            rssi = result.rssi,
                            device = result.device
                        )
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BleManager", "Scan failed: $errorCode")
            }
        }

        scanner.startScan(scanCallback)
        Log.d("BleManager", "Scanning started...")

        awaitClose {
            scanner.stopScan(scanCallback)
            Log.d("BleManager", "Scanning stopped")
        }
    }

    // Підключення до пристрою
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        _connectionState.value = "Connecting to ${device.address}..."

        gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _connectionState.value = "Connected"
                    Log.d("BleManager", "Connected to GATT server.")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _connectionState.value = "Disconnected"
                    Log.d("BleManager", "Disconnected from GATT server.")
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BleManager", "Services discovered: ${gatt.services.size}")
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = "Disconnected"
    }
}