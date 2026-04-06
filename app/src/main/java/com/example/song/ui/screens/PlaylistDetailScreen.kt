package com.example.song.ui.screens

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
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
    
    val repeatMode by viewModel.repeatMode.collectAsState()
    var rotationAngle by remember { mutableStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = rotationAngle,
        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
        label = "RepeatRotation"
    )

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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header with Image and Overlay
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    AsyncImage(
                        model = if (songsInPlaylist.isNotEmpty()) songsInPlaylist.first().imageUrl else null,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Gradient overlay for text readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                )
                            )
                    )

                    // Back Button
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.TopStart)
                            .background(Color.White.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    // Playlist Name and Info
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(24.dp)
                    ) {
                        Text(
                            text = playlistName,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "${songsInPlaylist.size} songs",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        )
                    }
                }
            }

            // Play All and Repeat Buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { 
                            if (songsInPlaylist.isNotEmpty()) {
                                viewModel.playSong(songsInPlaylist.first(), songsInPlaylist)
                                onSongClick()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFE040FB), Color(0xFFFF4081))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Play All", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .height(56.dp)
                            .width(110.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(28.dp),
                        onClick = { 
                            rotationAngle += 360f
                            viewModel.toggleRepeatMode() 
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (repeatMode) {
                                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                        else -> Icons.Default.Repeat
                                    },
                                    contentDescription = null, 
                                    tint = if (repeatMode == Player.REPEAT_MODE_OFF) Color(0xFF424242) else Color(0xFF4CAF50),
                                    modifier = Modifier.rotate(animatedRotation)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Repeat", 
                                    color = if (repeatMode == Player.REPEAT_MODE_OFF) Color(0xFF424242) else Color(0xFF4CAF50), 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Recommended Section (Songs in Playlist)
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

        // Add Song Fab (Glass style)
        if (playlistId != -1) {
            FloatingActionButton(
                onClick = { showAddSongDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = Color.White.copy(alpha = 0.8f),
                contentColor = Color(0xFFE91E63),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Song")
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
