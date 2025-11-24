package com.example.phonecam

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.phonecam.data.WebcamRepository
import com.example.phonecam.data.AppDatabase
import com.example.phonecam.data.LogEntity
import com.example.phonecam.network.RetrofitInstance
import com.example.phonecam.ui.theme.PhoneCamTheme
import com.example.phonecam.worker.SyncWorker
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// Основна программа
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // WorkManager (Фонова синхронізація)
        setupBackgroundSync()

        setContent {
            PhoneCamTheme(darkTheme = false) { // ЗАНЯТТЯ 4: Темізація
                val context = LocalContext.current

                // Room Database
                val database = AppDatabase.getDatabase(context)
                val dao = database.settingsDao()
                val logDao = database.logDao()
                val api = RetrofitInstance.api

                // Repository Pattern
                val repository = remember { WebcamRepository(dao, logDao, api) }

                //  ViewModel
                val viewModel: WebcamViewModel = viewModel(
                    factory = WebcamViewModelFactory(repository)
                )

                AppNavigation(viewModel)
            }
        }
    }

    private fun setupBackgroundSync() {
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SyncPublicIp",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}

data class ParameterItem(val label: String, val value: String)

// Navigation Component
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(viewModel: WebcamViewModel) {
    val navController = rememberNavController()
    // StateFlow (Підписка на стан UI)
    val uiState by viewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // SharedFlow (Обробка одноразових подій)
    LaunchedEffect(key1 = true) {
        viewModel.eventFlow.collectLatest { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Scaffold (Структура екрану)
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("Історія") },
                    selected = false,
                    onClick = { navController.navigate("history") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Налаштування") },
                    selected = false,
                    onClick = { }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                MainContent(
                    state = uiState,
                    onToggleStream = { viewModel.toggleStreaming() },
                    onCheckController = { viewModel.checkControllerStatus() },
                    onProtocolClick = { protocolName ->
                        viewModel.updateProtocol(protocolName)
                        navController.navigate("details/$protocolName")
                    }
                )
            }
            // Екран історії
            composable("history") {
                LogHistoryScreen(viewModel)
            }
            // Передача параметрів між екранами
            composable(
                route = "details/{protocolName}",
                arguments = listOf(navArgument("protocolName") { type = NavType.StringType })
            ) { backStackEntry ->
                val protocolName = backStackEntry.arguments?.getString("protocolName") ?: "Unknown"
                ProtocolDetailScreen(
                    protocolName = protocolName,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

// Віртуалізація списків
@Composable
fun LogHistoryScreen(viewModel: WebcamViewModel) {
    // collectAsLazyPagingItems - отримання даних зі спеціального Flow
    val logItems = viewModel.logsPagingFlow.collectAsLazyPagingItems()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Історія подій", style = MaterialTheme.typography.headlineSmall)

            IconButton(
                onClick = { viewModel.clearLogs() }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Очистити історію",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Основа для списків
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                count = logItems.itemCount,
                key = { index -> logItems[index]?.id ?: index }
            ) { index ->
                val log = logItems[index]
                if (log != null) {
                    LogItemCard(log)
                }
            }
        }
    }
}

@Composable
fun LogItemCard(log: LogEntity) {
    val date = Date(log.timestamp)
    val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (log.type == "ERROR") Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = log.message, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = log.type,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (log.type == "ERROR") Color.Red else Color.Gray
                )
            }
            Text(
                text = format.format(date),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// Основні компоненти UI
@Composable
fun MainContent(
    state: WebcamUiState,
    onToggleStream: () -> Unit,
    onProtocolClick: (String) -> Unit,
    onCheckController: () -> Unit,
    modifier: Modifier = Modifier
) {
    val parametersList = listOf(
        ParameterItem("Камера", state.cameraSettings.cameraName),
        ParameterItem("Роздільна здатність", "${state.cameraSettings.width}x${state.cameraSettings.height}"),
        ParameterItem("Кодек", "H.264 (AVC)"),
        ParameterItem("Протокол", state.currentProtocol),
        ParameterItem("FPS", "${state.currentFps}"),
        ParameterItem("Бітрейт", state.formattedBitrate),
        ParameterItem("Тривалість", state.connectionDuration)
    )
    val protocols = listOf("RTSP", "HTTP", "MJPEG", "USB")

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Картка Статусу
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
                Spacer(modifier = Modifier.height(8.dp))
                // Відображення Public IP з API
                Text("Public IP: ${state.publicIp}", color = Color.White.copy(alpha = 0.9f))
                if (state.isStreaming) {
                    Text("Local IP: ${state.cameraSettings.serverIp}", color = Color.White)
                    Text("Час: ${state.connectionDuration}", color = Color.White, fontWeight = FontWeight.Light)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // UI для JSON/XML парсингу
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Діагностика (JSON/XML)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onCheckController, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Оновити")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))

                // Відображення розпарсеного тексту
                Text(
                    text = state.controllerStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Оберіть протокол:", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        // Горизонтальний список
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(protocols) { protocol ->
                SuggestionChip(
                    onClick = { onProtocolClick(protocol) },
                    label = { Text(protocol) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (protocol == state.currentProtocol)
                            MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Моніторинг потоку", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(parametersList) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.value, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Button(onClick = onToggleStream, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (state.isStreaming) "Зупинити" else "Запустити трансляцію")
        }
    }
}

@Composable
fun ProtocolDetailScreen(protocolName: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text = "Налаштування збережено!", color = Color(0xFF2E7D32))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = protocolName, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Назад")
        }
    }
}