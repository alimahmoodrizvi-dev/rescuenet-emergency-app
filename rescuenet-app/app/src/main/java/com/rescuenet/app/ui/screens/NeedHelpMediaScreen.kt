@file:OptIn(ExperimentalMaterial3Api::class)

package com.rescuenet.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rescuenet.app.data.viewmodel.EmergencyReportViewModel

@Composable
fun NeedHelpMediaScreen(
    viewModel: EmergencyReportViewModel,
    isOffline: Boolean,
    onSend: () -> Unit,
) {
    val draft by viewModel.draft.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Anything else? (optional)") }) },
        bottomBar = {
            Button(
                onClick = {
                    viewModel.requestAiSummary(isOffline)
                    onSend()
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp)
            ) { Text("Send Emergency Report", style = MaterialTheme.typography.titleMedium) }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(20.dp)) {
            Text(
                "You don't need to fill this out. Every field is optional — the details you already gave are enough to send help.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = draft.description,
                onValueChange = viewModel::setDescription,
                label = { Text("Describe what's happening (any language)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { /* capture photo — Phase 3 */ }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Photo")
                }
                OutlinedButton(onClick = { /* record voice — Phase 3 */ }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Mic, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Voice Note")
                }
            }
        }
    }
}
