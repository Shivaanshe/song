package com.example.song.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.song.viewmodel.SongViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: SongViewModel,
    onBackClick: () -> Unit
) {
    val song by viewModel.currentPlayingSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()

    // Vibrant background gradient
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
                            "Now Playing",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF424242)
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .padding(8.dp)
                                .background(Color.White.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Back",
                                tint = Color(0xFF424242)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleRepeatMode() },
                            modifier = Modifier
                                .padding(8.dp)
                                .background(Color.White.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                    Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                                    else -> Icons.Default.Repeat
                                },
                                contentDescription = "Repeat Mode",
                                tint = if (repeatMode == Player.REPEAT_MODE_OFF) Color(0xFF424242) else Color(0xFF4CAF50)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.1f))

                // Album Art with Smooth Transition
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .shadow(24.dp, RoundedCornerShape(32.dp))
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Crossfade(targetState = song?.imageUrl, label = "AlbumArt") { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(0.1f))

                // Song Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = song?.title ?: "No Song",
                            transitionSpec = {
                                fadeIn() + slideInVertically { it / 2 } togetherWith
                                        fadeOut() + slideOutVertically { -it / 2 }
                            },
                            label = "SongTitle"
                        ) { title ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF333333),
                                    fontSize = 24.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        AnimatedContent(
                            targetState = song?.artist ?: "Unknown Artist",
                            label = "ArtistName"
                        ) { artist ->
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color(0xFF666666),
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(onClick = { 
                        song?.let { viewModel.updateFavorite(it, !it.isFavorite) }
                    }) {
                        Icon(
                            imageVector = if (song?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (song?.isFavorite == true) Color(0xFFE91E63) else Color(0xFF424242),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Seekbar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFFE91E63),
                            inactiveTrackColor = Color.White.copy(alpha = 0.5f)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatTime(currentPosition),
                            color = Color(0xFF555555),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            formatTime(duration),
                            color = Color(0xFF555555),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.skipToPrevious() },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = null,
                            tint = Color(0xFF424242),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFFF4081), Color(0xFFE040FB))
                                )
                            )
                            .noRippleClickable { viewModel.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.skipToNext() },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = Color(0xFF424242),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(0.1f))
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Composable
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
)
