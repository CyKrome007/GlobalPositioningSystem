package com.CyKrome007.GlobalPositioningSystem

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import com.CyKrome007.GlobalPositioningSystem.ui.theme.GlobalPositioningSystemTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission is granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        Configuration.getInstance().load(this, getSharedPreferences(packageName, MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        setContent {
            GlobalPositioningSystemTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
            MainScreen(this)
                }
            }
        }
    }
}

@Composable
fun MainScreen(context: Context) {
    var selectedLocation by remember { mutableStateOf<GeoPoint?>(null) }
    val favoritesManager = remember { FavoritesManager(context) }
    var showFavorites by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var isSpoofingActive by remember {
        mutableStateOf(
            try {
                val prefs = context.getSharedPreferences("LocationSpoofer", Context.MODE_PRIVATE)
                val enabled = prefs.getBoolean("spoofing_enabled", false)
                val lat = prefs.getString("latitude", null)
                val lon = prefs.getString("longitude", null)
                logD("GPS", "Initializing isSpoofingActive: enabled=$enabled, lat=$lat, lon=$lon")
                
                // Also check if file exists and log its content
                try {
                    val prefsFile = java.io.File(context.applicationInfo.dataDir + "/shared_prefs/LocationSpoofer.xml")
                    logD("GPS", "Preferences file on init: exists=${prefsFile.exists()}, canRead=${prefsFile.canRead()}, path=${prefsFile.absolutePath}")
                    if (prefsFile.exists()) {
                        try {
                            val content = prefsFile.readText()
                            logD("GPS", "Preferences file content on init: $content")
                        } catch (e: Exception) {
                            logE("GPS", "Failed to read preferences file on init: ${e.message}", e)
                        }
                    }
                } catch (e: Exception) {
                    logE("GPS", "Error checking preferences file on init: ${e.message}", e)
                }
                
                enabled
            } catch (e: Exception) {
                logE("GPS", "Error reading spoofing state on init: ${e.message}, ${e.javaClass.name}", e)
                false
            }
        )
    }
    var showAddFavoriteDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !showFavorites,
            enter = fadeIn() + slideInHorizontally(),
            exit = fadeOut() + slideOutHorizontally()
        ) {
            MapScreen(
                context = context,
                selectedLocation = selectedLocation,
                isSpoofingActive = isSpoofingActive,
                onLocationSelected = { selectedLocation = it },
                onSearch = { selectedLocation = it },
                onStartSpoofing = {
                    selectedLocation?.let {
                        // Use MODE_WORLD_READABLE for XSharedPreferences compatibility
                        // This is deprecated but necessary for Xposed modules to read preferences
                        // No root required - the Xposed module runs with system privileges
                        val prefs = try {
                            @Suppress("DEPRECATION")
                            context.getSharedPreferences("LocationSpoofer", Context.MODE_WORLD_READABLE)
                        } catch (e: SecurityException) {
                            // MODE_WORLD_READABLE throws SecurityException on Android 7.0+ in some cases
                            // Fallback to MODE_PRIVATE - XSharedPreferences should still be able to read it
                            // when running in system_server context
                            logW("GPS", "MODE_WORLD_READABLE not available, using MODE_PRIVATE: ${e.message}")
                            context.getSharedPreferences("LocationSpoofer", Context.MODE_PRIVATE)
                        } catch (e: Exception) {
                            logW("GPS", "Error getting preferences, using MODE_PRIVATE: ${e.message}")
                            context.getSharedPreferences("LocationSpoofer", Context.MODE_PRIVATE)
                        }
                        
                        val editor = prefs.edit()
                        editor.putString("latitude", it.latitude.toString())
                        editor.putString("longitude", it.longitude.toString())
                        editor.putBoolean("spoofing_enabled", true)
                        val committed = editor.commit() // Use commit() for immediate write
                        
                        logD("GPS", "Spoofing STARTED - Lat=${it.latitude}, Lon=${it.longitude}, Committed=$committed")
                        
                        // Wait a moment for file to be written, then set permissions
                        // Use a coroutine to avoid blocking UI
                        coroutineScope.launch(Dispatchers.IO) {
                            delay(100) // Small delay to ensure file is written
                            
                            // Make preferences file readable for XSharedPreferences
                            // Try both Java file permissions and root chmod for maximum compatibility
                            try {
                                val prefsFile = java.io.File(context.applicationInfo.dataDir + "/shared_prefs/LocationSpoofer.xml")
                                
                                // Retry a few times if file doesn't exist yet
                                var retries = 5
                                while (!prefsFile.exists() && retries > 0) {
                                    delay(50)
                                    retries--
                                }
                                
                                if (prefsFile.exists()) {
                                    // First, try Java file permissions (works without root on some systems)
                                    val readable = prefsFile.setReadable(true, false) // readable by all
                                    val writable = prefsFile.setWritable(true, true) // writable by owner only
                                    
                                    logD("GPS", "Java setReadable result: readable=$readable, writable=$writable")
                                    
                                    // Then try root chmod for guaranteed permissions (if root is available)
                                    val rootSuccess = setFilePermissionsWithRoot(prefsFile.absolutePath, "644")
                                    
                                    // Verify final permissions
                                    logD("GPS", "Preferences file: exists=${prefsFile.exists()}, canRead=${prefsFile.canRead()}, canWrite=${prefsFile.canWrite()}, rootChmod=$rootSuccess, path=${prefsFile.absolutePath}")
                                    
                                    // Verify the file contents
                                    try {
                                        val content = prefsFile.readText()
                                        logD("GPS", "Preferences file content: $content")
                                    } catch (e: Exception) {
                                        logE("GPS", "Failed to read preferences file: ${e.message}", e)
                                    }
                                } else {
                                    logE("GPS", "Preferences file does not exist after commit: ${prefsFile.absolutePath}")
                                    // Try to list directory to see what files exist
                                    try {
                                        val prefsDir = java.io.File(context.applicationInfo.dataDir + "/shared_prefs/")
                                        if (prefsDir.exists()) {
                                            val files = prefsDir.listFiles()
                                            val fileNames = files?.map { it.name } ?: emptyList()
                                            logD("GPS", "shared_prefs directory exists, files: $fileNames")
                                        } else {
                                            logE("GPS", "shared_prefs directory does not exist: ${prefsDir.absolutePath}")
                                        }
                                    } catch (e: Exception) {
                                        logE("GPS", "Error listing shared_prefs directory: ${e.message}", e)
                                    }
                                }
                            } catch (e: Exception) {
                                logE("GPS", "Failed to set preferences file permissions: ${e.message}", e)
                            }
                        }
                        
                        isSpoofingActive = true
                        showToast(context, "Location spoofing activated!")
                    }
                },
                onStopSpoofing = {
                    val prefs = context.getSharedPreferences("LocationSpoofer", Context.MODE_PRIVATE)
                    val committed = prefs.edit().putBoolean("spoofing_enabled", false).commit()
                    logD("GPS", "Spoofing STOPPED - Committed=$committed")
                    isSpoofingActive = false
                    showToast(context, "Location spoofing deactivated!")
                },
                onShowFavorites = { showFavorites = true },
                onAddToFavoritesClick = { showAddFavoriteDialog = true }
            )
        }

        AnimatedVisibility(
            visible = showFavorites,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it })
        ) {
            FavoritesScreen(
                favoritesManager = favoritesManager,
                onFavoriteSelected = {
                    selectedLocation = it
                    showFavorites = false
                },
                onBack = { showFavorites = false }
            )
        }

        if (showAddFavoriteDialog) {
            AddFavoriteDialog(
                onDismiss = { showAddFavoriteDialog = false },
                onConfirm = { name ->
                    selectedLocation?.let {
                        favoritesManager.addFavorite(name, it)
                        showToast(context, "Added to favorites!")
                        showAddFavoriteDialog = false
                    }
                }
            )
        }
    }
}

