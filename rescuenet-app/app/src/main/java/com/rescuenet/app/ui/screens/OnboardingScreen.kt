@file:OptIn(ExperimentalMaterial3Api::class)

package com.rescuenet.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.rescuenet.app.data.viewmodel.OnboardingViewModel

private data class OnboardStep(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val body: String)

private val steps = listOf(
    OnboardStep(Icons.Filled.LocationOn, "Location", "Used only when you report an emergency or share your safety status. You control when it's shared."),
    OnboardStep(Icons.Filled.Bluetooth, "Nearby Devices", "Lets your emergency reports relay through nearby phones when the internet is down."),
    OnboardStep(Icons.Filled.Mic, "Microphone", "Optional — for voice emergency reports in English or Urdu."),
    OnboardStep(Icons.Filled.Notifications, "Notifications", "Alerts you to nearby emergencies and family safety updates."),
)

/** Permissions requested match exactly what's declared in AndroidManifest.xml for the
 *  offline mesh (Part 3) and reporting (Part 5) features — requested once, up front, with
 *  a plain-language reason shown before the system dialog appears. */
private fun runtimePermissions(): List<String> {
    val perms = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        perms += Manifest.permission.BLUETOOTH_SCAN
        perms += Manifest.permission.BLUETOOTH_ADVERTISE
        perms += Manifest.permission.BLUETOOTH_CONNECT
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        perms += Manifest.permission.POST_NOTIFICATIONS
        perms += Manifest.permission.NEARBY_WIFI_DEVICES
    }
    return perms
}

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var languageIsUrdu by remember { mutableStateOf(false) }
    var permissionsRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Denied permissions degrade gracefully per Part 29 (e.g. no GPS fix attached, no
        // mesh relay) rather than blocking the app — nothing else to do with the result here.
        permissionsRequested = true
    }

    Scaffold(
        bottomBar = {
            Button(
                onClick = {
                    if (!permissionsRequested) permissionLauncher.launch(runtimePermissions().toTypedArray())
                    viewModel.completeOnboarding(if (languageIsUrdu) "ur" else "en", onDone)
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp)
            ) { Text("Continue", style = MaterialTheme.typography.titleMedium) }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Text("Welcome to RescueNet", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(4.dp))
            Text("A few permissions help RescueNet work even without internet.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Language", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilterChip(selected = !languageIsUrdu, onClick = { languageIsUrdu = false }, label = { Text("English") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = languageIsUrdu, onClick = { languageIsUrdu = true }, label = { Text("اردو") })
            }

            Spacer(Modifier.height(20.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(steps) { step ->
                    ElevatedCard {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(step.icon, contentDescription = null, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(step.title, style = MaterialTheme.typography.titleMedium)
                                Text(step.body, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
