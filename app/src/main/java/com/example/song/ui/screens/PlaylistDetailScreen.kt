package com.example.song.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.song.ui.components.SongListItem
import com.example.song.viewmodel.SongViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Int,
    playlistName: String,
    viewModel: SongViewModel,
    onBackClick: () -> Unit,
    onSongClick: () -> Unit
) {
    val songsInPlaylist by if (playlistId == -1) {
        viewModel.favoriteSongs.collectAsState(initial = emptyList())
    } else {
        viewModel.getSongsInPlaylist(playlistId).collectAsState(initial = emptyList())
    }
    
    var showAddSongDialog by remember { mutableStateOf(false) }
    val allSongs by viewModel.allSongs.collectAsState()

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
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color(0xFF333333),
                        navigationIconContentColor = Color(0xFF333333)
                    ),
                    title = { Text(playlistName, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.background(Color.White.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            floatingActionButton = {
                if (playlistId != -1) {
                    FloatingActionButton(
                        onClick = { showAddSongDialog = true },
                        containerColor = Color.White,
                        contentColor = Color(0xFFE91E63)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Song to Playlist")
                    }
                }
            }
        ) { padding ->
            if (songsInPlaylist.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No songs here yet.", color = Color(0xFF666666))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(songsInPlaylist) { song ->
                        SongListItem(
                            song = song,
                            onPlayClick = {
                                viewModel.playSong(song, songsInPlaylist)
                                onSongClick()
                            },
                            onFavoriteToggle = {
                                viewModel.updateFavorite(song, !song.isFavorite)
                            },
                            onDelete = {
                                if (playlistId == -1) {
                                    viewModel.updateFavorite(song, false)
                                } else {
                                    viewModel.removeSongFromPlaylist(song.id, playlistId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddSongDialog) {
        AlertDialog(
            onDismissRequest = { showAddSongDialog = false },
            title = { Text("Add Song to Playlist") },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(allSongs) { song ->
                        ListItem(
                            headlineContent = { Text(song.title) },
                            modifier = Modifier.clickable {
                                viewModel.addSongToPlaylist(song.id, playlistId)
                                showAddSongDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddSongDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
