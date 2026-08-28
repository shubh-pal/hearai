package com.hearai.app.ui.screens.transcript

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** §6.5 Live Transcript (full-screen). */
@Composable
fun LiveTranscriptScreen(
    onBack: () -> Unit,
    viewModel: LiveTranscriptViewModel = hiltViewModel(),
) {
    val segments by viewModel.segments.collectAsState()
    val listState = rememberLazyListState()
    var textSizeSp by remember { mutableFloatStateOf(16f) }
    var flipped by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }

    // Auto-scroll to newest text as new segments arrive.
    LaunchedEffect(segments.size) {
        if (segments.isNotEmpty() && !paused) {
            listState.animateScrollToItem(segments.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Transcript") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    // Adjustable text size (accessibility-critical, §6.5).
                    IconButton(onClick = { textSizeSp = if (textSizeSp >= 24f) 16f else textSizeSp + 4f }) {
                        Icon(Icons.Default.TextFields, contentDescription = "Text size")
                    }
                    // Flip 180° for handing the phone to the other person to read.
                    IconButton(onClick = { flipped = !flipped }) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Flip")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .rotate(if (flipped) 180f else 0f),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(segments) { segment ->
                    Column {
                        Row {
                            Text(
                                formatTimestamp(segment.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                            )
                            Text(
                                "  •  ${segment.detectedLanguage}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                            )
                        }
                        Text(segment.text, fontSize = textSizeSp.sp)
                    }
                }
            }

            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                androidx.compose.material3.Button(onClick = { paused = !paused }) {
                    Text(if (paused) "Resume" else "Pause")
                }
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
