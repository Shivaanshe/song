package com.example.song.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.song.data.model.Song

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: Song,
    onPlayClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDelete: () -> Unit,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    selectionMode: Boolean = false,
    isPlaying: Boolean = false,
    isArrangeMode: Boolean = false,
    isDragging: Boolean = false
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else if (isSelected) 0.95f else if (isPlaying) pulseScale else 1f,
        animationSpec = if (isDragging || isSelected || isPlaying) spring(dampingRatio = Spring.DampingRatioMediumBouncy) else tween(300),
        label = "SelectionScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                if (isDragging) {
                    shadowElevation = 16.dp.toPx()
                }
            }
            .combinedClickable(
                enabled = !isArrangeMode,
                onClick = {
                    if (selectionMode) onLongClick()
                    else onPlayClick()
                },
                onLongClick = onLongClick
            )
            .border(
                width = if (isDragging) 3.dp else if (isSelected || isPlaying) 2.dp else 0.dp,
                brush = when {
                    isDragging -> Brush.linearGradient(colors = listOf(Color(0xFFFF4081), Color(0xFFFF4081)))
                    isSelected -> Brush.linearGradient(colors = listOf(Color(0xFFE040FB), Color(0xFFFF4081)))
                    isPlaying -> Brush.linearGradient(colors = listOf(Color(0xFF00E676), Color(0xFF1DE9B6)))
                    else -> Brush.linearGradient(colors = listOf(Color.Transparent, Color.Transparent))
                },
                shape = RoundedCornerShape(20.dp)
            ),
        color = when {
            isSelected -> Color.White.copy(alpha = 0.4f)
            isPlaying -> Color.White.copy(alpha = 0.5f)
            else -> Color.White.copy(alpha = 0.3f)
        },
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isArrangeMode) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "Reorder",
                    tint = Color(0xFF424242).copy(alpha = 0.6f),
                    modifier = Modifier.padding(end = 12.dp).size(24.dp)
                )
            }
            Box {
                AsyncImage(
                    model = song.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                                .shadow(4.dp, CircleShape)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (song.isFavorite) Color.Red else Color(0xFF424242),
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.5f), CircleShape).size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color(0xFF424242),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Song") },
            text = { Text("Are you sure you want to delete '${song.title}'?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
