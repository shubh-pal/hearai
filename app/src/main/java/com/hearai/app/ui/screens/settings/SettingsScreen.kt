package com.hearai.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hearai.app.data.model.AppTheme
import com.hearai.app.data.model.TextSizeOption
import com.hearai.app.data.model.VadSensitivity

/** §6.8 Settings. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    var showRemoveKeyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader("API Key")
            SettingsRow(
                title = "Gemini API key",
                value = viewModel.maskedKey() ?: "Not set",
                onClick = { showRemoveKeyDialog = true },
            )

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            SectionHeader("General")
            DropdownSettingRow(
                title = "Summarization interval",
                value = intervalLabel(settings.summarizationIntervalMinutes),
                options = listOf(0, 2, 5, 10, 15).map { it to intervalLabel(it) },
                onSelect = { viewModel.setSummarizationInterval(it) },
            )
            DropdownSettingRow(
                title = "VAD sensitivity",
                value = settings.vadSensitivity.name.lowercase().replaceFirstChar { it.uppercase() },
                options = VadSensitivity.entries.map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                onSelect = { viewModel.setVadSensitivity(it) },
            )
            SettingsSwitchRow(
                title = "Floating overlay bubble",
                subtitle = "Requires Accessibility permission",
                checked = settings.overlaySurfaceEnabled,
                onCheckedChange = { viewModel.setOverlayEnabled(it) },
            )
            DropdownSettingRow(
                title = "Text size",
                value = settings.textSize.name.lowercase().replaceFirstChar { it.uppercase() },
                options = TextSizeOption.entries.map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                onSelect = { viewModel.setTextSize(it) },
            )
            DropdownSettingRow(
                title = "Theme",
                value = settings.theme.name.lowercase().replaceFirstChar { it.uppercase() },
                options = AppTheme.entries.map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                onSelect = { viewModel.setTheme(it) },
            )

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            SectionHeader("Data & privacy")
            Text(
                "Session data stays local unless you explicitly export it. Your Gemini API key's free-tier terms may let Google use your audio/text to improve their models.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            DropdownSettingRow(
                title = "Auto-delete session history",
                value = settings.retentionDays?.let { "$it days" } ?: "Never",
                options = listOf(null, 7, 30, 90).map { it to (it?.let { d -> "$d days" } ?: "Never") },
                onSelect = { viewModel.setRetentionDays(it) },
            )
        }
    }

    if (showRemoveKeyDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveKeyDialog = false },
            title = { Text("Remove API key?") },
            text = { Text("This immediately stops any active listening session. You'll need to add a key again to use HearAI.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeKey()
                    showRemoveKeyDialog = false
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveKeyDialog = false }) { Text("Cancel") }
            },
        )
    }
}

private fun intervalLabel(minutes: Int) = if (minutes == 0) "Off" else "$minutes min"

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun SettingsRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onClick) { Text(value) }
    }
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> DropdownSettingRow(title: String, value: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Column {
            TextButton(onClick = { expanded = true }) { Text(value) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (optionValue, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        onSelect(optionValue)
                        expanded = false
                    })
                }
            }
        }
    }
}
