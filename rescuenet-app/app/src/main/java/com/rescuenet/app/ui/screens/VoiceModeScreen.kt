@file:OptIn(ExperimentalMaterial3Api::class)

package com.rescuenet.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rescuenet.app.data.viewmodel.VoiceModeUiState
import com.rescuenet.app.data.viewmodel.VoiceModeViewModel
import com.rescuenet.app.ui.theme.EmergencyRed

/**
 * Phase 6: real on-device speech recognition (VoiceRecognitionProvider) drives this screen —
 * no more scripted transcript. The state machine (Idle -> Listening -> Partial -> Done, or
 * -> Failed) is exactly what a real STT integration looks like: partial results stream in
 * live, and a failure path is a first-class outcome with a manual-typing fallback, not an
 * afterthought.
 */
@Composable
fun VoiceModeScreen(
    onTranscribed: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: VoiceModeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.startListening() }

    LaunchedEffect(state) {
        val done = state as? VoiceModeUiState.Done
        if (done != null) onTranscribed(done.text.ifBlank { "Emergency reported by voice — no additional details captured." })
    }

    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulseScale"
    )
    val isListening = state is VoiceModeUiState.Listening || state is VoiceModeUiState.Partial

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Voice Emergency Mode") },
            navigationIcon = {
                IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
            }
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val s = state) {
                is VoiceModeUiState.Failed -> {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(56.dp), tint = EmergencyRed)
                    Spacer(Modifier.height(16.dp))
                    Text(s.message, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onCancel) {
                        Icon(Icons.Filled.Keyboard, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Type instead")
                    }
                }
                else -> {
                    Surface(
                        shape = CircleShape,
                        color = EmergencyRed,
                        modifier = Modifier.size(140.dp).scale(if (isListening) scale else 1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        when (s) {
                            is VoiceModeUiState.Partial -> "\"${s.text}\""
                            is VoiceModeUiState.Done -> "Got it — structuring your report"
                            else -> "Listening… speak in English or Urdu"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    if (s is VoiceModeUiState.Listening) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "e.g. \"Help, there are five people trapped inside the building.\"",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
