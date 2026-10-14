@file:OptIn(ExperimentalMaterial3Api::class)

package com.rescuenet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rescuenet.app.data.model.FamilyMember
import com.rescuenet.app.data.model.SafetyStatus
import com.rescuenet.app.data.repository.AddMemberResult
import com.rescuenet.app.data.viewmodel.HomeViewModel
import com.rescuenet.app.ui.theme.SeverityCritical
import com.rescuenet.app.ui.theme.SeveritySafeGreen
import com.rescuenet.app.ui.theme.SeverityUnknownGray

@Composable
fun FamilyScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val members by viewModel.familyMembers.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<FamilyMember?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("My Family") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add family member")
            }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "Family members you add are saved on this phone. Their safe / needs-help status " +
                        "can't update automatically yet — linking to a family member's own phone isn't " +
                        "available in this version.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (members.isEmpty()) {
                item {
                    Text(
                        "No family members yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            items(members, key = { it.id }) { member ->
                FamilyMemberRow(member, onRemove = { memberToRemove = member })
            }
            item { Spacer(Modifier.height(72.dp)) } // keep the last row clear of the + button
        }
    }

    if (showAddDialog) {
        AddFamilyMemberDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, onResult -> viewModel.addFamilyMember(name, onResult) },
        )
    }

    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Remove ${member.name}?") },
            text = { Text("They'll be taken off your family list on this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFamilyMember(member.id)
                    memberToRemove = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { memberToRemove = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AddFamilyMemberDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, onResult: (AddMemberResult) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add family member") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("Name") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { message -> { Text(message) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(name) { result ->
                    when (result) {
                        AddMemberResult.ADDED -> onDismiss()
                        AddMemberResult.EMPTY -> error = "Enter a name."
                        AddMemberResult.DUPLICATE -> error = "That name is already on your list."
                    }
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FamilyMemberRow(member: FamilyMember, onRemove: () -> Unit) {
    val (color, label) = when (member.status) {
        SafetyStatus.SAFE -> SeveritySafeGreen to "SAFE"
        SafetyStatus.NEEDS_HELP -> SeverityCritical to "NEEDS HELP"
        SafetyStatus.UNKNOWN -> SeverityUnknownGray to "UNKNOWN"
    }
    // NEEDS_HELP is the single most important thing this screen can tell someone — a
    // screen-reader user should be proactively told the moment a family member's status
    // changes to it, not only if they happen to be re-reading this row (Part 18).
    val liveRegionMode = if (member.status == SafetyStatus.NEEDS_HELP) LiveRegionMode.Assertive else LiveRegionMode.Polite

    ElevatedCard {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${member.name}: $label. Updated ${member.lastUpdatedLabel}."
                        liveRegion = liveRegionMode
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
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
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove ${member.name}")
            }
        }
    }
}
