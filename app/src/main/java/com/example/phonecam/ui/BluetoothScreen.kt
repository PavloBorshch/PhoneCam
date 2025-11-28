package com.example.phonecam.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.phonecam.network.BleDevice

// ЗАВДАННЯ 17: Екран сканування Bluetooth
@Composable
fun BluetoothScreen(
    scannedDevices: List<BleDevice>,
    connectionState: String,
    onScanStart: () -> Unit,
    onConnect: (BleDevice) -> Unit,
    onDisconnect: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // Заголовок та статус
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Bluetooth Low Energy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Статус: ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = connectionState,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (connectionState == "Connected") Color(0xFF2E7D32) else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (connectionState == "Connected") {
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier.padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Відключитися")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка сканування
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Знайдені пристрої:", style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onScanStart) {
                Icon(Icons.Default.Refresh, contentDescription = "Scan")
            }
        }

        // Список пристроїв
        if (scannedDevices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("Натисніть сканувати...", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scannedDevices) { device ->
                    BleDeviceItem(device = device, onClick = { onConnect(device) })
                }
            }
        }
    }
}

@Composable
fun BleDeviceItem(device: BleDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = device.name, fontWeight = FontWeight.Bold)
                Text(text = device.address, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "${device.rssi} dBm",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}