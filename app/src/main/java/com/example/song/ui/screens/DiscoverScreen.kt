package com.example.song.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.example.song.SongApplication
import com.example.song.data.model.StreamingItem
import com.example.song.viewmodel.SongViewModel

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun DiscoverScreen(viewModel: SongViewModel) {
    val items by viewModel.topLevelStreamingItems.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val isEngineReady by SongApplication.getInstance().isReady.collectAsState()
    val resolvingUrlId by viewModel.resolvingUrlId.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var youtubeUrl by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf<StreamingItem?>(null) }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFD1C4E9),
            Color(0xFFBBDEFB)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                title = {
                    Text(
                        selectedPlaylist?.title ?: "Discover",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333)
                        )
                    )
                },
                navigationIcon = {
                    if (selectedPlaylist != null) {
                        IconButton(onClick = { selectedPlaylist = null }) {
                            Icon(Icons.Default.MusicNote, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (selectedPlaylist == null) {
                        IconButton(
                            onClick = { if (isEngineReady) showAddDialog = true },
                            modifier = Modifier.background(
                                if (isEngineReady) Color.White.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.2f), 
                                CircleShape
                            ),
                            enabled = isEngineReady
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add URL")
                        }
                    }
                }
            )

            if (isExtracting) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFE91E63)
                )
            }

            if (selectedPlaylist == null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items) { item ->
                        StreamingItemCard(
                            item = item,
                            enabled = isEngineReady,
                            isResolving = resolvingUrlId == item.id,
                            onClick = {
                                if (isEngineReady) {
                                    if (item.isPlaylist) {
                                        selectedPlaylist = item
                                    } else {
                                        viewModel.playStreamingItem(item, items)
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                val playlistItems by viewModel.getItemsForStreamingPlaylist(selectedPlaylist!!.youtubeUrl).collectAsState(initial = emptyList())
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(playlistItems) { item ->
                        StreamingItemCard(
                            item = item,
                            enabled = isEngineReady,
                            isResolving = resolvingUrlId == item.id,
                            onClick = {
                                if (isEngineReady) {
                                    viewModel.playStreamingItem(item, playlistItems)
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add YouTube Link") },
                text = {
                    TextField(
                        value = youtubeUrl,
                        onValueChange = { youtubeUrl = it },
                        placeholder = { Text("https://youtube.com/...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (youtubeUrl.isNotBlank()) {
                            viewModel.addStreamingItem(youtubeUrl)
                            youtubeUrl = ""
                            showAddDialog = false
                        }
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun StreamingItemCard(
    item: StreamingItem, 
    enabled: Boolean = true, 
    isResolving: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !isResolving) { onClick() }
            .clip(RoundedCornerShape(16.dp)),
        color = if (enabled) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, if (enabled) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Gray.copy(alpha = 0.2f))
            ) {
                if (item.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                if (item.isPlaylist) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White)
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.isPlaylist) "Playlist" else "YouTube Stream",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
            }
            
            if (isResolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFFE91E63)
                )
            } else {
                IconButton(onClick = onClick, enabled = enabled) {
                    Icon(
                        if (item.isPlaylist) Icons.Default.Folder else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (enabled) LocalContentColor.current else Color.Gray
                    )
                }
            }
        }
    }
}
