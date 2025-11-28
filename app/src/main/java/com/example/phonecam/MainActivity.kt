package com.example.phonecam

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.example.phonecam.ui.BluetoothScreen
import com.example.phonecam.ui.DashboardSection
import com.example.phonecam.ui.MapScreen
import com.example.phonecam.ui.ScanScreen
import com.example.phonecam.ui.theme.PhoneCamTheme
import com.example.phonecam.worker.SyncWorker
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var viewModel: WebcamViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // WorkManager (Фонова синхронізація)
        setupBackgroundSync()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            PhoneCamTheme(darkTheme = false) {
                // Request Permission Logic
                RequestNotificationPermission()

                val context = LocalContext.current
                // Приводимо context до Application для ViewModelFactory
                val application = context.applicationContext as android.app.Application

                // Room Database
                val database = AppDatabase.getDatabase(context)
                val dao = database.settingsDao()
                val logDao = database.logDao()
                val api = RetrofitInstance.api

                // Repository Pattern
                val repository = remember { WebcamRepository(dao, logDao, api) }

                viewModel = viewModel(
                    factory = WebcamViewModelFactory(application, repository)
                )

                AppNavigation(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {

            val tagId = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)?.joinToString("") { "%02X".format(it) }
            val message = "NFC Tag Detected: $tagId"

            if (::viewModel.isInitialized) {
                viewModel.onDeviceIdentified("NFC-$tagId-CAM-01")
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        val filters = arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, filters, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    @Composable
    fun RequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = {}
            )
            LaunchedEffect(Unit) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

@Composable
fun RequestBluetoothPermissions(onPermissionGranted: () -> Unit) {
    val context = LocalContext.current
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted) {
            onPermissionGranted()
        } else {
            Toast.makeText(context, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            onPermissionGranted()
        }
    }
}


data class ParameterItem(val label: String, val value: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(viewModel: WebcamViewModel) {
    val navController = rememberNavController()
    // StateFlow (Підписка на стан UI)
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) {
            viewModel.startBleScan()
        } else {
            Toast.makeText(context, "Необхідні дозволи Bluetooth", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(key1 = true) {
        viewModel.eventFlow.collectLatest { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
                withDismissAction = true
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
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Скан") },
                    selected = false,
                    onClick = { navController.navigate("scan") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Share, contentDescription = null) },
                    label = { Text("BLE") },
                    selected = false,
                    onClick = { navController.navigate("bluetooth") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    label = { Text("Карта") },
                    selected = false,
                    onClick = { navController.navigate("map") }
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
            composable("map") {
                MapScreen(controllers = uiState.mapLocations)
            }
            composable("bluetooth") {
                BluetoothScreen(
                    scannedDevices = uiState.scannedDevices,
                    connectionState = uiState.bleConnectionState,
                    onScanStart = {
                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                        } else {
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                        blePermissionLauncher.launch(permissions)
                    },
                    onConnect = { device -> viewModel.connectToBleDevice(device) },
                    onDisconnect = { viewModel.disconnectBle() }
                )
            }
            composable("scan") {
                ScanScreen(
                    onCodeScanned = { code ->
                        viewModel.onDeviceIdentified(code)
                        navController.popBackStack()
                    }
                )
            }
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
    val infiniteTransition = rememberInfiniteTransition(label = "AlertPulse")
    val alertPulseColor by infiniteTransition.animateColor(
        initialValue = Color.Red,
        targetValue = Color(0xFF8B0000), // Dark Red
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlertColor"
    )

    val cardColor = when {
        state.isCriticalAlert -> alertPulseColor
        state.isStreaming -> Color(0xFF2E7D32)
        else -> Color(0xFFC62828)
    }

    val parametersList = listOf(
        ParameterItem("Камера", state.cameraSettings.cameraName),
        ParameterItem("Роздільна здатність", "${state.cameraSettings.width}x${state.cameraSettings.height}"),
        ParameterItem("Кодек", "H.264 (AVC)"),
        ParameterItem("Протокол", state.currentProtocol),
        ParameterItem("FPS", "${state.currentFps}"),
        ParameterItem("Бітрейт", state.formattedBitrate),
        ParameterItem("Тривалість", state.connectionDuration),
        ParameterItem("Battery Temp", "%.1f°C".format(state.cpuTemp)),
        ParameterItem("Light Level", "%.1f lx".format(state.lightLevel)),
        ParameterItem("Status", state.movementAlert),
        ParameterItem("BLE Status", state.bleConnectionState),
        // ЗАВДАННЯ 20: Статус MQTT в UI
        ParameterItem("Smart Home", state.mqttStatus)
    )
    val protocols = listOf("RTSP", "HTTP", "MJPEG", "USB")

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

        // Картка статусу
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        state.isCriticalAlert -> {
                            if (state.cpuTemp > 45f) "КРИТИЧНИЙ ПЕРЕГРІВ!"
                            else if (state.inputVoltage > 0.1f && state.inputVoltage < 4.0f) "НИЗЬКА НАПРУГА!"
                            else if (state.inputVoltage <= 0.1f) "ОЧІКУВАННЯ ДАНИХ..."
                            else "КРИТИЧНА ПОМИЛКА!"
                        }
                        state.isStreaming -> "ТРАНСЛЯЦІЯ АКТИВНА"
                        else -> "ТРАНСЛЯЦІЯ ЗУПИНЕНА"
                    },
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Відображення Public IP з API
                Text("Public IP: ${state.publicIp}", color = Color.White.copy(alpha = 0.9f))
                if (state.isStreaming) {
                    Text("Live Stream (WebSocket)", color = Color.White.copy(alpha = 0.9f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Вибір протоколу
        Text("Оберіть протокол:", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
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

        // Scrollable Area
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            // Діагностика
            item {
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
                        Text(
                            text = state.controllerStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // KPI Dashboard
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Передаємо alert state для підсвічування рамок
                DashboardSection(state)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Графік стабільності
            if (state.isStreaming) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                // Червона рамка якщо бітрейт падає низько
                                if (state.currentBitrate < 2000 && state.currentBitrate > 0)
                                    Modifier.border(2.dp, Color.Red, RoundedCornerShape(12.dp))
                                else Modifier
                            )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Стабільність мережі (Live)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            com.example.phonecam.BitrateChart(
                                dataPoints = state.bitrateHistory,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Список параметрів
            items(parametersList) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.value, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Кнопка запуску
        Button(
            onClick = onToggleStream,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = when {
                    state.isStreaming && state.isCriticalAlert -> MaterialTheme.colorScheme.error
                    state.isStreaming -> Color(0xFF455A64)
                    else -> MaterialTheme.colorScheme.primary
                }
            )
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (state.isStreaming) "Зупинити трансляцію" else "Запустити трансляцію")
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