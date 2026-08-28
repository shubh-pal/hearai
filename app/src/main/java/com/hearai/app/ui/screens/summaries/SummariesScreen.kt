package com.hearai.app.ui.screens.summaries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hearai.app.data.model.Summary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** §6.6 Summaries. */
@Composable
fun SummariesScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: SummariesViewModel = hiltViewModel(),
) {
    val summaries by viewModel.summaries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Summaries") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::summarizeNow) { Text("Now") }
        },
    ) { padding ->
        if (summaries.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                Text("No summaries yet.", modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(summaries, key = { it.id }) { summary ->
                    SummaryCard(summary, onClick = { onOpenSession(summary.sessionId) })
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: Summary, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(summary.timeRangeStart)),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(summary.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
