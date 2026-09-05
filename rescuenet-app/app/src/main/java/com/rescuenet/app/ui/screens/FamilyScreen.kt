@file:OptIn(ExperimentalMaterial3Api::class)

package com.rescuenet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rescuenet.app.data.model.FamilyMember
import com.rescuenet.app.data.model.SafetyStatus
import com.rescuenet.app.data.viewmodel.HomeViewModel
import com.rescuenet.app.ui.theme.SeverityCritical
import com.rescuenet.app.ui.theme.SeveritySafeGreen
import com.rescuenet.app.ui.theme.SeverityUnknownGray

@Composable
fun FamilyScreen(viewModel: HomeViewModel = hiltViewModel(), onAddContact: () -> Unit) {
    val members by viewModel.familyMembers.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("My Family") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddContact) { Icon(Icons.Filled.Add, contentDescription = "Add trusted contact") }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(members) { member -> FamilyMemberRow(member) }
        }
    }
}

@Composable
private fun FamilyMemberRow(member: FamilyMember) {
    val (color, label) = when (member.status) {
        SafetyStatus.SAFE -> SeveritySafeGreen to "SAFE"
        SafetyStatus.NEEDS_HELP -> SeverityCritical to "NEEDS HELP"
        SafetyStatus.UNKNOWN -> SeverityUnknownGray to "UNKNOWN"
    }
    // NEEDS_HELP is the single most important thing this screen can tell someone — a
    // screen-reader user should be proactively told the moment a family member's status
    // changes to it, not only if they happen to be re-reading this row (Part 18).
    val liveRegionMode = if (member.status == SafetyStatus.NEEDS_HELP) LiveRegionMode.Assertive else LiveRegionMode.Polite

    ElevatedCard(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "${member.name}: $label. Updated ${member.lastUpdatedLabel}."
            liveRegion = liveRegionMode
        }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(member.name, style = MaterialTheme.typography.titleMedium)
                Text("Updated ${member.lastUpdatedLabel}", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(label, color = color, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
