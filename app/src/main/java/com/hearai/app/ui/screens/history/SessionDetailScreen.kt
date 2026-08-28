package com.hearai.app.ui.screens.history

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** §7b Session Detail — Transcript / Summary tabs, share + export actions (§6.7). */
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val segments by viewModel.segments.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Transcript") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Summary") })
            }

            LazyColumn(modifier = Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tabIndex == 0) {
                    items(segments) { segment ->
                        Column {
                            Text("${segment.detectedLanguage}", style = MaterialTheme.typography.labelSmall)
                            Text(segment.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    items(summaries) { summary ->
                        Text(summary.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, viewModel.exportAsText())
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share transcript"))
                }) {
                    Text("Share")
                }
                OutlinedButton(onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, viewModel.exportAsText())
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Export as TXT"))
                }) {
                    Text("Export TXT")
                }
            }
        }
    }
}
