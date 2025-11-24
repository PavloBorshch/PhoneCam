package com.example.phonecam

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.phonecam.ui.theme.PhoneCamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhoneCamTheme(darkTheme = false) {
                // Викликаємо головний граф навігації
                AppNavigation()
            }
        }
    }
}

data class ParameterItem(val label: String, val value: String)

// Головний граф навігації
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    viewModel: WebcamViewModel = viewModel()
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    // Scaffold залишається спільним для всіх екранів
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Webcam Controller") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Головна") },
                    selected = true,
                    onClick = { navController.navigate("home") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Налаштування") },
                    selected = false,
                    onClick = { /* Можна додати екран settings */ }
                )
            }
        }
    ) { innerPadding ->

        // Navigation Component (NavHost)
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {

            // ЕКРАН Головна сторінка
            composable("home") {
                MainContent(
                    state = uiState,
                    onToggleStream = { viewModel.toggleStreaming() },
                    // Перехід на екран деталей з параметром
                    onProtocolClick = { protocolName ->
                        navController.navigate("details/$protocolName")
                    }
                )
            }

            // ЕКРАН Деталі протоколу
            //Передача параметрів
            composable(
                route = "details/{protocolName}",
                arguments = listOf(navArgument("protocolName") { type = NavType.StringType }),
                // Deep Linking ---
                deepLinks = listOf(navDeepLink { uriPattern = "phonecam://details/{protocolName}" })
            ) { backStackEntry ->
                // Отримання переданого параметру
                val protocolName = backStackEntry.arguments?.getString("protocolName") ?: "Unknown"
                ProtocolDetailScreen(
                    protocolName = protocolName,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun MainContent(
    state: WebcamUiState,
    onToggleStream: () -> Unit,
    onProtocolClick: (String) -> Unit, // Callback для навігації
    modifier: Modifier = Modifier
) {
    val parametersList = listOf(
        ParameterItem("Камера", state.cameraSettings.cameraName),
        ParameterItem("Роздільна здатність", "${state.cameraSettings.width}x${state.cameraSettings.height}"),
        ParameterItem("Кодек", "H.264 (AVC)"),
        ParameterItem("FPS (Поточний)", "${state.currentFps}"),
        ParameterItem("Бітрейт (Трафік)", "${state.currentBitrate} Kbps"),
        ParameterItem("Тривалість", state.connectionDuration)
    )

    val protocols = listOf("RTSP", "HTTP", "MJPEG", "USB")

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

        // Картка статусу
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (state.isStreaming) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (state.isStreaming) "ТРАНСЛЯЦІЯ АКТИВНА" else "ТРАНСЛЯЦІЯ ЗУПИНЕНА",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (state.isStreaming) {
                    Text("IP: ${state.cameraSettings.serverIp}", color = Color.White)
                    Text("Час: ${state.connectionDuration}", color = Color.White, fontWeight = FontWeight.Light)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Натисніть на протокол для деталей:", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(protocols) { protocol ->
                SuggestionChip(
                    // При натисканні викликаємо навігацію
                    onClick = { onProtocolClick(protocol) },
                    label = { Text(protocol) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Моніторинг потоку", style = MaterialTheme.typography.labelMedium, color = Color.Gray)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(parametersList) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.value, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Button(
            onClick = onToggleStream,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (state.isStreaming) "Зупинити" else "Запустити трансляцію")
        }
    }
}

@Composable
fun ProtocolDetailScreen(protocolName: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Деталі протоколу", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        // Відображення переданого параметра
        Text(
            text = protocolName,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))
        Text("Тут будуть розширені налаштування для $protocolName")

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Назад")
        }
    }
}