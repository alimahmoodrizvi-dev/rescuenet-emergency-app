@file:OptIn(ExperimentalMaterial3Api::class)

package com.rescuenet.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rescuenet.app.data.model.NearbyHelpPlace
import com.rescuenet.app.data.viewmodel.HomeViewModel

private fun categoryIcon(category: String) = when (category) {
    "Hospital" -> Icons.Filled.LocalHospital
    "Shelter" -> Icons.Filled.Cabin
    "Police" -> Icons.Filled.LocalPolice
    "Fire" -> Icons.Filled.LocalFireDepartment
    else -> Icons.Filled.Place
}

@Composable
fun NearbyHelpScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val places by viewModel.nearbyHelp.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Nearby Help") }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(places) { place -> PlaceRow(place) }
        }
    }
}

@Composable
private fun PlaceRow(place: NearbyHelpPlace) {
    ElevatedCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(categoryIcon(place.category), contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                Text(place.category, style = MaterialTheme.typography.bodyMedium)
            }
            Text("${place.distanceKm} km", style = MaterialTheme.typography.labelLarge)
        }
    }
}
