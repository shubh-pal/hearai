package com.hearai.app.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hearai.app.data.model.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** §6.7 Session History. */
@Composable
fun SessionHistoryScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: SessionHistoryViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session History") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                Text("No past sessions.", modifier = Modifier.padding(24.dp))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(session, onClick = { onOpenSession(session.id) }, onDelete = { viewModel.deleteSession(session) })
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: Session, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(session.startTime)),
                    style = MaterialTheme.typography.titleMedium,
                )
                val durationMin = ((session.endTime ?: System.currentTimeMillis()) - session.startTime).let {
                    TimeUnit.MILLISECONDS.toMinutes(it)
                }
                Text(
                    "$durationMin min • ${session.detectedLanguages.joinToString(", ").ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        }
    }
}
