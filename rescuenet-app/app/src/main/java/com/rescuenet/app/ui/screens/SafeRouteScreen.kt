@file:OptIn(ExperimentalMaterial3Api::class)

package com.rescuenet.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rescuenet.app.ui.components.SimulationTag
import com.rescuenet.app.ui.theme.SeverityCritical
import com.rescuenet.app.ui.theme.SeveritySafeGreen

private data class RouteOption(
    val label: String, val distanceKm: Double, val minutes: Int, val risk: String, val riskColor: androidx.compose.ui.graphics.Color, val recommended: Boolean
)

@Composable
fun SafeRouteScreen() {
    val options = listOf(
        RouteOption("Route A", 6.2, 14, "HIGH RISK", SeverityCritical, recommended = false),
        RouteOption("Route B", 7.1, 17, "LOW RISK", SeveritySafeGreen, recommended = true),
    )

    Scaffold(topBar = { TopAppBar(title = { Text("Safe Route") }) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Route, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("We prioritize the safest route, not just the shortest.", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            SimulationTag("Simulated hazard data — demo")
            Spacer(Modifier.height(16.dp))

            options.forEach { option ->
                Card(
                    border = if (option.recommended) BorderStroke(2.dp, SeveritySafeGreen) else null,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(option.label, style = MaterialTheme.typography.titleLarge)
                            if (option.recommended) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SeveritySafeGreen, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("RECOMMENDED", color = SeveritySafeGreen, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("${option.distanceKm} km — ${option.minutes} minutes", style = MaterialTheme.typography.bodyLarge)
                        Text(option.risk, color = option.riskColor, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Text(
                "No known hazard is currently recorded for the recommended route. Conditions may change — this is guidance, not a guarantee.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
