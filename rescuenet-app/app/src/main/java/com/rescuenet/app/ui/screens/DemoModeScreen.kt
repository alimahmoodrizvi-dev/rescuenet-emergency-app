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
import com.rescuenet.app.data.viewmodel.DemoModeViewModel
import com.rescuenet.app.data.viewmodel.HomeViewModel
import com.rescuenet.app.ui.components.SimulationTag

private data class DemoStep(val title: String, val detail: String)

private val scenarioSteps = listOf(
    DemoStep("1. Karachi Flood begins", "Scenario loaded. Internet starts CONNECTED."),
    DemoStep("2. Citizen presses Need Help", "Flood selected, 6 people, mother injured."),
    DemoStep("3. Urdu voice report", "AI structures the report — see the real parsed result below."),
    DemoStep("4. Internet fails", "Network banner flips to OFFLINE (display simulation — see DemoRunbook.md)."),
    DemoStep("5. Offline relay", "A real EmergencyIncident row is created and queued via IncidentRepository."),
    DemoStep("6. Internet returns", "Sync is forced immediately — watch the Command Center's Incidents page."),
    DemoStep("7. Command center ingest", "Open the Command Center — the incident should already be there."),
    DemoStep("8. AI cluster detected", "Two more nearby reports are created; run 'Clustering' on the Command Center."),
    DemoStep("9. Resource recommended", "On the Command Center, open the incident and click 'Get recommendation'."),
    DemoStep("10. Safe route shown", "Back in the app: Home → Safe Route (simulated hazard data, clearly labeled)."),
    DemoStep("11. Family dashboard updates", "Ali flips to NEEDS HELP (local demo write — see DemoRunbook.md)."),
)

@Composable
fun DemoModeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    demoViewModel: DemoModeViewModel = hiltViewModel(),
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val demoState by demoViewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Demo Mode — Karachi Flood")
                    Spacer(Modifier.width(8.dp))
                    SimulationTag()
                }
            })
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(20.dp)) {
            Text(
                "This walkthrough performs real actions against this device's local database, " +
                        "and — when the backend is reachable — the real RescueNet backend and Command " +
                        "Center. Steps that remain display-only simulations are labeled below and " +
                        "explained fully in DemoRunbook.md.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { (currentStep + 1) / scenarioSteps.size.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(scenarioSteps.size) { index ->
                    val step = scenarioSteps[index]
                    val isActive = index == currentStep
                    val isDone = index < currentStep
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isDone) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(step.title, style = MaterialTheme.typography.titleMedium)
                                    Text(step.detail, style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            if (isActive) {
                                DemoStepLiveDetail(index, demoState)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (demoState.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        currentStep = 0
                        demoViewModel.reset()
                        homeViewModel.simulateInternetRecovery()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Reset") }

                Button(
                    onClick = {
                        val next = (currentStep + 1).coerceAtMost(scenarioSteps.lastIndex)
                        when (next) {
                            2 -> demoViewModel.runAiParsingStep()
                            3 -> homeViewModel.simulateInternetFailure()
                            4 -> demoViewModel.createDemoIncident()
                            5 -> {
                                homeViewModel.simulateInternetRecovery()
                                demoViewModel.triggerImmediateSync()
                            }
                            7 -> demoViewModel.seedNearbyIncidentsForClustering()
                            10 -> demoViewModel.markAliNeedsHelp()
                        }
                        currentStep = next
                    },
                    enabled = !demoState.busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Next Step") }
            }
        }
    }
}

@Composable
private fun DemoStepLiveDetail(stepIndex: Int, state: DemoModeViewModel.DemoState) {
    when (stepIndex) {
        2 -> state.aiParsedSummary?.let { s ->
            Spacer(Modifier.height(8.dp))
            Text(
                "AI parsed → ${s.type}, severity ${s.severity}, ${s.peopleCount} people, " +
                        "injuries: ${s.injuriesPresent}, confidence ${s.confidencePercent}%" +
                        if (s.isSimulated) " (SIMULATION)" else " (live backend)",
                style = MaterialTheme.typography.labelLarge
            )
        }
        4 -> state.createdEventUuid?.let { id ->
            Spacer(Modifier.height(8.dp))
            Text("Created incident locally: ${id.take(8)}…", style = MaterialTheme.typography.labelLarge)
        }
        7 -> if (state.clusterSeedEventUuids.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Seeded ${state.clusterSeedEventUuids.size} additional nearby reports for clustering.", style = MaterialTheme.typography.labelLarge)
        }
        else -> {}
    }
}
