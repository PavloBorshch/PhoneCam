package com.example.phonecam.network

import android.util.Log
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID

// Клас для роботи з протоколом MQTT
class MqttHelper(
    private val onMessageReceived: (String) -> Unit
) {
    // Використовуємо публічний брокер для тестування
    private val brokerUrl = "tcp://broker.hivemq.com:1883"
    private val clientId = "PhoneCam_" + UUID.randomUUID().toString().substring(0, 8)
    private val topicTelemetry = "phonecam/smart_home/telemetry"
    private val topicControl = "phonecam/smart_home/control"

    private var mqttClient: MqttClient? = null

    fun connect(): Boolean {
        return try {
            mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())

            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 20
            }

            mqttClient?.connect(options)

            // Підписка на команди керування від "Розумного будинку"
            mqttClient?.subscribe(topicControl)

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.d("MqttHelper", "Connection lost: ${cause?.message}")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    message?.let {
                        val payload = String(it.payload)
                        Log.d("MqttHelper", "Command received: $payload")
                        onMessageReceived(payload)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            Log.d("MqttHelper", "Connected to MQTT Broker: $brokerUrl")
            true
        } catch (e: MqttException) {
            Log.e("MqttHelper", "Connect error: ${e.message}")
            false
        }
    }

    fun publishTelemetry(jsonPayload: String) {
        try {
            if (mqttClient?.isConnected == true) {
                val message = MqttMessage(jsonPayload.toByteArray())
                message.qos = 1 // At least once delivery
                mqttClient?.publish(topicTelemetry, message)
                Log.d("MqttHelper", "Published: $jsonPayload")
            }
        } catch (e: MqttException) {
            Log.e("MqttHelper", "Publish error: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
            mqttClient?.close()
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }
}