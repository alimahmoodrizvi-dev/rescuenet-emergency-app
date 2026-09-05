@file:OptIn(ExperimentalMaterial3Api::class)

package com.rescuenet.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rescuenet.app.data.model.Severity
import com.rescuenet.app.data.viewmodel.EmergencyReportViewModel
import com.rescuenet.app.data.viewmodel.ReportSendState
import com.rescuenet.app.ui.components.SeverityChip
import com.rescuenet.app.ui.components.SimulationTag

@Composable
fun AiSummaryScreen(viewModel: EmergencyReportViewModel, onContinue: () -> Unit) {
    val summary by viewModel.aiSummary.collectAsState()
    val sendState by viewModel.sendState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("AI Incident Summary") }) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {

            if (sendState == ReportSendState.AnalyzingWithAi || summary == null) {
                Spacer(Modifier.height(60.dp))
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Structuring your report…", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }

            val s = summary!!

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Psychology, contentDescription = null)
                if (s.isSimulated) SimulationTag()
            }

            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SummaryRow("Incident Type", s.type.name.replace('_', ' '))
                    SummaryRowSeverity(s.severity)
                    SummaryRow("People", s.peopleCount.toString())
                    SummaryRow("Injuries", if (s.injuriesPresent) "Yes" else "No / Unknown")
                    SummaryRow("Needed", if (s.requiredResources.isEmpty()) "Not specified" else s.requiredResources.joinToString { it.name.replace('_', ' ') })
                    SummaryRow("AI Confidence", "${s.confidencePercent}%")
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "This is AI-assisted, not a medical or emergency diagnosis. Review the fields above — they're editable on the previous screens if anything looks wrong.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Confirm & Continue", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SummaryRowSeverity(severity: Severity) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Severity", style = MaterialTheme.typography.bodyMedium)
        SeverityChip(severity)
    }
}

@Composable
fun ReportStatusScreen(viewModel: EmergencyReportViewModel, onDone: () -> Unit) {
    val sendState by viewModel.sendState.collectAsState()

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (sendState) {
                ReportSendState.QueuedOffline -> {
                    Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Report Queued", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No internet right now. Your report is saved on this phone and will relay through nearby RescueNet devices, then sync automatically once connectivity returns.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                ReportSendState.Synced -> {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Report Delivered", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("The command center has received your report.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                else -> {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Sending…", style = MaterialTheme.typography.headlineMedium)
                }
            }
            Spacer(Modifier.height(32.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Back to Home") }
        }
    }
}
