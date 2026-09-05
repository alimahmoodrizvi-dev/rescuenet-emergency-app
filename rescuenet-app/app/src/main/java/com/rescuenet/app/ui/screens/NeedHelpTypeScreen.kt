@file:OptIn(ExperimentalMaterial3Api::class)

package com.rescuenet.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rescuenet.app.data.model.IncidentType
import com.rescuenet.app.data.viewmodel.EmergencyReportViewModel
import com.rescuenet.app.ui.components.SecondaryActionCard

private val typeIcons: Map<IncidentType, androidx.compose.ui.graphics.vector.ImageVector> = mapOf(
    IncidentType.MEDICAL to Icons.Filled.MedicalServices,
    IncidentType.FIRE to Icons.Filled.LocalFireDepartment,
    IncidentType.FLOOD to Icons.Filled.Water,
    IncidentType.EARTHQUAKE to Icons.Filled.Landslide,
    IncidentType.ACCIDENT to Icons.Filled.CarCrash,
    IncidentType.TRAPPED to Icons.Filled.DoorFront,
    IncidentType.MISSING_PERSON to Icons.Filled.PersonSearch,
    IncidentType.BUILDING_COLLAPSE to Icons.Filled.Domain,
    IncidentType.SECURITY to Icons.Filled.Shield,
    IncidentType.OTHER to Icons.Filled.MoreHoriz,
)

private fun label(type: IncidentType) = when (type) {
    IncidentType.MEDICAL -> "Medical Emergency"
    IncidentType.FIRE -> "Fire"
    IncidentType.FLOOD -> "Flood"
    IncidentType.EARTHQUAKE -> "Earthquake"
    IncidentType.ACCIDENT -> "Accident"
    IncidentType.TRAPPED -> "Trapped Person"
    IncidentType.MISSING_PERSON -> "Missing Person"
    IncidentType.BUILDING_COLLAPSE -> "Building Collapse"
    IncidentType.SECURITY -> "Security Emergency"
    IncidentType.OTHER -> "Other"
}

@Composable
fun NeedHelpTypeScreen(
    viewModel: EmergencyReportViewModel,
    onVoiceMode: () -> Unit,
    onNext: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("What's happening?") }) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp)) {
            OutlinedButton(onClick = onVoiceMode, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Mic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Speak instead (English / Urdu)")
            }
            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(IncidentType.entries) { type ->
                    SecondaryActionCard(
                        icon = typeIcons.getValue(type),
                        label = label(type),
                        onClick = {
                            viewModel.setType(type)
                            onNext()
                        }
                    )
                }
            }
        }
    }
}
