package com.localnvr.dashboard

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localnvr.dashboard.ui.theme.LocalNvrTheme
import com.localnvr.dashboard.ui.theme.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember {
                getSharedPreferences("local-nvr", MODE_PRIVATE)
            }
            var themeModeString by rememberSaveable {
                mutableStateOf(preferences.getString("theme-mode", "system") ?: "system")
            }
            val themeMode = when (themeModeString) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }

            LocalNvrTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(
                        themeModeString = themeModeString,
                        onThemeModeChange = { newMode ->
                            themeModeString = newMode
                            preferences.edit().putString("theme-mode", newMode).apply()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(
    themeModeString: String,
    onThemeModeChange: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = LocalFocusManager.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val preferences = remember {
        context.getSharedPreferences("local-nvr", android.content.Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    var serverAddress by rememberSaveable { mutableStateOf(preferences.getString("server-address", "").orEmpty()) }
    var connectedServer by rememberSaveable { mutableStateOf<String?>(null) }
    var cameras by remember { mutableStateOf<List<Camera>>(emptyList()) }
    var selectedCameraId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedGridSize by rememberSaveable { mutableIntStateOf(4) } // Default grid count: 4 per page
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    var isServerAccordionExpanded by rememberSaveable { mutableStateOf(true) }
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    var showReorderDialog by rememberSaveable { mutableStateOf(false) }
    var jumpingCamera by remember { mutableStateOf<Camera?>(null) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    // Map storing persistent movable player composables for each camera
    val playerContentMap = remember { mutableMapOf<String, @Composable (Modifier) -> Unit>() }

    // Clear player content cache when server connection changes
    LaunchedEffect(connectedServer) {
        playerContentMap.clear()
    }

    fun getPlayerContent(camera: Camera, serverUrl: String, isCompact: Boolean): @Composable (Modifier) -> Unit {
        return playerContentMap.getOrPut(camera.id) {
            movableContentOf { modifier: Modifier ->
                WhepPlayer(
                    serverUrl = serverUrl,
                    camera = camera,
                    isCompact = isCompact,
                    modifier = modifier
                )
            }
        }
    }

    fun applySavedOrder(loaded: List<Camera>): List<Camera> {
        val savedOrder = preferences.getString("camera-order", null) ?: return loaded
        val idList = savedOrder.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (idList.isEmpty()) return loaded
        val orderMap = idList.withIndex().associate { it.value to it.index }
        return loaded.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
    }

    fun moveCamera(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in cameras.indices || toIndex !in cameras.indices || fromIndex == toIndex) return
        val mutable = cameras.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        cameras = mutable
        val newOrderString = mutable.joinToString(",") { it.id }
        preferences.edit().putString("camera-order", newOrderString).apply()
    }

    fun resetCameraOrder() {
        preferences.edit().remove("camera-order").apply()
        if (connectedServer != null) {
            scope.launch {
                try {
                    val loaded = DashboardApi.getCameras(connectedServer.orEmpty()).filter { camera ->
                        val cleanName = camera.name.trim().lowercase()
                        cleanName != "no camera" && !cleanName.startsWith("no camera")
                    }
                    cameras = loaded
                } catch (_: Exception) {}
            }
        }
    }

    fun loadCameras(address: String) {
        focusManager.clearFocus()
        scope.launch {
            loading = true
            message = null
            try {
                val normalized = DashboardApi.normalizeServerUrl(address)
                val loaded = DashboardApi.getCameras(normalized).filter { camera ->
                    val cleanName = camera.name.trim().lowercase()
                    cleanName != "no camera" && !cleanName.startsWith("no camera")
                }
                preferences.edit().putString("server-address", normalized).apply()
                serverAddress = normalized
                connectedServer = normalized
                cameras = applySavedOrder(loaded)
                currentPage = 0
                // Collapse accordion automatically when connected to give full room to cameras
                isServerAccordionExpanded = false
                message = if (loaded.isEmpty()) {
                    "No valid cameras are configured yet. Add cameras from the web dashboard first."
                } else {
                    null
                }
            } catch (error: Exception) {
                connectedServer = null
                cameras = emptyList()
                isServerAccordionExpanded = true
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
    val activeCamera = cameras.find { it.id == selectedCameraId }

    val totalPages = if (cameras.isEmpty()) 0 else (cameras.size + selectedGridSize - 1) / selectedGridSize
    val safePage = currentPage.coerceIn(0, maxOf(0, totalPages - 1))
    val displayCameras = if (cameras.isEmpty()) emptyList() else cameras.drop(safePage * selectedGridSize).take(selectedGridSize)
    val visibleCameraIds = remember(displayCameras) { displayCameras.map { it.id }.toSet() }

    // Offscreen Keep-Alive Container: Keeps loaded off-page camera streams active in background
    if (activeServer != null) {
        Box(
            modifier = Modifier
                .size(0.dp)
                .clipToBounds()
        ) {
            cameras.forEach { camera ->
                if (camera.id !in visibleCameraIds && camera.id in playerContentMap) {
                    val player = getPlayerContent(camera, activeServer, selectedGridSize >= 4)
                    player(Modifier.size(1.dp))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Videocam,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Local NVR",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Camera Monitoring",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    var showThemeMenu by remember { mutableStateOf(false) }

                    // Theme Selector Action
                    Box {
                        IconButton(
                            onClick = { showThemeMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = when (themeModeString) {
                                    "light" -> Icons.Rounded.LightMode
                                    "dark" -> Icons.Rounded.DarkMode
                                    else -> Icons.Rounded.BrightnessAuto
                                },
                                contentDescription = "Theme Mode",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("System Default") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.BrightnessAuto,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (themeModeString == "system") {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    onThemeModeChange("system")
                                    showThemeMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Light Mode") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.LightMode,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (themeModeString == "light") {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    onThemeModeChange("light")
                                    showThemeMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Dark Mode") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.DarkMode,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (themeModeString == "dark") {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    onThemeModeChange("dark")
                                    showThemeMenu = false
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Connected Status Pill
                    Surface(
                        color = if (connectedServer != null) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = CircleShape,
                        modifier = Modifier.clickable {
                            // Toggle accordion on status click
                            isServerAccordionExpanded = !isServerAccordionExpanded
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        color = if (connectedServer != null) Color(0xFF10B981) else Color(0xFF94A3B8),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (connectedServer != null) "Connected" else "Offline",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (connectedServer != null) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        // Single Camera Focused Stream Detail View
        if (activeServer != null && activeCamera != null) {
            val playerContent = getPlayerContent(activeCamera, activeServer, false)
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Panel (Back Button & Camera Info)
                    Card(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { selectedCameraId = null },
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                                CircleShape
                                            )
                                            .size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = "Back to Grid",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Back to Grid",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = activeCamera.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = activeCamera.ip,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "CHANNEL ${activeCamera.channel}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Right Panel (Full Height Video Player)
                    playerContent(
                        Modifier
                            .weight(0.65f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Back Button & Camera Title Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { selectedCameraId = null },
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        CircleShape
                                    )
                                    .size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back to Grid",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeCamera.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = activeCamera.ip,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = " • ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "CH${activeCamera.channel}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Video Player Component
                    playerContent(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )
                }
            }
            return@Scaffold
        }

        // Main Dashboard Multi-Camera View (ALWAYS 2 cameras per row in portrait for grid sizes 4, 16, 32)
        val columnsCount = when {
            selectedGridSize == 1 -> 1
            isLandscape -> when (selectedGridSize) {
                4 -> 2
                16 -> 4
                32 -> 4
                else -> 2
            }
            else -> 2 // 2 cameras per row for 4, 16, and 32 in portrait
        }

        val isCompactGrid = selectedGridSize >= 4

        val itemSpacing = when {
            selectedGridSize >= 16 -> 6.dp
            selectedGridSize == 4 -> 8.dp
            else -> 10.dp
        }

        val gridState = rememberLazyGridState()

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnsCount),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = if (selectedGridSize >= 16) 10.dp else 16.dp,
                vertical = 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            // Server Connection Card (ACCORDION)
            item(span = { GridItemSpan(maxLineSpan) }) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column {
                        // Accordion Header Bar (Clickable)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isServerAccordionExpanded = !isServerAccordionExpanded }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Dashboard Server",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!isServerAccordionExpanded && connectedServer != null) {
                                        Text(
                                            text = connectedServer.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { isServerAccordionExpanded = !isServerAccordionExpanded },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isServerAccordionExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (isServerAccordionExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Accordion Body (Collapsible)
                        AnimatedVisibility(
                            visible = isServerAccordionExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = serverAddress,
                                    onValueChange = { serverAddress = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Server Address") },
                                    placeholder = { Text("http://100.x.y.z:3001") },
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Lan,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { loadCameras(serverAddress) }
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { loadCameras(serverAddress) },
                                        enabled = !loading && serverAddress.isNotBlank(),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        if (loading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Connecting…")
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Connect")
                                        }
                                    }

                                    if (connectedServer != null) {
                                        OutlinedButton(
                                            onClick = { loadCameras(connectedServer.orEmpty()) },
                                            enabled = !loading,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = "Refresh",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Connect over Tailscale mesh. Keep Tailscale active.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                message?.let { msg ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Warning,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = msg,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Camera Section Header with 1 / 4 / 16 / 32 Selector & Arrangement Toggle
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GridView,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Live Grid",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            if (cameras.isNotEmpty()) {
                                Surface(
                                    color = if (isEditMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { isEditMode = !isEditMode }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isEditMode) Icons.Rounded.CheckCircle else Icons.Rounded.SwapVert,
                                            contentDescription = "Arrange Layout",
                                            tint = if (isEditMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isEditMode) "Done" else "Arrange",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isEditMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (isEditMode) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { showReorderDialog = true },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Tune,
                                            contentDescription = "Reorder List",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Grid Size Selector (1, 4, 16, 32)
                        GridSelectorRow(
                            selectedSize = selectedGridSize,
                            options = listOf(1, 4, 16, 32),
                            onSelect = { size ->
                                selectedGridSize = size
                                currentPage = 0
                            }
                        )
                    }

                    if (isEditMode) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "💡 Tip: Drag cards, tap ⇡ to jump to top, or tap position # to enter exact rank",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Camera Grid View or Empty State
            if (cameras.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(32.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.VideocamOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (connectedServer != null) "No Cameras Found" else "Not Connected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (connectedServer != null) {
                                    "Add cameras from your NVR web dashboard to start streaming."
                                } else {
                                    "Enter your server address above and click Connect to load configured cameras."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            } else {
                // Grid containing visible cameras for current page with persistent keep-alive players
                items(displayCameras, key = { it.id }) { camera ->
                    val positionIndex = cameras.indexOf(camera)
                    val playerContent = getPlayerContent(camera, activeServer.orEmpty(), isCompactGrid)
                    GridCameraItem(
                        camera = camera,
                        positionIndex = positionIndex,
                        totalCount = cameras.size,
                        columnsCount = columnsCount,
                        isEditMode = isEditMode,
                        isCompact = isCompactGrid,
                        playerContent = playerContent,
                        onMoveToTop = { moveCamera(positionIndex, 0) },
                        onMoveUp = { moveCamera(positionIndex, maxOf(0, positionIndex - columnsCount)) },
                        onMoveDown = { moveCamera(positionIndex, minOf(cameras.size - 1, positionIndex + columnsCount)) },
                        onMoveLeft = { moveCamera(positionIndex, maxOf(0, positionIndex - 1)) },
                        onMoveRight = { moveCamera(positionIndex, minOf(cameras.size - 1, positionIndex + 1)) },
                        onJumpToPosition = { jumpingCamera = camera },
                        onExpand = { selectedCameraId = camera.id }
                    )
                }

                // Single Pagination Bar at the Bottom of the Camera Grid
                if (totalPages > 1) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                                enabled = safePage > 0,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Previous Page",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Prev", style = MaterialTheme.typography.labelMedium)
                            }

                            Text(
                                text = "Page ${safePage + 1} of $totalPages (${displayCameras.size} of ${cameras.size} cameras)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            OutlinedButton(
                                onClick = { currentPage = (currentPage + 1).coerceAtMost(totalPages - 1) },
                                enabled = safePage < totalPages - 1,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Next", style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Next Page",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dialog to move camera to specific position number (e.g. Move #32 to #1)
        if (jumpingCamera != null) {
            val targetCam = jumpingCamera!!
            val currentIdx = cameras.indexOf(targetCam)
            var inputPosText by remember { mutableStateOf("${currentIdx + 1}") }

            AlertDialog(
                onDismissRequest = { jumpingCamera = null },
                title = {
                    Text(
                        text = "Set Camera Position",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "${targetCam.name}\nCurrently position #${currentIdx + 1} of ${cameras.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = inputPosText,
                            onValueChange = { inputPosText = it.filter { char -> char.isDigit() } },
                            label = { Text("Target Rank (1 - ${cameras.size})") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { inputPosText = "1" },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Text("Top (#1)", style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = { inputPosText = "${(cameras.size / 2).coerceAtLeast(1)}" },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Text("Mid (#${(cameras.size / 2).coerceAtLeast(1)})", style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = { inputPosText = "${cameras.size}" },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Text("End (#${cameras.size})", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val pos = inputPosText.toIntOrNull() ?: (currentIdx + 1)
                            val targetIndex = (pos - 1).coerceIn(0, cameras.size - 1)
                            if (currentIdx != -1) {
                                moveCamera(currentIdx, targetIndex)
                            }
                            jumpingCamera = null
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Move")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { jumpingCamera = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Dialog for full list reordering & resetting order
        if (showReorderDialog) {
            AlertDialog(
                onDismissRequest = { showReorderDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Reorder Cameras",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = { resetCameraOrder() }) {
                            Text("Reset", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                text = {
                    val density = LocalDensity.current
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(cameras, key = { _, cam -> cam.id }) { index, camera ->
                            var dragOffsetY by remember { mutableFloatStateOf(0f) }
                            var isDragging by remember { mutableStateOf(false) }
                            var lastDragTime by remember { mutableLongStateOf(0L) }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        translationY = if (isDragging) dragOffsetY else 0f
                                        shadowElevation = if (isDragging) 8f else 0f
                                    }
                                    .pointerInput(index, cameras.size) {
                                        detectDragGestures(
                                            onDragStart = { isDragging = true },
                                            onDragEnd = {
                                                isDragging = false
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                isDragging = false
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount.y
                                                val thresholdPx = with(density) { 35.dp.toPx() }
                                                val now = System.currentTimeMillis()

                                                if (now - lastDragTime > 70L) {
                                                    if (dragOffsetY > thresholdPx && index < cameras.size - 1) {
                                                        moveCamera(index, index + 1)
                                                        dragOffsetY -= thresholdPx
                                                        lastDragTime = now
                                                    } else if (dragOffsetY < -thresholdPx && index > 0) {
                                                        moveCamera(index, index - 1)
                                                        dragOffsetY += thresholdPx
                                                        lastDragTime = now
                                                    }
                                                }
                                            }
                                        )
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragHandle,
                                            contentDescription = "Drag to reorder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .padding(end = 4.dp)
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .clickable { jumpingCamera = camera }
                                                .size(22.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${index + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = camera.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "CH${camera.channel} • ${camera.ip}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (index > 0) {
                                            IconButton(
                                                onClick = { moveCamera(index, 0) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.VerticalAlignTop,
                                                    contentDescription = "Move to Top (#1)",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { moveCamera(index, index - 1) },
                                            enabled = index > 0,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ArrowUpward,
                                                contentDescription = "Move Up",
                                                tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { moveCamera(index, index + 1) },
                                            enabled = index < cameras.size - 1,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ArrowDownward,
                                                contentDescription = "Move Down",
                                                tint = if (index < cameras.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showReorderDialog = false },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Done")
                    }
                }
            )
        }
    }
}

@Composable
private fun GridSelectorRow(
    selectedSize: Int,
    options: List<Int>,
    onSelect: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { size ->
                val isSelected = size == selectedSize
                Surface(
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onSelect(size) }
                ) {
                    Text(
                        text = "$size",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GridCameraItem(
    camera: Camera,
    positionIndex: Int,
    totalCount: Int,
    columnsCount: Int,
    isEditMode: Boolean,
    isCompact: Boolean,
    playerContent: @Composable (Modifier) -> Unit,
    onMoveToTop: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onJumpToPosition: () -> Unit,
    onExpand: () -> Unit
) {
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var lastDragTime by remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current

    LaunchedEffect(camera.id, positionIndex) {
        dragOffsetX = 0f
        dragOffsetY = 0f
        isDragging = false
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = if (isDragging) dragOffsetX else 0f
                translationY = if (isDragging) dragOffsetY else 0f
                shadowElevation = if (isDragging) 12f else 2f
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
            }
            .then(
                if (isEditMode) {
                    Modifier.pointerInput(positionIndex, totalCount, columnsCount) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = {
                                isDragging = false
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                isDragging = false
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX += dragAmount.x
                                dragOffsetY += dragAmount.y
                                val thresholdPx = with(density) { 38.dp.toPx() }
                                val now = System.currentTimeMillis()

                                if (now - lastDragTime > 75L) {
                                    if (dragOffsetY < -thresholdPx) {
                                        onMoveUp()
                                        dragOffsetY += thresholdPx
                                        lastDragTime = now
                                    } else if (dragOffsetY > thresholdPx) {
                                        onMoveDown()
                                        dragOffsetY -= thresholdPx
                                        lastDragTime = now
                                    } else if (dragOffsetX > thresholdPx) {
                                        onMoveRight()
                                        dragOffsetX -= thresholdPx
                                        lastDragTime = now
                                    } else if (dragOffsetX < -thresholdPx) {
                                        onMoveLeft()
                                        dragOffsetX += thresholdPx
                                        lastDragTime = now
                                    }
                                }
                            }
                        )
                    }
                } else Modifier
            )
            .clip(RoundedCornerShape(if (isCompact) 10.dp else 14.dp)),
        shape = RoundedCornerShape(if (isCompact) 10.dp else 14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (isEditMode) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = if (isDragging) 8.dp else 2.dp
    ) {
        Column {
            // Streamlined Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(
                        horizontal = if (isCompact) 6.dp else 8.dp,
                        vertical = if (isCompact) 3.dp else 5.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditMode) {
                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(if (isCompact) 16.dp else 18.dp)
                                .padding(end = 2.dp)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onJumpToPosition() }
                                .padding(end = 4.dp)
                        ) {
                            Text(
                                text = "#${positionIndex + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = if (isCompact) 9.sp else 11.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = camera.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isCompact) 11.sp else 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "CH${camera.channel} • ${camera.ip}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = if (isCompact) 9.sp else 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isEditMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        if (positionIndex > 0) {
                            IconButton(
                                onClick = onMoveToTop,
                                modifier = Modifier.size(if (isCompact) 24.dp else 28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VerticalAlignTop,
                                    contentDescription = "Move to Top (#1)",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(if (isCompact) 14.dp else 16.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onMoveLeft,
                            enabled = positionIndex > 0,
                            modifier = Modifier.size(if (isCompact) 24.dp else 28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Move Left",
                                tint = if (positionIndex > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(if (isCompact) 14.dp else 16.dp)
                            )
                        }

                        IconButton(
                            onClick = onMoveRight,
                            enabled = positionIndex < totalCount - 1,
                            modifier = Modifier.size(if (isCompact) 24.dp else 28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Move Right",
                                tint = if (positionIndex < totalCount - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(if (isCompact) 14.dp else 16.dp)
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onExpand,
                        modifier = Modifier.size(if (isCompact) 22.dp else 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = "Focus Camera",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(if (isCompact) 16.dp else 18.dp)
                        )
                    }
                }
            }

            // Live Stream Player (Persistent Keep-Alive Player)
            playerContent(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
        }
    }
}
