@file:OptIn(ExperimentalMaterial3Api::class)

package com.rescuenet.app.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.rescuenet.app.data.model.RouteDestination
import com.rescuenet.app.data.remote.RouteOptionDto
import com.rescuenet.app.data.remote.SafeRouteDto
import com.rescuenet.app.data.viewmodel.RouteUiState
import com.rescuenet.app.data.viewmodel.SafeRouteViewModel
import com.rescuenet.app.ui.components.HelpLoading
import com.rescuenet.app.ui.components.HelpProblem
import com.rescuenet.app.ui.components.LOCATION_PERMISSIONS
import com.rescuenet.app.ui.components.formatDistanceKm
import com.rescuenet.app.ui.components.formatDuration
import com.rescuenet.app.ui.components.formatMetres
import com.rescuenet.app.ui.components.openExternally
import com.rescuenet.app.ui.theme.SeverityCritical
import com.rescuenet.app.ui.theme.SeveritySafeGreen
import kotlinx.serialization.json.Json

// The route is handed to the bundled map page (assets/route_map.html) as the server's own JSON.
private val mapJson = Json { encodeDefaults = true }

private const val MODE_DRIVING = "driving"
private const val MODE_WALKING = "walking"

@Composable
fun SafeRouteScreen(
    destination: RouteDestination?,
    onDestinationChange: (RouteDestination?) -> Unit,
    viewModel: SafeRouteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val choices by viewModel.choices.collectAsState()
    var mode by rememberSaveable { mutableStateOf(MODE_DRIVING) }
    var refreshKey by remember { mutableStateOf(0) }
    var showChooser by remember { mutableStateOf(false) }
    // Which of the offered routes is shown; resets to the recommended one whenever a new route loads.
    var selectedIndex by remember(state) { mutableStateOf(0) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshKey += 1 }

    // Recalculates from the phone's current position whenever the destination, travel mode,
    // or the refresh button changes.
    LaunchedEffect(destination, mode, refreshKey) { viewModel.load(destination, mode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safe Route") },
                actions = {
                    IconButton(onClick = { refreshKey += 1 }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Recalculate route from my location")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == MODE_DRIVING,
                        onClick = { mode = MODE_DRIVING },
                        label = { Text("Drive") },
                    )
                    FilterChip(
                        selected = mode == MODE_WALKING,
                        onClick = { mode = MODE_WALKING },
                        label = { Text("Walk") },
                    )
                }
            }
            when (val s = state) {
                is RouteUiState.Loading -> item {
                    HelpLoading("Working out the safest route from where you are…")
                }
                is RouteUiState.Failed -> item {
                    HelpProblem(
                        kind = s.kind,
                        message = s.message,
                        onRetry = { refreshKey += 1 },
                        onGrantPermission = { permissionLauncher.launch(LOCATION_PERMISSIONS) },
                    )
                }
                is RouteUiState.Loaded -> routeContent(
                    route = s.route,
                    selectedIndex = selectedIndex,
                    userChoseDestination = destination != null,
                    context = context,
                    onSelect = { selectedIndex = it },
                    onChangeDestination = {
                        showChooser = true
                        viewModel.loadChoices()
                    },
                    onUseNearest = { onDestinationChange(null) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showChooser) {
        AlertDialog(
            onDismissRequest = { showChooser = false },
            title = { Text("Choose a safe place") },
            text = {
                if (choices.isEmpty()) {
                    Text("No shelters, assembly points or hospitals found near you.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(choices, key = { it.id }) { place ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDestinationChange(
                                            RouteDestination(place.name, place.category, place.latitude, place.longitude)
                                        )
                                        showChooser = false
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(place.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${place.category} · ${formatDistanceKm(place.distanceKm)} away" +
                                        (place.detail?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChooser = false }) { Text("Close") } },
        )
    }
}

private fun LazyListScope.routeContent(
    route: SafeRouteDto,
    selectedIndex: Int,
    userChoseDestination: Boolean,
    context: Context,
    onSelect: (Int) -> Unit,
    onChangeDestination: () -> Unit,
    onUseNearest: () -> Unit,
) {
    val options: List<RouteOptionDto> = listOf(route.recommended) + route.alternatives
    val selected = options.getOrElse(selectedIndex) { route.recommended }

    // Where this route is heading, and how that was decided.
    item {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Heading to", style = MaterialTheme.typography.labelMedium)
                Text(route.destination.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (route.destination.chosenAutomatically) {
                        "${route.destination.category} · the nearest safe area, chosen automatically"
                    } else {
                        "The destination you chose"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onChangeDestination) { Text("Change destination") }
                    if (userChoseDestination) {
                        TextButton(onClick = onUseNearest) { Text("Use nearest safe area") }
                    }
                }
            }
        }
    }

    // The offered routes. Only shown when there is a real choice.
    if (options.size > 1) {
        itemsIndexed(options) { index, option ->
            val isSelected = index == selectedIndex
            val riskColor = if (option.risk == "CLEAR") SeveritySafeGreen else SeverityCritical
            OutlinedCard(
                onClick = { onSelect(index) },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) riskColor else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        (if (index == 0) "Safest route" else "Other option $index") +
                            " · ${formatDuration(option.durationS)} · ${formatMetres(option.distanceM)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(option.label, style = MaterialTheme.typography.bodySmall, color = riskColor)
                }
            }
        }
    }

    // Summary of the route currently shown.
    item {
        val riskColor = if (selected.risk == "CLEAR") SeveritySafeGreen else SeverityCritical
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${formatDuration(selected.durationS)} · ${formatMetres(selected.distanceM)} " +
                    "by ${if (route.mode == MODE_WALKING) "foot" else "road"}",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(selected.label, style = MaterialTheme.typography.bodyMedium, color = riskColor)
        }
    }

    // The full route on a real map, from the phone's location to the destination.
    item {
        RouteMap(route = route, selectedIndex = selectedIndex, modifier = Modifier.fillMaxWidth().height(340.dp))
    }

    item {
        Button(
            onClick = {
                val travelMode = if (route.mode == MODE_WALKING) "walking" else "driving"
                val uri = Uri.parse(
                    "https://www.google.com/maps/dir/?api=1" +
                        "&origin=${route.originLat},${route.originLng}" +
                        "&destination=${route.destination.latitude},${route.destination.longitude}" +
                        "&travelmode=$travelMode"
                )
                openExternally(context, Intent(Intent.ACTION_VIEW, uri))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open in Maps for turn-by-turn navigation")
        }
    }

    item { Text("Directions", style = MaterialTheme.typography.titleMedium) }
    itemsIndexed(selected.steps) { index, step ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text("${index + 1}.", modifier = Modifier.width(32.dp), style = MaterialTheme.typography.bodyMedium)
            Column {
                Text(step.instruction, style = MaterialTheme.typography.bodyMedium)
                if (step.distanceM > 0) {
                    Text(
                        formatMetres(step.distanceM),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    item {
        Text(
            route.caveat,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The route drawn on an OpenStreetMap map. This is a small bundled web page (Leaflet) shown in
 * a WebView rather than a map SDK, so it needs no API key, no extra dependency, and no Google
 * Play Services map component — the page itself is part of the app (assets/route_map.html).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RouteMap(route: SafeRouteDto, selectedIndex: Int, modifier: Modifier = Modifier) {
    var pageReady by remember { mutableStateOf(false) }
    val payload = remember(route) { mapJson.encodeToString(SafeRouteDto.serializer(), route) }
    val webViewHolder = remember { arrayOfNulls<WebView>(1) }

    DisposableEffect(Unit) {
        onDispose {
            webViewHolder[0]?.destroy()
            webViewHolder[0] = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            WebView(viewContext).apply {
                settings.javaScriptEnabled = true
                // OpenStreetMap's tile servers ask apps to identify themselves.
                settings.userAgentString = settings.userAgentString + " RescueNet/1.0"
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        pageReady = true
                    }
                }
                webViewHolder[0] = this
                loadUrl("file:///android_asset/route_map.html")
            }
        },
        update = { webView ->
            // Re-draws whenever the route, the selected option, or the page becoming ready changes.
            if (pageReady) {
                webView.evaluateJavascript("window.renderRoute($payload, $selectedIndex);", null)
            }
        },
    )
}
