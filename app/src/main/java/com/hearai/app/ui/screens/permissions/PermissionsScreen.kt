package com.hearai.app.ui.screens.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class PermissionInfo(val title: String, val rationale: String)

/**
 * §6.3 Onboarding — Permissions. Sequential requests with plain-language rationale for each:
 * microphone, notifications, battery-optimization exemption, and optionally Accessibility
 * Service (only if the user wants the floating overlay — must be skippable).
 */
@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val context = LocalContext.current

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Permissions", style = MaterialTheme.typography.headlineSmall)
        Text(
            "We need a few permissions to work in the background and keep you informed.",
            style = MaterialTheme.typography.bodyMedium,
        )

        PermissionRow(
            title = "Microphone",
            rationale = "Capture audio for transcription. HearAI never streams audio without your say-so.",
            onGrant = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(
                title = "Notifications",
                rationale = "Show ongoing status and alerts while a session is active.",
                onGrant = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }

        PermissionRow(
            title = "Battery optimization",
            rationale = "Allow background listening — otherwise Android may kill the session when the screen turns off.",
            onGrant = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            },
        )

        var overlayRequested by remember { mutableStateOf(false) }
        PermissionRow(
            title = "Accessibility (optional)",
            rationale = "Required only for the floating overlay bubble. Skip if you don't need it.",
            onGrant = {
                overlayRequested = true
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
        )

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Grant all")
        }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Maybe later")
        }
    }
}

@Composable
private fun PermissionRow(title: String, rationale: String, onGrant: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(rationale, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onGrant, modifier = Modifier.padding(top = 4.dp)) {
                Text("Grant")
            }
        }
    }
}
