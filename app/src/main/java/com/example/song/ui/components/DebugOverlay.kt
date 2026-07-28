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
                    .height(680.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF121212)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pulse Debugger", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        IconButton(onClick = { showDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        // Section 1: Engine Status
                        item {
                            DebugSection("Engine & Task Status") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DebugRowFixed("Live Task", currentTask ?: "Idle", Color(0xFF81C784))
                                    DebugRowFixed("Extracting", isExtracting.toString())
                                    DebugRowFixed("Resolving ID", resolvingId?.toString() ?: "None")
                                    currentSong?.let {
                                        DebugRowFixed("Active Song", it.title)
                                    }
                                }
                            }
                        }

                        // Section 2: Cache Monitor
                        item {
                            DebugSection("Cache Monitor (${cachedKeys.size} spans)") {
                                Box(
                                    modifier = Modifier
                                        .heightIn(max = 120.dp)
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                        .padding(8.dp)
                                ) {
                                    if (cachedKeys.isEmpty()) {
                                        Text("Cache is empty", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    } else {
                                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                        }

                        // Section 3: Playback Queue
                        item {
                            DebugSection("Playback Queue (${currentQueue.size})") {
                                Column(
                                    modifier = Modifier
                                        .heightIn(max = 150.dp)
                                        .verticalScroll(rememberScrollState())
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (playbackError != null) {
                                        Text("ERROR: $playbackError", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (extractionError != null) {
                                        Text("ERROR: $extractionError", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                                    }
                                    currentQueue.forEach { song ->
                                        val isPlaying = song.id == currentSong?.id
                                        val isCached = cachedKeys.any { it.contains(song.audioUri) || (song.id.toString() in cachedKeys) }
                                        
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = (if (isPlaying) "▶ " else "• "),
                                                color = if (isPlaying) Color(0xFFE91E63) else Color.White.copy(alpha = 0.4f)
                                            )
                                            Text(
                                                text = song.title,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isPlaying) Color.White else Color.White.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isCached) {
                                                Surface(
                                                    color = Color(0xFF81C784).copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text("CACHED", modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp), color = Color(0xFF81C784))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Section 4: System Logs
                        item {
                            DebugSection("System Logs (Live)") {
                                Box(
                                    modifier = Modifier
                                        .height(200.dp)
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    val verticalScrollState = rememberScrollState()
                                    val horizontalScrollState = rememberScrollState()
                                    
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(verticalScrollState)
                                            .horizontalScroll(horizontalScrollState)
                                    ) {
                                        systemLogs.forEach { log ->
                                            Text(
                                                text = log,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = if (log.contains("[ERROR]")) Color(0xFFFF5252) else Color.Gray,
                                                fontFamily = FontFamily.Monospace,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.clearPlaybackError() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333), contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Clear Logs & Errors", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DebugSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Color(0xFF81C784), fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
fun DebugRowFixed(label: String, value: String, valueColor: Color = Color.White) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
