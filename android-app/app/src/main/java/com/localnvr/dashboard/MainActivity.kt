package com.localnvr.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DashboardScreen()
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("local-nvr", android.content.Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    var serverAddress by remember { mutableStateOf(preferences.getString("server-address", "").orEmpty()) }
    var connectedServer by remember { mutableStateOf<String?>(null) }
    var cameras by remember { mutableStateOf<List<Camera>>(emptyList()) }
    var selectedCamera by remember { mutableStateOf<Camera?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun loadCameras(address: String) {
        scope.launch {
            loading = true
            message = null
            try {
                val normalized = DashboardApi.normalizeServerUrl(address)
                val loaded = DashboardApi.getCameras(normalized)
                preferences.edit().putString("server-address", normalized).apply()
                serverAddress = normalized
                connectedServer = normalized
                cameras = loaded
                message = if (loaded.isEmpty()) {
                    "No cameras are configured yet. Add cameras from the web dashboard first."
                } else {
                    null
                }
            } catch (error: Exception) {
                connectedServer = null
                cameras = emptyList()
                message = error.message ?: "Could not connect to the dashboard."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (serverAddress.isNotBlank()) loadCameras(serverAddress)
    }

    val activeServer = connectedServer
    val activeCamera = selectedCamera
    if (activeServer != null && activeCamera != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            TextButton(onClick = { selectedCamera = null }) {
                Text("← Cameras")
            }
            Text(activeCamera.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "${activeCamera.ip} · CH${activeCamera.channel} · ${activeCamera.stream.uppercase()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            WhepPlayer(activeServer, activeCamera)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Local NVR", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = serverAddress,
            onValueChange = { serverAddress = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Dashboard address") },
            placeholder = { Text("http://100.x.y.z:3001") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { loadCameras(serverAddress) },
                enabled = !loading && serverAddress.isNotBlank(),
            ) {
                Text(if (loading) "Connecting…" else "Connect")
            }
            if (connectedServer != null) {
                TextButton(
                    onClick = { loadCameras(connectedServer.orEmpty()) },
                    enabled = !loading,
                ) {
                    Text("Refresh")
                }
            }
        }
        Text(
            "Connect to your dashboard over Tailscale. Keep Tailscale connected on this phone.",
            style = MaterialTheme.typography.bodySmall,
        )
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Text("Cameras", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (cameras.isEmpty()) {
            Text("Connect to the dashboard to load its configured cameras.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cameras, key = { it.id }) { camera ->
                    CameraRow(camera) { selectedCamera = camera }
                }
            }
        }
    }
}

@Composable
private fun CameraRow(camera: Camera, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(camera.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${camera.ip} · CH${camera.channel} · ${camera.stream.uppercase()}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
