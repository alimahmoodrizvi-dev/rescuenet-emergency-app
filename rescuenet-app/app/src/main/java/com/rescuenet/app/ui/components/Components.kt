package com.rescuenet.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rescuenet.app.data.model.Severity
import com.rescuenet.app.ui.theme.*

/** The single most important widget in the app. Large touch target (Part 18), high contrast,
 *  icon + text (never color alone) so it reads instantly under stress. A confirming haptic
 *  pulse on press (Part 18 — haptic feedback) matters more here than almost anywhere else in
 *  the app: under stress, a person may not be looking at the screen closely enough to trust
 *  a purely visual confirmation that the tap registered. */
@Composable
fun NeedHelpButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress) // a stronger pulse than the default click tick
            onClick()
        },
        shape = CircleShape,
        color = EmergencyRed,
        modifier = modifier
            .size(200.dp)
            .semantics { contentDescription = "Need Help. Report an emergency now." }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(8.dp))
            Text("NEED HELP", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun ImSafeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        colors = ButtonDefaults.buttonColors(containerColor = SeveritySafeGreen, contentColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .semantics { contentDescription = "I'm Safe. Notify your family you are safe." }
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("I'M SAFE", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SecondaryActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .height(96.dp)
            .semantics { contentDescription = label }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** Severity is always shown as color + icon + text label together — never color alone. */
@Composable
fun SeverityChip(severity: Severity, modifier: Modifier = Modifier) {
    val (color, label, icon) = when (severity) {
        Severity.CRITICAL -> Triple(SeverityCritical, "CRITICAL", Icons.Filled.PriorityHigh)
        Severity.HIGH -> Triple(SeverityHigh, "HIGH", Icons.Filled.ErrorOutline)
        Severity.MODERATE -> Triple(SeverityModerate, "MODERATE", Icons.Filled.Info)
        Severity.RESOLVED -> Triple(SeveritySafeGreen, "RESOLVED", Icons.Filled.CheckCircle)
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Reports connectivity status — a proactive `liveRegion` announcement matters specifically
 *  here, more than almost anywhere else in the app: a screen-reader user needs to know the
 *  instant the network drops or recovers, not only if they happen to navigate back to this
 *  banner and re-read it (Part 18 — accessibility for users under stress). `mergeDescendants`
 *  makes TalkBack read the whole card as one coherent sentence instead of three disconnected
 *  fragments. */
@Composable
fun NetworkStatusBanner(internetConnected: Boolean, nearbyNodes: Int, lastSyncLabel: String, modifier: Modifier = Modifier) {
    val bg = if (internetConnected) SeveritySafeGreen.copy(alpha = 0.12f) else SeverityCritical.copy(alpha = 0.12f)
    val fg = if (internetConnected) SeveritySafeGreen else SeverityCritical
    val statusDescription = "Internet ${if (internetConnected) "connected" else "offline"}. " +
        "RescueNet nodes nearby: $nearbyNodes. Last server sync $lastSyncLabel."

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = statusDescription
                liveRegion = LiveRegionMode.Polite
            }
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (internetConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                    contentDescription = null, tint = fg, modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (internetConnected) "Internet: CONNECTED" else "Internet: OFFLINE",
                    color = fg, style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("RescueNet Nodes Nearby: $nearbyNodes", style = MaterialTheme.typography.bodyMedium)
            Text("Last Server Sync: $lastSyncLabel", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Small "SIMULATION" / "DEMO ALERT" tag — required whenever data isn't real (Part 36). */
@Composable
fun SimulationTag(text: String = "SIMULATION", modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.takeOrElse(),
        modifier = modifier
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// Fallback since tertiaryContainer may not always be tuned in our custom scheme.
private fun Color.takeOrElse(): Color = if (this == Color.Unspecified) Color(0xFFEDE7A3) else this