// Data class to hold location with name
data class LocationResult(
    val location: GeoPoint,
    val name: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    context: Context,
    selectedLocation: GeoPoint?,
    isSpoofingActive: Boolean,
    onLocationSelected: (GeoPoint) -> Unit,
    onSearch: (GeoPoint) -> Unit,
    onStartSpoofing: () -> Unit,
    onStopSpoofing: () -> Unit,
    onShowFavorites: () -> Unit,
    onAddToFavoritesClick: () -> Unit
) {
    var showSearchSuggestions by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    val searchSuggestions = remember { mutableStateListOf<LocationResult>() }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        // Map View
        InteractiveMapView(
            modifier = Modifier.fillMaxSize(),
            selectedLocation = selectedLocation,
            onLocationSelected = onLocationSelected
        )

        // Top App Bar
        TopAppBar(
            title = { Text("Global Positioning System") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Search Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .offset(y = 56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        if (query.isNotBlank() && query.length > 2) {
                            isSearching = true
                            coroutineScope.launch {
                                // Debounce search
                                delay(500)
                                if (searchQuery == query) { // Only search if query hasn't changed
                                    performSearch(query) { results ->
                                        searchSuggestions.clear()
                                        if (results.isNotEmpty()) {
                                            searchSuggestions.addAll(results)
                                            showSearchSuggestions = true
                                        } else {
                                            showSearchSuggestions = false
                                        }
                                        isSearching = false
                                    }
                                }
                            }
                        } else {
                            showSearchSuggestions = false
                            searchSuggestions.clear()
                            isSearching = false
                        }
                    },
                    label = { Text("Search location") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { 
                                searchQuery = ""
                                showSearchSuggestions = false
                                searchSuggestions.clear()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Search Suggestions
                AnimatedVisibility(
                    visible = showSearchSuggestions && searchSuggestions.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp)
                        ) {
                            items(searchSuggestions.size) { index ->
                                SearchSuggestionItem(
                                    locationResult = searchSuggestions[index],
                                    onClick = {
                                        onSearch(searchSuggestions[index].location)
                                        searchQuery = ""
                                        showSearchSuggestions = false
                                        searchSuggestions.clear()
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Show message if search returned no results
                if (!isSearching && searchQuery.length > 2 && !showSearchSuggestions && searchSuggestions.isEmpty()) {
                    Text(
                        text = "No locations found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp, start = 12.dp)
                    )
                }
            }
        }

        // Bottom Action Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Spoofing Toggle Button
            val buttonScale by animateFloatAsState(
                targetValue = if (isSpoofingActive) 1.05f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )

            Button(
                onClick = if (isSpoofingActive) onStopSpoofing else onStartSpoofing,
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(buttonScale),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSpoofingActive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = if (isSpoofingActive) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = if (isSpoofingActive) "Stop Spoofing" else "Start Spoofing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onShowFavorites,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Favorites")
                }

                OutlinedButton(
                    onClick = onAddToFavoritesClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = selectedLocation != null
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Add")
                }
            }

            // Location Info Card
            AnimatedVisibility(
                visible = selectedLocation != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Selected Location",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        selectedLocation?.let {
                            Text(
                                text = "Lat: ${String.format("%.6f", it.latitude)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Lon: ${String.format("%.6f", it.longitude)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchSuggestionItem(locationResult: LocationResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = locationResult.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${String.format("%.4f", locationResult.location.latitude)}, ${String.format("%.4f", locationResult.location.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
    HorizontalDivider()
}

@Composable
fun InteractiveMapView(
    modifier: Modifier = Modifier,
    selectedLocation: GeoPoint?,
    onLocationSelected: (GeoPoint) -> Unit
) {
    val mapView = rememberMapViewWithLifecycle()
    var currentMarker by remember { mutableStateOf<Marker?>(null) }

    LaunchedEffect(selectedLocation) {
        selectedLocation?.let { location ->
            currentMarker?.let { mapView.overlays.remove(it) }
            val marker = Marker(mapView).apply {
                position = location
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Selected Location"
            }
            mapView.overlays.add(marker)
            currentMarker = marker
            mapView.controller.animateTo(location)
            mapView.invalidate()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier) { view ->
        view.setTileSource(TileSourceFactory.MAPNIK)
        view.setMultiTouchControls(true)
        view.controller.setZoom(15.0)
        view.controller.setCenter(GeoPoint(51.5074, -0.1278))

        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let {
                    onLocationSelected(it)
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let {
                    onLocationSelected(it)
                }
                return true
            }
        }

        val overlay = MapEventsOverlay(receiver)
        view.overlays.add(0, overlay)
    }
}

@Composable
fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
        }
    }
    return mapView
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favoritesManager: FavoritesManager,
    onFavoriteSelected: (GeoPoint) -> Unit,
    onBack: () -> Unit
) {
    val favorites = remember { mutableStateListOf<Pair<String, GeoPoint>>() }
    
    LaunchedEffect(Unit) {
        favorites.clear()
        favorites.addAll(favoritesManager.getFavorites())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favorites") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "No favorites yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favorites, key = { it.first }) { (name, location) ->
                    FavoriteCard(
                        name = name,
                        location = location,
                        onClick = { onFavoriteSelected(location) },
                        onDelete = {
                            favoritesManager.removeFavorite(name)
                            favorites.removeAll { it.first == name }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FavoriteCard(
    name: String,
    location: GeoPoint,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AddFavoriteDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var favoriteName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Favorites") },
        text = {
            OutlinedTextField(
                value = favoriteName,
                onValueChange = { favoriteName = it },
                label = { Text("Location name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(favoriteName) },
                enabled = favoriteName.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun performSearch(query: String, onResults: (List<LocationResult>) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // Set user agent for Nominatim (required)
            System.setProperty("http.agent", "GlobalPositioningSystem/1.0")
            
            val geocoder = org.osmdroid.bonuspack.location.GeocoderNominatim("GlobalPositioningSystem/1.0")
            
            // Try multiple search strategies for typo tolerance
            var addresses: List<android.location.Address>? = null
            
            // Strategy 1: Direct search
            addresses = withTimeoutOrNull(8000) {
                try {
                    geocoder.getFromLocationName(query, 5)
                } catch (e: Exception) {
                    null
                }
            }
            
            // Strategy 2: If no results, try with fuzzy matching (add ~ for fuzzy search in Nominatim)
            if (addresses.isNullOrEmpty()) {
                addresses = withTimeoutOrNull(8000) {
                    try {
                        // Nominatim supports fuzzy matching with ~ prefix or by using different query format
                        geocoder.getFromLocationName("$query~", 5)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            
            // Strategy 3: Try with common typo variations
            if (addresses.isNullOrEmpty() && query.length > 3) {
                // Try with Levenshtein-like variations (swap adjacent chars, common typos)
                val variations = generateTypoVariations(query)
                for (variation in variations.take(3)) { // Limit to 3 variations to avoid too many requests
                    addresses = withTimeoutOrNull(5000) {
                        try {
                            geocoder.getFromLocationName(variation, 5)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (!addresses.isNullOrEmpty()) break
                }
            }
            
            if (addresses != null && addresses.isNotEmpty()) {
                val results = addresses.mapNotNull { address ->
                    try {
                        val location = GeoPoint(address.latitude, address.longitude)
                        // Extract location name from address
                        val name = extractLocationName(address)
                        LocationResult(location, name)
                    } catch (e: Exception) {
                        null
                    }
                }
                withContext(Dispatchers.Main) {
                    onResults(results)
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResults(emptyList())
                }
            }
        } catch (e: Exception) {
            // Log error for debugging
            logE("LocationSearch", "Search error: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onResults(emptyList())
            }
        }
    }
}

// Extract a readable location name from Address object
private fun extractLocationName(address: android.location.Address): String {
    // Try to get the most descriptive name
    val featureName = address.featureName
    val locality = address.locality
    val adminArea = address.adminArea
    val countryName = address.countryName
    
    // Build a readable address string
    val parts = mutableListOf<String>()
    
    if (!featureName.isNullOrBlank() && featureName != locality) {
        parts.add(featureName)
    }
    if (!locality.isNullOrBlank()) {
        parts.add(locality)
    }
    if (!adminArea.isNullOrBlank() && adminArea != locality) {
        parts.add(adminArea)
    }
    if (!countryName.isNullOrBlank()) {
        parts.add(countryName)
    }
    
    // If we have address lines, use the first one
    if (parts.isEmpty() && address.maxAddressLineIndex >= 0) {
        val addressLine = address.getAddressLine(0)
        if (!addressLine.isNullOrBlank()) {
            return addressLine
        }
    }
    
    return parts.joinToString(", ").takeIf { it.isNotBlank() } 
        ?: "${address.latitude}, ${address.longitude}"
}

// Generate common typo variations for fuzzy matching
private fun generateTypoVariations(query: String): List<String> {
    val variations = mutableListOf<String>()
    val lowerQuery = query.lowercase()
    
    // Swap adjacent characters (common typo: "zatok" -> "zotak")
    for (i in 0 until lowerQuery.length - 1) {
        val chars = lowerQuery.toCharArray()
        val temp = chars[i]
        chars[i] = chars[i + 1]
        chars[i + 1] = temp
        variations.add(String(chars))
    }
    
    // Common character substitutions
    val substitutions = mapOf(
        'a' to listOf('e', 'o', 'i'),
        'e' to listOf('a', 'i', 'o'),
        'i' to listOf('e', 'a', 'o'),
        'o' to listOf('a', 'e', 'u'),
        'u' to listOf('o', 'a'),
        'z' to listOf('s'),
        's' to listOf('z'),
        'c' to listOf('k'),
        'k' to listOf('c')
    )
    
    // Try one character substitution
    for ((char, replacements) in substitutions) {
        if (lowerQuery.contains(char)) {
            for (replacement in replacements) {
                variations.add(lowerQuery.replaceFirst(char, replacement))
            }
        }
    }
    
    return variations.distinct()
}
