package com.example.song.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
                    .height(500.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pulse Debugger", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            DebugSection("Engine Status") {
                                DebugRow("Extracting", isExtracting.toString())
                                DebugRow("Resolving ID", resolvingId?.toString() ?: "None")
                            }
                        }

                        item {
                            DebugSection("Last Playback Error") {
                                Text(
                                    playbackError ?: "No errors recorded",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (playbackError != null) Color.Red else Color.Gray,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        item {
                            DebugSection("Last Extraction Error") {
                                Text(
                                    extractionError ?: "No errors recorded",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (extractionError != null) Color.Red else Color.Gray,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        
                        item {
                            Button(
                                onClick = { viewModel.clearPlaybackError() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Text("Clear Playback Error")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DebugSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .padding(8.dp)
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
