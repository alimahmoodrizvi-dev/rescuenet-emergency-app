@file:OptIn(ExperimentalMaterial3Api::class)

package com.rescuenet.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rescuenet.app.data.viewmodel.HomeViewModel
import com.rescuenet.app.ui.components.NetworkStatusBanner

@Composable
fun NetworkStatusScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val status by viewModel.networkStatus.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Network Status") }) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp)) {
            NetworkStatusBanner(status.internetConnected, status.nearbyNodeCount, status.lastSyncLabel)
            Spacer(Modifier.height(20.dp))
            Text(
                "RescueNet relays emergency messages between nearby phones over Bluetooth and Wi-Fi Direct when the internet is unavailable. " +
                        "This works over short distances — roughly tens to a couple hundred meters between devices — and multiple phones can relay a " +
                        "message hop by hop. It is not a substitute for cellular or satellite range.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Messages are encrypted so relaying phones can route them without reading their contents. Once any device in the chain reaches the internet, all queued reports sync automatically.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun SettingsScreen(
    darkMode: Boolean, onDarkModeChange: (Boolean) -> Unit,
    largeText: Boolean, onLargeTextChange: (Boolean) -> Unit,
    highContrast: Boolean, onHighContrastChange: (Boolean) -> Unit,
    backendBaseUrl: String, onBackendBaseUrlChange: (String) -> Unit,
) {
    var urlDraft by remember(backendBaseUrl) { mutableStateOf(backendBaseUrl) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 20.dp)) {
            SettingsSwitchRow("Dark mode", darkMode, onDarkModeChange)
            SettingsSwitchRow("Large text", largeText, onLargeTextChange)
            SettingsSwitchRow("High contrast", highContrast, onHighContrastChange)

            Spacer(Modifier.height(24.dp))
            Text("Developer / Demo", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Backend URL — point this at wherever the RescueNet backend is running. " +
                        "Defaults to the emulator's alias for your computer's localhost; on a " +
                        "physical device, use your computer's LAN IP instead (e.g. http://192.168.1.20:8000/).",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = urlDraft,
                onValueChange = { urlDraft = it },
                label = { Text("Backend base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onBackendBaseUrlChange(urlDraft) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }

            Spacer(Modifier.height(24.dp))
            Text("Privacy", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Location is only shared when you send a report or an \"I'm Safe\" update. " +
                        "Reports are stored locally, encrypted, and kept only as long as needed for response and safety records. " +
                        "You can review or delete your data at any time.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
