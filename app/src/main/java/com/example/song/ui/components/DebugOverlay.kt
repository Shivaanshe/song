package com.example.song.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.song.viewmodel.SongViewModel

@Composable
fun DebugOverlay(viewModel: SongViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    val playbackError by viewModel.playbackError.collectAsState()
    val extractionError by viewModel.extractionError.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val resolvingId by viewModel.resolvingUrlId.collectAsState()
    val systemLogs by viewModel.systemLogs.collectAsState()
    val currentQueue by viewModel.currentQueue.collectAsState()
    val currentSong by viewModel.currentPlayingSong.collectAsState()
    val currentTask by viewModel.currentTask.collectAsState()
    val cachedKeys by viewModel.cachedKeys.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        SmallFloatingActionButton(
            onClick = { showDialog = true },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .size(32.dp),
            containerColor = Color.Black.copy(alpha = 0.4f),
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.BugReport, contentDescription = "Debug", modifier = Modifier.size(16.dp))
        }
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(650.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF121212) // Deeper dark for high contrast
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pulse Debugger", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        IconButton(onClick = { showDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        item {
                            DebugSection("Engine & Task Status") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    DebugRow("Live Task", currentTask ?: "Idle")
                                    DebugRow("Extracting", isExtracting.toString())
                                    DebugRow("Resolving ID", resolvingId?.toString() ?: "None")
                                    currentSong?.let {
                                        DebugRow("Active Song", it.title)
                                    }
                                }
                            }
                        }

                        item {
                            DebugSection("Cache Monitor (${cachedKeys.size} spans)") {
                                Column(
                                    modifier = Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (cachedKeys.isEmpty()) {
                                        Text("Cache is empty", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    } else {
                                        cachedKeys.forEach { key ->
                                            Text(
                                                text = "• $key",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                color = Color(0xFF81C784),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            DebugSection("Playback Queue (${currentQueue.size})") {
                                Column(
                                    modifier = Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    currentQueue.forEach { song ->
                                        val isPlaying = song.id == currentSong?.id
                                        Text(
                                            text = (if (isPlaying) "▶ " else "• ") + song.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isPlaying) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        if (playbackError != null || extractionError != null) {
                            item {
                                DebugSection("Active Errors") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        playbackError?.let {
                                            Text("Playback: $it", style = MaterialTheme.typography.bodySmall, color = Color.Red)
                                        }
                                        extractionError?.let {
                                            Text("Extraction: $it", style = MaterialTheme.typography.bodySmall, color = Color.Red)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            DebugSection("System Logs (Scrollable)") {
                                Box(
                                    modifier = Modifier
                                        .height(200.dp)
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    val logScrollState = rememberScrollState()
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(logScrollState)
                                            .horizontalScroll(rememberScrollState())
                                    ) {
                                        systemLogs.forEach { log ->
                                            Text(
                                                text = log,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = if (log.contains("[ERROR]")) Color.Red else Color.Gray,
                                                fontFamily = FontFamily.Monospace,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.clearPlaybackError() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clear Logs & Errors")
                    }
                }
            }
        }
    }
}

@Composable
fun DebugSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(12.dp)
        ) {
            content()
        }
    }
}

@Composable
fun DebugRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
