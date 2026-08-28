package com.hearai.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** §6.4 Home / Listening Screen (main screen). */
@Composable
fun HomeScreen(
    onOpenTranscript: () -> Unit,
    onOpenSummaries: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.listeningState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HearAI") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                NavigationBarItem(selected = false, onClick = onOpenSummaries, icon = { Icon(Icons.Default.Notes, null) }, label = { Text("Summaries") })
                NavigationBarItem(selected = false, onClick = onOpenHistory, icon = { Icon(Icons.Default.History, null) }, label = { Text("History") })
                NavigationBarItem(selected = false, onClick = onOpenSettings, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // §6.4: visual "actively listening" indicator — legible to bystanders too (§8 Privacy).
            if (state.isListening) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                    Text(
                        "● Actively listening",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // Large start/stop listening toggle.
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(if (state.isSpeechActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer)
                    .then(Modifier),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.TextButton(onClick = viewModel::toggleListening) {
                    Text(if (state.isListening) "Tap to stop" else "Tap to start", color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            if (!viewModel.hasApiKey) {
                Text(
                    "Add your Gemini API key in Settings to start listening.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            state.currentLanguage?.let {
                Text("Detected language: $it", style = MaterialTheme.typography.labelMedium)
            }

            // Live-updating preview of the last few transcript lines while active.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Live preview", style = MaterialTheme.typography.titleSmall)
                        androidx.compose.material3.TextButton(onClick = onOpenTranscript) { Text("View full transcript") }
                    }
                    if (state.recentLines.isEmpty()) {
                        Text("Nothing transcribed yet.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    } else {
                        state.recentLines.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
