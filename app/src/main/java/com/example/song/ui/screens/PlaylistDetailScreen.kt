package com.example.song.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import com.example.song.data.model.Song
import com.example.song.ui.components.SongListItem
import com.example.song.util.dragGestureHandler
import com.example.song.viewmodel.SongViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    
    var localSongsInPlaylist by remember { mutableStateOf(emptyList<Song>()) }
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var activeDraggedItem by remember { mutableStateOf<Song?>(null) }
    var currentDragY by remember { mutableFloatStateOf(0f) }
    var itemTouchOffset by remember { mutableFloatStateOf(0f) }
    var targetIndex by remember { mutableStateOf<Int?>(null) }
    var measuredItemHeightPx by remember { mutableFloatStateOf(0f) }
    var isManualOrder by remember { mutableStateOf(false) }
    
    LaunchedEffect(songsInPlaylist, draggedItemIndex, isManualOrder) {
        if (draggedItemIndex == null && !isManualOrder) {
            localSongsInPlaylist = songsInPlaylist
        }
    }
    
    val isArrangeModeEnabled by viewModel.isArrangeModeEnabled.collectAsState()

    LaunchedEffect(isArrangeModeEnabled) {
        if (!isArrangeModeEnabled) {
            draggedItemIndex = null
            activeDraggedItem = null
            targetIndex = null
        }
    }

    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedSongIds by viewModel.selectedSongIds.collectAsState()
    val currentSong by viewModel.currentPlayingSong.collectAsState()
    
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val repeatMode by viewModel.repeatMode.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .dragGestureHandler(
                            listState = listState,
                            isReorderMode = isArrangeModeEnabled,
                            onSelectStart = { key ->
                                if (key is Int) {
                                    viewModel.startRangeSelection(key, localSongsInPlaylist.map { it.id })
                                }
                            },
                            onSelectUpdate = { key ->
                                if (key is Int) {
                                    viewModel.updateRangeSelection(key, localSongsInPlaylist.map { it.id })
                                }
                            },
                            onSelectEnd = {
                                viewModel.endRangeSelection()
                            },
                            onReorderStart = { key, fingerY, itemTop ->
                                if (key is Int && playlistId != -1) {
                                    val index = localSongsInPlaylist.indexOfFirst { it.id == key }
                                    if (index != -1) {
                                        draggedItemIndex = index
                                        activeDraggedItem = localSongsInPlaylist[index]
                                        targetIndex = index
                                        currentDragY = fingerY
                                        itemTouchOffset = fingerY - itemTop
                                    }
                                }
                            },
                            onReorderUpdate = { y ->
                                if (draggedItemIndex != null) {
                                    currentDragY = y
                                    val info = listState.layoutInfo
                                    val itemUnderFinger = info.visibleItemsInfo.find { 
                                        y.toInt() in it.offset..(it.offset + it.size)
                                    }
                                    itemUnderFinger?.let { hitItem ->
                                        val hitKey = hitItem.key
                                        if (hitKey is Int) {
                                            val newTarget = localSongsInPlaylist.indexOfFirst { it.id == hitKey }
                                            if (newTarget != -1 && newTarget != targetIndex) {
                                                targetIndex = newTarget
                                            }
                                        }
                                    }
                                }
                            },
                            onReorderEnd = {
                                if (draggedItemIndex != null && targetIndex != null) {
                                    isManualOrder = true
                                    val mutable = localSongsInPlaylist.toMutableList()
                                    val song = mutable.removeAt(draggedItemIndex!!)
                                    mutable.add(targetIndex!!, song)
                                    localSongsInPlaylist = mutable
                                    viewModel.updatePlaylistSongOrder(playlistId, localSongsInPlaylist)
                                    scope.launch {
                                        delay(800)
                                        isManualOrder = false
                                    }
                                }
                                draggedItemIndex = null
                                activeDraggedItem = null
                                targetIndex = null
                            }
                        ),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        ) {
                            // Cover Image with Gradient
                            val coverImage = localSongsInPlaylist.firstOrNull()?.imageUrl
                            if (coverImage != null) {
                                coil.compose.AsyncImage(
                                    model = coverImage,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(Color(0xFF64B5F6), Color(0xFF1976D2))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(100.dp),
                                        tint = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            // Dark overlay gradient
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.7f)
                                            )
                                        )
                                    )
                            )

                            // 3. Back Button (Safe Area)
                            if (!isSelectionMode && !isArrangeModeEnabled) {
                                IconButton(
                                    onClick = onBackClick,
                                    modifier = Modifier
                                        .statusBarsPadding()
                                        .padding(16.dp)
                                        .align(Alignment.TopStart)
                                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                            }

                            // Done Button for Arrange Mode
                            if (isArrangeModeEnabled) {
                                TextButton(
                                    onClick = { viewModel.toggleArrangeMode(false) },
                                    modifier = Modifier
                                        .statusBarsPadding()
                                        .padding(16.dp)
                                        .align(Alignment.TopEnd)
                                ) {
                                    Text("Done", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isArrangeModeEnabled) "Arrange Songs" else playlistName,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${localSongsInPlaylist.size} songs",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // Playlist Controls
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (localSongsInPlaylist.isNotEmpty()) {
                                        viewModel.playSong(localSongsInPlaylist.first(), localSongsInPlaylist)
                                        onSongClick()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Play All")
                            }
                            
                            IconButton(
                                onClick = { viewModel.toggleRepeatMode() }
                            ) {
                                Icon(
                                    imageVector = when (repeatMode) {
                                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                        else -> Icons.Default.Repeat
                                    },
                                    contentDescription = null, 
                                    tint = if (repeatMode == Player.REPEAT_MODE_OFF) Color(0xFF424242) else Color(0xFF4CAF50)
                                )
                            }
                        }
                    }

                    itemsIndexed(localSongsInPlaylist, key = { _, song -> song.id }) { index, song ->
                        val isDragging = draggedItemIndex == index
                        val itemHeightPx = measuredItemHeightPx
                        val targetDisplacement = when {
                            isDragging -> 0f
                            draggedItemIndex == null || targetIndex == null || itemHeightPx == 0f -> 0f
                            draggedItemIndex!! < targetIndex!! && index > draggedItemIndex!! && index <= targetIndex!! -> -itemHeightPx
                            draggedItemIndex!! > targetIndex!! && index < draggedItemIndex!! && index >= targetIndex!! -> itemHeightPx
                            else -> 0f
                        }

                        val itemTranslationY by animateFloatAsState(
                            targetValue = targetDisplacement,
                            label = "DragTranslation",
                            animationSpec = spring(stiffness = Spring.StiffnessLow)
                        )
                        val isGhostSlot = !isDragging && targetIndex == index

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .zIndex(if (isGhostSlot) 1f else 0f)
                                .onGloballyPositioned { 
                                    if (measuredItemHeightPx == 0f) measuredItemHeightPx = it.size.height.toFloat()
                                }
                                .graphicsLayer { translationY = itemTranslationY }
                        ) {
                            if (isGhostSlot) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(with(LocalDensity.current) { measuredItemHeightPx.toDp() })
                                        .graphicsLayer { translationY = -itemTranslationY }
                                        .padding(horizontal = 24.dp, vertical = 8.dp)
                                        .border(
                                            width = 2.dp,
                                            brush = Brush.linearGradient(colors = listOf(Color(0xFFFF4081).copy(alpha = 0.5f), Color(0xFFFF4081).copy(alpha = 0.2f))),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("DROP SONG HERE", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF4081).copy(alpha = 0.6f), letterSpacing = 2.sp))
                                }
                            }

                            Box(modifier = Modifier.graphicsLayer { alpha = if (isDragging) 0f else 1f }) {
                                SongListItem(
                                    song = song,
                                    onPlayClick = {
                                        if (isSelectionMode) viewModel.toggleSongSelection(song.id)
                                        else { viewModel.playSong(song, localSongsInPlaylist); onSongClick() }
                                    },
                                    onFavoriteToggle = { viewModel.updateFavorite(song, !song.isFavorite) },
                                    onDelete = { if (playlistId == -1) viewModel.updateFavorite(song, false) else viewModel.removeSongFromPlaylist(song.id, playlistId) },
                                    isSelected = selectedSongIds.contains(song.id),
                                    onLongClick = { viewModel.toggleSelectionMode(true); viewModel.toggleSongSelection(song.id) },
                                    selectionMode = isSelectionMode,
                                    isPlaying = currentSong?.id == song.id,
                                    isArrangeMode = isArrangeModeEnabled,
                                    isDragging = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Overlay
        activeDraggedItem?.let { draggedItem ->
            Box(modifier = Modifier.fillMaxWidth().offset { IntOffset(0, (currentDragY - itemTouchOffset).roundToInt()) }.zIndex(100f)) {
                val itemScale by animateFloatAsState(targetValue = 1.1f, label = "FloatingScale", animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                Box(modifier = Modifier.graphicsLayer { scaleX = itemScale; scaleY = itemScale; shadowElevation = 32.dp.toPx(); shape = RoundedCornerShape(20.dp); clip = true }) {
                    SongListItem(song = draggedItem, onPlayClick = {}, onFavoriteToggle = {}, onDelete = {}, isArrangeMode = true, isDragging = true)
                }
            }
        }
    }
}
