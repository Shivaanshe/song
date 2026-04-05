package com.example.song.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.song.ui.components.SongRow
import com.example.song.viewmodel.SongViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Int,
    playlistName: String,
    viewModel: SongViewModel
) {
    val songsInPlaylist by viewModel.getSongsInPlaylist(playlistId).collectAsState(initial = emptyList())
    var showAddSongDialog by remember { mutableStateOf(false) }
    val allSongs by viewModel.allSongs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(playlistName) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSongDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Song to Playlist")
            }
        }
    ) { padding ->
        if (songsInPlaylist.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No songs in this playlist yet.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(songsInPlaylist) { song ->
                    SongRow(
                        song = song,
                        onClick = { viewModel.playSong(song, songsInPlaylist) },
                        onDelete = { viewModel.removeSongFromPlaylist(song.id, playlistId) }
                    )
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
