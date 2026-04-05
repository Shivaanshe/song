package com.example.song.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.song.data.model.Song
import com.example.song.ui.components.SongRow

@Composable
fun LibraryScreen(
    songs: List<Song>,
    onPlaySong: (Song) -> Unit,
    onAddSong: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val song = Song(
                    title = it.lastPathSegment ?: "Unknown",
                    audioUri = it.toString(),
                    artist = "Unknown Artist"
                )
                onAddSong(song)
            }
        }
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { launcher.launch(arrayOf("audio/*")) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Song")
            }
        }
    ) { padding ->
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No songs added yet. Tap + to add from storage.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(songs) { song ->
                    SongRow(
                        song = song,
                        onClick = { onPlaySong(song) },
                        onDelete = { onDeleteSong(song) }
                    )
                }
            }
        }
    }
}
