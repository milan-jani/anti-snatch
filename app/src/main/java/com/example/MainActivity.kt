package com.example

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.detection.SnatchDetector
import com.example.security.SnatchDeviceAdminReceiver
import com.example.service.ProtectionService
import com.example.service.SensorStateMonitor
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var showInfoDialog by remember { mutableStateOf(false) }
                
                if (showInfoDialog) {
                    AlertDialog(
                        onDismissRequest = { showInfoDialog = false },
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        title = { Text("How it works") },
                        text = {
                            Text(
                                "Anti-Snatch protects your device by constantly monitoring the built-in Accelerometer and Gyroscope.\n\n" +
                                "When it detects a sudden, aggressive movement (like someone forcefully pulling the phone from your hand), the 'Snatch Score' spikes.\n\n" +
                                "If the score reaches 100, the app instantly triggers Android's Device Administrator to lock your screen, preventing unauthorized access."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showInfoDialog = false }) {
                                Text("Got it")
                            }
                        }
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = { Text("Anti-Snatch", fontWeight = FontWeight.Bold) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            actions = {
                                IconButton(onClick = { showInfoDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "App Information",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    DashboardScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isMonitoring by SensorStateMonitor.isMonitoring.collectAsStateWithLifecycle()
    val score by SensorStateMonitor.score.collectAsStateWithLifecycle()
    val accelHistory by SensorStateMonitor.accelHistory.collectAsStateWithLifecycle()
    val gyroHistory by SensorStateMonitor.gyroHistory.collectAsStateWithLifecycle()
    val lockHistory by SensorStateMonitor.lockHistory.collectAsStateWithLifecycle()

    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val adminComponent = remember { ComponentName(context, SnatchDeviceAdminReceiver::class.java) }

    var isAdminActive by remember { mutableStateOf(dpm.isAdminActive(adminComponent)) }
    
    // Refresh admin status when returning to the app
    DisposableEffect(Unit) {
        val checkAdmin = {
            isAdminActive = dpm.isAdminActive(adminComponent)
        }
        checkAdmin()
        onDispose { }
    }

    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isAdminActive = dpm.isAdminActive(adminComponent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted && isAdminActive) {
            val serviceIntent = Intent(context, ProtectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    fun toggleService() {
        if (isMonitoring) {
            val serviceIntent = Intent(context, ProtectionService::class.java)
            context.stopService(serviceIntent)
        } else {
            if (!isAdminActive) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }
            val serviceIntent = Intent(context, ProtectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Device Admin Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isAdminActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isAdminActive) Icons.Default.Lock else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isAdminActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAdminActive) "Device Admin Active" else "Device Admin Required",
                            fontWeight = FontWeight.Bold,
                            color = if (isAdminActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Required to lock the screen automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAdminActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                if (!isAdminActive) {
                    Button(onClick = {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to automatically lock the device when a snatch is detected.")
                        }
                        adminLauncher.launch(intent)
                    }) {
                        Text("Enable")
                    }
                }
            }
        }

        // Protection Toggle Card
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isMonitoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Protection Mode", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = if (isMonitoring) "Monitoring active in background" else "Monitoring stopped",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isMonitoring,
                    onCheckedChange = { toggleService() },
                    enabled = isAdminActive
                )
            }
        }

        // Score Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (score > 50) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Snatch Score", 
                    style = MaterialTheme.typography.titleMedium,
                    color = if (score > 50) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { score / 100f },
                        modifier = Modifier.size(160.dp),
                        strokeWidth = 14.dp,
                        color = if (score > 50) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = if (score > 50) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        strokeCap = StrokeCap.Round
                    )
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (score > 50) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "A score of 100 will immediately lock the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (score > 50) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Sensor Graphs
        Text(text = "Sensor Telemetry", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        
        SensorGraphCard("Linear Acceleration (m/s²)", accelHistory, 0f, 40f)
        SensorGraphCard("Angular Velocity (rad/s)", gyroHistory, 0f, 15f)
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SensorGraphCard(title: String, dataPoints: List<Float>, minY: Float, maxY: Float) {
    val currentValue = dataPoints.lastOrNull() ?: 0f
    
    val ratio = (currentValue / maxY).coerceIn(0f, 1f)
    val dynamicColor = if (ratio < 0.5f) {
        androidx.compose.ui.graphics.lerp(com.example.ui.theme.GraphSafe, com.example.ui.theme.GraphWarning, ratio * 2f)
    } else {
        androidx.compose.ui.graphics.lerp(com.example.ui.theme.GraphWarning, com.example.ui.theme.GraphDanger, (ratio - 0.5f) * 2f)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(text = String.format(java.util.Locale.US, "%.2f", currentValue), fontWeight = FontWeight.ExtraBold, color = dynamicColor)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(8.dp)
            ) {
                if (dataPoints.isEmpty()) {
                    Text(
                        text = "Waiting for data...",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val range = maxY - minY
                        
                        val path = Path()
                        val stepX = width / 100f // max history size is 100

                        dataPoints.forEachIndexed { index, value ->
                            val coercedValue = value.coerceIn(minY, maxY)
                            val normalizedY = 1f - ((coercedValue - minY) / range)
                            val x = index * stepX
                            val y = normalizedY * height

                            if (index == 0) {
                                path.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }
                        }

                        drawPath(
                            path = path,
                            color = dynamicColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

