package com.rescuenet.app.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rescuenet.app.data.repository.HelpResult
import com.rescuenet.app.ui.theme.SeverityHigh

/** What the Nearby Help and Safe Route screens ask for when the user taps "Allow location". */
val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

fun categoryIcon(category: String): ImageVector = when (category) {
    "Hospital" -> Icons.Filled.LocalHospital
    "Clinic" -> Icons.Filled.MedicalServices
    "Shelter" -> Icons.Filled.Cabin
    "Assembly Point" -> Icons.Filled.Groups
    "Police" -> Icons.Filled.LocalPolice
    "Fire" -> Icons.Filled.LocalFireDepartment
    else -> Icons.Filled.Place
}

fun formatDistanceKm(km: Double): String =
    if (km < 1.0) "${(km * 1000).toInt()} m" else String.format("%.1f km", km)

fun formatMetres(metres: Double): String = formatDistanceKm(metres / 1000.0)

fun formatDuration(seconds: Double): String {
    val minutes = Math.round(seconds / 60.0).toInt()
    return when {
        minutes < 1 -> "under 1 min"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

/** Opens a link or phone dialler; does nothing (rather than crashing) if no app can handle it. */
fun openExternally(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No handler installed — nothing sensible to do.
    }
}

fun dialIntent(phone: String) = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)))

@Composable
fun HelpLoading(message: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A failure explained in plain language, with the one action that can fix it. */
@Composable
fun HelpProblem(
    kind: HelpResult.Kind,
    message: String,
    onRetry: () -> Unit,
    onGrantPermission: () -> Unit,
) {
    val context = LocalContext.current
    val icon = when (kind) {
        HelpResult.Kind.NO_PERMISSION, HelpResult.Kind.NO_LOCATION -> Icons.Filled.LocationOff
        HelpResult.Kind.UNREACHABLE -> Icons.Filled.CloudOff
        HelpResult.Kind.SERVER -> Icons.Filled.Warning
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = SeverityHigh)
            Text(message, style = MaterialTheme.typography.bodyLarge)
            if (kind == HelpResult.Kind.NO_PERMISSION) {
                Button(onClick = onGrantPermission) { Text("Allow location") }
                // After "Don't ask again" Android won't show the permission dialog any more, so
                // also offer the app's settings page, where it can still be switched on.
                TextButton(onClick = {
                    openExternally(
                        context,
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text("Open app settings") }
            } else {
                Button(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}
