package com.example.song.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.song.ui.components.SongListItem
import com.example.song.util.multiSelectDragHandler
import com.example.song.viewmodel.SongViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: SongViewModel,
    onSongClick: () -> Unit
) {
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedSongIds by viewModel.selectedSongIds.collectAsState()
    val currentSong by viewModel.currentPlayingSong.collectAsState()
    
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    title = {
                        Text(
                            "Favorites",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF333333)
                            )
                        )
                    }
                )
            }
        ) { padding ->
            if (favoriteSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        "No favorite songs yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF666666)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .multiSelectDragHandler(
                            listState = listState,
                            onDragStart = { key ->
                                if (key is Int) {
                                    viewModel.startRangeSelection(key, favoriteSongs.map { it.id })
                                }
                            },
                            onDragUpdate = { key ->
                                if (key is Int) {
                                    viewModel.updateRangeSelection(key, favoriteSongs.map { it.id })
                                }
                            },
                            onDragEnd = {
                                viewModel.endRangeSelection()
                            }
                        ),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(favoriteSongs, key = { it.id }) { song ->
                        SongListItem(
                            song = song,
                            onPlayClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSongSelection(song.id)
                                } else {
                                    viewModel.playSong(song, favoriteSongs)
                                    onSongClick()
                                }
                            },
                            onFavoriteToggle = {
                                viewModel.updateFavorite(song, !song.isFavorite)
                            },
                            onDelete = {
                                viewModel.deleteSong(song.id)
                            },
                            isSelected = selectedSongIds.contains(song.id),
                            onLongClick = {
                                viewModel.toggleSelectionMode(true)
                                viewModel.toggleSongSelection(song.id)
                            },
                            selectionMode = isSelectionMode,
                            isPlaying = currentSong?.id == song.id
                        )
                    }
                }
            }
        }

        // Selection Top Bar Overlay
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.zIndex(10f)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.85f),
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleSelectionMode(false) }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFF424242))
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Text(
                        text = "${selectedSongIds.size} Selected",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(onClick = {
                        val count = selectedSongIds.size
                        viewModel.deleteSelectedItems()
                        scope.launch {
                            snackbarHostState.showSnackbar("Deleted $count items")
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color.Red)
                    }
                }
            }
        }
    }
}
