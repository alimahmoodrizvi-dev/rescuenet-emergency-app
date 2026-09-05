@file:OptIn(ExperimentalMaterial3Api::class)

package com.rescuenet.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rescuenet.app.data.viewmodel.HomeViewModel
import com.rescuenet.app.ui.components.ImSafeButton
import com.rescuenet.app.ui.components.NeedHelpButton
import com.rescuenet.app.ui.components.NetworkStatusBanner
import com.rescuenet.app.ui.components.SecondaryActionCard

@Composable
fun HomeScreen(
    onNeedHelp: () -> Unit,
    onImSafe: () -> Unit,
    onMyLocation: () -> Unit,
    onNearbyHelp: () -> Unit,
    onSafeRoute: () -> Unit,
    onNetworkStatus: () -> Unit,
    onFamily: () -> Unit,
    onSettings: () -> Unit,
    onDemoMode: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val networkStatus by viewModel.networkStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RescueNet") },
                actions = {
                    IconButton(onClick = onFamily) { Icon(Icons.Filled.Groups, contentDescription = "Family") }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            NetworkStatusBanner(
                internetConnected = networkStatus.internetConnected,
                nearbyNodes = networkStatus.nearbyNodeCount,
                lastSyncLabel = networkStatus.lastSyncLabel,
                modifier = Modifier.clickable(onClick = onNetworkStatus)
            )

            Spacer(Modifier.height(20.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                NeedHelpButton(onClick = onNeedHelp)
            }

            Spacer(Modifier.height(20.dp))

            ImSafeButton(onClick = onImSafe)

            Spacer(Modifier.height(20.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(220.dp)
            ) {
                items(quickActions(onMyLocation, onNearbyHelp, onSafeRoute, onDemoMode)) { action ->
                    SecondaryActionCard(icon = action.icon, label = action.label, onClick = action.onClick)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class QuickAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val onClick: () -> Unit
)

private fun quickActions(
    onMyLocation: () -> Unit,
    onNearbyHelp: () -> Unit,
    onSafeRoute: () -> Unit,
    onDemoMode: () -> Unit,
) = listOf(
    QuickAction(Icons.Filled.MyLocation, "My Location", onMyLocation),
    QuickAction(Icons.Filled.LocalHospital, "Nearby Help", onNearbyHelp),
    QuickAction(Icons.Filled.Map, "Safe Route", onSafeRoute),
    QuickAction(Icons.Filled.PlayCircle, "Demo Mode", onDemoMode),
)
