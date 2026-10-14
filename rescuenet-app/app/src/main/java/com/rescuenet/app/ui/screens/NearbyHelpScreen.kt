@file:OptIn(ExperimentalMaterial3Api::class)

package com.rescuenet.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rescuenet.app.data.remote.NearbyPlaceDto
import com.rescuenet.app.data.viewmodel.NearbyHelpViewModel
import com.rescuenet.app.data.viewmodel.NearbyUiState
import com.rescuenet.app.ui.components.HelpLoading
import com.rescuenet.app.ui.components.HelpProblem
import com.rescuenet.app.ui.components.LOCATION_PERMISSIONS
import com.rescuenet.app.ui.components.categoryIcon
import com.rescuenet.app.ui.components.dialIntent
import com.rescuenet.app.ui.components.formatDistanceKm
import com.rescuenet.app.ui.components.openExternally
import com.rescuenet.app.ui.theme.SeverityHigh

private const val ALL = "All"

@Composable
fun NearbyHelpScreen(
    onGetRoute: (NearbyPlaceDto) -> Unit,
    viewModel: NearbyHelpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var filter by rememberSaveable { mutableStateOf(ALL) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refresh() }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nearby Help") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize()) {
            when (val s = state) {
                is NearbyUiState.Loading -> HelpLoading("Finding help near your location…")
                is NearbyUiState.Failed -> HelpProblem(
                    kind = s.kind,
                    message = s.message,
                    onRetry = { viewModel.refresh() },
                    onGrantPermission = { permissionLauncher.launch(LOCATION_PERMISSIONS) },
                )
                is NearbyUiState.Loaded -> NearbyList(
                    state = s,
                    filter = filter,
                    onFilterChange = { filter = it },
                    onGetRoute = onGetRoute,
                )
            }
        }
    }
}

@Composable
private fun NearbyList(
    state: NearbyUiState.Loaded,
    filter: String,
    onFilterChange: (String) -> Unit,
    onGetRoute: (NearbyPlaceDto) -> Unit,
) {
    val categories = listOf(ALL) + state.places.map { it.category }.distinct()
    val shown = if (filter == ALL) state.places else state.places.filter { it.category == filter }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                "Showing help within ${state.radiusKm.toInt()} km of your location " +
                    "(${String.format("%.4f", state.origin.latitude)}, ${String.format("%.4f", state.origin.longitude)}, " +
                    "accurate to about ${state.origin.accuracyMeters.toInt()} m).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!state.osmAvailable) {
            item {
                // Never present a partial list as if it were complete.
                Text(
                    "The public map data service couldn't be reached, so this list may be missing " +
                        "places. Use Refresh above to try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SeverityHigh,
                )
            }
        }
        if (categories.size > 2) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { category ->
                        FilterChip(
                            selected = filter == category,
                            onClick = { onFilterChange(category) },
                            label = { Text(category) },
                        )
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (state.places.isEmpty()) {
                        "No help found within ${state.radiusKm.toInt()} km of you. If you're in danger, " +
                            "call your local emergency number."
                    } else {
                        "Nothing in this category nearby."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        items(shown, key = { it.id }) { place ->
            PlaceRow(place, onGetRoute = { onGetRoute(place) })
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PlaceRow(place: NearbyPlaceDto, onGetRoute: () -> Unit) {
    val context = LocalContext.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(categoryIcon(place.category), contentDescription = place.category)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(place.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${place.category} · ${place.source}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(formatDistanceKm(place.distanceKm), style = MaterialTheme.typography.titleMedium)
            }
            place.detail?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onGetRoute) {
                    Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Route")
                }
                place.phone?.let { phone ->
                    OutlinedButton(onClick = { openExternally(context, dialIntent(phone)) }) {
                        Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Call")
                    }
                }
            }
        }
    }
}
