@file:OptIn(ExperimentalMaterial3Api::class)

package com.rescuenet.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rescuenet.app.data.model.InjuryLevel
import com.rescuenet.app.data.model.ResourceNeed
import com.rescuenet.app.data.viewmodel.EmergencyReportViewModel

private fun injuryLabel(level: InjuryLevel) = when (level) {
    InjuryLevel.NONE -> "No"
    InjuryLevel.MINOR -> "Minor"
    InjuryLevel.SERIOUS -> "Serious"
    InjuryLevel.CRITICAL -> "Critical"
    InjuryLevel.UNKNOWN -> "Unknown"
}

private fun needLabel(need: ResourceNeed) = when (need) {
    ResourceNeed.AMBULANCE -> "Ambulance"
    ResourceNeed.RESCUE_TEAM -> "Rescue Team"
    ResourceNeed.FIRE_SERVICE -> "Fire Service"
    ResourceNeed.FOOD -> "Food"
    ResourceNeed.WATER -> "Water"
    ResourceNeed.SHELTER -> "Shelter"
    ResourceNeed.EVACUATION -> "Evacuation"
    ResourceNeed.MEDICAL_ASSISTANCE -> "Medical Assistance"
    ResourceNeed.OTHER -> "Other"
}

@Composable
fun NeedHelpDetailsScreen(
    viewModel: EmergencyReportViewModel,
    onNext: () -> Unit,
) {
    val draft by viewModel.draft.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("A few quick details") }) },
        bottomBar = {
            Button(
                onClick = onNext,
                enabled = draft.injuryLevel != null,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp)
            ) { Text("Continue", style = MaterialTheme.typography.titleMedium) }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(20.dp)) {

            Text("How many people need help?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.setPeopleCount(draft.peopleCount - 1) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                }
                Text("${draft.peopleCount}", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 16.dp))
                IconButton(onClick = { viewModel.setPeopleCount(draft.peopleCount + 1) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase")
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Are there injuries?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InjuryLevel.entries.forEach { level ->
                    FilterChip(
                        selected = draft.injuryLevel == level,
                        onClick = { viewModel.setInjuryLevel(level) },
                        label = { Text(injuryLabel(level)) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("What do you need?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(ResourceNeed.entries.chunked(2)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        row.forEach { need ->
                            FilterChip(
                                selected = need in draft.needs,
                                onClick = { viewModel.toggleNeed(need) },
                                label = { Text(needLabel(need)) }
                            )
                        }
                    }
                }
            }
        }
    }
}
