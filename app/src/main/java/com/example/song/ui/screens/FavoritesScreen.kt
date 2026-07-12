package com.example.song.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.song.ui.components.SongListItem
import com.example.song.viewmodel.SongViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: SongViewModel,
    onSongClick: () -> Unit
) {
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()

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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(favoriteSongs) { song ->
                        SongListItem(
                            song = song,
                            onPlayClick = {
                                viewModel.playSong(song, favoriteSongs)
                                onSongClick()
                            },
                            onFavoriteToggle = {
                                viewModel.updateFavorite(song, !song.isFavorite)
                            },
                            onDelete = {
                                viewModel.deleteSong(song.id)
                            }
                        )
                    }
                }
            }
        }
    }
}
